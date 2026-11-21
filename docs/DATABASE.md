# Database foundation

## Migrations

1. `create_aafatlink_domain` creates accounts, authorization, volunteer readiness, requests, evidence, verification, priority, assignments, progress, completion, closure and audit tables.
2. `add_authorized_workflow_rpcs` creates idempotent transactional functions for request submission, evidence commit, verification, priority and volunteer assignment.
3. `configure_evidence_storage` creates the private evidence bucket and storage policies.
4. `add_account_onboarding` creates the Auth user trigger and restricted volunteer-registration RPC.
5. `harden_database_baseline` moves `btree_gist` out of `public`, removes Data API execution from Supabase's automatic-RLS event function, adds missing foreign-key indexes and removes duplicate SELECT policy evaluation.
6. `align_request_submission_with_uc01` adds the mandatory request fields, request source, duplicate flag, 5 MB evidence limit and same-ID resubmission transaction required by Deliverable 3.
7. `allow_requester_progress_tracking` permits requesters to read progress only through assignments on their own requests.
8. `add_reliable_evidence_staging` adds invoker-based staging and reconciliation functions plus one tightly scoped definer cleanup function.
9. `align_priority_with_uc04` records previous priority and permits reprioritization only while Verified, Assigned or In Progress.
10. `add_volunteer_coordination_workflows` adds readiness status, reference data, candidate matching, conflict-aware assignment, reassignment and volunteer response RPCs.
11. `index_volunteer_assignment_matches` covers both UC05 reference joins reported by the hosted performance advisor.
12. `add_task_execution_and_closure_workflows` adds append-only progress, numbered completion attempts, completion-evidence links and idempotent progress, completion and closure RPCs.
13. `allow_volunteer_rework_visibility` permits only the assigned volunteer, requester owner and privileged reviewers to read the relevant closure decision.
14. `add_reporting_and_access_management` adds four authorized committed-data report functions and the atomic, idempotent UC12 access-change workflow.
15. `align_coordinator_entry_and_review_details` permits coordinator-recorded phone requests without inventing a requester account, snapshots caller details separately from the recorder and returns the full UC03 review context.
16. `minimize_request_review_privileges` moves requester contact snapshotting to an invoker trigger and makes review-detail retrieval `SECURITY INVOKER`.
17. `support_coordinator_request_operations` extends operation begin/abandon handling to the authorized coordinator-recording operation without weakening requester ownership checks.

All exposed `public` tables enable RLS. Broad `anon` and `authenticated` table privileges are revoked before minimal grants are added.

## Assignment conflict rule

UC05 does not impose a blanket one-active-assignment rule. PostgreSQL prevents overlapping scheduled active assignments for the same volunteer through a GiST exclusion constraint and repeats the conflict check while the assignment RPC holds relevant row locks. Non-overlapping scheduled assignments remain valid. If either the proposed or existing active assignment is unscheduled, the operation is conservatively treated as a conflict because non-overlap cannot be proved. One active assignment per request is also enforced.

Active means `PENDING_ACCEPTANCE`, `ACCEPTED` or `IN_PROGRESS` for volunteer conflicts. `AWAITING_CLOSURE_REVIEW` no longer occupies field time, but it remains the one active assignment attached to its request until closure or rework.

## Evidence recovery

Evidence paths use `<actor-id>/<operation-id>/<filename>` in a private bucket. A client creates a PENDING operation, uploads an object, records a STAGED evidence row, and commits it through RPC. After a timeout, the client must read `workflow_operations` by operation ID:

- `COMMITTED`: retain the object and use the stored result.
- `PENDING`: retry or reconcile; never delete.
- confirmed failed or absent: cleanup may be attempted.

The Storage DELETE policy independently rejects deletion when a `COMMITTED` evidence row references the object path.

## RPC security

Direct workflow table updates are not granted to desktop users. The public workflow RPCs therefore require definer rights. Each one derives the caller from `auth.uid()`, checks the database role, uses `search_path = ''`, fully qualifies objects, revokes execution from `PUBLIC` and `anon`, and grants only `authenticated` execution.

The private role lookup is the sole base helper requiring definer rights because an invoker lookup inside the accounts policy would recurse through account RLS. It lives in a non-exposed schema and applies the same identity and search-path controls.

## Validation status

All seventeen migrations through coordinator request/review alignment are applied to the designated `aafatlink-dev` project. Hosted checks confirmed the workflow functions and their invoker/definer modes. Request-review details and the request-status, outstanding-request and response-performance reports use `SECURITY INVOKER`. Coordinator phone recording, volunteer workload and atomic access changes use narrowly scoped definer boundaries because they cross deliberate RLS boundaries; each derives the actor from `auth.uid()`, repeats authorization checks, uses an empty search path and is executable only by authenticated users.

Security and performance advisors were rerun after deployment. SECURITY DEFINER notices cover the reviewed atomic/privacy boundaries described above; leaked-password protection remains an owner-controlled hosted Auth setting.

Java contract tests check RLS coverage, request status vocabulary, assignment conflict constraints, definer hardening, hosted hardening and evidence cleanup protection. Docker is still unavailable, so `supabase db reset` and local pgTAP execution remain blocked.
