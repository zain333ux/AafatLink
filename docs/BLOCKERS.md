# External blockers

No Phase 1 build or launch blocker remains. The `aafatlink-dev` hosted project is configured and all current migrations are applied.

## Local Supabase runtime unavailable

- **Blocked:** Parsing and executing migrations with `supabase db reset`, pgTAP policy tests and database advisors.
- **Why:** Docker is not installed or available on this machine.
- **Needed from owner:** Install and start Docker Desktop, or provide a disposable development Supabase project.
- **Work that can continue:** Static migration contract tests, Java domain/application work and HTTP adapter tests with local fakes.

## Live authentication proof unavailable

- **Blocked:** Completing real email confirmation, redirect allowlisting and password recovery through an actual email link.
- **Why:** A test Requester account and matching application profile were created and the confirmation email was sent, but the inbox owner must open the confirmation link. The dashboard redirect allowlist also remains to be reviewed.
- **Needed from owner:** Open the latest AafatLink/Supabase confirmation message sent to the supplied test inbox and confirm the account. Do not send the inbox password. Add only reviewed loopback redirect patterns.
- **Work that can continue:** Unit/integration tests against local HTTP servers and UI work in demonstration mode.
