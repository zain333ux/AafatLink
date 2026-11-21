-- Make UC03 detail reads invoker-safe by snapshotting requester contact on the request row.

create or replace function private.populate_request_contact_snapshot()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
  account_name text;
  account_contact text;
begin
  if new.requester_account_id is not null
      and (new.requester_contact_name is null or new.requester_contact_no is null) then
    select account.display_name, coalesce(account.phone, account.email)
    into account_name, account_contact
    from public.accounts account
    where account.id = new.requester_account_id;
    new.requester_contact_name := coalesce(new.requester_contact_name, account_name);
    new.requester_contact_no := coalesce(new.requester_contact_no, account_contact);
  end if;
  return new;
end;
$$;

revoke all on function private.populate_request_contact_snapshot() from public, anon, authenticated;

drop trigger if exists assistance_requests_contact_snapshot on public.assistance_requests;
create trigger assistance_requests_contact_snapshot
before insert or update of requester_account_id on public.assistance_requests
for each row execute function private.populate_request_contact_snapshot();

update public.assistance_requests request
set requester_contact_name = account.display_name,
    requester_contact_no = coalesce(account.phone, account.email)
from public.accounts account
where account.id = request.requester_account_id
  and (request.requester_contact_name is null or request.requester_contact_no is null);

create or replace function public.get_request_review_details(p_request_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  result jsonb;
begin
  if auth.uid() is null or private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Request review permission required' using errcode = '42501';
  end if;

  select jsonb_build_object(
    'requester_name', coalesce(request.requester_contact_name, 'Requester name unavailable'),
    'requester_contact', coalesce(request.requester_contact_no, 'Contact unavailable'),
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
  where request.id = p_request_id;

  if result is null then
    raise exception 'Request not found' using errcode = 'P0002';
  end if;
  return result;
end;
$$;

revoke all on function public.get_request_review_details(uuid) from public, anon;
grant execute on function public.get_request_review_details(uuid) to authenticated;
