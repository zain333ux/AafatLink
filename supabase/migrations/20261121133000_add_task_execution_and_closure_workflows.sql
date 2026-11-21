-- UC09, UC10 and UC06: append-only progress, repeatable completion attempts after
-- rework, and atomic coordinator closure review.

alter table public.task_progress_updates
  drop constraint if exists task_progress_updates_note_check;
alter table public.task_progress_updates
  add constraint task_progress_note_length check (length(btrim(note)) between 1 and 500);

alter table public.task_completions
  drop constraint if exists task_completions_assignment_id_key;
alter table public.task_completions
  add column if not exists attempt_no integer not null default 1 check (attempt_no > 0);
create unique index if not exists task_completions_assignment_attempt_idx
  on public.task_completions (assignment_id, attempt_no);
create index if not exists task_completions_assignment_time_idx
  on public.task_completions (assignment_id, completed_at desc);

alter table public.request_evidence
  add column if not exists task_completion_id uuid
    references public.task_completions(id) on delete restrict;
create index if not exists request_evidence_completion_idx
  on public.request_evidence (task_completion_id)
  where task_completion_id is not null;

create or replace function public.begin_task_operation(p_operation_id uuid, p_operation_type text)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  existing public.workflow_operations%rowtype;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  if p_operation_type <> 'COMPLETE_TASK' then
    raise exception 'Unsupported task operation type' using errcode = '22023';
  end if;
  select * into existing from public.workflow_operations
  where operation_id = p_operation_id;
  if found then
    if existing.actor_account_id <> actor_id or existing.operation_type <> p_operation_type then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    return;
  end if;
  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, p_operation_type);
end;
$$;

create or replace function public.reconcile_workflow_operation(p_operation_id uuid)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if not found then return jsonb_build_object('status', 'MISSING'); end if;
  if operation.actor_account_id <> actor_id then
    raise exception 'Operation is unavailable' using errcode = '42501';
  end if;
  return jsonb_build_object('status', operation.status, 'result', operation.result);
end;
$$;

create or replace function public.abandon_task_operation(p_operation_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if not found then return false; end if;
  if operation.actor_account_id <> actor_id or operation.operation_type <> 'COMPLETE_TASK' then
    raise exception 'Operation is unavailable' using errcode = '42501';
  end if;
  if operation.status <> 'PENDING' then return false; end if;
  if exists (select 1 from public.task_completions where operation_id = p_operation_id)
      or exists (select 1 from public.request_evidence
          where operation_id = p_operation_id and state = 'COMMITTED') then
    return false;
  end if;
  update public.request_evidence
  set state = 'ORPHAN_CANDIDATE'
  where operation_id = p_operation_id and state = 'STAGED';
  update public.workflow_operations
  set status = 'FAILED', failure_code = 'CLIENT_ABANDONED', completed_at = now()
  where operation_id = p_operation_id;
  return true;
end;
$$;

create or replace function public.record_task_progress(
  p_operation_id uuid,
  p_update_id uuid,
  p_assignment_id uuid,
  p_request_id uuid,
  p_expected_assignment_version bigint,
  p_expected_request_version bigint,
  p_note text,
  p_percentage smallint default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  assignment public.volunteer_assignments%rowtype;
  request_record public.assistance_requests%rowtype;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  if p_note is null or length(btrim(p_note)) not between 1 and 500 then
    raise exception 'Progress note must contain 1 to 500 characters' using errcode = '22023';
  end if;
  if p_percentage is not null and p_percentage not between 0 and 99 then
    raise exception 'Progress percentage must be from 0 to 99' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'RECORD_PROGRESS' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into assignment from public.volunteer_assignments
  where id = p_assignment_id for update;
  if not found or assignment.request_id <> p_request_id
      or assignment.volunteer_account_id <> actor_id then
    raise exception 'Task does not belong to this volunteer' using errcode = '42501';
  end if;
  if assignment.status not in ('ACCEPTED', 'IN_PROGRESS')
      or assignment.version <> p_expected_assignment_version then
    raise exception 'Task changed or does not accept progress updates' using errcode = '40001';
  end if;
  select * into request_record from public.assistance_requests
  where id = p_request_id for update;
  if not found or request_record.status not in ('ASSIGNED', 'IN_PROGRESS')
      or request_record.version <> p_expected_request_version then
    raise exception 'Request changed or does not accept progress updates' using errcode = '40001';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'RECORD_PROGRESS');
  insert into public.task_progress_updates(
    id, assignment_id, volunteer_account_id, note, percentage, operation_id
  ) values (
    p_update_id, p_assignment_id, actor_id, btrim(p_note), p_percentage, p_operation_id
  );
  update public.volunteer_assignments
  set status = 'IN_PROGRESS', version = version + 1, updated_at = now()
  where id = p_assignment_id;
  update public.assistance_requests
  set status = 'IN_PROGRESS', version = version + 1, updated_at = now()
  where id = p_request_id;

  operation_result := jsonb_build_object(
    'update_id', p_update_id, 'assignment_id', p_assignment_id,
    'assignment_status', 'IN_PROGRESS', 'request_id', p_request_id,
    'request_status', 'IN_PROGRESS',
    'assignment_version', assignment.version + 1,
    'request_version', request_record.version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.complete_assistance_task(
  p_operation_id uuid,
  p_completion_id uuid,
  p_assignment_id uuid,
  p_request_id uuid,
  p_expected_assignment_version bigint,
  p_expected_request_version bigint,
  p_remarks text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  assignment public.volunteer_assignments%rowtype;
  request_record public.assistance_requests%rowtype;
  next_attempt integer;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  if p_remarks is null or length(btrim(p_remarks)) not between 1 and 4000 then
    raise exception 'Completion remarks must contain 1 to 4000 characters' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'COMPLETE_TASK' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    if operation.status <> 'PENDING' then
      raise exception 'Operation can no longer be completed' using errcode = '55000';
    end if;
  else
    insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
    values (p_operation_id, actor_id, 'COMPLETE_TASK');
  end if;

  select * into assignment from public.volunteer_assignments
  where id = p_assignment_id for update;
  if not found or assignment.request_id <> p_request_id
      or assignment.volunteer_account_id <> actor_id then
    raise exception 'Task does not belong to this volunteer' using errcode = '42501';
  end if;
  if assignment.status not in ('ACCEPTED', 'IN_PROGRESS')
      or assignment.version <> p_expected_assignment_version then
    raise exception 'Task changed or cannot be completed' using errcode = '40001';
  end if;
  select * into request_record from public.assistance_requests
  where id = p_request_id for update;
  if not found or request_record.status not in ('ASSIGNED', 'IN_PROGRESS')
      or request_record.version <> p_expected_request_version then
    raise exception 'Request changed or cannot be completed' using errcode = '40001';
  end if;
  if exists (select 1 from public.request_evidence evidence
      where evidence.operation_id = p_operation_id
        and (evidence.uploaded_by_account_id <> actor_id or evidence.state <> 'STAGED')) then
    raise exception 'Completion evidence is not valid for this operation' using errcode = '42501';
  end if;

  select coalesce(max(completion.attempt_no), 0) + 1 into next_attempt
  from public.task_completions completion
  where completion.assignment_id = p_assignment_id;
  insert into public.task_completions(
    id, assignment_id, volunteer_account_id, remarks, operation_id, attempt_no
  ) values (
    p_completion_id, p_assignment_id, actor_id, btrim(p_remarks), p_operation_id, next_attempt
  );
  update public.request_evidence
  set request_id = p_request_id, task_completion_id = p_completion_id,
      state = 'COMMITTED', committed_at = now()
  where operation_id = p_operation_id and uploaded_by_account_id = actor_id and state = 'STAGED';
  update public.volunteer_assignments
  set status = 'AWAITING_CLOSURE_REVIEW', version = version + 1, updated_at = now()
  where id = p_assignment_id;
  update public.assistance_requests
  set status = 'AWAITING_CLOSURE_REVIEW', version = version + 1, updated_at = now()
  where id = p_request_id;

  operation_result := jsonb_build_object(
    'completion_id', p_completion_id, 'assignment_id', p_assignment_id,
    'assignment_status', 'AWAITING_CLOSURE_REVIEW', 'request_id', p_request_id,
    'request_status', 'AWAITING_CLOSURE_REVIEW',
    'assignment_version', assignment.version + 1,
    'request_version', request_record.version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.review_request_closure(
  p_operation_id uuid,
  p_review_id uuid,
  p_request_id uuid,
  p_assignment_id uuid,
  p_expected_assignment_version bigint,
  p_expected_request_version bigint,
  p_decision public.closure_decision,
  p_remarks text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  assignment public.volunteer_assignments%rowtype;
  request_record public.assistance_requests%rowtype;
  target_assignment_status public.assignment_status;
  target_request_status public.request_status;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  if p_decision = 'RETURNED_FOR_REWORK' and nullif(btrim(p_remarks), '') is null then
    raise exception 'A rework reason is required' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'REVIEW_CLOSURE' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into assignment from public.volunteer_assignments
  where id = p_assignment_id for update;
  if not found or assignment.request_id <> p_request_id
      or assignment.status <> 'AWAITING_CLOSURE_REVIEW'
      or assignment.version <> p_expected_assignment_version then
    raise exception 'Assignment changed or is not awaiting closure review' using errcode = '40001';
  end if;
  select * into request_record from public.assistance_requests
  where id = p_request_id for update;
  if not found or request_record.status <> 'AWAITING_CLOSURE_REVIEW'
      or request_record.version <> p_expected_request_version then
    raise exception 'Request changed or is not awaiting closure review' using errcode = '40001';
  end if;
  if not exists (select 1 from public.task_completions completion
      where completion.assignment_id = p_assignment_id) then
    raise exception 'Completion details are required before closure review' using errcode = '23514';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'REVIEW_CLOSURE');
  insert into public.closure_reviews(
    id, request_id, assignment_id, coordinator_account_id, decision, reason, operation_id
  ) values (
    p_review_id, p_request_id, p_assignment_id, actor_id, p_decision,
    nullif(btrim(p_remarks), ''), p_operation_id
  );
  if p_decision = 'APPROVED' then
    target_assignment_status := 'COMPLETED';
    target_request_status := 'CLOSED';
  else
    target_assignment_status := 'IN_PROGRESS';
    target_request_status := 'IN_PROGRESS';
  end if;
  update public.volunteer_assignments
  set status = target_assignment_status, version = version + 1, updated_at = now()
  where id = p_assignment_id;
  update public.assistance_requests
  set status = target_request_status, version = version + 1, updated_at = now()
  where id = p_request_id;

  operation_result := jsonb_build_object(
    'review_id', p_review_id, 'assignment_id', p_assignment_id,
    'assignment_status', target_assignment_status, 'request_id', p_request_id,
    'request_status', target_request_status,
    'assignment_version', assignment.version + 1,
    'request_version', request_record.version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

revoke all on function public.begin_task_operation(uuid, text) from public, anon;
revoke all on function public.reconcile_workflow_operation(uuid) from public, anon;
revoke all on function public.abandon_task_operation(uuid) from public, anon;
revoke all on function public.record_task_progress(uuid, uuid, uuid, uuid, bigint, bigint, text, smallint)
  from public, anon;
revoke all on function public.complete_assistance_task(uuid, uuid, uuid, uuid, bigint, bigint, text)
  from public, anon;
revoke all on function public.review_request_closure(uuid, uuid, uuid, uuid, bigint, bigint,
  public.closure_decision, text) from public, anon;
grant execute on function public.begin_task_operation(uuid, text) to authenticated;
grant execute on function public.reconcile_workflow_operation(uuid) to authenticated;
grant execute on function public.abandon_task_operation(uuid) to authenticated;
grant execute on function public.record_task_progress(uuid, uuid, uuid, uuid, bigint, bigint, text, smallint)
  to authenticated;
grant execute on function public.complete_assistance_task(uuid, uuid, uuid, uuid, bigint, bigint, text)
  to authenticated;
grant execute on function public.review_request_closure(uuid, uuid, uuid, uuid, bigint, bigint,
  public.closure_decision, text) to authenticated;
