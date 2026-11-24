create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  volunteer boolean := coalesce(new.raw_user_meta_data ->> 'account_type' = 'VOLUNTEER', false);
  requested_name text := nullif(btrim(new.raw_user_meta_data ->> 'display_name'), '');
begin
  insert into public.accounts(id, email, display_name, role, status)
  values (
    new.id,
    coalesce(new.email, new.id::text || '@unverified.local'),
    case
      when requested_name is not null and length(requested_name) between 2 and 120 then requested_name
      when length(split_part(coalesce(new.email, ''), '@', 1)) between 2 and 120
        then split_part(new.email, '@', 1)
      else 'AafatLink User'
    end,
    case when volunteer then 'VOLUNTEER'::public.app_role else 'REQUESTER'::public.app_role end,
    'ACTIVE'
  );
  if volunteer then
    insert into public.volunteer_profiles(account_id) values (new.id);
  end if;
  return new;
end;
$$;

revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

