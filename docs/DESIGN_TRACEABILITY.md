# Deliverables 4-5 design traceability

This file maps the reviewed domain model, system events and design interactions to the actual Java implementation. Deliverable 3 remains the authority for behavior; this map explains the smallest implementation corrections where secure Supabase transactions replace in-memory persistence steps.

## Domain model realization

| Reviewed class | Java implementation and responsibility |
|---|---|
| `Permission` | `domain.account.Permission`; immutable capability identity and name. |
| `Role` | `domain.account.Role`; owns an immutable permission set and implements `grants`. |
| `UserAccount` | `domain.account.UserAccount`; owns `Role`, account state, approval compatibility, `isActive`, `hasPermission`, `canApplyAccessChanges` and `applyAccessChanges`. |
| `AccessChangeLog` | `domain.account.AccessChangeLog`; created by `AccessController` from before/after state and authenticated administrator identity. |
| `Skill`, `ServiceArea` | `domain.volunteer.Skill` and `ServiceArea`; validated controlled-reference values. |
| `VolunteerProfile` | `domain.volunteer.VolunteerProfile`; owns approval/availability matching, readiness application and contact update. |
| `AssistanceRequest` | `domain.request.AssistanceRequest`; owns verification, prioritization, assignment and closure eligibility, priority suggestion, immutable priority updates and lifecycle-transition validation. |
| `DisasterType` | `domain.request.DisasterType`; the five documented compiled workflow codes. Display names/activation are controlled in PostgreSQL. |
| `VerificationRecord` | `domain.request.VerificationRecord`; validates UC03 outcome and mandatory reason rules. |
| `PriorityAssessment` | `domain.request.PriorityAssessment`; validates selected level and override justification. |
| `VolunteerAssignment` | `domain.assignment.VolunteerAssignment`; owns response, reassignment, progress, completion, rework and final-completion transitions. |
| `ProgressUpdate` | `domain.assignment.ProgressUpdate`; immutable append-only entry with note and 0-99 validation. |
| `TaskCompletion` | `domain.assignment.TaskCompletion`; owns completion remarks and associated completion evidence. |
| `ClosureReview` | `domain.assignment.ClosureReview`; validates approval/return-for-rework decisions and reasons. |
| `EvidenceAttachment` | `domain.evidence.EvidenceAttachment`; owns supported content type and 5 MB validation. Storage staging remains behind `EvidenceStorage`. |

Database foreign keys realize the reviewed associations. Composition between requests and stored evidence is enforced by `request_evidence.request_id`; completion evidence uses the ordinary `task_completion_evidence` association so prior attempts remain auditable.

## GRASP controller realization

| Reviewed controller | Implemented use cases | Main collaborators |
|---|---|---|
| `RequestController` | UC01-UC02 | `AssistanceRequestDraft`, `EvidenceStorage`, `WorkflowRepository` |
| `RequestReviewController` | UC03, UC04, UC06 | `AssistanceRequest`, `VerificationRecord`, `PriorityAssessment`, `ClosureReview`, `WorkflowRepository` |
| `AssignmentController` | UC05 | `AssistanceRequest`, `VolunteerProfile`, `VolunteerAssignment`, `VolunteerRepository`, `WorkflowRepository` |
| `VolunteerController` | UC07-UC10 | `UserRepository`, `VolunteerRepository`, `VolunteerAssignment`, `ProgressUpdate`, `TaskCompletion`, `WorkflowRepository` |
| `ReportController` | UC11 | `ReportService`, polymorphic `ReportGenerator`, `ReportRepository` |
| `AccessController` | UC12 | `UserAccount`, `Role`, `AccessChangeLog`, `UserRepository`, `WorkflowRepository` |

JavaFX presentation controllers remain under `ui` and depend on these plain-Java controllers. They never invoke PostgREST or workflow RPCs directly.

## SSD and design-sequence event mapping

| Use case | Boundary event | Application/domain operation | Repository/database operation |
|---|---|---|---|
| UC01 | submit or resubmit request | `RequestController.submitRequest`, `resubmitRequest`, `recordCoordinatorRequest` | staged evidence lifecycle plus submit/resubmit/coordinator RPC; duplicate check occurs inside the locked transaction |
| UC02 | open list and request detail | `getMyRequests`, `getRequestDetails` | `findRequestsByRequester`, `findRequestForRequester`; RLS derives the requester |
| UC03 | verify, reject or request information | `RequestReviewController` creates `VerificationRecord`; `AssistanceRequest.canBeVerified` | `saveVerificationAndStatus` invokes `decide_request_verification` |
| UC04 | assess and set priority | `AssistanceRequest.suggestPriorityLevel`; `PriorityAssessment.validateSelection` | `savePriorityAndRequest` invokes `record_priority_assessment` |
| UC05 | find, assign or reassign volunteer | `AssistanceRequest.canBeAssigned`; `VolunteerProfile.matchesRequirements`; `VolunteerAssignment.markReassigned` | eligibility query plus atomic assignment/reassignment RPCs |
| UC06 | approve closure or return for rework | `ClosureReview`; `VolunteerAssignment.markCompleted` or `returnForRework` | `saveClosureAndStatus` invokes `review_request_closure` and reconciles by operation ID |
| UC07 | maintain readiness | `VolunteerProfile.applyReadinessChanges`, `updateContactNo` | `saveReadinessAndContact` invokes the invoker-secured readiness transaction |
| UC08 | accept or decline assignment | `VolunteerAssignment.canRespond`, `accept`, `decline` | atomic `respond_to_assignment` RPC |
| UC09 | record progress | `VolunteerAssignment.recordProgress`, `markInProgress`; `ProgressUpdate.isValid` | `saveProgressAndStatus` plus operation reconciliation |
| UC10 | complete task | `createCompletion`, `TaskCompletion.hasValidRemarks`, `linkEvidence`, `submitForClosureReview` | staged evidence lifecycle plus atomic completion RPC and reconciliation |
| UC11 | generate/export report | `ReportController`, `ReportService`, runtime-polymorphic `ReportGenerator.generateReport` | four read-only report functions; CSV export remains local |
| UC12 | update access/reference data | `AccessController`, `UserAccount.canApplyAccessChanges`, `AccessChangeLog`, `ReferenceDataChange` | atomic `update_user_access` and `save_controlled_reference_value` RPCs |

## Reviewed implementation corrections

- Database identities use UUIDs or PostgreSQL identity values rather than UML's technology-neutral `Integer` placeholders.
- FXML screen controllers replace the four coarse `RequesterView`, `VolunteerView`, `CoordinatorView` and `AdminView` boundary boxes. The role-aware shell groups the same actor interactions without creating God view classes.
- Specialized Supabase HTTP adapters replace one generic `SupabaseApiClient`. This keeps endpoint mapping testable and prevents a generic map-based client from leaking into domain/application code.
- Request priority and status previews are immutable in Java. PostgreSQL RPCs perform the authoritative multi-row changes, version checks and audit writes atomically.
- Duplicate detection runs inside request submission/resubmission RPCs so the flag and request record cannot disagree under concurrency.
- Authenticated actor IDs and final audit timestamps are re-derived in PostgreSQL; IDs supplied by the desktop are never trusted for authorization.
