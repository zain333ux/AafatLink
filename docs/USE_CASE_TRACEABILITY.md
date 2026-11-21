# Mandatory use-case traceability

Deliverable 3 is the functional authority. A use case is marked complete only when its required main flow, important alternatives and automated tests are implemented. Phase 1 establishes destinations and traceability; it does not claim workflow completion.

## UC01: Submit Disaster Assistance Request

- **Status:** Implemented and deployed; the working JavaFX form, application controller, private Storage staging and idempotent database transaction exist. A live authenticated demonstration record remains to be prepared.
- **Java:** `application.request.RequestController`, `ui.request.RequestFormViewController`, request domain records, `service.evidence.EvidenceFileValidator`, `EvidenceStorage`, `repository.WorkflowRepository`, `SupabaseWorkflowRepository` and `SupabaseEvidenceStorage`.
- **Methods:** `RequestController.submitRequest`, `RequestController.resubmitRequest`, `WorkflowRepository.submitRequest` and `WorkflowRepository.resubmitRequest`.
- **Screen:** `request-form-view.fxml`, opened from Requester > Submit request; returned requests reuse the screen with pre-filled fields and the coordinator reason.
- **Database:** `assistance_requests`, `request_evidence`, `workflow_operations`; deployed submit/resubmit, begin, register, reconcile and abandon evidence-operation RPCs.
- **Success:** validate, check duplicates, stage evidence, commit the request as `PENDING_VERIFICATION`, return the request ID.
- **Alternatives:** coordinator recording with separate requester/recorder IDs; duplicate warning; safe evidence retry; resubmission of `ADDITIONAL_INFORMATION_REQUIRED` using the same ID.
- **Tests:** `AssistanceRequestDraftTest`, `RequestStatusTest`, `EvidenceFileValidatorTest`, `RequestControllerTest`, `SupabaseWorkflowRepositoryTest`, `SupabaseEvidenceStorageTest` and `DatabaseMigrationContractTest`. Live hosted evidence recovery remains.
- **Demonstration:** create a request, acknowledge any duplicate warning, submit, then resubmit the same request after an information-return decision.

## UC02: Track Assistance Request Status

- **Status:** Implemented and deployed; owner-scoped request list/detail queries, assignment/progress history and the JavaFX list/detail screen exist. A seeded live demonstration record remains.
- **Java:** `application.request.RequestController`, `ui.request.RequestListViewController`, `domain.request.AssistanceRequest`, `RequestStatus`, `repository.WorkflowRepository` and `infrastructure.supabase.SupabaseWorkflowRepository`.
- **Methods:** `RequestController.getMyRequests`, `RequestController.getRequestDetails`, `WorkflowRepository.findRequestsByRequester` and `WorkflowRepository.findRequestForRequester`.
- **Screen:** `request-list-view.fxml`, opened from Requester > My requests; it includes loading, empty, retry, detail, lifecycle and resubmission states.
- **Database:** `assistance_requests`, `volunteer_assignments` and `task_progress_updates`; deployed migration `allow_requester_progress_tracking` scopes progress reads through requester-owned requests.
- **Success:** show the request's current status and chronological progress without exposing other requesters' records.
- **Alternatives:** empty state, network retry and requests awaiting more information.
- **Tests:** `RequestControllerTest` proves authenticated identity scoping; `RequestStatusTest` covers understandable labels and resubmission eligibility; `SupabaseWorkflowRepositoryTest` covers assignment/progress enrichment; `DatabaseMigrationContractTest` covers the owner-only progress policy. Hosted RLS execution and full history ordering remain.
- **Demonstration:** sign in as a requester and open the status timeline for a seeded request.

## UC03: Verify Assistance Request

- **Status:** Implemented and deployed for queue and decision paths; a seeded live demonstration transaction and richer evidence presentation remain.
- **Java:** `application.request.RequestReviewController`, `ui.request.RequestReviewViewController`, `VerificationDecision`, `RequestReviewResult`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `loadVerificationQueue`, `getRequestForReview`, `verify`, `reject`, `requestAdditionalInformation`, `findPendingVerificationRequests` and `decideRequestVerification`.
- **Screen:** Coordinator > Verification queue > Review request.
- **Database:** `request_verifications`, `assistance_requests`; existing authorized `decide_request_verification` RPC with row locking and expected-version validation.
- **Success:** an authorized coordinator records verification and moves a pending request to `VERIFIED`.
- **Alternatives:** reject with reason, return for information with reason, duplicate evidence review and stale-version conflict.
- **Tests:** `RequestReviewControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest`; hosted authorization/concurrency proof remains.
- **Demonstration:** review a pending request and exercise verify and return-for-information paths.

## UC04: Prioritize Emergency Request

- **Status:** Implemented and deployed; a seeded live demonstration transaction remains.
- **Java:** `application.request.RequestReviewController`, `ui.request.RequestReviewViewController`, `PriorityAssessmentService`, `PriorityAssessmentResult`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `loadPriorityQueue`, `suggestPriority`, `setPriority`, `findPrioritizableRequests` and `recordPriorityAssessment`.
- **Screen:** Coordinator > Priority queue > Priority assessment.
- **Database:** `priority_assessments`, `assistance_requests`; `record_priority_assessment` RPC plus pending `align_priority_with_uc04` migration for previous-level audit and documented reprioritization statuses.
- **Success:** calculate a suggestion and record Critical, High, Medium or Low separately from request status.
- **Alternatives:** a manual override requires justification; stale-version conflict.
- **Tests:** `PriorityAssessmentServiceTest`, `RequestReviewControllerTest`, `RequestStatusTest` and `DatabaseMigrationContractTest` cover scoring, justification, eligibility and priority-not-status behavior.
- **Demonstration:** assess a verified request, show the suggestion, then justify an override.

## UC05: Assign Volunteer to Assistance Request

- **Status:** Implemented and deployed; controller, eligibility rules, JavaFX workflow, protected repository boundaries and transactional RPCs are tested.
- **Java:** `application.assignment.AssignmentController`, `domain.assignment.AssignmentFilter`, `VolunteerCandidate`, `VolunteerAssignment`, `repository.VolunteerRepository`, `WorkflowRepository`, `SupabaseVolunteerRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `getAssignableRequests`, `findEligibleVolunteers`, `assignVolunteer`, `getActiveAssignments` and `reassignVolunteer`.
- **Screen:** Coordinator > Volunteer assignment.
- **Database:** `volunteer_assignments`, `volunteer_profiles`, `volunteer_skills`, `volunteer_service_areas`, `skills` and `service_areas`; deployed `find_eligible_volunteers`, `assign_volunteer_matching` and `reassign_volunteer` RPCs.
- **Success:** choose an approved, capable and available volunteer, create `PENDING_ACCEPTANCE`, and move the request to `ASSIGNED`.
- **Alternatives:** overlapping active assignments are rejected; non-overlapping work is allowed; reassignment records a reason and supersedes the old assignment.
- **Tests:** `AssignmentControllerTest`, `VolunteerProfileTest`, `VolunteerAssignmentTest` and `DatabaseMigrationContractTest` cover approval, skill/area matching, overlap, non-overlap, unscheduled conflicts, idempotency and stale versions.
- **Demonstration:** compare candidate eligibility, create an assignment, then show a conflicting and a valid nonconflicting case.

## UC06: Close Assistance Request

- **Status:** Implemented and deployed; coordinator closure approval and return-for-rework paths are connected through JavaFX, domain rules, the repository and an atomic RPC.
- **Java:** `application.request.RequestReviewController`, `ui.request.ClosureReviewViewController`, `domain.assignment.ClosureReview`, `ClosureDecision`, `TaskContext`, `VolunteerAssignment`, `repository.WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `loadClosureQueue`, `getClosureDetails`, `approveClosure`, `returnForRework`, `saveClosureAndStatus` and `reconcileClosureOperation`.
- **Screen:** Coordinator > Closure review (`closure-review-view.fxml`).
- **Database:** `closure_reviews`, `task_completions`, `task_progress_updates`, `request_evidence`, `assistance_requests`, `volunteer_assignments`, `workflow_operations`; deployed `review_request_closure` RPC and rework-visibility policy.
- **Success:** inspect the latest completion context, approve it and atomically mark the request `CLOSED` and assignment `COMPLETED` with an audit record.
- **Alternatives:** return both records to `IN_PROGRESS` with a mandatory reason visible to the assigned volunteer; reject stale versions, invalid states and unauthorized actors; reconcile uncertain RPC results by operation ID.
- **Tests:** `ClosureReviewTest`, `VolunteerAssignmentTest`, `RequestReviewControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest` cover decisions, state transitions, rework reason, authorization, idempotency and concurrency contracts.
- **Demonstration:** inspect completion evidence, approve closure, then demonstrate a separate return-for-rework record.

## UC07: Maintain Volunteer Readiness Profile

- **Status:** Implemented and deployed; authenticated ownership, readiness validation, reference selections and approval-state visibility are connected to JavaFX.
- **Java:** `application.volunteer.VolunteerController`, `domain.volunteer.VolunteerProfile`, `VolunteerReadinessChanges`, `VolunteerRepository`, `WorkflowRepository` and `SupabaseVolunteerRepository`.
- **Methods:** `getReadinessProfile`, `getSkills`, `getServiceAreas` and `updateReadiness`.
- **Screen:** Volunteer > Readiness profile.
- **Database:** `accounts`, `volunteer_profiles`, `volunteer_skills`, `volunteer_service_areas`, `skills` and `service_areas`; deployed SECURITY INVOKER `update_volunteer_readiness` RPC.
- **Success:** save availability, skills and service areas for the authenticated volunteer.
- **Alternatives:** pending approval remains visible and prevents assignment eligibility; invalid availability or reference values are rejected.
- **Tests:** `VolunteerControllerTest`, `VolunteerProfileTest` and `DatabaseMigrationContractTest` cover actor scoping, validation, approval preservation and database authorization.
- **Demonstration:** update readiness as an approved volunteer and show the awaiting-approval state for a new volunteer.

## UC08: Respond to Assignment

- **Status:** Implemented and deployed; the assignment list/detail screen connects to the domain object and an atomic response RPC.
- **Java:** `application.volunteer.VolunteerController`, `domain.assignment.VolunteerAssignment`, `AssignmentContext`, `AssignmentResponseResult`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `getPendingAssignments`, `getAssignmentDetails` and `respondToAssignment`; the domain `accept` and `decline` methods enforce state rules.
- **Screen:** Volunteer > Assignments > Assignment detail.
- **Database:** `volunteer_assignments`, `assignment_decisions`, `assistance_requests` and `workflow_operations`; deployed hardened `respond_to_assignment` RPC.
- **Success:** the assigned volunteer accepts a pending assignment, moving it to `ACCEPTED`.
- **Alternatives:** decline requires a reason and returns the request for reassignment; expired or superseded assignments cannot be accepted.
- **Tests:** `VolunteerControllerTest`, `VolunteerAssignmentTest` and `DatabaseMigrationContractTest` cover ownership delegation, mandatory decline reason, terminal states, idempotency and row locking.
- **Demonstration:** accept one pending assignment and decline another with a reason.

## UC09: Update Assistance Task Progress

- **Status:** Implemented and deployed; active-task lookup, append-only history, domain validation, JavaFX controls and an atomic version-checked RPC are connected.
- **Java:** `application.volunteer.VolunteerController`, `ui.volunteer.TaskProgressViewController`, `domain.assignment.ProgressUpdate`, `TaskContext`, `VolunteerAssignment`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `getActiveTasks`, `getTaskDetails`, `recordProgress`, `VolunteerAssignment.recordProgress`, `saveProgressAndStatus` and `reconcileProgressOperation`.
- **Screen:** Volunteer > Task progress (`task-progress-view.fxml`).
- **Database:** `task_progress_updates`, `volunteer_assignments`, `assistance_requests`, `workflow_operations`; deployed `record_task_progress` RPC.
- **Success:** append a required note and optional 0 to 99 percentage, then atomically move accepted request/assignment work to `IN_PROGRESS`.
- **Alternatives:** reject a blank or over-500-character note, invalid percentage, wrong owner, stale/reassigned task, invalid state or concurrent version; recover an uncertain result by operation ID.
- **Tests:** `ProgressUpdateTest`, `VolunteerAssignmentTest`, `VolunteerControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest` cover boundaries, state transitions, authenticated ownership delegation, adapter mapping and RPC hardening.
- **Demonstration:** add progress without a percentage, then add a valid percentage and inspect history.

## UC10: Complete Assistance Task

- **Status:** Implemented and deployed; validated completion, optional reliable evidence staging, retry reconciliation and atomic state changes are connected.
- **Java:** `application.volunteer.VolunteerController`, `ui.volunteer.TaskProgressViewController`, `domain.assignment.TaskCompletion`, `CompletionEvidence`, `TaskCompletionResult`, `VolunteerAssignment`, `EvidenceStorage`, `WorkflowRepository` and their Supabase adapters.
- **Methods:** `completeTask`, `VolunteerAssignment.createCompletion`, `beginTaskOperation`, `saveCompletionAndStatus`, `reconcileTaskCompletion` and `abandonTaskOperation`.
- **Screen:** Volunteer > Task progress > Complete task (`task-progress-view.fxml`).
- **Database:** `task_completions`, `request_evidence`, `workflow_operations`, `volunteer_assignments`, `assistance_requests`; deployed `begin_task_operation`, `complete_assistance_task`, `reconcile_workflow_operation` and `abandon_task_operation` functions.
- **Success:** store required completion remarks and optional staged evidence, then atomically move assignment and request to `AWAITING_CLOSURE_REVIEW`.
- **Alternatives:** multiple auditable attempts after rework; invalid remarks/evidence/owner/state/version; uncertain RPC recovery by operation ID; cleanup only after confirmed non-commit and with committed-reference protection.
- **Tests:** `VolunteerAssignmentTest`, `VolunteerControllerTest`, `SupabaseWorkflowRepositoryTest`, `SupabaseEvidenceStorageTest` and `DatabaseMigrationContractTest` cover direct accepted completion, rework attempts, idempotency, evidence safety, ownership and atomic transition contracts.
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
