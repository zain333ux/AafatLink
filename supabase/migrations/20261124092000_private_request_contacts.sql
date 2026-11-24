-- Keep identifying contact snapshots outside assignment-visible request rows.
create table private.request_contacts (
  request_id uuid primary key references public.assistance_requests(id) deferrable initially deferred,
  name text,
  contact text
);
revoke all on private.request_contacts from public, anon, authenticated;
insert into private.request_contacts
select id, requester_contact_name, requester_contact_no from public.assistance_requests;

drop trigger assistance_requests_contact_snapshot on public.assistance_requests;
update public.assistance_requests set requester_contact_name = null, requester_contact_no = null;

create or replace function private.populate_request_contact_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  contact_name text := new.requester_contact_name;
  contact_value text := new.requester_contact_no;
begin
  if new.requester_account_id is not null then
    select display_name, coalesce(phone, email) into contact_name, contact_value
    from public.accounts where id = new.requester_account_id;
  end if;
  insert into private.request_contacts values (new.id, contact_name, contact_value)
    on conflict (request_id) do update set name = excluded.name, contact = excluded.contact;
  new.requester_contact_name := null;
  new.requester_contact_no := null;
  return new;
end;
$$;
revoke all on function private.populate_request_contact_snapshot() from public, anon, authenticated;
create trigger assistance_requests_contact_snapshot before insert or update of requester_account_id
on public.assistance_requests for each row execute function private.populate_request_contact_snapshot();
alter table public.assistance_requests add constraint contact_snapshot_private
check (requester_contact_name is null and requester_contact_no is null);

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
  if auth.uid() is null or private.current_app_role() is distinct from 'COORDINATOR' then
    raise exception 'Request review permission required' using errcode = '42501';
  end if;

  select jsonb_build_object(
    'requester_name', coalesce(contact.name, 'Requester name unavailable'),
    'requester_contact', coalesce(contact.contact, 'Contact unavailable'),
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
  left join private.request_contacts contact on contact.request_id = request.id
  where request.id = p_request_id;

  if result is null then
    raise exception 'Request not found' using errcode = 'P0002';
  end if;
  return result;
end;
$$;

revoke all on function public.get_request_review_details(uuid) from public, anon;
grant execute on function public.get_request_review_details(uuid) to authenticated;
