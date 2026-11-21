-- UC11 read-only reports and UC12 atomic access administration.

create or replace function public.report_request_status(
  p_start_date date default null,
  p_end_date date default null,
  p_disaster_type text default null,
  p_status text default null,
  p_priority text default null,
  p_service_area_id bigint default null
)
returns table(status text, disaster_type text, priority text, request_count bigint)
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  if private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Reporting permission required' using errcode = '42501';
  end if;
  return query
  select r.status::text, r.disaster_type, coalesce(r.priority::text, 'UNSET'), count(*)::bigint
  from public.assistance_requests r
  where (p_start_date is null or r.submitted_at >= p_start_date::timestamptz)
    and (p_end_date is null or r.submitted_at < (p_end_date + 1)::timestamptz)
    and (p_disaster_type is null or lower(r.disaster_type) = lower(p_disaster_type))
    and (p_status is null or r.status::text = p_status)
    and (p_priority is null or r.priority::text = p_priority)
    and (p_service_area_id is null or exists (
      select 1 from public.volunteer_assignments a
      join public.volunteer_service_areas area on area.volunteer_account_id = a.volunteer_account_id
      where a.request_id = r.id and area.service_area_id = p_service_area_id
    ))
  group by r.status, r.disaster_type, r.priority
  order by r.status, r.disaster_type, r.priority;
end;
$$;

create or replace function public.report_outstanding_requests(
  p_start_date date default null,
  p_end_date date default null,
  p_disaster_type text default null,
  p_status text default null,
  p_priority text default null,
  p_service_area_id bigint default null
)
returns table(request_id uuid, submitted_at timestamptz, disaster_type text, status text,
  priority text, location text, waiting_hours numeric)
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  if private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Reporting permission required' using errcode = '42501';
  end if;
  return query
  select r.id, r.submitted_at, r.disaster_type, r.status::text,
    coalesce(r.priority::text, 'UNSET'), r.location_address,
    round((extract(epoch from (now() - r.submitted_at)) / 3600)::numeric, 1)
  from public.assistance_requests r
  where r.status not in ('CLOSED', 'REJECTED')
    and (p_start_date is null or r.submitted_at >= p_start_date::timestamptz)
    and (p_end_date is null or r.submitted_at < (p_end_date + 1)::timestamptz)
    and (p_disaster_type is null or lower(r.disaster_type) = lower(p_disaster_type))
    and (p_status is null or r.status::text = p_status)
    and (p_priority is null or r.priority::text = p_priority)
    and (p_service_area_id is null or exists (
      select 1 from public.volunteer_assignments a
      join public.volunteer_service_areas area on area.volunteer_account_id = a.volunteer_account_id
      where a.request_id = r.id and area.service_area_id = p_service_area_id
    ))
  order by r.priority nulls last, r.submitted_at
  limit 5000;
end;
$$;

-- Definer is required only here because Coordinator RLS intentionally does not expose all account emails.
-- The result exposes display names and aggregate counts only.
create or replace function public.report_volunteer_workload(
  p_start_date date default null,
  p_end_date date default null,
  p_disaster_type text default null,
  p_status text default null,
  p_priority text default null,
  p_service_area_id bigint default null
)
returns table(volunteer_name text, assignment_count bigint, active_count bigint, completed_count bigint)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Reporting permission required' using errcode = '42501';
  end if;
  return query
  select account.display_name, count(a.id)::bigint,
    count(a.id) filter (where a.status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW'))::bigint,
    count(a.id) filter (where a.status = 'COMPLETED')::bigint
  from public.volunteer_profiles profile
  join public.accounts account on account.id = profile.account_id
  join public.volunteer_assignments a on a.volunteer_account_id = profile.account_id
  join public.assistance_requests r on r.id = a.request_id
  where (p_start_date is null or a.assigned_at >= p_start_date::timestamptz)
    and (p_end_date is null or a.assigned_at < (p_end_date + 1)::timestamptz)
    and (p_disaster_type is null or lower(r.disaster_type) = lower(p_disaster_type))
    and (p_status is null or r.status::text = p_status)
    and (p_priority is null or r.priority::text = p_priority)
    and (p_service_area_id is null or exists (
      select 1 from public.volunteer_service_areas area
      where area.volunteer_account_id = profile.account_id and area.service_area_id = p_service_area_id
    ))
  group by account.id, account.display_name
  order by count(a.id) desc, account.display_name
  limit 5000;
end;
$$;

create or replace function public.report_response_performance(
  p_start_date date default null,
  p_end_date date default null,
  p_disaster_type text default null,
  p_status text default null,
  p_priority text default null,
  p_service_area_id bigint default null
)
returns table(request_count bigint, closed_count bigint, closure_rate numeric,
  avg_verification_hours numeric, avg_assignment_hours numeric, avg_resolution_hours numeric)
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  if private.current_app_role() not in ('COORDINATOR', 'ADMINISTRATOR') then
    raise exception 'Reporting permission required' using errcode = '42501';
  end if;
  return query
  with filtered as (
    select r.*
    from public.assistance_requests r
    where (p_start_date is null or r.submitted_at >= p_start_date::timestamptz)
      and (p_end_date is null or r.submitted_at < (p_end_date + 1)::timestamptz)
      and (p_disaster_type is null or lower(r.disaster_type) = lower(p_disaster_type))
      and (p_status is null or r.status::text = p_status)
      and (p_priority is null or r.priority::text = p_priority)
      and (p_service_area_id is null or exists (
        select 1 from public.volunteer_assignments a
        join public.volunteer_service_areas area on area.volunteer_account_id = a.volunteer_account_id
        where a.request_id = r.id and area.service_area_id = p_service_area_id
      ))
    order by r.submitted_at desc
    limit 5000
  ), timings as (
    select r.id, r.status, r.submitted_at,
      (select min(v.created_at) from public.request_verifications v
        where v.request_id = r.id and v.decision = 'VERIFIED') verified_at,
      (select min(a.assigned_at) from public.volunteer_assignments a where a.request_id = r.id) assigned_at,
      (select min(c.created_at) from public.closure_reviews c
        where c.request_id = r.id and c.decision = 'APPROVED') closed_at
    from filtered r
  )
  select count(*)::bigint,
    count(*) filter (where status = 'CLOSED')::bigint,
    round((100.0 * count(*) filter (where status = 'CLOSED') / nullif(count(*), 0))::numeric, 2),
    round(avg(extract(epoch from (verified_at - submitted_at)) / 3600)::numeric, 2),
    round(avg(extract(epoch from (assigned_at - verified_at)) / 3600)::numeric, 2),
    round(avg(extract(epoch from (closed_at - submitted_at)) / 3600)::numeric, 2)
  from timings
  having count(*) > 0;
end;
$$;

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
  result jsonb;
  restricts_access boolean;
begin
  if actor_id is null or private.current_app_role() <> 'ADMINISTRATOR' then
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

  result := jsonb_build_object('account_id', target.id, 'display_name', target.display_name,
    'email', target.email, 'role', p_role, 'status', p_status,
    'volunteer_approval', p_volunteer_approval, 'version', target.version + 1);
  update public.workflow_operations set status = 'COMMITTED', result = result, completed_at = now()
  where operation_id = p_operation_id;
  return result;
end;
$$;

revoke all on function public.report_request_status(date,date,text,text,text,bigint) from public, anon;
revoke all on function public.report_outstanding_requests(date,date,text,text,text,bigint) from public, anon;
revoke all on function public.report_volunteer_workload(date,date,text,text,text,bigint) from public, anon;
revoke all on function public.report_response_performance(date,date,text,text,text,bigint) from public, anon;
revoke all on function public.update_user_access(uuid,uuid,bigint,public.app_role,public.account_status,public.volunteer_approval_status,text) from public, anon;
grant execute on function public.report_request_status(date,date,text,text,text,bigint) to authenticated;
grant execute on function public.report_outstanding_requests(date,date,text,text,text,bigint) to authenticated;
grant execute on function public.report_volunteer_workload(date,date,text,text,text,bigint) to authenticated;
grant execute on function public.report_response_performance(date,date,text,text,text,bigint) to authenticated;
grant execute on function public.update_user_access(uuid,uuid,bigint,public.app_role,public.account_status,public.volunteer_approval_status,text) to authenticated;
