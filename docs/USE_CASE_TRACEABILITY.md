# Mandatory use-case traceability

Deliverable 3 is the functional authority. A use case is marked complete only when its required main flow, important alternatives and automated tests are implemented. Phase 1 establishes destinations and traceability; it does not claim workflow completion.

## UC01: Submit Disaster Assistance Request

- **Status:** In progress; domain, controller, repository and database transaction exist. JavaFX form and evidence staging UI remain.
- **Java:** `application.request.RequestController`, `domain.request.AssistanceRequestDraft`, `AssistanceRequest`, `DisasterType`, `RequestSubmissionResult`, `repository.WorkflowRepository` and `infrastructure.supabase.SupabaseWorkflowRepository`.
- **Methods:** `RequestController.submitRequest`, `RequestController.resubmitRequest`, `WorkflowRepository.submitRequest` and `WorkflowRepository.resubmitRequest`.
- **Screen:** Requester > Submit request.
- **Database:** `assistance_requests`, `request_evidence`, `workflow_operations`; deployed `submit_assistance_request` and `resubmit_assistance_request` RPCs.
- **Success:** validate, check duplicates, stage evidence, commit the request as `PENDING_VERIFICATION`, return the request ID.
- **Alternatives:** coordinator recording with separate requester/recorder IDs; duplicate warning; safe evidence retry; resubmission of `ADDITIONAL_INFORMATION_REQUIRED` using the same ID.
- **Tests:** `AssistanceRequestDraftTest`, `RequestControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest`. Live idempotency and evidence-recovery tests remain.
- **Demonstration:** create a request, acknowledge any duplicate warning, submit, then resubmit the same request after an information-return decision.

## UC02: Track Assistance Request Status

- **Status:** In progress; owner-scoped request list/detail queries exist. JavaFX screens and assignment/progress timeline mapping remain.
- **Java:** `application.request.RequestController`, `domain.request.AssistanceRequest`, `repository.WorkflowRepository` and `infrastructure.supabase.SupabaseWorkflowRepository`.
- **Methods:** `RequestController.getMyRequests`, `RequestController.getRequestDetails`, `WorkflowRepository.findRequestsByRequester` and `WorkflowRepository.findRequestForRequester`.
- **Screen:** Requester > My requests > Request detail.
- **Database:** `assistance_requests`, verification, priority, assignment, progress, completion and closure history under requester-owned RLS.
- **Success:** show the request's current status and chronological progress without exposing other requesters' records.
- **Alternatives:** empty state, network retry and requests awaiting more information.
- **Tests:** `RequestControllerTest` proves authenticated identity scoping. Repository list/detail, timeline ordering and unavailable-service tests remain.
- **Demonstration:** sign in as a requester and open the status timeline for a seeded request.

## UC03: Verify Assistance Request

- **Status:** Planned; Coordinator navigation destination exists only.
- **Java:** `application.RequestReviewController`; request review domain service and `WorkflowRepository`.
- **Methods:** planned `loadVerificationQueue`, `verify`, `reject`, `requestAdditionalInformation`.
- **Screen:** Coordinator > Verification queue > Review request.
- **Database:** `request_verifications`, `assistance_requests`; planned `decide_request_verification` RPC.
- **Success:** an authorized coordinator records verification and moves a pending request to `VERIFIED`.
- **Alternatives:** reject with reason, return for information with reason, duplicate evidence review and stale-version conflict.
- **Tests:** planned transition, mandatory reason, authorization and concurrency tests.
- **Demonstration:** review a pending request and exercise verify and return-for-information paths.

## UC04: Prioritize Emergency Request

- **Status:** Planned; Coordinator navigation destination exists only.
- **Java:** `application.RequestReviewController`; planned `PriorityAssessmentService` and `WorkflowRepository`.
- **Methods:** planned `suggestPriority` and `recordPriorityAssessment`.
- **Screen:** Coordinator > Priority queue > Priority assessment.
- **Database:** `priority_assessments`, `assistance_requests`; planned `record_priority_assessment` RPC.
- **Success:** calculate a suggestion and record Critical, High, Medium or Low separately from request status.
- **Alternatives:** a manual override requires justification; stale-version conflict.
- **Tests:** planned scoring, override justification and priority-not-status tests.
- **Demonstration:** assess a verified request, show the suggestion, then justify an override.

## UC05: Assign Volunteer to Assistance Request

- **Status:** Planned; Coordinator navigation destination exists only.
- **Java:** `application.AssignmentController`; volunteer eligibility/domain scheduling services, `VolunteerRepository` and `WorkflowRepository`.
- **Methods:** planned `findCandidates`, `assignVolunteer`, `reassignVolunteer`.
- **Screen:** Coordinator > Volunteer assignment.
- **Database:** `volunteer_assignments`, profiles, skills and service areas; planned `assign_volunteer` RPC plus active-time exclusion constraints.
- **Success:** choose an approved, capable and available volunteer, create `PENDING_ACCEPTANCE`, and move the request to `ASSIGNED`.
- **Alternatives:** overlapping active assignments are rejected; non-overlapping work is allowed; reassignment records a reason and supersedes the old assignment.
- **Tests:** planned eligibility, approval, overlap, non-overlap, unscheduled conflict and concurrent assignment tests.
- **Demonstration:** compare candidate eligibility, create an assignment, then show a conflicting and a valid nonconflicting case.

## UC06: Close Assistance Request

- **Status:** Planned; Coordinator navigation destination exists only.
- **Java:** `application.RequestReviewController`; planned closure service and `WorkflowRepository`.
- **Methods:** planned `approveClosure` and `returnForRework`.
- **Screen:** Coordinator > Closure review.
- **Database:** `closure_reviews`, `assistance_requests`, `volunteer_assignments`; planned `review_request_closure` RPC.
- **Success:** approve a request awaiting review and atomically mark request `CLOSED` and assignment `COMPLETED`.
- **Alternatives:** return for rework with a mandatory reason; stale-version conflict.
- **Tests:** planned atomic transition, rework reason, authorization and concurrency tests.
- **Demonstration:** inspect completion evidence, approve closure, then demonstrate a separate return-for-rework record.

## UC07: Maintain Volunteer Readiness Profile

- **Status:** Planned; Volunteer navigation destination exists only.
- **Java:** `application.VolunteerController`; `UserRepository`, `VolunteerRepository` and readiness validation service.
- **Methods:** planned `getReadinessProfile` and `updateReadinessProfile`.
- **Screen:** Volunteer > Readiness profile.
- **Database:** `volunteer_profiles`, `volunteer_skills`, `volunteer_service_areas`.
- **Success:** save availability, skills and service areas for the authenticated volunteer.
- **Alternatives:** pending approval remains visible and prevents assignment eligibility; invalid availability or reference values are rejected.
- **Tests:** planned ownership, approval-state and validation tests.
- **Demonstration:** update readiness as an approved volunteer and show the awaiting-approval state for a new volunteer.

## UC08: Respond to Assignment

- **Status:** Planned; Volunteer navigation destination exists only.
- **Java:** `application.AssignmentController`; assignment domain service and `WorkflowRepository`.
- **Methods:** planned `acceptAssignment` and `declineAssignment`.
- **Screen:** Volunteer > Assignments > Assignment detail.
- **Database:** `volunteer_assignments`, `assignment_decisions`; planned `respond_to_assignment` RPC.
- **Success:** the assigned volunteer accepts a pending assignment, moving it to `ACCEPTED`.
- **Alternatives:** decline requires a reason and returns the request for reassignment; expired or superseded assignments cannot be accepted.
- **Tests:** planned ownership, mandatory decline reason, status and race tests.
- **Demonstration:** accept one pending assignment and decline another with a reason.

## UC09: Update Assistance Task Progress

- **Status:** Planned; Volunteer task destination exists only.
- **Java:** `application.AssignmentController`; progress validation service and `WorkflowRepository`.
- **Methods:** planned `addProgressUpdate`.
- **Screen:** Volunteer > Task progress.
- **Database:** `task_progress_updates`, `volunteer_assignments`, `assistance_requests`; planned `record_task_progress` RPC.
- **Success:** record a note and optional 0 to 99 percent value, moving accepted work to `IN_PROGRESS` atomically.
- **Alternatives:** reject invalid percentage, wrong volunteer, completed assignment or stale version.
- **Tests:** planned boundary, transition, ownership and concurrency tests.
- **Demonstration:** add progress without a percentage, then add a valid percentage and inspect history.

## UC10: Complete Assistance Task

- **Status:** Planned; Volunteer task destination exists only.
- **Java:** `application.AssignmentController`; completion validation/evidence services and `WorkflowRepository`.
- **Methods:** planned `completeTask` and uncertain-operation recovery.
- **Screen:** Volunteer > Task progress > Complete task.
- **Database:** `task_completions`, `request_evidence`, assignments and requests; planned `complete_assistance_task` RPC.
- **Success:** record completion remarks and optional evidence, then atomically move assignment and request to `AWAITING_CLOSURE_REVIEW`.
- **Alternatives:** staged-upload failure, uncertain RPC result resolved by operation ID, invalid owner and safe retry.
- **Tests:** planned idempotency, evidence reference safety, ownership and atomic transition tests.
- **Demonstration:** stage evidence, complete the task and show its closure-review state.

## UC11: Generate Disaster Response Reports

- **Status:** Planned; Coordinator and Administrator navigation destination exists only.
- **Java:** `application.ReportController`; planned `ReportGenerator` interface and four implementations with `ReportRepository`.
- **Methods:** planned `generateReport` and `exportCsv`.
- **Screen:** Reports.
- **Database:** authorized reporting views/RPCs over requests, assignments and history.
- **Success:** generate Request Status Summary, Outstanding Requests, Volunteer Workload or Response Performance with validated filters.
- **Alternatives:** empty result, invalid date range, export failure and restricted scope.
- **Tests:** planned calculation, filter, polymorphism, CSV escaping and authorization tests.
- **Demonstration:** generate each report from sample data and export one CSV.

## UC12: Configure User Access Permissions

- **Status:** Planned; Administrator navigation destination exists only.
- **Java:** `application.AccessController`; `UserRepository` and required `WorkflowRepository` dependency.
- **Methods:** planned `changeRole`, `changeAccountStatus`, `reviewVolunteerApproval`.
- **Screen:** Administrator > Access management.
- **Database:** `accounts`, `account_roles`, `role_permissions`, `volunteer_approval_reviews`, `access_change_audit`; restricted authorization RPCs.
- **Success:** an administrator performs an allowed access change and creates an immutable audit record.
- **Alternatives:** block self-promotion, last-administrator removal, active-assignment deactivation and unauthorized calls.
- **Tests:** planned privilege escalation, last-admin, active assignment, volunteer approval and audit tests.
- **Demonstration:** approve a volunteer, update a safe account permission, then show blocked self/last-admin operations.

## Additional portfolio features

The professional login surface, role-specific shell, account settings, search/filter controls, loading/empty/error states and dashboard visualizations are tracked separately. Their presence does not change the completion status of UC01 through UC12.
