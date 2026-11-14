# Engineering decisions

## D001: Nonmodular Maven application

**Decision:** Use a conventional classpath build without `module-info.java`.

**Reason:** It is the simplest reliable arrangement for JavaFX FXML, Ikonli, Maven and Eclipse. Package boundaries still express the approved architecture.

## D002: Demonstration access is isolated and explicit

**Decision:** Phase 1 offers role previews through `SessionManager.startDemonstrationSession`. Real login controls remain disabled and labelled for Phase 3.

**Reason:** This permits UI evaluation without implying that Supabase authentication has succeeded.

## D003: Desktop configuration contains public values only

**Decision:** Read only `AAFATLINK_SUPABASE_URL` and `AAFATLINK_SUPABASE_PUBLISHABLE_KEY`. Reject a service-role environment variable at startup.

**Reason:** The desktop application is a public client. Privileged credentials would be extractable.

## D004: UC05 conflict scope

**Decision:** A volunteer may have more than one active assignment only when the assignments do not overlap operationally. Phase 2 models this with an assignment time range and an exclusion constraint for statuses `PENDING_ACCEPTANCE`, `ACCEPTED` and `IN_PROGRESS`. Requests without a scheduled range use a conservative rule: one unscheduled active assignment at a time.

**Reason:** A blanket unique-active-assignment rule rejects valid nonconflicting work, while time-range overlap protection expresses the actual conflict.

## D005: Evidence retry recovery

**Decision:** Every evidence workflow carries a client-generated operation UUID. After a timeout, the client queries the operation record. It removes a staged object only after confirming that the operation did not commit and that no evidence row references the object path.

**Reason:** Storage and PostgreSQL do not share a transaction; a timeout does not prove rollback.

## D006: Desktop password recovery

**Decision:** Use Supabase PKCE with an ephemeral loopback HTTP listener on `127.0.0.1` and an exact high-port redirect URI, opened in the system browser. The listener accepts one callback, validates state, exchanges the code, and closes. Custom URI schemes are avoided unless loopback redirect registration proves unavailable.

**Reason:** Loopback redirects are the established simple pattern for installed applications and avoid registering a platform-specific protocol handler. End-to-end validation remains blocked until a development Supabase project is configured.
