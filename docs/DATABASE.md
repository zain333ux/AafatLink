# Database foundation

## Migrations

1. `create_aafatlink_domain` creates accounts, authorization, volunteer readiness, requests, evidence, verification, priority, assignments, progress, completion, closure and audit tables.
2. `add_authorized_workflow_rpcs` creates idempotent transactional functions for request submission, evidence commit, verification, priority and volunteer assignment.
3. `configure_evidence_storage` creates the private evidence bucket and storage policies.

All exposed `public` tables enable RLS. Broad `anon` and `authenticated` table privileges are revoked before minimal grants are added.

## Assignment conflict rule

UC05 does not impose a blanket one-active-assignment rule. PostgreSQL prevents overlapping scheduled active assignments for the same volunteer through a GiST exclusion constraint. Non-overlapping scheduled assignments remain valid. Because an unscheduled assignment has no interval to compare, at most one unscheduled active assignment is allowed per volunteer. One active assignment per request is also enforced.

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

## Local validation limit

Java contract tests check RLS coverage, request status vocabulary, assignment conflict constraints, definer hardening and evidence cleanup protection. Applying and exercising the SQL needs Docker for the local Supabase stack or a designated development project; neither is currently available.
