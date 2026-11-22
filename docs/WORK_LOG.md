# Work log

## 2026-11-15

### Completed

- Initialized Git on `phase1-foundation` and added Maven Wrapper 3.10.0.
- Corrected the product and Eclipse project name to AafatLink.
- Added Java 17, JavaFX, Jackson, Ikonli and JUnit dependencies with pinned versions.
- Implemented safe public configuration with tests for demo mode, incomplete values, HTTPS and service-role rejection.
- Implemented tested session and role-aware navigation policies.
- Built the login surface, external CSS theme and reusable role-aware shell.
- Visually inspected the login and Requester shell in the running JavaFX application.
- Began architecture, decisions, blocker and UC traceability documentation.

### Verification so far

- Focused configuration, session and navigation tests: passed.
- Full test suite after JavaFX foundation: passed.
- `javafx:run`: application window opened; login and Requester shell were visually verified.
- A CSS warning about unsupported intermediate font weights was found during launch and corrected. Final clean verification remains to run.

### Next

Implement and validate the PostgreSQL schema, RLS foundation and transactional workflow functions for Phase 2.

### Phase 2 progress

- Initialized the Supabase CLI 3.1.0 project configuration.
- Added 20 domain and audit tables, reference data, constraints, foreign-key indexes and queue indexes.
- Added RLS to every public table and removed broad Data API grants.
- Added a private, hardened role helper plus five restricted transactional RPCs.
- Implemented scheduled overlap exclusion and the documented unscheduled-assignment fallback rule for UC05.
- Added private evidence-bucket policies that prevent deleting committed evidence.
- Added Java migration contract tests and executable request/assignment lifecycle tests.
- Local migration execution is blocked because Docker and development Supabase credentials are unavailable.

### Phase 3 progress

- Implemented Supabase Auth registration, sign-in, token refresh, logout, recovery start, PKCE exchange and password update over Java HTTP Client.
- Added database-authoritative account loading and an application authentication controller.
- Connected real sign-in to the JavaFX login form only when public Supabase configuration is present.
- Added public Requester/Volunteer registration UI; privileged roles are not selectable.
- Added an Auth user trigger and restricted pending-Volunteer onboarding RPC.
- Implemented and tested RFC 7636 S256 plus a one-use `127.0.0.1` callback server.
- Added safe in-memory sessions whose string representation cannot print tokens.
- UI automation was stopped from the keyboard before the newly added registration view could be visually inspected. No further computer-use actions were attempted.

### Current continuation point

- Run the final clean test/package verification for the Phase 3 increment.
- Visually inspect the connected login and registration views in a fresh session.
- Apply migrations to a disposable Supabase project and test signup, email confirmation, RLS and loopback redirect allowlisting.
- Complete the Volunteer onboarding call after verified sign-in, then start UC01 and UC02.

### Hosted Supabase validation

- Confirmed the supplied URL maps to the active `aafatlink-dev` project.
- Applied all four existing migrations, then added and applied `harden_database_baseline` from hosted advisor findings.
- Verified 20 public tables with RLS enabled, 29 public policies, the private evidence bucket and the Auth onboarding trigger.
- Verified Auth settings return HTTP 200 and anonymous access to `accounts` is denied with HTTP 401.
- Reran Supabase security, performance and health advisors. Remaining security warnings are the six intentional authenticated workflow RPCs; new-project unused-index notices remain informational.
- Ran `mvnw clean test`: 33 tests passed with no failures, errors or skips.
- Ran `mvnw package`: build succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- Ran `mvnw javafx:run` with connected public configuration and visually verified both the connected login and public registration screens. The process was terminated manually after inspection.

### Next

- Use a controlled test inbox to prove email confirmation and password recovery.
- Implement UC01 and UC02 as the next complete vertical slice.

### UC01 and UC02 foundation

- Rechecked Deliverable 3 and corrected the request schema to include all mandatory fields, request source, possible-duplicate flag and the documented 5 MB evidence limit.
- Added and deployed `align_request_submission_with_uc01` with idempotent submission, staged-evidence commit and same-ID resubmission RPCs.
- Added the validated `AssistanceRequestDraft` domain model, `AssistanceRequest` tracking model, GRASP `RequestController`, `WorkflowRepository` and `SupabaseWorkflowRepository`.
- Added controller, validation, HTTP adapter and migration contract tests.
- `mvnw clean test`: 41 tests passed with no failures, errors or skips.
- `mvnw package`: build succeeded.

### Current continuation point

- Build the JavaFX request form and owner-scoped request list/detail screens.
- Add the staged evidence upload adapter and uncertain-result reconciliation by operation ID.
- Use a controlled test inbox to run the first authenticated live UC01/UC02 transaction.

### Remote repository and live registration

- Connected the local repository to `https://github.com/zain333ux/AafatLink.git`, renamed the branch to `main` and pushed the verified history.
- Created a live Requester test identity with a transient generated password that was not displayed or stored.
- Verified the Auth trigger created exactly one linked `public.accounts` row with role `REQUESTER` and status `ACTIVE`.
- The inbox owner completed email confirmation. Password recovery is used so the owner chooses the test password without sharing it.

### Live password recovery

- Verified the test identity is email-confirmed.
- Added the restricted hosted Auth redirect rule `http://127.0.0.1:49152/auth/recovery**`.
- Switched the PKCE callback from an ephemeral port to fixed high port 49152 and added the JavaFX recovery screen.
- The new password remains inside the desktop process; the UI waits up to ten minutes for the one-use email callback.
- `mvn clean test`: 41 tests passed with no failures, errors or skips.
- `mvn package -DskipTests`: build succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.

### Next

- Run the live recovery screen, open the email link and verify sign-in with the owner-chosen password.
- Continue the UC01/UC02 JavaFX vertical slice.

### Recovery callback diagnosis

- Supabase Auth logs confirmed the first valid recovery link was opened in Samsung Browser on a phone, not on the computer running AafatLink.
- The phone consumed the one-time PKCE token; subsequent desktop clicks correctly returned `otp_expired`.
- Updated the recovery UI to require opening Gmail on the same computer and clicking the newest link once.
- Updated the loopback callback to distinguish an expired Supabase link from an invalid AafatLink flow identifier.
- `mvn clean test`: 42 tests passed with no failures, errors or skips.

## 2026-11-19

### UC01 and UC02 requester interface

- Replaced shell placeholders with a real dashboard, assistance-request form and owner-scoped request list/detail screen.
- Connected submission and same-ID resubmission actions to the existing plain-Java `RequestController`; remote calls run outside the JavaFX thread.
- Added explicit loading, empty, retry, success, failure and read-only demonstration states.
- Added understandable request-status labels, lifecycle presentation and the `Additional Information Required` resubmission action.
- Added local JPG, PNG and PDF evidence validation with the documented 5 MB limit. The form blocks evidence submission until secure Storage staging is connected rather than claiming that a local file was uploaded.
- Reduced the shell launch height to keep sign-out and session information reachable on a 720-pixel display.

### Verification

- Focused request-status and evidence tests: 6 passed.
- `mvnw.cmd clean test`: 46 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: login, dashboard, request form and request-tracking empty state loaded successfully and were visually inspected. Connected submission was not exercised because the live recovery/password sign-in remains pending.

### Next

- Implement Storage staging, committed-reference checks and operation-ID reconciliation for evidence.
- Expand UC02 repository mapping with assignment and progress history.
- Complete the same-computer password recovery and run a live authenticated UC01/UC02 transaction.

### UC02 tracking enrichment

- Extended request detail retrieval with the latest assignment state and latest progress note/percentage.
- Added an owner-scoped RLS migration so requesters can read progress only through assignments belonging to their own requests.
- Added HTTP adapter and migration contract tests; the focused 10-test set passes.
- Hosted deployment remains pending because the Supabase connector failed twice at the transport layer. No hosted success is claimed.

## 2026-11-20

### Verified requester workflow increment

- `mvnw.cmd clean test`: 48 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: started the JavaFX application without an application error; the login and requester shell had already been visually inspected during this increment. The process was stopped manually after the launch check.
- `git diff --check`: passed; only Git line-ending notices were reported on Windows.

### Next

- Implement secure staged evidence upload with operation-ID reconciliation and committed-reference protection.
- Apply the pending requester progress policy migration when the Supabase connector is available.
- Complete a same-computer recovery flow and run a live authenticated UC01/UC02 transaction.

### Reliable evidence staging

- Added authenticated private Storage upload/delete through `SupabaseEvidenceStorage`.
- Added operation preparation, staged-row registration and row-lock reconciliation RPC contracts.
- Added a restricted definer cleanup RPC that derives the actor from `auth.uid()`, rejects committed operations and marks staged rows as orphan candidates before object cleanup.
- Connected optional evidence to both initial submission and same-ID resubmission.
- Added controller, HTTP Storage, reconciliation and migration security tests.
- `mvnw.cmd clean test`: 55 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: login, requester shell and the updated request/evidence form loaded successfully; the process was stopped manually after inspection.

### Next

- Apply the two pending migrations when the connector is available; do not pause mandatory implementation for this external blocker.
- Move directly to UC03 and UC04 verification and priority workflows.
- Defer optional portfolio features and further decorative polish until UC01 through UC12 are implemented.

### UC03 and UC04 coordinator workflows

- Added the pending-verification and prioritizable coordinator queues with connected JavaFX screens.
- Added verify, reject and return-for-information decisions with mandatory reasons, audit RPC calls and optimistic version checks.
- Added deterministic UC04 suggestion rules and mandatory written justification for overrides.
- Added a migration that permits reprioritization only in `VERIFIED`, `ASSIGNED` and `IN_PROGRESS` while auditing the previous level.
- `mvnw.cmd clean test`: 63 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded.
- `mvnw.cmd javafx:run`: coordinator dashboard, verification queue and priority queue loaded successfully in demonstration mode.

### Next

- Implement UC05, UC07 and UC08 from Deliverable 3 before any optional portfolio work.
- Add UC03 evidence and possible-duplicate detail retrieval while implementing the shared coordinator repository queries.

## 2026-11-21

### UC05, UC07 and UC08 volunteer coordination

- Implemented volunteer readiness profiles with authenticated ownership, approval visibility, availability, skills and service areas.
- Implemented coordinator candidate matching, conflict-aware assignment and reasoned reassignment.
- Implemented volunteer assignment acceptance and mandatory-reason decline.
- Added domain objects that own readiness and assignment state rules, plain-Java GRASP application controllers, protected repository interfaces, Supabase HTTP adapters and three connected JavaFX screens.
- Added transactional database functions with actor, role, ownership, version, idempotency and concurrency checks.
- Final conflict rule: overlapping active schedules are rejected; non-overlapping schedules are allowed; any unscheduled active work conflicts because non-overlap cannot be proven.
- Deployed the requester-progress, evidence-recovery, UC04-alignment and volunteer-coordination migrations to `aafatlink-dev`.
- Verified the expected RPCs and schema columns on the hosted database. Hosted security warnings are the intentional hardened SECURITY DEFINER workflow boundaries; the readiness and eligibility functions use SECURITY INVOKER.
- Added and deployed covering indexes for both new UC05 reference foreign keys identified by the hosted performance advisor.
- `mvnw.cmd clean test`: 76 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: the login, Volunteer dashboard, readiness screen, assignments empty state and Coordinator assignment screen opened without FXML errors and were visually inspected.

### Next

- Implement UC09 task progress, UC10 task completion and UC06 coordinator closure as one atomic workflow slice.

### UC06, UC09 and UC10 task execution and closure

- Implemented volunteer progress updates with a required note, optional 0-99 percentage, chronological history and atomic `IN_PROGRESS` transitions.
- Implemented task completion from Accepted or In Progress with required remarks, optional private evidence, operation-ID reconciliation and safe cleanup.
- Implemented coordinator closure approval and mandatory-reason return for rework with immutable review records.
- Preserved multiple completion attempts after rework instead of overwriting prior evidence or remarks.
- Added domain-owned transition methods, plain-Java application controllers, protected repository methods, Supabase HTTP mappings and two connected JavaFX screens.
- Added and deployed `add_task_execution_and_closure_workflows` and `allow_volunteer_rework_visibility` to `aafatlink-dev`.
- Hosted verification confirmed invoker begin/reconcile functions and narrowly hardened definer functions for the atomic task workflows.
- Visually verified the Volunteer task-progress and Coordinator closure-review screens; fixed the default JavaFX table placeholder overlapping the custom empty state.
- `mvnw.cmd clean test`: 89 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: succeeded and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.

### Next

- Commit UC06/UC09/UC10, then implement UC11 reporting and UC12 access management before optional portfolio work.

### UC11 reporting and UC12 access management

- Implemented all four documented reports with validated date, disaster-type, request-status, priority and service-area filters.
- Added `ReportGenerator` inheritance and runtime polymorphism for Request Status Summary, Outstanding Requests, Volunteer Workload and Response Performance.
- Added committed-data Supabase report adapters and CSV export with stable column order and correct field escaping.
- Implemented administrator-only user listing, role/account-status changes and volunteer approval through `AccessController`, `UserRepository` and the required `WorkflowRepository` active-assignment check.
- Added own-role, last-active-administrator, incompatible-role, active-assignment and optimistic-version protections in both Java and PostgreSQL.
- Added the atomic, idempotent `update_user_access` RPC and immutable access/approval audit records.
- Added and deployed `add_reporting_and_access_management` to `aafatlink-dev`; live function inspection confirmed the three invoker reports and the two narrowly justified definer boundaries.
- Reran hosted security and performance advisors. New findings are limited to intentional hardened definer functions and unused indexes in the unseeded development project.
- `mvnw.cmd clean test`: 100 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: reran all 100 tests successfully and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: Coordinator Reports and Administrator Access Management loaded without FXML errors and were visually inspected at 1280 x 720; both demonstration empty states are explicit and read-only.

### Next

- Run the final package and diff checks, commit the complete UC11/UC12 increment, then audit Deliverables 2-5 against the implementation before adding optional portfolio features.

### Deliverables 3-5 alignment audit: UC01, UC03 and UC07

- Added the documented coordinator phone-entry path for UC01, including validated caller details, a distinct database-derived coordinator recorder and optional requester account identity.
- Added authorized operation begin/abandon support and an atomic, idempotent coordinator request RPC with safe evidence commit handling.
- Expanded UC03 review details to include the caller/requester snapshot, committed evidence metadata and related open-request matches before a coordinator decision.
- Reduced UC03 database privilege by replacing the initial privileged review projection with a `SECURITY INVOKER` function and an invoker contact-snapshot trigger.
- Restored the reviewed UC07 `UserRepository` dependency: account contact retrieval is separate from volunteer readiness persistence and the application controller composes the profile.
- Added and deployed `align_coordinator_entry_and_review_details`, `minimize_request_review_privileges` and `support_coordinator_request_operations` to `aafatlink-dev`.
- Hosted checks confirmed nullable requester account support, invoker review-detail retrieval and the hardened coordinator-recording boundary.
- Visually inspected Coordinator > Record phone request and Coordinator > Verification queue at 1280 x 720; both FXML screens loaded without errors.
- `mvnw.cmd clean test`: 108 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: reran all 108 tests successfully and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.

### Next

- Run the full regression/package checks, commit this alignment slice, then continue the artifact audit with UC12 permissions/reference data and exact domain/design class responsibilities.

### Deliverables 3-5 alignment audit: domain records and UC12 reference data

- Added the reviewed `Role` and `Permission` domain objects; `UserAccount` now owns a role and immutable permission set loaded from the database.
- Added the reviewed `VerificationRecord` and `PriorityAssessment` objects and aligned repository operations with the design-sequence names `saveVerificationAndStatus` and `savePriorityAndRequest`.
- Implemented UC12 controlled disaster types, volunteer skills, service areas and standard decline reasons through a connected Administrator screen, plain-Java application controller, repository port and Supabase adapter.
- Connected active disaster types to UC01 and standard decline reasons to UC08; UC07/UC05 already consume database-controlled skills and service areas.
- Added version checks, idempotent operation IDs, mandatory audit reasons, immutable audit rows and deactivate-without-delete behavior.
- Deployed `add_controlled_reference_management` and `optimize_reference_audit_and_request_policy` to `aafatlink-dev`. Hosted inspection confirmed all nineteen migrations, five disaster types, four decline reasons, hardened function configuration and versioned skill/service-area catalogs.
- Reran hosted advisors. The audit foreign-key index and duplicate request-policy findings are resolved; only unused-index information remains in the empty development project. Security warnings are the reviewed privileged workflow boundaries plus owner-controlled leaked-password protection.
- `mvnw.cmd clean test`: 121 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: reran all 121 tests and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.
- `mvnw.cmd javafx:run`: the login, Volunteer workspace and Assignments screen launched without FXML errors; the process was then intentionally stopped after visual inspection.

### Next

- Continue the Deliverables 2-5 audit against the remaining exact domain-model classes, SSD events and design-sequence responsibilities before optional portfolio work.

### Deliverables 4-5 domain-responsibility alignment

- Moved UC04 priority suggestion and request-state eligibility into `AssistanceRequest`, matching the reviewed Information Expert responsibility while keeping committed transitions inside atomic RPCs.
- Added immutable `AssistanceRequest.setPriority` and `transitionTo` previews so Java validates the documented state rules without presenting an uncommitted desktop change as persisted.
- Updated `RequestReviewController` and `AssignmentController` to ask the request entity whether verification, prioritization and assignment are allowed.
- Made `VolunteerAssignment` expose and reuse the reviewed `markInProgress`, `submitForClosureReview` and `markCompleted` operations.
- Renamed volunteer readiness operations to the reviewed `applyReadinessChanges` and `updateContactNo` responsibilities.
- Moved `EvidenceAttachment` into the domain layer and made it own the supported content-type and 5 MB rules; storage and local-file reading remain separated services.
- Made `AccessController` create the reviewed `AccessChangeLog` before repository persistence. PostgreSQL still derives the authoritative actor and timestamp inside the secured transaction.
- Added `AssistanceRequestTest` and expanded domain/application tests for these responsibilities.
- `mvnw.cmd clean test`: 124 tests passed with no failures, errors or skips.
- `mvnw.cmd package`: reran all 124 tests and produced `target/aafatlink-0.1.0-SNAPSHOT.jar`.

### Next

- Finish a method-by-method SSD/design trace matrix and correct any remaining behavior-bearing gaps before preparing live evaluation data.
