-- Development SQL editor. Fresh fixtures have one row in each open queue and three closed requests.
with fixtures as (
 select r.*, i from public.assistance_requests r cross join generate_series(1,7) i
 where r.id = md5('aafatlink-demo-v1:' || r.requester_account_id::text || ':' || i)::uuid
)
select r.requester_account_id, r.i as fixture, r.status, r.priority, a.status as assignment_state,
 requester.role as requester_role, coordinator.role as coordinator_role, volunteer.role as volunteer_role,
 count(*) over(partition by r.requester_account_id,r.status,r.priority) as queue_count
from fixtures r left join public.volunteer_assignments a on a.request_id=r.id
join public.accounts requester on requester.id=r.requester_account_id
left join public.accounts coordinator on coordinator.id=a.assigned_by_account_id
left join public.accounts volunteer on volunteer.id=a.volunteer_account_id
order by r.requester_account_id,r.i;
