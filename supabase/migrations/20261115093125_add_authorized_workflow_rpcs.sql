-- Public RPCs are SECURITY DEFINER only where direct table writes are deliberately
-- unavailable. Every function derives actor identity from auth.uid(), validates
-- authorization, pins an empty search_path and has restricted execution grants.

create or replace function public.submit_assistance_request(
  p_operation_id uuid,
  p_requester_account_id uuid,
  p_disaster_type text,
  p_location_address text,
  p_latitude numeric,
  p_longitude numeric,
  p_assistance_details text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  actor_role public.app_role;
  existing_operation public.workflow_operations%rowtype;
  new_request_id uuid;
  operation_result jsonb;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  actor_role := private.current_app_role();
  if actor_role is null then raise exception 'Active AafatLink account required' using errcode = '42501'; end if;
  if p_requester_account_id <> actor_id and actor_role <> 'COORDINATOR' then
    raise exception 'Only a coordinator may record a request for another requester' using errcode = '42501';
  end if;
  if not exists (select 1 from public.accounts where id = p_requester_account_id and status = 'ACTIVE') then
    raise exception 'Requester account is not active' using errcode = '23503';
  end if;

  select * into existing_operation from public.workflow_operations where operation_id = p_operation_id for update;
  if found then
    if existing_operation.actor_account_id <> actor_id or existing_operation.operation_type <> 'SUBMIT_REQUEST' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if existing_operation.status = 'COMMITTED' then return existing_operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'SUBMIT_REQUEST');

  insert into public.assistance_requests(
    requester_account_id, recorded_by_account_id, operation_id, disaster_type,
    location_address, latitude, longitude, assistance_details
  ) values (
    p_requester_account_id, actor_id, p_operation_id, p_disaster_type,
    p_location_address, p_latitude, p_longitude, p_assistance_details
  ) returning id into new_request_id;

  operation_result := jsonb_build_object('request_id', new_request_id, 'status', 'PENDING_VERIFICATION');
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.commit_request_evidence(
  p_operation_id uuid,
  p_evidence_id uuid,
  p_request_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  evidence public.request_evidence%rowtype;
  request_record public.assistance_requests%rowtype;
  operation_result jsonb;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  select * into operation from public.workflow_operations where operation_id = p_operation_id for update;
  if not found or operation.actor_account_id <> actor_id then
    raise exception 'Evidence operation is unavailable' using errcode = '42501';
  end if;
  if operation.status = 'COMMITTED' then return operation.result; end if;
  if operation.status <> 'PENDING' then raise exception 'Evidence operation is not pending' using errcode = '55000'; end if;

  select * into evidence from public.request_evidence where id = p_evidence_id for update;
  if not found or evidence.operation_id <> p_operation_id or evidence.uploaded_by_account_id <> actor_id then
    raise exception 'Staged evidence is unavailable' using errcode = '42501';
  end if;
  select * into request_record from public.assistance_requests where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.requester_account_id <> actor_id and private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Evidence cannot be attached to this request' using errcode = '42501';
  end if;

  update public.request_evidence
  set request_id = p_request_id, state = 'COMMITTED', committed_at = now()
  where id = p_evidence_id;
  operation_result := jsonb_build_object('evidence_id', p_evidence_id, 'request_id', p_request_id, 'state', 'COMMITTED');
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.decide_request_verification(
  p_operation_id uuid,
  p_request_id uuid,
  p_expected_version bigint,
  p_decision public.verification_decision,
  p_reason text
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
  next_status public.request_status;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  select * into operation from public.workflow_operations where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'VERIFY_REQUEST' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;
  if p_decision <> 'VERIFIED' and nullif(btrim(p_reason), '') is null then
    raise exception 'A reason is required for this decision' using errcode = '23514';
  end if;

  select * into request_record from public.assistance_requests where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.status <> 'PENDING_VERIFICATION' or request_record.version <> p_expected_version then
    raise exception 'Request changed or is no longer pending verification' using errcode = '40001';
  end if;
  next_status := p_decision::text::public.request_status;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'VERIFY_REQUEST');
  insert into public.request_verifications(request_id, coordinator_account_id, decision, reason, reviewed_request_version)
  values (p_request_id, actor_id, p_decision, nullif(btrim(p_reason), ''), p_expected_version);
  update public.assistance_requests set
    status = next_status,
    information_request_reason = case when next_status = 'ADDITIONAL_INFORMATION_REQUIRED' then btrim(p_reason) end,
    rejection_reason = case when next_status = 'REJECTED' then btrim(p_reason) end,
    version = version + 1,
    updated_at = now()
  where id = p_request_id;
  operation_result := jsonb_build_object('request_id', p_request_id, 'status', next_status, 'version', p_expected_version + 1);
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

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
  if p_suggested_priority <> p_selected_priority and nullif(btrim(p_override_justification), '') is null then
    raise exception 'Priority override justification is required' using errcode = '23514';
  end if;

  select * into request_record from public.assistance_requests where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.status <> 'VERIFIED' or request_record.version <> p_expected_version then
    raise exception 'Request changed or is not verified' using errcode = '40001';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'PRIORITIZE_REQUEST');
  insert into public.priority_assessments(
    request_id, coordinator_account_id, suggested_priority, selected_priority,
    override_justification, reviewed_request_version
  ) values (
    p_request_id, actor_id, p_suggested_priority, p_selected_priority,
    nullif(btrim(p_override_justification), ''), p_expected_version
  );
  update public.assistance_requests
  set priority = p_selected_priority, version = version + 1, updated_at = now()
  where id = p_request_id;
  operation_result := jsonb_build_object('request_id', p_request_id, 'priority', p_selected_priority, 'version', p_expected_version + 1);
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.assign_volunteer(
  p_operation_id uuid,
  p_request_id uuid,
  p_volunteer_account_id uuid,
  p_expected_request_version bigint,
  p_scheduled_start_at timestamptz,
  p_scheduled_end_at timestamptz
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
  profile public.volunteer_profiles%rowtype;
  new_assignment_id uuid;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  if (p_scheduled_start_at is null) <> (p_scheduled_end_at is null)
    or (p_scheduled_start_at is not null and p_scheduled_start_at >= p_scheduled_end_at) then
    raise exception 'Assignment schedule is invalid' using errcode = '22007';
  end if;
  select * into operation from public.workflow_operations where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'ASSIGN_VOLUNTEER' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into request_record from public.assistance_requests where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.status <> 'VERIFIED' or request_record.priority is null
    or request_record.version <> p_expected_request_version then
    raise exception 'Request changed, is not verified, or has no priority' using errcode = '40001';
  end if;
  select * into profile from public.volunteer_profiles where account_id = p_volunteer_account_id for update;
  if not found or profile.approval_status <> 'APPROVED' then
    raise exception 'Volunteer is not approved' using errcode = '42501';
  end if;
  if p_scheduled_start_at is not null and (
    (profile.available_from is not null and profile.available_from > p_scheduled_start_at)
    or (profile.available_until is not null and profile.available_until < p_scheduled_end_at)
  ) then
    raise exception 'Assignment is outside volunteer availability' using errcode = '23514';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'ASSIGN_VOLUNTEER');
  insert into public.volunteer_assignments(
    request_id, volunteer_account_id, assigned_by_account_id, operation_id,
    scheduled_start_at, scheduled_end_at
  ) values (
    p_request_id, p_volunteer_account_id, actor_id, p_operation_id,
    p_scheduled_start_at, p_scheduled_end_at
  ) returning id into new_assignment_id;
  update public.assistance_requests
  set status = 'ASSIGNED', version = version + 1, updated_at = now()
  where id = p_request_id;
  operation_result := jsonb_build_object(
    'assignment_id', new_assignment_id, 'request_id', p_request_id,
    'status', 'PENDING_ACCEPTANCE', 'request_version', p_expected_request_version + 1
  );
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

revoke all on function public.submit_assistance_request(uuid, uuid, text, text, numeric, numeric, text) from public, anon;
revoke all on function public.commit_request_evidence(uuid, uuid, uuid) from public, anon;
revoke all on function public.decide_request_verification(uuid, uuid, bigint, public.verification_decision, text) from public, anon;
revoke all on function public.record_priority_assessment(uuid, uuid, bigint, public.request_priority, public.request_priority, text) from public, anon;
revoke all on function public.assign_volunteer(uuid, uuid, uuid, bigint, timestamptz, timestamptz) from public, anon;
grant execute on function public.submit_assistance_request(uuid, uuid, text, text, numeric, numeric, text) to authenticated;
grant execute on function public.commit_request_evidence(uuid, uuid, uuid) to authenticated;
grant execute on function public.decide_request_verification(uuid, uuid, bigint, public.verification_decision, text) to authenticated;
grant execute on function public.record_priority_assessment(uuid, uuid, bigint, public.request_priority, public.request_priority, text) to authenticated;
grant execute on function public.assign_volunteer(uuid, uuid, uuid, bigint, timestamptz, timestamptz) to authenticated;
