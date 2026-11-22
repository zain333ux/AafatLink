-- UC12 controlled reference data. Values are deactivated, never deleted, so historical rows remain valid.
alter table public.skills
  add column if not exists version bigint not null default 0 check (version >= 0),
  add column if not exists updated_at timestamptz not null default now();

alter table public.service_areas
  add column if not exists version bigint not null default 0 check (version >= 0),
  add column if not exists updated_at timestamptz not null default now();

create table if not exists public.disaster_types (
  code text primary key check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
  name text not null unique check (length(btrim(name)) between 2 and 80),
  active boolean not null default true,
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.standard_decline_reasons (
  id bigint generated always as identity primary key,
  name text not null unique check (length(btrim(name)) between 2 and 160),
  active boolean not null default true,
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.reference_data_audit (
  id bigint generated always as identity primary key,
  reference_type text not null check (reference_type in (
    'DISASTER_TYPE', 'VOLUNTEER_SKILL', 'SERVICE_AREA', 'DECLINE_REASON')),
  reference_id text not null,
  actor_account_id uuid not null references public.accounts(id) on delete restrict,
  before_state jsonb,
  after_state jsonb not null,
  reason text not null check (length(btrim(reason)) between 5 and 500),
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  changed_at timestamptz not null default now()
);

insert into public.disaster_types(code, name) values
  ('flood', 'Flood'),
  ('earthquake', 'Earthquake'),
  ('fire', 'Fire'),
  ('landslide', 'Landslide'),
  ('other', 'Other')
on conflict (code) do nothing;

insert into public.standard_decline_reasons(name) values
  ('Unavailable during the scheduled response window'),
  ('Required skill or equipment is not available'),
  ('Unable to safely reach the affected service area'),
  ('Conflicting emergency assignment')
on conflict (name) do nothing;

alter table public.disaster_types enable row level security;
alter table public.standard_decline_reasons enable row level security;
alter table public.reference_data_audit enable row level security;

create policy disaster_types_read_authenticated on public.disaster_types
for select to authenticated using (true);
create policy decline_reasons_read_authenticated on public.standard_decline_reasons
for select to authenticated using (true);
create policy reference_data_audit_admin_read on public.reference_data_audit
for select to authenticated using (private.current_app_role() = 'ADMINISTRATOR');

grant select on public.disaster_types, public.standard_decline_reasons to authenticated;
grant select on public.reference_data_audit to authenticated;

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
  result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'ADMINISTRATOR' then
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

  result := jsonb_build_object('reference_type', p_reference_type, 'reference_id', value_id,
    'name', after_state ->> 'name', 'active', (after_state ->> 'active')::boolean,
    'version', (after_state ->> 'version')::bigint);
  update public.workflow_operations set status = 'COMMITTED', result = result, completed_at = now()
  where operation_id = p_operation_id;
  return result;
end;
$$;

revoke all on function public.save_controlled_reference_value(uuid,text,text,text,boolean,bigint,text)
  from public, anon;
grant execute on function public.save_controlled_reference_value(uuid,text,text,text,boolean,bigint,text)
  to authenticated;
