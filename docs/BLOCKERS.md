# External blockers

No Phase 1 build or launch blocker remains. The `aafatlink-dev` hosted project is configured and all current migrations are applied.

## Local Supabase runtime unavailable

- **Blocked:** Parsing and executing migrations with `supabase db reset`, pgTAP policy tests and database advisors.
- **Why:** Docker is not installed or available on this machine.
- **Needed from owner:** Install and start Docker Desktop, or provide a disposable development Supabase project.
- **Work that can continue:** Static migration contract tests, Java domain/application work and HTTP adapter tests with local fakes.

## Live password-recovery link pending

- **Blocked:** Final end-to-end proof of the password update and subsequent sign-in.
- **Why:** Email confirmation and the restricted redirect allowlist are complete. The inbox owner must use the next recovery link while the AafatLink recovery screen is waiting.
- **Needed from owner:** Choose the new password only inside AafatLink and open the recovery email within ten minutes. Do not send the password in chat.
- **Work that can continue:** All local tests, database work and unrelated JavaFX implementation.
