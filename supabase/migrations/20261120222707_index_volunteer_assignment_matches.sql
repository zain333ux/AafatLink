-- Cover UC05 reference joins reported by the hosted performance advisor.
create index if not exists volunteer_assignments_matched_skill_idx
  on public.volunteer_assignments (matched_skill_id);

create index if not exists volunteer_assignments_matched_service_area_idx
  on public.volunteer_assignments (matched_service_area_id);
