-- Follow-up indexes and policy consolidation identified by the hosted performance advisor.
create index if not exists reference_data_audit_actor_idx
  on public.reference_data_audit (actor_account_id, changed_at desc);

-- Preserve the exact requester/coordinator/administrator/assigned-volunteer read rules while
-- avoiding two permissive SELECT policies being evaluated for every request row.
drop policy if exists requests_select_authorized on public.assistance_requests;
drop policy if exists requests_select_assigned_volunteer on public.assistance_requests;

create policy requests_select_authorized on public.assistance_requests
for select to authenticated
using (
  requester_account_id = (select auth.uid())
  or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR')
  or (select private.is_request_assigned_to_actor(id))
);
