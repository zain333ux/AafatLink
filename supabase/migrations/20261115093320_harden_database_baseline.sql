-- Resolve advisor findings from the first hosted development deployment.
-- Keep extensions outside the exposed public schema.
alter extension btree_gist set schema extensions;

-- Supabase created this event-trigger function when automatic RLS was enabled.
-- Event triggers do not need Data API callers to execute it directly.
revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

-- Cover foreign keys used for parent-row updates and deletes.
create index request_evidence_uploader_idx
  on public.request_evidence (uploaded_by_account_id);
create index role_permissions_permission_idx
  on public.role_permissions (permission_code);
create index volunteer_approval_volunteer_idx
  on public.volunteer_approval_reviews (volunteer_account_id);

-- Join-table ownership policies need INSERT and DELETE only. Splitting the old
-- FOR ALL policies avoids duplicate SELECT policy evaluation.
drop policy volunteer_skills_manage_own on public.volunteer_skills;
create policy volunteer_skills_insert_own
on public.volunteer_skills for insert to authenticated
with check (volunteer_account_id = (select auth.uid()));
create policy volunteer_skills_delete_own
on public.volunteer_skills for delete to authenticated
using (volunteer_account_id = (select auth.uid()));

drop policy volunteer_areas_manage_own on public.volunteer_service_areas;
create policy volunteer_areas_insert_own
on public.volunteer_service_areas for insert to authenticated
with check (volunteer_account_id = (select auth.uid()));
create policy volunteer_areas_delete_own
on public.volunteer_service_areas for delete to authenticated
using (volunteer_account_id = (select auth.uid()));
