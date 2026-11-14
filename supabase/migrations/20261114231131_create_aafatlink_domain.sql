-- AafatLink domain foundation. Apply only to a designated development project.
create extension if not exists pgcrypto;
create extension if not exists btree_gist;

create type public.app_role as enum ('REQUESTER', 'VOLUNTEER', 'COORDINATOR', 'ADMINISTRATOR');
create type public.account_status as enum ('ACTIVE', 'SUSPENDED', 'DISABLED');
create type public.volunteer_approval_status as enum ('PENDING', 'APPROVED', 'REJECTED');
create type public.request_status as enum (
  'PENDING_VERIFICATION', 'ADDITIONAL_INFORMATION_REQUIRED', 'VERIFIED', 'ASSIGNED',
  'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW', 'CLOSED', 'REJECTED'
);
create type public.request_priority as enum ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW');
create type public.assignment_status as enum (
  'PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW',
  'COMPLETED', 'DECLINED', 'REASSIGNED'
);
create type public.operation_status as enum ('PENDING', 'COMMITTED', 'FAILED');
create type public.evidence_state as enum ('STAGED', 'COMMITTED', 'ORPHAN_CANDIDATE');
create type public.verification_decision as enum ('VERIFIED', 'REJECTED', 'ADDITIONAL_INFORMATION_REQUIRED');
create type public.closure_decision as enum ('APPROVED', 'RETURNED_FOR_REWORK');

create table public.accounts (
  id uuid primary key references auth.users(id) on delete restrict,
  email text not null,
  display_name text not null check (length(btrim(display_name)) between 2 and 120),
  phone text,
  role public.app_role not null default 'REQUESTER',
  status public.account_status not null default 'ACTIVE',
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (email)
);

create table public.permissions (
  code text primary key check (code ~ '^[a-z][a-z0-9_.-]+$'),
  description text not null
);

create table public.role_permissions (
  role public.app_role not null,
  permission_code text not null references public.permissions(code) on delete cascade,
  primary key (role, permission_code)
);

create table public.skills (
  id bigint generated always as identity primary key,
  name text not null unique check (length(btrim(name)) between 2 and 80),
  active boolean not null default true
);

create table public.service_areas (
  id bigint generated always as identity primary key,
  name text not null unique check (length(btrim(name)) between 2 and 120),
  active boolean not null default true
);

create table public.volunteer_profiles (
  account_id uuid primary key references public.accounts(id) on delete restrict,
  approval_status public.volunteer_approval_status not null default 'PENDING',
  availability_notes text,
  available_from timestamptz,
  available_until timestamptz,
  approved_by_account_id uuid references public.accounts(id) on delete restrict,
  approved_at timestamptz,
  rejection_reason text,
  version bigint not null default 0 check (version >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint volunteer_availability_order check (
    available_from is null or available_until is null or available_from < available_until
  ),
  constraint volunteer_approval_audit check (
    (approval_status = 'PENDING' and approved_by_account_id is null and approved_at is null)
    or (approval_status = 'APPROVED' and approved_by_account_id is not null and approved_at is not null and rejection_reason is null)
    or (approval_status = 'REJECTED' and approved_by_account_id is not null and approved_at is not null and length(btrim(rejection_reason)) > 0)
  )
);

create table public.volunteer_skills (
  volunteer_account_id uuid not null references public.volunteer_profiles(account_id) on delete cascade,
  skill_id bigint not null references public.skills(id) on delete restrict,
  primary key (volunteer_account_id, skill_id)
);

create table public.volunteer_service_areas (
  volunteer_account_id uuid not null references public.volunteer_profiles(account_id) on delete cascade,
  service_area_id bigint not null references public.service_areas(id) on delete restrict,
  primary key (volunteer_account_id, service_area_id)
);

create table public.workflow_operations (
  operation_id uuid primary key,
  actor_account_id uuid not null references public.accounts(id) on delete restrict,
  operation_type text not null check (length(btrim(operation_type)) between 3 and 80),
  status public.operation_status not null default 'PENDING',
  result jsonb,
  failure_code text,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  constraint operation_completion_consistency check (
    (status = 'PENDING' and completed_at is null)
    or (status in ('COMMITTED', 'FAILED') and completed_at is not null)
  )
);

create table public.assistance_requests (
  id uuid primary key default gen_random_uuid(),
  requester_account_id uuid not null references public.accounts(id) on delete restrict,
  recorded_by_account_id uuid not null references public.accounts(id) on delete restrict,
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  disaster_type text not null check (length(btrim(disaster_type)) between 2 and 80),
  location_address text not null check (length(btrim(location_address)) between 5 and 300),
  latitude numeric(9, 6) check (latitude between -90 and 90),
  longitude numeric(9, 6) check (longitude between -180 and 180),
  assistance_details text not null check (length(btrim(assistance_details)) between 10 and 4000),
  status public.request_status not null default 'PENDING_VERIFICATION',
  priority public.request_priority,
  information_request_reason text,
  rejection_reason text,
  version bigint not null default 0 check (version >= 0),
  submitted_at timestamptz not null default now(),
  resubmitted_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint request_status_reason_consistency check (
    (status = 'ADDITIONAL_INFORMATION_REQUIRED' and length(btrim(information_request_reason)) > 0)
    or (status = 'REJECTED' and length(btrim(rejection_reason)) > 0)
    or (status not in ('ADDITIONAL_INFORMATION_REQUIRED', 'REJECTED'))
  )
);

create table public.request_evidence (
  id uuid primary key default gen_random_uuid(),
  request_id uuid references public.assistance_requests(id) on delete restrict,
  operation_id uuid not null references public.workflow_operations(operation_id) on delete restrict,
  uploaded_by_account_id uuid not null references public.accounts(id) on delete restrict,
  bucket_id text not null default 'request-evidence',
  object_path text not null,
  original_filename text not null,
  content_type text not null,
  size_bytes bigint not null check (size_bytes between 1 and 10485760),
  state public.evidence_state not null default 'STAGED',
  created_at timestamptz not null default now(),
  committed_at timestamptz,
  unique (bucket_id, object_path),
  constraint evidence_commit_consistency check (
    (state = 'COMMITTED' and request_id is not null and committed_at is not null)
    or (state <> 'COMMITTED' and committed_at is null)
  )
);

create table public.request_verifications (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.assistance_requests(id) on delete restrict,
  coordinator_account_id uuid not null references public.accounts(id) on delete restrict,
  decision public.verification_decision not null,
  reason text,
  reviewed_request_version bigint not null,
  created_at timestamptz not null default now(),
  constraint verification_reason_required check (
    decision = 'VERIFIED' or length(btrim(reason)) > 0
  )
);

create table public.priority_assessments (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.assistance_requests(id) on delete restrict,
  coordinator_account_id uuid not null references public.accounts(id) on delete restrict,
  suggested_priority public.request_priority not null,
  selected_priority public.request_priority not null,
  override_justification text,
  reviewed_request_version bigint not null,
  created_at timestamptz not null default now(),
  constraint priority_override_justification check (
    suggested_priority = selected_priority or length(btrim(override_justification)) > 0
  )
);

create table public.volunteer_assignments (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.assistance_requests(id) on delete restrict,
  volunteer_account_id uuid not null references public.volunteer_profiles(account_id) on delete restrict,
  assigned_by_account_id uuid not null references public.accounts(id) on delete restrict,
  replaces_assignment_id uuid references public.volunteer_assignments(id) on delete restrict,
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  status public.assignment_status not null default 'PENDING_ACCEPTANCE',
  scheduled_start_at timestamptz,
  scheduled_end_at timestamptz,
  active_window tstzrange generated always as (
    case when scheduled_start_at is not null and scheduled_end_at is not null
      then tstzrange(scheduled_start_at, scheduled_end_at, '[)')
    end
  ) stored,
  reassignment_reason text,
  version bigint not null default 0 check (version >= 0),
  assigned_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint assignment_schedule_complete check (
    (scheduled_start_at is null and scheduled_end_at is null)
    or (scheduled_start_at is not null and scheduled_end_at is not null and scheduled_start_at < scheduled_end_at)
  )
);

alter table public.volunteer_assignments
  add constraint volunteer_assignment_no_time_overlap
  exclude using gist (volunteer_account_id with =, active_window with &&)
  where (status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS') and active_window is not null);

create unique index volunteer_one_unscheduled_active_assignment_idx
  on public.volunteer_assignments (volunteer_account_id)
  where scheduled_start_at is null and scheduled_end_at is null
    and status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS');

create unique index request_one_active_assignment_idx
  on public.volunteer_assignments (request_id)
  where status in ('PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW');

create table public.assignment_decisions (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.volunteer_assignments(id) on delete restrict,
  volunteer_account_id uuid not null references public.accounts(id) on delete restrict,
  accepted boolean not null,
  decline_reason text,
  created_at timestamptz not null default now(),
  constraint assignment_decline_reason check (accepted or length(btrim(decline_reason)) > 0)
);

create table public.task_progress_updates (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.volunteer_assignments(id) on delete restrict,
  volunteer_account_id uuid not null references public.accounts(id) on delete restrict,
  note text not null check (length(btrim(note)) between 2 and 2000),
  percentage smallint check (percentage between 0 and 99),
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  created_at timestamptz not null default now()
);

create table public.task_completions (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null unique references public.volunteer_assignments(id) on delete restrict,
  volunteer_account_id uuid not null references public.accounts(id) on delete restrict,
  remarks text not null check (length(btrim(remarks)) between 2 and 4000),
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  completed_at timestamptz not null default now()
);

create table public.closure_reviews (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.assistance_requests(id) on delete restrict,
  assignment_id uuid not null references public.volunteer_assignments(id) on delete restrict,
  coordinator_account_id uuid not null references public.accounts(id) on delete restrict,
  decision public.closure_decision not null,
  reason text,
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  created_at timestamptz not null default now(),
  constraint closure_return_reason check (
    decision = 'APPROVED' or length(btrim(reason)) > 0
  )
);

create table public.volunteer_approval_reviews (
  id uuid primary key default gen_random_uuid(),
  volunteer_account_id uuid not null references public.volunteer_profiles(account_id) on delete restrict,
  administrator_account_id uuid not null references public.accounts(id) on delete restrict,
  decision public.volunteer_approval_status not null check (decision <> 'PENDING'),
  reason text,
  created_at timestamptz not null default now(),
  constraint volunteer_rejection_reason check (decision = 'APPROVED' or length(btrim(reason)) > 0)
);

create table public.access_change_audit (
  id uuid primary key default gen_random_uuid(),
  target_account_id uuid not null references public.accounts(id) on delete restrict,
  actor_account_id uuid not null references public.accounts(id) on delete restrict,
  action text not null check (length(btrim(action)) between 3 and 80),
  before_state jsonb not null,
  after_state jsonb not null,
  reason text,
  operation_id uuid not null unique references public.workflow_operations(operation_id) on delete restrict,
  created_at timestamptz not null default now()
);

-- Foreign-key and queue indexes.
create index volunteer_profiles_approved_by_idx on public.volunteer_profiles (approved_by_account_id);
create index volunteer_skills_skill_idx on public.volunteer_skills (skill_id);
create index volunteer_service_areas_area_idx on public.volunteer_service_areas (service_area_id);
create index workflow_operations_actor_idx on public.workflow_operations (actor_account_id, created_at desc);
create index assistance_requests_requester_idx on public.assistance_requests (requester_account_id, submitted_at desc);
create index assistance_requests_recorder_idx on public.assistance_requests (recorded_by_account_id);
create index assistance_requests_verification_queue_idx on public.assistance_requests (submitted_at)
  where status = 'PENDING_VERIFICATION';
create index assistance_requests_operational_queue_idx on public.assistance_requests (priority, updated_at)
  where status in ('VERIFIED', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_CLOSURE_REVIEW');
create index request_evidence_request_idx on public.request_evidence (request_id);
create index request_evidence_operation_idx on public.request_evidence (operation_id);
create index request_verifications_request_idx on public.request_verifications (request_id, created_at desc);
create index request_verifications_coordinator_idx on public.request_verifications (coordinator_account_id);
create index priority_assessments_request_idx on public.priority_assessments (request_id, created_at desc);
create index priority_assessments_coordinator_idx on public.priority_assessments (coordinator_account_id);
create index volunteer_assignments_volunteer_idx on public.volunteer_assignments (volunteer_account_id, updated_at desc);
create index volunteer_assignments_assigner_idx on public.volunteer_assignments (assigned_by_account_id);
create index volunteer_assignments_replaced_idx on public.volunteer_assignments (replaces_assignment_id);
create index assignment_decisions_assignment_idx on public.assignment_decisions (assignment_id);
create index assignment_decisions_volunteer_idx on public.assignment_decisions (volunteer_account_id);
create index task_progress_assignment_idx on public.task_progress_updates (assignment_id, created_at);
create index task_progress_volunteer_idx on public.task_progress_updates (volunteer_account_id);
create index task_completions_volunteer_idx on public.task_completions (volunteer_account_id);
create index closure_reviews_request_idx on public.closure_reviews (request_id, created_at desc);
create index closure_reviews_assignment_idx on public.closure_reviews (assignment_id);
create index closure_reviews_coordinator_idx on public.closure_reviews (coordinator_account_id);
create index volunteer_approval_admin_idx on public.volunteer_approval_reviews (administrator_account_id);
create index access_change_target_idx on public.access_change_audit (target_account_id, created_at desc);
create index access_change_actor_idx on public.access_change_audit (actor_account_id, created_at desc);

-- This private helper is the one necessary SECURITY DEFINER function in the base
-- migration. It avoids recursive account RLS while deriving identity from auth.uid().
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.current_app_role()
returns public.app_role
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  actor_id uuid := auth.uid();
  actor_role public.app_role;
begin
  if actor_id is null then
    return null;
  end if;
  select a.role into actor_role from public.accounts a where a.id = actor_id and a.status = 'ACTIVE';
  return actor_role;
end;
$$;

revoke all on function private.current_app_role() from public, anon;
grant execute on function private.current_app_role() to authenticated;

-- RLS is enabled on every public table. Policies are intentionally operation-specific.
alter table public.accounts enable row level security;
alter table public.permissions enable row level security;
alter table public.role_permissions enable row level security;
alter table public.skills enable row level security;
alter table public.service_areas enable row level security;
alter table public.volunteer_profiles enable row level security;
alter table public.volunteer_skills enable row level security;
alter table public.volunteer_service_areas enable row level security;
alter table public.workflow_operations enable row level security;
alter table public.assistance_requests enable row level security;
alter table public.request_evidence enable row level security;
alter table public.request_verifications enable row level security;
alter table public.priority_assessments enable row level security;
alter table public.volunteer_assignments enable row level security;
alter table public.assignment_decisions enable row level security;
alter table public.task_progress_updates enable row level security;
alter table public.task_completions enable row level security;
alter table public.closure_reviews enable row level security;
alter table public.volunteer_approval_reviews enable row level security;
alter table public.access_change_audit enable row level security;

create policy accounts_select_own_or_admin on public.accounts for select to authenticated
using (id = (select auth.uid()) or (select private.current_app_role()) = 'ADMINISTRATOR');
create policy accounts_update_own_profile on public.accounts for update to authenticated
using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy permissions_read_authenticated on public.permissions for select to authenticated using (true);
create policy role_permissions_read_authenticated on public.role_permissions for select to authenticated using (true);
create policy skills_read_authenticated on public.skills for select to authenticated using (true);
create policy service_areas_read_authenticated on public.service_areas for select to authenticated using (true);

create policy volunteer_profiles_select on public.volunteer_profiles for select to authenticated
using (account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy volunteer_profiles_update_own on public.volunteer_profiles for update to authenticated
using (account_id = (select auth.uid())) with check (account_id = (select auth.uid()));
create policy volunteer_skills_select on public.volunteer_skills for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy volunteer_skills_manage_own on public.volunteer_skills for all to authenticated
using (volunteer_account_id = (select auth.uid())) with check (volunteer_account_id = (select auth.uid()));
create policy volunteer_areas_select on public.volunteer_service_areas for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy volunteer_areas_manage_own on public.volunteer_service_areas for all to authenticated
using (volunteer_account_id = (select auth.uid())) with check (volunteer_account_id = (select auth.uid()));

create policy operations_select_own_or_admin on public.workflow_operations for select to authenticated
using (actor_account_id = (select auth.uid()) or (select private.current_app_role()) = 'ADMINISTRATOR');
create policy operations_insert_own on public.workflow_operations for insert to authenticated
with check (actor_account_id = (select auth.uid()));

create policy requests_select_authorized on public.assistance_requests for select to authenticated
using (requester_account_id = (select auth.uid())
  or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy requests_insert_authorized on public.assistance_requests for insert to authenticated
with check (
  status = 'PENDING_VERIFICATION' and priority is null
  and (
    (requester_account_id = (select auth.uid()) and recorded_by_account_id = (select auth.uid()))
    or (recorded_by_account_id = (select auth.uid()) and (select private.current_app_role()) = 'COORDINATOR')
  )
);

create policy evidence_select_authorized on public.request_evidence for select to authenticated
using (uploaded_by_account_id = (select auth.uid())
  or exists (select 1 from public.assistance_requests r where r.id = request_id));
create policy evidence_insert_own_staging on public.request_evidence for insert to authenticated
with check (
  uploaded_by_account_id = (select auth.uid()) and state = 'STAGED' and request_id is null
  and exists (
    select 1 from public.workflow_operations operation
    where operation.operation_id = request_evidence.operation_id
      and operation.actor_account_id = (select auth.uid())
      and operation.status = 'PENDING'
  )
);

create policy verifications_select on public.request_verifications for select to authenticated
using (exists (select 1 from public.assistance_requests r where r.id = request_id));
create policy priorities_select on public.priority_assessments for select to authenticated
using (exists (select 1 from public.assistance_requests r where r.id = request_id));

create policy assignments_select on public.volunteer_assignments for select to authenticated
using (volunteer_account_id = (select auth.uid())
  or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR')
  or exists (select 1 from public.assistance_requests r where r.id = request_id and r.requester_account_id = (select auth.uid())));
create policy assignment_decisions_select on public.assignment_decisions for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy progress_select on public.task_progress_updates for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy completions_select on public.task_completions for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR'));
create policy closure_reviews_select on public.closure_reviews for select to authenticated
using ((select private.current_app_role()) in ('COORDINATOR', 'ADMINISTRATOR')
  or exists (select 1 from public.assistance_requests r where r.id = request_id and r.requester_account_id = (select auth.uid())));
create policy volunteer_approval_select on public.volunteer_approval_reviews for select to authenticated
using (volunteer_account_id = (select auth.uid()) or (select private.current_app_role()) = 'ADMINISTRATOR');
create policy access_audit_admin_select on public.access_change_audit for select to authenticated
using ((select private.current_app_role()) = 'ADMINISTRATOR');

-- Remove broad defaults, then grant only the Data API operations currently required.
revoke all on all tables in schema public from anon, authenticated;
grant select on public.accounts, public.permissions, public.role_permissions, public.skills, public.service_areas,
  public.volunteer_profiles, public.volunteer_skills, public.volunteer_service_areas, public.workflow_operations,
  public.assistance_requests, public.request_evidence, public.request_verifications, public.priority_assessments,
  public.volunteer_assignments, public.assignment_decisions, public.task_progress_updates, public.task_completions,
  public.closure_reviews, public.volunteer_approval_reviews, public.access_change_audit to authenticated;
grant insert on public.workflow_operations, public.assistance_requests, public.request_evidence,
  public.volunteer_skills, public.volunteer_service_areas to authenticated;
grant delete on public.volunteer_skills, public.volunteer_service_areas to authenticated;
grant update (display_name, phone, version, updated_at) on public.accounts to authenticated;
grant update (availability_notes, available_from, available_until, version, updated_at)
  on public.volunteer_profiles to authenticated;

insert into public.permissions (code, description) values
  ('request.submit', 'Submit and resubmit an assistance request'),
  ('request.track', 'Track owned assistance requests'),
  ('request.verify', 'Verify or reject assistance requests'),
  ('request.prioritize', 'Set request priority'),
  ('assignment.manage', 'Assign and reassign volunteers'),
  ('assignment.respond', 'Accept or decline owned assignments'),
  ('task.update', 'Record progress on owned assignments'),
  ('task.complete', 'Complete owned assignments'),
  ('request.close', 'Approve closure or return work for rework'),
  ('report.generate', 'Generate disaster response reports'),
  ('access.manage', 'Manage roles, status and volunteer approval');

insert into public.role_permissions (role, permission_code) values
  ('REQUESTER', 'request.submit'), ('REQUESTER', 'request.track'),
  ('VOLUNTEER', 'assignment.respond'), ('VOLUNTEER', 'task.update'), ('VOLUNTEER', 'task.complete'),
  ('COORDINATOR', 'request.submit'), ('COORDINATOR', 'request.verify'),
  ('COORDINATOR', 'request.prioritize'), ('COORDINATOR', 'assignment.manage'),
  ('COORDINATOR', 'request.close'), ('COORDINATOR', 'report.generate'),
  ('ADMINISTRATOR', 'report.generate'), ('ADMINISTRATOR', 'access.manage');
