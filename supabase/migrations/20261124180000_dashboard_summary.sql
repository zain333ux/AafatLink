-- One count and one actionable record per nonempty queue, under the caller's RLS policies.
create function public.dashboard_summary(expected_role public.app_role)
returns table(queue text, total bigint, record_id uuid, caption text)
language plpgsql stable security invoker set search_path = '' as $$
begin
  if expected_role is null or auth.uid() is null or private.current_app_role() is distinct from expected_role then
    raise exception 'Sign in again to refresh your access.' using errcode = '42501';
  end if;
  case expected_role
    when 'REQUESTER' then
      return query select 'returned'::text, count(*) over (), id, location_address
        from public.assistance_requests
        where requester_account_id = auth.uid() and status = 'ADDITIONAL_INFORMATION_REQUIRED'
        order by submitted_at, id limit 1;
      return query select 'requests'::text, count(*) over (), id, location_address || ' · ' || initcap(replace(status::text, '_', ' '))
        from public.assistance_requests
        where requester_account_id = auth.uid()
        order by submitted_at desc, id limit 1;
    when 'VOLUNTEER' then
      return query select 'offers'::text, count(*) over (), id, 'Request ' || left(request_id::text, 8)
        from public.volunteer_assignments
        where volunteer_account_id = auth.uid() and status = 'PENDING_ACCEPTANCE'
        order by assigned_at, id limit 1;
      return query select 'tasks'::text, count(*) over (), id, 'Request ' || left(request_id::text, 8)
        from public.volunteer_assignments
        where volunteer_account_id = auth.uid() and status in ('ACCEPTED','IN_PROGRESS')
        order by updated_at desc, id limit 1;
    when 'COORDINATOR' then
      return query select 'verify'::text, count(*) over (), id, location_address
        from public.assistance_requests
        where status = 'PENDING_VERIFICATION'
        order by submitted_at, id limit 1;
      return query select 'priority'::text, count(*) over (), id, location_address
        from public.assistance_requests
        where status = 'VERIFIED' and priority is null
        order by submitted_at, id limit 1;
      return query select 'match'::text, count(*) over (), id, location_address
        from public.assistance_requests
        where status = 'VERIFIED' and priority is not null
        order by priority, submitted_at, id limit 1;
      return query select 'close'::text, count(*) over (), id, 'Request ' || left(request_id::text, 8)
        from public.volunteer_assignments
        where status = 'AWAITING_CLOSURE_REVIEW'
        order by updated_at, id limit 1;
    when 'ADMINISTRATOR' then
      return query select 'approvals'::text, count(*) over (), a.id, a.display_name
        from public.volunteer_profiles v join public.accounts a on a.id = v.account_id
        where v.approval_status = 'PENDING' and a.role = 'VOLUNTEER' and a.status = 'ACTIVE'
        order by v.created_at, a.id limit 1;
  end case;
end;
$$;
revoke all on function public.dashboard_summary(public.app_role) from public, anon;
grant execute on function public.dashboard_summary(public.app_role) to authenticated;
