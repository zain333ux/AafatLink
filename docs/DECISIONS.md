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
