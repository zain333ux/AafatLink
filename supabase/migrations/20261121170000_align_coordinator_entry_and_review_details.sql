-- Close UC01 coordinator-entry and UC03 review-detail gaps from the reviewed artifacts.

alter table public.assistance_requests
  alter column requester_account_id drop not null,
  add column requester_contact_name text,
  add column requester_contact_no text,
  add constraint request_contact_snapshot_pair check (
    (requester_contact_name is null and requester_contact_no is null)
    or (
      length(btrim(requester_contact_name)) between 2 and 120
      and requester_contact_no ~ '^[+0-9][0-9 +()\-]{6,24}$'
    )
  );

update public.assistance_requests request
set requester_contact_name = account.display_name,
    requester_contact_no = account.phone
from public.accounts account
where account.id = request.requester_account_id
  and account.phone is not null
  and request.requester_contact_name is null;

create or replace function public.record_coordinator_assistance_request(
  p_operation_id uuid,
  p_requester_contact_name text,
  p_requester_contact_no text,
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
  existing_operation public.workflow_operations%rowtype;
  new_request_id uuid;
  duplicate_found boolean;
  evidence_count integer := 0;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator permission required' using errcode = '42501';
  end if;
  if p_requester_contact_name is null
      or length(btrim(p_requester_contact_name)) not between 2 and 120 then
    raise exception 'Requester name is required' using errcode = '22023';
  end if;
  if p_requester_contact_no is null
      or btrim(p_requester_contact_no) !~ '^[+0-9][0-9 +()\-]{6,24}$' then
    raise exception 'A valid requester contact number is required' using errcode = '22023';
  end if;

  select * into existing_operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if existing_operation.actor_account_id <> actor_id
        or existing_operation.operation_type <> 'RECORD_COORDINATOR_REQUEST' then
      raise exception 'Operation ID belongs to another action' using errcode = '42501';
    end if;
    if existing_operation.status = 'COMMITTED' then return existing_operation.result; end if;
    if existing_operation.status <> 'PENDING' then
      raise exception 'Operation cannot be retried' using errcode = '55000';
    end if;
  else
    insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
    values (p_operation_id, actor_id, 'RECORD_COORDINATOR_REQUEST');
  end if;

  select exists (
    select 1 from public.assistance_requests candidate
    where lower(btrim(candidate.disaster_type)) = lower(btrim(p_disaster_type))
      and lower(btrim(candidate.location_address)) = lower(btrim(p_location_address))
      and candidate.status not in ('CLOSED', 'REJECTED')
  ) into duplicate_found;

  insert into public.assistance_requests(
    requester_account_id, recorded_by_account_id, operation_id, requester_contact_name,
    requester_contact_no, disaster_type, disaster_type_other, location_address,
    description, required_assistance, affected_person_details, source, possible_duplicate
  ) values (
    null, actor_id, p_operation_id, btrim(p_requester_contact_name), btrim(p_requester_contact_no),
    lower(btrim(p_disaster_type)),
    case when lower(btrim(p_disaster_type)) = 'other' then btrim(p_disaster_type_other) end,
    btrim(p_location_address), btrim(p_description), btrim(p_required_assistance),
    btrim(p_affected_person_details), 'COORDINATOR_ENTERED', duplicate_found
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

-- Coordinator/Admin-only review context. It returns no credentials, email addresses or storage paths.
create or replace function public.get_request_review_details(p_request_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  result jsonb;
begin
  if auth.uid() is null or private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Request review permission required' using errcode = '42501';
  end if;

  select jsonb_build_object(
    'requester_name', coalesce(request.requester_contact_name, account.display_name, 'Unregistered requester'),
    'requester_contact', coalesce(request.requester_contact_no, account.phone, 'Contact unavailable'),
    'evidence', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', evidence.id,
        'file_name', evidence.original_filename,
        'content_type', evidence.content_type,
        'size_bytes', evidence.size_bytes,
        'uploaded_at', evidence.created_at
      ) order by evidence.created_at)
      from public.request_evidence evidence
      where evidence.request_id = request.id and evidence.state = 'COMMITTED'
    ), '[]'::jsonb),
    'possible_duplicates', coalesce((
      select jsonb_agg(jsonb_build_object(
        'request_id', duplicate.id,
        'status', duplicate.status,
        'priority', duplicate.priority,
        'submitted_at', duplicate.submitted_at
      ) order by duplicate.submitted_at)
      from (
        select candidate.id, candidate.status, candidate.priority, candidate.submitted_at
        from public.assistance_requests candidate
        where candidate.id <> request.id
          and lower(btrim(candidate.disaster_type)) = lower(btrim(request.disaster_type))
          and lower(btrim(candidate.location_address)) = lower(btrim(request.location_address))
          and candidate.status not in ('CLOSED', 'REJECTED')
        order by candidate.submitted_at
        limit 20
      ) duplicate
    ), '[]'::jsonb)
  ) into result
  from public.assistance_requests request
  left join public.accounts account on account.id = request.requester_account_id
  where request.id = p_request_id;

  if result is null then
    raise exception 'Request not found' using errcode = 'P0002';
  end if;
  return result;
end;
$$;

revoke all on function public.record_coordinator_assistance_request(
  uuid,text,text,text,text,text,text,text,text
) from public, anon;
revoke all on function public.get_request_review_details(uuid) from public, anon;
grant execute on function public.record_coordinator_assistance_request(
  uuid,text,text,text,text,text,text,text,text
) to authenticated;
grant execute on function public.get_request_review_details(uuid) to authenticated;
