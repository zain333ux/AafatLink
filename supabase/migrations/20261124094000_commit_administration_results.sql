create or replace function public.update_user_access(
  p_operation_id uuid,
  p_target_account_id uuid,
  p_expected_version bigint,
  p_role public.app_role,
  p_status public.account_status,
  p_volunteer_approval public.volunteer_approval_status,
  p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  target public.accounts%rowtype;
  existing_operation public.workflow_operations%rowtype;
  previous_approval public.volunteer_approval_status;
  before_state jsonb;
  after_state jsonb;
  operation_result jsonb;
  restricts_access boolean;
begin
  if actor_id is null or private.current_app_role() is distinct from 'ADMINISTRATOR' then
    raise exception 'Administrator permission required' using errcode = '42501';
  end if;
  if p_status = 'SUSPENDED' then
    raise exception 'Use Active or Deactivated status' using errcode = '22023';
  end if;
  if (p_role = 'VOLUNTEER' and p_volunteer_approval is null)
      or (p_role <> 'VOLUNTEER' and p_volunteer_approval is not null) then
    raise exception 'Role and volunteer approval are incompatible' using errcode = '22023';
  end if;

  select * into existing_operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if existing_operation.actor_account_id <> actor_id
        or existing_operation.operation_type <> 'UPDATE_USER_ACCESS' then
      raise exception 'Operation ID belongs to another action' using errcode = '42501';
    end if;
    if existing_operation.status = 'COMMITTED' then return existing_operation.result; end if;
    if existing_operation.status <> 'PENDING' then
      raise exception 'Operation cannot be retried' using errcode = '55000';
    end if;
  else
    insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
    values (p_operation_id, actor_id, 'UPDATE_USER_ACCESS');
  end if;

  select * into target from public.accounts where id = p_target_account_id for update;
  if not found then raise exception 'Target account no longer exists' using errcode = 'P0002'; end if;
  if target.version <> p_expected_version then
    raise exception 'Account changed; refresh and retry' using errcode = '40001';
  end if;
  if actor_id = target.id and p_role <> target.role then
    raise exception 'Administrators cannot change their own role' using errcode = '42501';
  end if;
  if target.role = 'ADMINISTRATOR' and target.status = 'ACTIVE'
      and (p_role <> 'ADMINISTRATOR' or p_status <> 'ACTIVE')
      and (select count(*) from public.accounts a
           where a.role = 'ADMINISTRATOR' and a.status = 'ACTIVE') <= 1 then
    raise exception 'The last active Administrator cannot be removed' using errcode = '23514';
  end if;

  select approval_status into previous_approval from public.volunteer_profiles
  where account_id = target.id;
  restricts_access := p_role <> target.role or p_status <> 'ACTIVE'
    or (target.role = 'VOLUNTEER' and p_volunteer_approval <> 'APPROVED');
  if restricts_access and exists (
    select 1 from public.volunteer_assignments a
    where a.volunteer_account_id = target.id
      and a.status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW')
  ) then
    raise exception 'Active assignments must be completed or reassigned first' using errcode = '23514';
  end if;

  before_state := jsonb_build_object('role', target.role, 'status', target.status,
    'volunteer_approval', previous_approval, 'version', target.version);

  update public.accounts set role = p_role, status = p_status,
    version = version + 1, updated_at = now()
  where id = target.id;

  if p_role = 'VOLUNTEER' then
    insert into public.volunteer_profiles(account_id) values (target.id)
    on conflict (account_id) do nothing;
    update public.volunteer_profiles
    set approval_status = p_volunteer_approval,
      approved_by_account_id = case when p_volunteer_approval = 'PENDING' then null else actor_id end,
      approved_at = case when p_volunteer_approval = 'PENDING' then null else now() end,
      rejection_reason = case when p_volunteer_approval = 'REJECTED' then nullif(btrim(p_reason), '') else null end,
      version = version + 1, updated_at = now()
    where account_id = target.id;
    if p_volunteer_approval = 'REJECTED' and nullif(btrim(p_reason), '') is null then
      raise exception 'Volunteer rejection requires a reason' using errcode = '23514';
    end if;
    if previous_approval is distinct from p_volunteer_approval and p_volunteer_approval <> 'PENDING' then
      insert into public.volunteer_approval_reviews(
        volunteer_account_id, administrator_account_id, decision, reason)
      values (target.id, actor_id, p_volunteer_approval,
        case when p_volunteer_approval = 'REJECTED' then btrim(p_reason) else null end);
    end if;
  end if;

  after_state := jsonb_build_object('role', p_role, 'status', p_status,
    'volunteer_approval', p_volunteer_approval, 'version', target.version + 1);
  insert into public.access_change_audit(target_account_id, actor_account_id, action,
    before_state, after_state, reason, operation_id)
  values (target.id, actor_id, 'UPDATE_USER_ACCESS', before_state, after_state,
    nullif(btrim(p_reason), ''), p_operation_id);

  operation_result := jsonb_build_object('account_id', target.id, 'display_name', target.display_name,
    'email', target.email, 'role', p_role, 'status', p_status,
    'volunteer_approval', p_volunteer_approval, 'version', target.version + 1);
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

create or replace function public.save_controlled_reference_value(
  p_operation_id uuid,
  p_reference_type text,
  p_reference_id text,
  p_name text,
  p_active boolean,
  p_expected_version bigint,
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
  normalized_name text := btrim(p_name);
  normalized_reason text := btrim(p_reason);
  value_id text;
  current_version bigint;
  before_state jsonb;
  after_state jsonb;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() is distinct from 'ADMINISTRATOR' then
    raise exception 'Administrator permission required' using errcode = '42501';
  end if;
  if p_reference_type not in ('DISASTER_TYPE', 'VOLUNTEER_SKILL', 'SERVICE_AREA', 'DECLINE_REASON') then
    raise exception 'Unsupported reference-data type' using errcode = '22023';
  end if;
  if length(normalized_name) not between 2 and 160 then
    raise exception 'Reference value must contain 2 to 160 characters' using errcode = '22023';
  end if;
  if length(normalized_reason) not between 5 and 500 then
    raise exception 'Change reason must contain 5 to 500 characters' using errcode = '22023';
  end if;
  if p_expected_version < 0 then
    raise exception 'Invalid expected version' using errcode = '22023';
  end if;
  if p_reference_id is null and p_expected_version <> 0 then
    raise exception 'A new reference value must start at version zero' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id
        or operation.operation_type <> 'SAVE_REFERENCE_DATA' then
      raise exception 'Operation ID belongs to another action' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    if operation.status <> 'PENDING' then
      raise exception 'Operation cannot be retried' using errcode = '55000';
    end if;
  else
    insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
    values (p_operation_id, actor_id, 'SAVE_REFERENCE_DATA');
  end if;

  if p_reference_type = 'DISASTER_TYPE' then
    if p_reference_id is null then
      raise exception 'New disaster-type codes require a reviewed application release' using errcode = '22023';
    end if;
    select version, jsonb_build_object('name', name, 'active', active, 'version', version)
      into current_version, before_state
    from public.disaster_types where code = p_reference_id for update;
    if not found then raise exception 'Reference value no longer exists' using errcode = 'P0002'; end if;
    if current_version <> p_expected_version then
      raise exception 'Reference value changed; refresh and retry' using errcode = '40001';
    end if;
    update public.disaster_types set name = normalized_name, active = p_active,
      version = version + 1, updated_at = now()
    where code = p_reference_id
    returning code, jsonb_build_object('name', name, 'active', active, 'version', version)
      into value_id, after_state;
  elsif p_reference_type = 'VOLUNTEER_SKILL' then
    if p_reference_id is null then
      insert into public.skills(name, active) values (normalized_name, p_active)
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    else
      if p_reference_id !~ '^[0-9]+$' then raise exception 'Invalid reference ID' using errcode = '22023'; end if;
      select version, jsonb_build_object('name', name, 'active', active, 'version', version)
        into current_version, before_state
      from public.skills where id = p_reference_id::bigint for update;
      if not found then raise exception 'Reference value no longer exists' using errcode = 'P0002'; end if;
      if current_version <> p_expected_version then
        raise exception 'Reference value changed; refresh and retry' using errcode = '40001';
      end if;
      update public.skills set name = normalized_name, active = p_active,
        version = version + 1, updated_at = now()
      where id = p_reference_id::bigint
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    end if;
  elsif p_reference_type = 'SERVICE_AREA' then
    if p_reference_id is null then
      insert into public.service_areas(name, active) values (normalized_name, p_active)
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    else
      if p_reference_id !~ '^[0-9]+$' then raise exception 'Invalid reference ID' using errcode = '22023'; end if;
      select version, jsonb_build_object('name', name, 'active', active, 'version', version)
        into current_version, before_state
      from public.service_areas where id = p_reference_id::bigint for update;
      if not found then raise exception 'Reference value no longer exists' using errcode = 'P0002'; end if;
      if current_version <> p_expected_version then
        raise exception 'Reference value changed; refresh and retry' using errcode = '40001';
      end if;
      update public.service_areas set name = normalized_name, active = p_active,
        version = version + 1, updated_at = now()
      where id = p_reference_id::bigint
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    end if;
  else
    if p_reference_id is null then
      insert into public.standard_decline_reasons(name, active) values (normalized_name, p_active)
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    else
      if p_reference_id !~ '^[0-9]+$' then raise exception 'Invalid reference ID' using errcode = '22023'; end if;
      select version, jsonb_build_object('name', name, 'active', active, 'version', version)
        into current_version, before_state
      from public.standard_decline_reasons where id = p_reference_id::bigint for update;
      if not found then raise exception 'Reference value no longer exists' using errcode = 'P0002'; end if;
      if current_version <> p_expected_version then
        raise exception 'Reference value changed; refresh and retry' using errcode = '40001';
      end if;
      update public.standard_decline_reasons set name = normalized_name, active = p_active,
        version = version + 1, updated_at = now()
      where id = p_reference_id::bigint
      returning id::text, jsonb_build_object('name', name, 'active', active, 'version', version)
        into value_id, after_state;
    end if;
  end if;

  insert into public.reference_data_audit(reference_type, reference_id, actor_account_id,
    before_state, after_state, reason, operation_id)
  values (p_reference_type, value_id, actor_id, before_state, after_state,
    normalized_reason, p_operation_id);

  operation_result := jsonb_build_object('reference_type', p_reference_type, 'reference_id', value_id,
    'name', after_state ->> 'name', 'active', (after_state ->> 'active')::boolean,
    'version', (after_state ->> 'version')::bigint);
  update public.workflow_operations set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;
