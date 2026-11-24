-- UC02 permits a requester to see the latest progress for assignments attached
-- to their own assistance requests. Other requesters remain excluded by RLS.

drop policy if exists progress_select on public.task_progress_updates;

create policy progress_select_authorized
on public.task_progress_updates for select to authenticated
using (
  volunteer_account_id = (select auth.uid())
  or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR')
  or exists (
    select 1
    from public.volunteer_assignments assignment
    join public.assistance_requests request
      on request.id = assignment.request_id
    where assignment.id = task_progress_updates.assignment_id
      and request.requester_account_id = (select auth.uid())
  )
);
