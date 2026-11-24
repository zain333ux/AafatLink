create or replace function public.submit_assistance_request(
  p_operation_id uuid,
  p_requester_account_id uuid,
  p_disaster_type text,
  p_disaster_type_other text,
  p_location_address text,
  p_description text,
  p_required_assistance text,
  p_affected_person_details text
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
  duplicate_found boolean;
  evidence_count integer := 0;
  operation_result jsonb;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  actor_role := private.current_app_role();
  if actor_role is null or actor_role not in ('REQUESTER','COORDINATOR') then
    raise exception 'Requester or coordinator access required' using errcode = '42501'; end if;
  if p_requester_account_id <> actor_id and actor_role <> 'COORDINATOR' then
    raise exception 'Only a coordinator may record a request for another requester' using errcode = '42501';
  end if;
  if not exists (select 1 from public.accounts where id = p_requester_account_id and status = 'ACTIVE') then
    raise exception 'Requester account is not active' using errcode = '23503';
  end if;

  select * into existing_operation
  from public.workflow_operations
  where operation_id = p_operation_id
  for update;
  if found then
    if existing_operation.actor_account_id <> actor_id
      or existing_operation.operation_type <> 'SUBMIT_REQUEST' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if existing_operation.status = 'COMMITTED' then return existing_operation.result; end if;
    if existing_operation.status <> 'PENDING' then
      raise exception 'Operation cannot be retried' using errcode = '55000';
    end if;
  else
    insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
    values (p_operation_id, actor_id, 'SUBMIT_REQUEST');
  end if;

  select exists (
    select 1
    from public.assistance_requests candidate
    where lower(btrim(candidate.disaster_type)) = lower(btrim(p_disaster_type))
      and lower(btrim(candidate.location_address)) = lower(btrim(p_location_address))
      and candidate.status not in ('CLOSED', 'REJECTED')
  ) into duplicate_found;

  insert into public.assistance_requests(
    requester_account_id, recorded_by_account_id, operation_id, disaster_type,
    disaster_type_other, location_address, description, required_assistance,
    affected_person_details, source, possible_duplicate
  ) values (
    p_requester_account_id, actor_id, p_operation_id, lower(btrim(p_disaster_type)),
    case when lower(btrim(p_disaster_type)) = 'other' then btrim(p_disaster_type_other) end,
    btrim(p_location_address), btrim(p_description), btrim(p_required_assistance),
    btrim(p_affected_person_details),
    case when p_requester_account_id = actor_id then 'REQUESTER_ENTERED'::public.request_source else 'COORDINATOR_ENTERED' end,
    duplicate_found
  ) returning id into new_request_id;

  update public.request_evidence
  set request_id = new_request_id, state = 'COMMITTED', committed_at = now()
  where operation_id = p_operation_id
    and uploaded_by_account_id = actor_id
    and state = 'STAGED';
  get diagnostics evidence_count = row_count;

  operation_result := jsonb_build_object(
    'request_id', new_request_id,
    'status', 'PENDING_VERIFICATION',
    'possible_duplicate', duplicate_found,
    'evidence_count', evidence_count
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

-- Request rows are created by the authorized submission functions.
revoke insert on public.assistance_requests from authenticated;
