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
  if actor_id is null or private.current_app_role() is distinct from 'COORDINATOR' then
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
  if (request_record.status <> 'PENDING_VERIFICATION' and not
      (request_record.status = 'VERIFIED' and p_decision = 'ADDITIONAL_INFORMATION_REQUIRED')) or request_record.version <> p_expected_version then
    raise exception 'Request changed or is no longer pending verification' using errcode = '40001';
  end if;
  next_status := p_decision::text::public.request_status;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'VERIFY_REQUEST');
  insert into public.request_verifications(request_id, coordinator_account_id, decision, reason, reviewed_request_version)
  values (p_request_id, actor_id, p_decision, nullif(btrim(p_reason), ''), p_expected_version);
  update public.assistance_requests set
    status = next_status,
    priority = case when next_status = 'ADDITIONAL_INFORMATION_REQUIRED' then null else priority end,
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

