# Engineering decisions

## Mandatory artifacts before portfolio extras

**Decision:** Complete the behavior documented by Deliverable 3 and the reviewed UML artifacts before spending implementation time on optional portfolio features or additional visual polish. Security, database integrity, testing and UI required by a mandatory use case remain part of that use case.

## D001: Nonmodular Maven application

**Decision:** Use a conventional classpath build without `module-info.java`.

**Reason:** It is the simplest reliable arrangement for JavaFX FXML, Ikonli, Maven and Eclipse. Package boundaries still express the approved architecture.

## D002: Demonstration access is isolated and explicit

**Decision:** Phase 1 offers role previews through `SessionManager.startDemonstrationSession`. Real login controls remain disabled and labelled for Phase 3.

**Reason:** This permits UI evaluation without implying that Supabase authentication has succeeded.

## D003: Desktop configuration contains public values only

**Decision:** Read only `AAFATLINK_SUPABASE_URL` and `AAFATLINK_SUPABASE_PUBLISHABLE_KEY`. Reject a service-role environment variable at startup.

## UC01 request fields and evidence limit

**Decision:** Deliverable 3 overrides the initial database draft. Store description, required assistance and affected-person details separately; derive request source from authenticated actor identity; store possible duplicate as a flag; and enforce 5 MB per evidence file. Resubmission updates the same request row and request ID.

**Reason:** The desktop application is a public client. Privileged credentials would be extractable.

## D004: UC05 conflict scope

**Decision:** A volunteer may have more than one active assignment only when the assignments do not overlap operationally. Phase 2 models this with an assignment time range and an exclusion constraint for statuses `PENDING_ACCEPTANCE`, `ACCEPTED` and `IN_PROGRESS`. Requests without a scheduled range use a conservative rule: one unscheduled active assignment at a time.

**Reason:** A blanket unique-active-assignment rule rejects valid nonconflicting work, while time-range overlap protection expresses the actual conflict.

## D005: Evidence retry recovery

**Decision:** Every evidence workflow carries a client-generated operation UUID. After a timeout, the client queries the operation record. It removes a staged object only after confirming that the operation did not commit and that no evidence row references the object path.

**Reason:** Storage and PostgreSQL do not share a transaction; a timeout does not prove rollback.

## D006: Desktop password recovery

**Decision:** Use Supabase PKCE with a loopback HTTP listener on the fixed high port `127.0.0.1:49152`. The listener accepts one callback, validates a one-use flow ID, exchanges the code, and closes. The hosted Auth allowlist is restricted to the exact host, port and recovery path. Custom URI schemes are avoided.

**Reason:** Loopback redirects are a simple installed-application pattern and avoid a platform-specific protocol handler. A fixed high port supports a narrow reviewed redirect rule and produces a clear retryable error if another process has occupied the port.

## D007: Requester screens share the application shell

**Decision:** Load dashboard, submission and tracking FXML into a single shell content host. The shell creates presentation controllers with `RequestController` and session dependencies; GRASP application controllers remain free of JavaFX.

**Reason:** This preserves one role-aware navigation frame, avoids rebuilding top-level scenes for every workflow and keeps database communication outside presentation controllers.

## D008: Evidence selection is honest until staging is connected

**Decision:** Validate a selected local JPG, PNG or PDF file against the 5 MB rule, upload it to the private bucket, register a staged row, and submit using the same operation UUID. Reconciliation locks the operation row before reporting its state. Cleanup first marks an uncommitted operation failed, then asks Storage to delete; Storage policy independently rejects deletion of a committed reference.

**Reason:** A network timeout must not produce a false failure or delete evidence that a committed request already references.

## D009: UC04 suggestion rules are deterministic and advisory

**Decision:** Derive the suggested level consistently from affected-person counts, urgent vulnerability/assistance keywords, disaster type and waiting time. Waiting at least 24 hours escalates one level. The coordinator remains the final decision-maker, but an override requires 10 to 1000 characters of justification. Reprioritization is restricted to `VERIFIED`, `ASSIGNED` and `IN_PROGRESS`, and every assessment stores the previous level.

**Reason:** Deliverable 3 requires consistent predefined criteria, a rule-based suggestion, coordinator control, traceable changes and no `PRIORITIZED` request status.

## D010: UC05 conflicts are based on operational overlap

**Decision:** Block active assignments whose time ranges overlap. Allow non-overlapping scheduled work. Treat an unscheduled new or existing active assignment as a conflict because the system cannot prove that the work is nonconflicting. Reassignment first records the old assignment as `REASSIGNED` and releases the request to `VERIFIED`; the ordinary eligibility flow then selects the replacement.

**Reason:** This protects volunteers from conflicting work without imposing an unnecessarily broad one-active-assignment limit. Reusing the normal assignment operation also prevents a reassignment path from bypassing approval, readiness, skill or service-area checks.

## D011: UC07 readiness is an Information Expert

**Decision:** `VolunteerProfile` owns readiness and matching rules, while `VolunteerController` coordinates authenticated repository operations. The JavaFX controller only collects input and presents outcomes.

**Reason:** This makes encapsulation, GRASP Information Expert, Controller, Low Coupling and High Cohesion visible in the implementation and keeps business rules out of JavaFX.

## D012: Intentional privileged RPCs are narrowly hardened

**Decision:** Use SECURITY INVOKER for readiness updates and eligibility reads. Use SECURITY DEFINER only for atomic multi-table workflows that cannot rely on direct table grants. Every such RPC derives the actor from `auth.uid()`, checks role and ownership, validates expected versions, uses `search_path = ''`, restricts execution to `authenticated`, and records idempotent operation results.

**Reason:** The hosted security advisor flags any authenticated SECURITY DEFINER function generically. These functions are intentional trust boundaries rather than unreviewed broad privilege grants.

## D013: Completion attempts remain auditable after rework

**Decision:** A task may have multiple numbered completion attempts. Returning work for rework does not overwrite or delete the previous completion, evidence or closure review. Coordinator closure always evaluates the latest attempt.

**Reason:** UC06 requires a traceable return-for-rework cycle, and UC10 must support a later resubmission without destroying the original decision history.

## D014: Direct completion from Accepted is valid

**Decision:** Permit `ACCEPTED` assignments and their `ASSIGNED` requests to move directly to `AWAITING_CLOSURE_REVIEW` when the volunteer completes work without an intermediate percentage update.

**Reason:** Deliverable 3 allows UC10 from Accepted or In Progress. UC09 progress updates are useful but not mandatory before completion.

## D015: Rework reasons use scoped database visibility

**Decision:** The assigned volunteer, requester owner, coordinator and administrator may read the closure review for an accessible request; other users cannot. A private helper derives identity from `auth.uid()` and is not exposed through the Data API.

**Reason:** The volunteer must see the coordinator's rework reason while RLS must still prevent disclosure across unrelated requests.

## D016: UC11 uses genuine generator polymorphism

**Decision:** Model the four documented reports as subclasses of `ReportGenerator`. `ReportService` selects them by `ReportType` and calls the shared parent operation. Each subclass supplies its query, column order and summary without type checks in the caller.

**Reason:** This is the reviewed design interaction and a meaningful OOP example: the reports share an algorithm shape but vary in committed-data retrieval and presentation metadata.

## D017: UC12 keeps the documented account states at the UI boundary

**Decision:** Present `ACTIVE` as Active and `DISABLED` as Deactivated in UC12. Retain the existing `SUSPENDED` database value for operational compatibility, but do not expose it as a new documented administrator choice.

**Reason:** Deliverable 3 defines Active and Deactivated. Removing an existing safe state would be a destructive schema change, while exposing it as a new option would silently change the use case.

## D018: Aggregated volunteer workload has a narrow privacy boundary

**Decision:** Keep three report functions as `SECURITY INVOKER`. Use one hardened `SECURITY DEFINER` function for Volunteer Workload because coordinator RLS deliberately cannot read every account email row. It derives the actor from `auth.uid()`, accepts no actor ID, returns only display name and workload aggregates, uses `search_path = ''`, and grants execution only to `authenticated`.

**Reason:** The report needs a volunteer label without weakening account-table RLS or exposing email/contact details. The smallest privileged surface returns only the documented aggregate.

## D019: Access checks run in both Java and PostgreSQL

**Decision:** `AccessController` performs fast validation for own-role, last-admin and active-assignment restrictions, then `update_user_access` locks and rechecks the target account, actor role, administrator count, active assignments, compatibility and expected version before writing both access state and audit records atomically.

**Reason:** Desktop checks improve feedback but are not a security boundary. Repeating every rule in the transaction prevents frontend tampering and time-of-check/time-of-use races.

## D020: Coordinator phone requests preserve caller and recorder identities

**Decision:** A coordinator may record a caller's UC01 request without creating or impersonating a requester account. `requester_account_id` is nullable for this source, caller name and contact are stored as a validated pair, and `recorded_by_account_id` always comes from the authenticated coordinator inside the RPC.

**Reason:** Deliverable 3 requires coordinator-entered requests and the reviewed correction requires caller/requester identity to remain separate from the employee who recorded it.

## D021: UC03 review projection remains invoker-secured

**Decision:** Snapshot requester contact onto the request through an invoker trigger and expose caller, committed evidence metadata and possible duplicate matches through `get_request_review_details` as `SECURITY INVOKER`.

**Reason:** The review screen needs the documented facts, but it does not need account-table privileges, credentials, email disclosure or storage object paths. Removing that join eliminates an unnecessary privileged function.

## D022: Roles and permissions are real domain objects

**Decision:** `UserAccount` owns a `Role`, and `Role` owns the immutable set of `Permission` values loaded from the database. Access management selects a role as one reviewed bundle and previews its permissions instead of allowing arbitrary per-user permission edits.

**Reason:** This matches the final class diagram, demonstrates composition and encapsulation, and preserves the database role/permission model without inventing undocumented privilege combinations.

## D023: Controlled reference values are deactivated, not deleted

**Decision:** UC12 administrators may add or change volunteer skills, service areas and standard decline reasons, and may rename or deactivate existing disaster-type codes. New disaster-type codes require a reviewed application release because Java workflow rules use the matching enum codes. Every change requires a reason, expected version and operation ID and creates an immutable audit record.

**Reason:** Deactivation preserves historical relationships. Restricting new disaster codes prevents the database and compiled domain vocabulary from silently diverging while still implementing the documented reference-data administration workflow.

## D024: Equivalent request read policies are consolidated

**Decision:** Keep requester ownership, coordinator/administrator review and assigned-volunteer visibility in one permissive SELECT policy on `assistance_requests`.

**Reason:** The combined expression preserves the same access union while avoiding duplicate policy evaluation reported by the hosted performance advisor.

## D025: Domain transitions use immutable request previews

**Decision:** `AssistanceRequest` owns verification, prioritization, assignment eligibility, priority suggestion and lifecycle-transition validation. Its `setPriority` and `transitionTo` operations return immutable preview snapshots; only the authorized version-checked RPC commits a new priority or status.

**Reason:** This keeps the Information Expert responsibilities shown in the reviewed class and sequence diagrams without allowing a desktop object to claim that a database transition succeeded before the transaction commits.

## D026: Application audit objects do not supply database actor identity

**Decision:** `AccessController` creates the reviewed `AccessChangeLog` from the fresh before state, requested after state, authenticated session and application clock. `UserRepository` receives that object, but PostgreSQL still derives the authoritative administrator identity and timestamp from `auth.uid()` and the transaction.

**Reason:** The Java interaction remains traceable to the design sequence while untrusted desktop audit fields cannot become the security boundary.
