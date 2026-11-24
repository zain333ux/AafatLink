# Mandatory use-case traceability

## Operational dashboards, November 24

Dashboard links provide entry points to existing workflows. Requesters can submit and track requests (UC01–02). Coordinators can verify, prioritize, assign and close requests (UC03–06). Volunteers can manage readiness, offers and active tasks (UC07–10). Reports and administrative access remain available to their authorized roles (UC11–12).

The linked aafatlink-dev database is current through migration 20261124180000 (26 migrations). A read-only account check found two active verified Requesters and one active verified Volunteer. The four-role hosted rehearsal still needs designated Coordinator and Administrator test accounts.

## November 24 local verification

The deployment labels below refer to the original nineteen migrations. Hosted verification of the following updates is pending.

| Use cases | Local implementation and coverage |
|---|---|
| UC01–UC02 | Email-only contact snapshots, private contact storage, typed request source, submission retries and same-ID resubmission. Database regression in `supabase/tests/completion_workflows.sql`. |
| UC03–UC04 | Coordinator-only contact details, committed evidence links, Critical-first queue ordering, recorded reasons for returns during priority assessment and refreshed decision queues. Java controller and HTTP tests plus PostgreSQL verification and resubmission. |
| UC05–UC10 | Approved volunteer matching, reassignment reason in the previous volunteer's inbox and private evidence opening during closure review. Local PostgreSQL exercise covered decline, reassignment, acceptance, progress, completion, rework and closure. |
| UC11–UC12 | All four report queries exercised on seeded history. Access and reference changes commit audited results, including retry of the reference change. |
| Onboarding | Only Requester and Volunteer are public signup choices. PostgreSQL creates a pending Volunteer profile and safely defaults privileged metadata to Requester. |

Deliverable 3 is the functional authority. A use case is marked implemented only when its required main flow, important alternatives and automated tests exist. Hosted demonstration data is tracked separately so implementation status never implies that a live evaluation record has already been prepared.

## UC01: Submit Disaster Assistance Request

- **Status:** Implemented and deployed; requester submission, same-ID resubmission, coordinator phone entry, private Storage staging and idempotent database transactions exist. A live authenticated demonstration record remains to be prepared.
- **Java:** `application.request.RequestController`, `ui.request.RequestFormViewController`, `RequesterContact`, request domain records, `domain.evidence.EvidenceAttachment`, `service.evidence.EvidenceFileValidator`, `EvidenceStorage`, `repository.WorkflowRepository`, `SupabaseWorkflowRepository` and `SupabaseEvidenceStorage`.
- **Methods:** `RequestController.submitRequest`, `RequestController.resubmitRequest`, `RequestController.recordCoordinatorRequest` and the corresponding `WorkflowRepository` operations.
- **Screen:** `request-form-view.fxml`, opened from Requester > Submit request or Coordinator > Record phone request; returned requests reuse the screen with pre-filled fields and the coordinator reason.
- **Database:** `assistance_requests`, `request_evidence`, `workflow_operations`; deployed submit/resubmit/coordinator-recording, begin, register, reconcile and abandon RPCs. Caller snapshots are stored independently of `recorded_by_account_id`, and a phone request may have no `requester_account_id`.
- **Success:** validate, check duplicates, stage evidence, commit the request as `PENDING_VERIFICATION`, return the request ID.
- **Alternatives:** coordinator phone recording with separate caller and recorder identities; duplicate warning; safe evidence retry; resubmission of `ADDITIONAL_INFORMATION_REQUIRED` using the same ID.
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

- **Status:** Implemented and deployed for queue, full review context and decision paths; a seeded live demonstration transaction remains.
- **Java:** `application.request.RequestReviewController`, `ui.request.RequestReviewViewController`, `RequestReviewDetails`, `ReviewEvidence`, `PossibleDuplicateMatch`, `VerificationRecord`, `VerificationDecision`, `RequestReviewResult`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `loadVerificationQueue`, `getRequestReviewDetails`, `verify`, `reject`, `requestAdditionalInformation`, `AssistanceRequest.canBeVerified`, `AssistanceRequest.transitionTo`, `findPendingVerificationRequests`, `findRequestReviewDetails` and `saveVerificationAndStatus`.
- **Screen:** Coordinator > Verification queue > Review request.
- **Database:** `request_verifications`, `assistance_requests`, `request_evidence`; authorized `decide_request_verification` RPC with row locking and expected-version validation plus SECURITY INVOKER `get_request_review_details` for caller, committed evidence and possible duplicate context.
- **Success:** an authorized coordinator records verification and moves a pending request to `VERIFIED`.
- **Alternatives:** reject with reason, return for information with reason, duplicate evidence review and stale-version conflict.
- **Tests:** `VerificationRecordTest`, `RequestReviewControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest`; hosted authorization/concurrency proof remains.
- **Demonstration:** review a pending request and exercise verify and return-for-information paths.

## UC04: Prioritize Emergency Request

- **Status:** Implemented and deployed; a seeded live demonstration transaction remains.
- **Java:** `application.request.RequestReviewController`, `ui.request.RequestReviewViewController`, `PriorityAssessment`, `PriorityAssessmentService`, `PriorityAssessmentResult`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `loadPriorityQueue`, `suggestPriority`, `setPriority`, `AssistanceRequest.suggestPriorityLevel`, `AssistanceRequest.canBePrioritized`, `AssistanceRequest.setPriority`, `PriorityAssessment.validateSelection`, `findPrioritizableRequests` and `savePriorityAndRequest`.
- **Screen:** Coordinator > Priority queue > Priority assessment.
- **Database:** `priority_assessments`, `assistance_requests`; deployed `record_priority_assessment` RPC and `align_priority_with_uc04` migration provide previous-level audit and documented reprioritization statuses.
- **Success:** calculate a suggestion and record Critical, High, Medium or Low separately from request status.
- **Alternatives:** a manual override requires justification; stale-version conflict.
- **Tests:** `PriorityAssessmentTest`, `PriorityAssessmentServiceTest`, `RequestReviewControllerTest`, `RequestStatusTest` and `DatabaseMigrationContractTest` cover scoring, justification, eligibility and priority-not-status behavior.
- **Demonstration:** assess a verified request, show the suggestion, then justify an override.

## UC05: Assign Volunteer to Assistance Request

- **Status:** Implemented and deployed; controller, eligibility rules, JavaFX workflow, protected repository boundaries and transactional RPCs are tested.
- **Java:** `application.assignment.AssignmentController`, `domain.assignment.AssignmentFilter`, `VolunteerCandidate`, `VolunteerAssignment`, `repository.VolunteerRepository`, `WorkflowRepository`, `SupabaseVolunteerRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `getAssignableRequests`, `findEligibleVolunteers`, `AssistanceRequest.canBeAssigned`, `assignVolunteer`, `getActiveAssignments`, `VolunteerAssignment.markReassigned` and `reassignVolunteer`.
- **Screen:** Coordinator > Volunteer assignment.
- **Database:** `volunteer_assignments`, `volunteer_profiles`, `volunteer_skills`, `volunteer_service_areas`, `skills` and `service_areas`; deployed `find_eligible_volunteers`, `assign_volunteer_matching` and `reassign_volunteer` RPCs.
- **Success:** choose an approved, capable and available volunteer, create `PENDING_ACCEPTANCE`, and move the request to `ASSIGNED`.
- **Alternatives:** overlapping active assignments are rejected; non-overlapping work is allowed; reassignment records a reason and supersedes the old assignment; a failed/stale assignment reloads the latest request and candidate data before retry.
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
- **Java:** `application.volunteer.VolunteerController`, `domain.volunteer.VolunteerProfile`, `VolunteerReadinessChanges`, `UserRepository`, `VolunteerRepository`, `WorkflowRepository`, `SupabaseUserRepository` and `SupabaseVolunteerRepository`.
- **Methods:** `getReadinessProfile`, `getSkills`, `getServiceAreas`, `VolunteerProfile.applyReadinessChanges`, `VolunteerProfile.updateContactNo` and `updateReadiness`.
- **Screen:** Volunteer > Readiness profile.
- **Database:** `accounts`, `volunteer_profiles`, `volunteer_skills`, `volunteer_service_areas`, `skills` and `service_areas`; deployed SECURITY INVOKER `update_volunteer_readiness` RPC.
- **Success:** load account contact through `UserRepository`, combine it with readiness through the controller, then save availability, skills and service areas for the authenticated volunteer.
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
- **Alternatives:** decline requires a reason and returns the request for reassignment; expired or superseded assignments cannot be accepted, and a denied response refreshes the volunteer's latest assignment list while retaining the error.
- **Tests:** `VolunteerControllerTest`, `VolunteerAssignmentTest` and `DatabaseMigrationContractTest` cover ownership delegation, mandatory decline reason, terminal states, idempotency and row locking.
- **Demonstration:** accept one pending assignment and decline another with a reason.

## UC09: Update Assistance Task Progress

- **Status:** Implemented and deployed; active-task lookup, append-only history, domain validation, JavaFX controls and an atomic version-checked RPC are connected.
- **Java:** `application.volunteer.VolunteerController`, `ui.volunteer.TaskProgressViewController`, `domain.assignment.ProgressUpdate`, `TaskContext`, `VolunteerAssignment`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- **Methods:** `getActiveTasks`, `getTaskDetails`, `recordProgress`, `VolunteerAssignment.recordProgress`, `VolunteerAssignment.markInProgress`, `ProgressUpdate.isValid`, `saveProgressAndStatus` and `reconcileProgressOperation`.
- **Screen:** Volunteer > Task progress (`task-progress-view.fxml`).
- **Database:** `task_progress_updates`, `volunteer_assignments`, `assistance_requests`, `workflow_operations`; deployed `record_task_progress` RPC.
- **Success:** append a required note and optional 0 to 99 percentage, then atomically move accepted request/assignment work to `IN_PROGRESS`.
- **Alternatives:** reject a blank or over-500-character note, invalid percentage, wrong owner, stale/reassigned task, invalid state or concurrent version; recover an uncertain result by operation ID and refresh the task list after a rejected write.
- **Tests:** `ProgressUpdateTest`, `VolunteerAssignmentTest`, `VolunteerControllerTest`, `SupabaseWorkflowRepositoryTest` and `DatabaseMigrationContractTest` cover boundaries, state transitions, authenticated ownership delegation, adapter mapping and RPC hardening.
- **Demonstration:** add progress without a percentage, then add a valid percentage and inspect history.

## UC10: Complete Assistance Task

- **Status:** Implemented and deployed; validated completion, optional reliable evidence staging, retry reconciliation and atomic state changes are connected.
- **Java:** `application.volunteer.VolunteerController`, `ui.volunteer.TaskProgressViewController`, `domain.assignment.TaskCompletion`, `CompletionEvidence`, `TaskCompletionResult`, `VolunteerAssignment`, `EvidenceStorage`, `WorkflowRepository` and their Supabase adapters.
- **Methods:** `completeTask`, `VolunteerAssignment.createCompletion`, `VolunteerAssignment.submitForClosureReview`, `TaskCompletion.hasValidRemarks`, `TaskCompletion.linkEvidence`, `EvidenceAttachment.isValid`, `beginTaskOperation`, `saveCompletionAndStatus`, `reconcileTaskCompletion` and `abandonTaskOperation`.
- **Screen:** Volunteer > Task progress > Complete task (`task-progress-view.fxml`).
- **Database:** `task_completions`, `request_evidence`, `workflow_operations`, `volunteer_assignments`, `assistance_requests`; deployed `begin_task_operation`, `complete_assistance_task`, `reconcile_workflow_operation` and `abandon_task_operation` functions.
- **Success:** store required completion remarks and optional staged evidence, then atomically move assignment and request to `AWAITING_CLOSURE_REVIEW`.
- **Alternatives:** multiple auditable attempts after rework; invalid remarks/evidence/owner/state/version; uncertain RPC recovery by operation ID; cleanup only after confirmed non-commit and with committed-reference protection; refresh the latest task list after a rejected completion.
- **Tests:** `VolunteerAssignmentTest`, `VolunteerControllerTest`, `SupabaseWorkflowRepositoryTest`, `SupabaseEvidenceStorageTest` and `DatabaseMigrationContractTest` cover direct accepted completion, rework attempts, idempotency, evidence safety, ownership and atomic transition contracts.
- **Demonstration:** stage evidence, complete the task and show its closure-review state.

## UC11: Generate Disaster Response Reports

- **Status:** Implemented and deployed; the four reports, validated filters, committed-data queries, polymorphic generation and CSV export are connected.
- **Java:** `application.reporting.ReportController`, `reporting.ReportService`, abstract `ReportGenerator`, `RequestStatusReport`, `OutstandingRequestsReport`, `VolunteerWorkloadReport`, `ResponsePerformanceReport`, `repository.ReportRepository` and `SupabaseReportRepository`.
- **Methods:** `generateReport`, `validateFilters`, polymorphic `ReportGenerator.generateReport`, repository report methods and `exportReportToCsv` / `exportCsv`.
- **Screen:** Coordinator or Administrator > Reports (`report-view.fxml`), with type, date, disaster, status, priority and service-area filters.
- **Database:** deployed `report_request_status`, `report_outstanding_requests`, `report_volunteer_workload` and `report_response_performance` functions over committed request and assignment history.
- **Success:** generate Request Status Summary, Outstanding Requests, Volunteer Workload or Response Performance with validated filters.
- **Alternatives:** reject incomplete/reversed date ranges; show an honest empty state; show no partial data after retrieval failure; preserve the generated report after export failure; reject non-report roles at the database boundary.
- **Tests:** `ReportServiceTest`, `SupabaseReportRepositoryTest` and `DatabaseMigrationContractTest` cover polymorphic strategy dispatch, date validation, CSV escaping, exact response-time definitions, data minimization and authorization/hardening contracts.
- **Demonstration:** generate one summary, show a response-performance metric, switch report type through the common generator contract and export one CSV.

## UC12: Configure User Access Permissions

- **Status:** Implemented and deployed; administrator-only role, account status, volunteer approval, permission preview and controlled reference-data changes are versioned, atomic and audited.
- **Java:** `application.access.AccessController`, `AccessValidation`, `domain.account.UserAccount`, `Role`, `Permission`, `AccessChanges`, `AccessChangeLog`, `ControlledReferenceValue`, `ReferenceDataChange`, `ReferenceDataType`, `repository.UserRepository`, the required `WorkflowRepository` dependency, `SupabaseUserRepository`, `ui.access.AccessManagementViewController` and `ReferenceDataViewController`.
- **Methods:** `listUsers`, `listRoles`, `getAccessConfiguration`, `validateAccessChange`, `updateAccess`, `UserAccount.applyAccessChanges`, `listReferenceValues`, `saveReferenceValue`, `hasActiveAssignment`, `saveAccessWithAudit` and `Role.grants`; `AccessController` constructs `AccessChangeLog` before persistence.
- **Screens:** Administrator > Access management (`access-management-view.fxml`) and Administrator > Reference data (`reference-data-view.fxml`).
- **Database:** `accounts`, `account_roles`, `role_permissions`, `permissions`, `volunteer_approval_reviews`, `access_change_audit`, `workflow_operations`, `disaster_types`, `skills`, `service_areas`, `standard_decline_reasons` and `reference_data_audit`; deployed restricted `update_user_access` and `save_controlled_reference_value` RPCs.
- **Success:** an administrator performs an allowed access or reference-data change and creates an immutable audit record. Request, readiness and assignment-response screens consume the controlled values.
- **Alternatives:** reject stale/deleted accounts, incompatible role/approval combinations, own-role changes, last-active-administrator removal, active-assignment restrictions, stale reference versions, invalid reference values and unauthorized calls without modifying state. Reference values are deactivated rather than deleted.
- **Tests:** `RoleTest`, `ReferenceDataChangeTest`, `AccessControllerTest`, `SupabaseUserRepositoryTest`, `SupabaseWorkflowRepositoryTest`, `VolunteerControllerTest`, `NavigationPolicyTest` and `DatabaseMigrationContractTest` cover role permissions, reference validation/auditing, self-role protection, last-admin protection, active-assignment restrictions, approved changes, versioning, database-derived actor identity and restricted execution.
- **Demonstration:** approve a volunteer or make one safe role/status change, inspect its audit data, then edit/deactivate one controlled value and show it in the relevant workflow. Finish by showing a blocked own-role, active-assignment or stale-version operation.

## Additional portfolio features

The professional login surface, role-specific shell, account settings, search/filter controls, loading/empty/error states and dashboard visualizations are tracked separately. Their presence does not change the completion status of UC01 through UC12.
