-- Auth identities become least-privileged Requester accounts. User metadata is
-- used only for display text, never for authorization.
create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
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
    'REQUESTER',
    'ACTIVE'
  );
  return new;
end;
$$;

revoke all on function private.handle_new_auth_user() from public, anon, authenticated;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function private.handle_new_auth_user();

create or replace function public.register_as_volunteer(p_operation_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  actor_role public.app_role;
  operation public.workflow_operations%rowtype;
  operation_result jsonb;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  actor_role := private.current_app_role();
  if actor_role not in ('REQUESTER', 'VOLUNTEER') then
    raise exception 'Privileged accounts cannot use public volunteer registration' using errcode = '42501';
  end if;

  select * into operation from public.workflow_operations where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'REGISTER_VOLUNTEER' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'REGISTER_VOLUNTEER');
  update public.accounts set role = 'VOLUNTEER', version = version + 1, updated_at = now()
  where id = actor_id and role in ('REQUESTER', 'VOLUNTEER');
  insert into public.volunteer_profiles(account_id)
  values (actor_id)
  on conflict (account_id) do nothing;

  operation_result := jsonb_build_object(
    'account_id', actor_id,
    'role', 'VOLUNTEER',
    'approval_status', 'PENDING'
  );
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

revoke all on function public.register_as_volunteer(uuid) from public, anon;
grant execute on function public.register_as_volunteer(uuid) to authenticated;
