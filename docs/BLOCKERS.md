# External blockers

No Phase 1 build or launch blocker remains. The `aafatlink-dev` hosted project is configured and all current migrations are applied.

## Local Supabase runtime unavailable

- **Blocked:** Parsing and executing migrations with `supabase db reset`, pgTAP policy tests and database advisors.
- **Why:** Docker is not installed or available on this machine.
- **Needed from owner:** Install and start Docker Desktop, or provide a disposable development Supabase project.
- **Work that can continue:** Static migration contract tests, Java domain/application work and HTTP adapter tests with local fakes.

## Live password-recovery link pending

- **Blocked:** Final end-to-end proof of the password update and subsequent sign-in.
- **Why:** Email confirmation and the restricted redirect allowlist are complete. The first valid recovery token was consumed on a phone, but PKCE requires the link to return to the computer that initiated the flow.
- **Needed from owner:** Choose the new password only inside AafatLink, open Gmail on the same computer, and click the newest recovery link once within ten minutes. Do not send the password in chat or open the link on a phone.
- **Work that can continue:** All local tests, database work and unrelated JavaFX implementation.

## Latest requester-progress policy not deployed

- **Blocked:** Applying `20261119235500_allow_requester_progress_tracking.sql` and `20261120090000_add_reliable_evidence_staging.sql` to the designated development project, then running hosted advisors.
- **Why:** The Supabase connector returned a transport failure on two consecutive read-only attempts on 2026-11-20. The standalone CLI is not installed, and the on-demand CLI download did not complete.
- **Needed from owner:** No immediate action. Retry the connected Supabase tools in a later session; if the connection remains unavailable, reconnect the Supabase plugin.
- **Work that can continue:** Java adapter tests, static migration contracts, UI work and all non-hosted development.
