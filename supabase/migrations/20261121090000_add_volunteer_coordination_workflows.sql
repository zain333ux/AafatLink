-- UC05, UC07 and UC08 volunteer coordination. Apply after the UC04 alignment migration.

do $$
begin
  if not exists (select 1 from pg_type where typnamespace = 'public'::regnamespace
      and typname = 'volunteer_availability_status') then
    create type public.volunteer_availability_status as enum ('AVAILABLE', 'UNAVAILABLE');
  end if;
end
$$;

alter table public.volunteer_profiles
  add column if not exists availability_status public.volunteer_availability_status
    not null default 'UNAVAILABLE';

alter table public.volunteer_assignments
  add column if not exists matched_skill_id bigint references public.skills(id) on delete restrict,
  add column if not exists matched_service_area_id bigint references public.service_areas(id) on delete restrict;

create unique index if not exists assignment_one_decision_idx
  on public.assignment_decisions (assignment_id);

create index if not exists volunteer_profiles_readiness_idx
  on public.volunteer_profiles (approval_status, availability_status)
  where approval_status = 'APPROVED' and availability_status = 'AVAILABLE';

-- Keep the request policy independent of assignment RLS to avoid a recursive
-- assistance_requests -> volunteer_assignments -> assistance_requests check.
create or replace function private.is_request_assigned_to_actor(p_request_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select auth.uid() is not null and exists (
    select 1
    from public.volunteer_assignments assignment
    where assignment.request_id = p_request_id
      and assignment.volunteer_account_id = auth.uid()
      and assignment.status in (
        'PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW'
      )
  );
$$;

revoke all on function private.is_request_assigned_to_actor(uuid) from public, anon;
grant execute on function private.is_request_assigned_to_actor(uuid) to authenticated;

create policy requests_select_assigned_volunteer on public.assistance_requests
for select to authenticated
using ((select private.is_request_assigned_to_actor(id)));

insert into public.skills (name) values
  ('First Aid'), ('Medical Support'), ('Search and Rescue'), ('Shelter Support'),
  ('Food Distribution'), ('Logistics'), ('Transportation'), ('Damage Assessment')
on conflict (name) do nothing;

insert into public.service_areas (name) values
  ('Islamabad - Central'), ('Islamabad - Rural'), ('Rawalpindi - Central'),
  ('Rawalpindi - Rural'), ('Islamabad Capital Territory')
on conflict (name) do nothing;

-- SECURITY INVOKER is sufficient: the volunteer updates only their own RLS-visible rows.
create or replace function public.update_volunteer_readiness(
  p_expected_version bigint,
  p_contact_number text,
  p_availability_status public.volunteer_availability_status,
  p_availability_notes text,
  p_available_from timestamptz,
  p_available_until timestamptz,
  p_skill_ids bigint[],
  p_service_area_ids bigint[]
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  profile public.volunteer_profiles%rowtype;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  if p_contact_number is null or btrim(p_contact_number) !~ '^[+0-9][0-9 +()\-]{6,24}$' then
    raise exception 'A valid contact number is required' using errcode = '22023';
  end if;
  if coalesce(array_length(p_skill_ids, 1), 0) = 0
      or coalesce(array_length(p_service_area_ids, 1), 0) = 0 then
    raise exception 'At least one skill and service area are required' using errcode = '22023';
  end if;
  if (p_available_from is null) <> (p_available_until is null)
      or (p_available_from is not null and p_available_from >= p_available_until) then
    raise exception 'Availability window is invalid' using errcode = '22007';
  end if;

  select * into profile from public.volunteer_profiles
  where account_id = actor_id for update;
  if not found then
    raise exception 'Volunteer profile not found' using errcode = 'P0002';
  end if;
  if profile.version <> p_expected_version then
    raise exception 'Volunteer profile changed' using errcode = '40001';
  end if;
  if exists (
    select requested.id from unnest(p_skill_ids) requested(id)
    left join public.skills skill on skill.id = requested.id and skill.active
    where skill.id is null
  ) or exists (
    select requested.id from unnest(p_service_area_ids) requested(id)
    left join public.service_areas area on area.id = requested.id and area.active
    where area.id is null
  ) then
    raise exception 'Readiness selection is inactive or unknown' using errcode = '22023';
  end if;

  update public.accounts
  set phone = btrim(p_contact_number), version = version + 1, updated_at = now()
  where id = actor_id;
  update public.volunteer_profiles
  set availability_status = p_availability_status,
      availability_notes = nullif(btrim(p_availability_notes), ''),
      available_from = p_available_from,
      available_until = p_available_until,
      version = version + 1,
      updated_at = now()
  where account_id = actor_id;
  delete from public.volunteer_skills where volunteer_account_id = actor_id;
  insert into public.volunteer_skills(volunteer_account_id, skill_id)
  select actor_id, selected.id from (select distinct unnest(p_skill_ids) id) selected;
  delete from public.volunteer_service_areas where volunteer_account_id = actor_id;
  insert into public.volunteer_service_areas(volunteer_account_id, service_area_id)
  select actor_id, selected.id from (select distinct unnest(p_service_area_ids) id) selected;

  return jsonb_build_object(
    'account_id', actor_id,
    'approval_status', profile.approval_status,
    'availability_status', p_availability_status,
    'contact_number', btrim(p_contact_number),
    'availability_notes', nullif(btrim(p_availability_notes), ''),
    'available_from', p_available_from,
    'available_until', p_available_until,
    'skill_ids', p_skill_ids,
    'service_area_ids', p_service_area_ids,
    'version', p_expected_version + 1
  );
end;
$$;

-- Coordinator-only eligibility query. RLS remains active because this is SECURITY INVOKER.
create or replace function public.find_eligible_volunteers(
  p_request_id uuid,
  p_skill_id bigint,
  p_service_area_id bigint,
  p_scheduled_start_at timestamptz default null,
  p_scheduled_end_at timestamptz default null
)
returns table (
  volunteer_account_id uuid,
  display_name text,
  contact_number text,
  approval_status public.volunteer_approval_status,
  availability_status public.volunteer_availability_status,
  availability_notes text,
  available_from timestamptz,
  available_until timestamptz,
  profile_version bigint,
  skill_ids bigint[],
  skill_names text[],
  service_area_ids bigint[],
  service_area_names text[],
  active_assignment_count bigint
)
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if auth.uid() is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  if p_skill_id is null or p_service_area_id is null then
    raise exception 'A required skill and service area must be selected' using errcode = '22023';
  end if;
  if (p_scheduled_start_at is null) <> (p_scheduled_end_at is null)
      or (p_scheduled_start_at is not null and p_scheduled_start_at >= p_scheduled_end_at) then
    raise exception 'Assignment schedule is invalid' using errcode = '22007';
  end if;
  if not exists (
    select 1 from public.assistance_requests request
    where request.id = p_request_id and request.status = 'VERIFIED' and request.priority is not null
  ) then
    raise exception 'Request is not eligible for assignment' using errcode = '23514';
  end if;

  return query
  select profile.account_id, account.display_name, account.phone, profile.approval_status,
    profile.availability_status, profile.availability_notes, profile.available_from,
    profile.available_until, profile.version,
    array_agg(distinct skill.id order by skill.id),
    array_agg(distinct skill.name order by skill.name),
    array_agg(distinct area.id order by area.id),
    array_agg(distinct area.name order by area.name),
    (select count(*) from public.volunteer_assignments workload
      where workload.volunteer_account_id = profile.account_id
        and workload.status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS'))
  from public.volunteer_profiles profile
  join public.accounts account on account.id = profile.account_id and account.status = 'ACTIVE'
  join public.volunteer_skills profile_skill on profile_skill.volunteer_account_id = profile.account_id
  join public.skills skill on skill.id = profile_skill.skill_id and skill.active
  join public.volunteer_service_areas profile_area on profile_area.volunteer_account_id = profile.account_id
  join public.service_areas area on area.id = profile_area.service_area_id and area.active
  where profile.approval_status = 'APPROVED'
    and profile.availability_status = 'AVAILABLE'
    and exists (select 1 from public.volunteer_skills matched_skill
      where matched_skill.volunteer_account_id = profile.account_id and matched_skill.skill_id = p_skill_id)
    and exists (select 1 from public.volunteer_service_areas matched_area
      where matched_area.volunteer_account_id = profile.account_id and matched_area.service_area_id = p_service_area_id)
    and (p_scheduled_start_at is null or (
      (profile.available_from is null or profile.available_from <= p_scheduled_start_at)
      and (profile.available_until is null or profile.available_until >= p_scheduled_end_at)
    ))
    and not exists (
      select 1 from public.volunteer_assignments existing_assignment
      where existing_assignment.volunteer_account_id = profile.account_id
        and existing_assignment.status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS')
        and (
          (p_scheduled_start_at is null and existing_assignment.active_window is null)
          or (p_scheduled_start_at is null and existing_assignment.active_window is not null)
          or (p_scheduled_start_at is not null and existing_assignment.active_window is null)
          or (p_scheduled_start_at is not null and existing_assignment.active_window &&
              tstzrange(p_scheduled_start_at, p_scheduled_end_at, '[)'))
        )
    )
  group by profile.account_id, account.display_name, account.phone, profile.approval_status,
    profile.availability_status, profile.availability_notes, profile.available_from,
    profile.available_until, profile.version
  order by active_assignment_count, account.display_name;
end;
$$;

-- Atomic assignment rechecks every eligibility condition under row locks.
create or replace function public.assign_volunteer_matching(
  p_operation_id uuid,
  p_request_id uuid,
  p_volunteer_account_id uuid,
  p_expected_request_version bigint,
  p_skill_id bigint,
  p_service_area_id bigint,
  p_scheduled_start_at timestamptz default null,
  p_scheduled_end_at timestamptz default null
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
  if p_skill_id is null or p_service_area_id is null then
    raise exception 'A required skill and service area must be selected' using errcode = '22023';
  end if;
  if (p_scheduled_start_at is null) <> (p_scheduled_end_at is null)
      or (p_scheduled_start_at is not null and p_scheduled_start_at >= p_scheduled_end_at) then
    raise exception 'Assignment schedule is invalid' using errcode = '22007';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'ASSIGN_VOLUNTEER' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into request_record from public.assistance_requests
  where id = p_request_id for update;
  if not found then raise exception 'Request not found' using errcode = 'P0002'; end if;
  if request_record.status <> 'VERIFIED' or request_record.priority is null
      or request_record.version <> p_expected_request_version then
    raise exception 'Request changed, is not verified, or has no priority' using errcode = '40001';
  end if;

  select * into profile from public.volunteer_profiles
  where account_id = p_volunteer_account_id for update;
  if not found or profile.approval_status <> 'APPROVED'
      or profile.availability_status <> 'AVAILABLE'
      or not exists (select 1 from public.accounts account
          where account.id = p_volunteer_account_id and account.status = 'ACTIVE') then
    raise exception 'Volunteer is not approved and available' using errcode = '42501';
  end if;
  if not exists (select 1 from public.volunteer_skills skill
      where skill.volunteer_account_id = p_volunteer_account_id and skill.skill_id = p_skill_id)
      or not exists (select 1 from public.volunteer_service_areas area
      where area.volunteer_account_id = p_volunteer_account_id
        and area.service_area_id = p_service_area_id) then
    raise exception 'Volunteer no longer matches the required skill or service area' using errcode = '23514';
  end if;
  if p_scheduled_start_at is not null and (
    (profile.available_from is not null and profile.available_from > p_scheduled_start_at)
    or (profile.available_until is not null and profile.available_until < p_scheduled_end_at)
  ) then
    raise exception 'Assignment is outside volunteer availability' using errcode = '23514';
  end if;
  if exists (
    select 1 from public.volunteer_assignments existing_assignment
    where existing_assignment.volunteer_account_id = p_volunteer_account_id
      and existing_assignment.status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS')
      and (
        p_scheduled_start_at is null
        or existing_assignment.active_window is null
        or existing_assignment.active_window && tstzrange(p_scheduled_start_at, p_scheduled_end_at, '[)')
      )
  ) then
    raise exception 'Volunteer has a conflicting active assignment' using errcode = '23P01';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'ASSIGN_VOLUNTEER');
  insert into public.volunteer_assignments(
    request_id, volunteer_account_id, assigned_by_account_id, operation_id,
    scheduled_start_at, scheduled_end_at, matched_skill_id, matched_service_area_id
  ) values (
    p_request_id, p_volunteer_account_id, actor_id, p_operation_id,
    p_scheduled_start_at, p_scheduled_end_at, p_skill_id, p_service_area_id
  ) returning id into new_assignment_id;
  update public.assistance_requests
  set status = 'ASSIGNED', version = version + 1, updated_at = now()
  where id = p_request_id;

  operation_result := jsonb_build_object(
    'assignment_id', new_assignment_id,
    'request_id', p_request_id,
    'assignment_status', 'PENDING_ACCEPTANCE',
    'request_status', 'ASSIGNED',
    'request_version', request_record.version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

-- Reassignment is deliberately two-step: release the request, then run normal eligibility again.
create or replace function public.reassign_volunteer(
  p_operation_id uuid,
  p_assignment_id uuid,
  p_expected_assignment_version bigint,
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
  assignment public.volunteer_assignments%rowtype;
  request_record public.assistance_requests%rowtype;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator authorization required' using errcode = '42501';
  end if;
  if nullif(btrim(p_reason), '') is null then
    raise exception 'A reassignment reason is required' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'REASSIGN_VOLUNTEER' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into assignment from public.volunteer_assignments
  where id = p_assignment_id for update;
  if not found then raise exception 'Assignment not found' using errcode = 'P0002'; end if;
  if assignment.status not in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS')
      or assignment.version <> p_expected_assignment_version then
    raise exception 'Assignment changed or cannot be reassigned' using errcode = '40001';
  end if;
  select * into request_record from public.assistance_requests
  where id = assignment.request_id for update;
  if not found or request_record.status not in ('ASSIGNED', 'IN_PROGRESS') then
    raise exception 'Request changed or cannot be reassigned' using errcode = '40001';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'REASSIGN_VOLUNTEER');
  update public.volunteer_assignments
  set status = 'REASSIGNED', reassignment_reason = btrim(p_reason),
      version = version + 1, updated_at = now()
  where id = assignment.id;
  update public.assistance_requests
  set status = 'VERIFIED', version = version + 1, updated_at = now()
  where id = request_record.id;

  operation_result := jsonb_build_object(
    'assignment_id', assignment.id,
    'assignment_status', 'REASSIGNED',
    'request_id', request_record.id,
    'request_status', 'VERIFIED',
    'assignment_version', assignment.version + 1,
    'request_version', request_record.version + 1
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

-- Multi-record response requires elevated writes, so identity and ownership are rechecked here.
create or replace function public.respond_to_assignment(
  p_operation_id uuid,
  p_assignment_id uuid,
  p_expected_version bigint,
  p_accept boolean,
  p_decline_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
  assignment public.volunteer_assignments%rowtype;
  request_record public.assistance_requests%rowtype;
  operation_result jsonb;
begin
  if actor_id is null or private.current_app_role() <> 'VOLUNTEER' then
    raise exception 'Volunteer authorization required' using errcode = '42501';
  end if;
  if not p_accept and nullif(btrim(p_decline_reason), '') is null then
    raise exception 'A decline reason is required' using errcode = '22023';
  end if;

  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if found then
    if operation.actor_account_id <> actor_id or operation.operation_type <> 'RESPOND_TO_ASSIGNMENT' then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    if operation.status = 'COMMITTED' then return operation.result; end if;
    raise exception 'Operation is already pending' using errcode = '55000';
  end if;

  select * into assignment from public.volunteer_assignments
  where id = p_assignment_id for update;
  if not found then raise exception 'Assignment not found' using errcode = 'P0002'; end if;
  if assignment.volunteer_account_id <> actor_id then
    raise exception 'Assignment does not belong to this volunteer' using errcode = '42501';
  end if;
  if assignment.status <> 'PENDING_ACCEPTANCE' or assignment.version <> p_expected_version then
    raise exception 'Assignment changed or is no longer pending' using errcode = '40001';
  end if;

  select * into request_record from public.assistance_requests
  where id = assignment.request_id for update;
  if not found or request_record.status <> 'ASSIGNED' then
    raise exception 'Request changed or is no longer assigned' using errcode = '40001';
  end if;

  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, 'RESPOND_TO_ASSIGNMENT');
  insert into public.assignment_decisions(assignment_id, volunteer_account_id, accepted, decline_reason)
  values (assignment.id, actor_id, p_accept,
    case when p_accept then null else btrim(p_decline_reason) end);
  update public.volunteer_assignments
  set status = case when p_accept then 'ACCEPTED'::public.assignment_status
                    else 'DECLINED'::public.assignment_status end,
      version = version + 1,
      updated_at = now()
  where id = assignment.id;
  if not p_accept then
    update public.assistance_requests
    set status = 'VERIFIED', version = version + 1, updated_at = now()
    where id = request_record.id;
  end if;

  operation_result := jsonb_build_object(
    'assignment_id', assignment.id,
    'assignment_status', case when p_accept then 'ACCEPTED' else 'DECLINED' end,
    'request_id', request_record.id,
    'request_status', case when p_accept then request_record.status else 'VERIFIED' end,
    'assignment_version', assignment.version + 1,
    'request_version', request_record.version + case when p_accept then 0 else 1 end
  );
  update public.workflow_operations
  set status = 'COMMITTED', result = operation_result, completed_at = now()
  where operation_id = p_operation_id;
  return operation_result;
end;
$$;

grant update (availability_status) on public.volunteer_profiles to authenticated;

revoke all on function public.update_volunteer_readiness(bigint, text,
  public.volunteer_availability_status, text, timestamptz, timestamptz, bigint[], bigint[])
  from public, anon;
revoke all on function public.find_eligible_volunteers(uuid, bigint, bigint, timestamptz, timestamptz)
  from public, anon;
revoke all on function public.respond_to_assignment(uuid, uuid, bigint, boolean, text)
  from public, anon;
revoke all on function public.assign_volunteer_matching(uuid, uuid, uuid, bigint, bigint, bigint,
  timestamptz, timestamptz) from public, anon;
revoke all on function public.reassign_volunteer(uuid, uuid, bigint, text) from public, anon;
revoke execute on function public.assign_volunteer(uuid, uuid, uuid, bigint, timestamptz, timestamptz)
  from authenticated;
grant execute on function public.update_volunteer_readiness(bigint, text,
  public.volunteer_availability_status, text, timestamptz, timestamptz, bigint[], bigint[])
  to authenticated;
grant execute on function public.find_eligible_volunteers(uuid, bigint, bigint, timestamptz, timestamptz)
  to authenticated;
grant execute on function public.respond_to_assignment(uuid, uuid, bigint, boolean, text)
  to authenticated;
grant execute on function public.assign_volunteer_matching(uuid, uuid, uuid, bigint, bigint, bigint,
  timestamptz, timestamptz) to authenticated;
grant execute on function public.reassign_volunteer(uuid, uuid, bigint, text) to authenticated;
