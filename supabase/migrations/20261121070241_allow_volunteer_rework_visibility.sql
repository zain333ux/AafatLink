-- UC06 requires a return-for-rework reason to be visible to the assigned volunteer.
-- A private helper avoids a recursive closure -> assignment -> request policy chain.
create or replace function private.can_read_closure_review(
  p_request_id uuid,
  p_assignment_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select auth.uid() is not null and (
    private.current_app_role() in ('COORDINATOR', 'ADMINISTRATOR')
    or exists (
      select 1 from public.assistance_requests request
      where request.id = p_request_id and request.requester_account_id = auth.uid()
    )
    or exists (
      select 1 from public.volunteer_assignments assignment
      where assignment.id = p_assignment_id and assignment.volunteer_account_id = auth.uid()
    )
  );
$$;

revoke all on function private.can_read_closure_review(uuid, uuid) from public, anon;
grant execute on function private.can_read_closure_review(uuid, uuid) to authenticated;

drop policy if exists closure_reviews_select on public.closure_reviews;
create policy closure_reviews_select
on public.closure_reviews for select to authenticated
using ((select private.can_read_closure_review(request_id, assignment_id)));
