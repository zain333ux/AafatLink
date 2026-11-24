-- UC04 permits reprioritization while Verified, Assigned or In Progress and audits the previous level.
alter table public.priority_assessments
  add column previous_priority public.request_priority;

create or replace function public.record_priority_assessment(
  p_operation_id uuid,
  p_request_id uuid,
  p_expected_version bigint,
  p_suggested_priority public.request_priority,
  p_selected_priority public.request_priority,
  p_override_justification text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  request_record public.assistance_requests%rowtype;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  select * into operation from public.workflow_operations where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'PRIORITIZE_REQUEST' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;
  if p_suggested_priority <> p_selected_priority
    and nullif(btrim(p_override_justification), '') is null then
    raise exception 'Priority override justification is required' using errcode = '23514';
  end if;

  select * into request_record from public.assistance_requests
  where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.status not in ('VERIFIED', 'ASSIGNED', 'IN_PROGRESS')
    or request_record.version <> p_expected_version then
    raise exception 'Request changed or cannot be prioritized in its current status' using errcode = '40001';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'PRIORITIZE_REQUEST');
  insert into public.priority_assessments(
    request_id, coordinator_account_id, previous_priority, suggested_priority,
    selected_priority, override_justification, reviewed_request_version
  ) values (
    p_request_id, actor_id, request_record.priority, p_suggested_priority,
    p_selected_priority, nullif(btrim(p_override_justification), ''), p_expected_version
  );
  update public.assistance_requests
  set priority = p_selected_priority, version = version + 1, updated_at = now()
  where id = p_request_id;
  operation_result := jsonb_build_object(
    'request_id', p_request_id,
    'previous_priority', request_record.priority,
    'priority', p_selected_priority,
    'status', request_record.status,
    'version', p_expected_version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

revoke all on function public.record_priority_assessment(
  uuid, uuid, bigint, public.request_priority, public.request_priority, text
) from public, anon;
grant execute on function public.record_priority_assessment(
  uuid, uuid, bigint, public.request_priority, public.request_priority, text
) to authenticated;
