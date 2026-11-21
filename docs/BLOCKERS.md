# External blockers

No build or launch blocker remains. The `aafatlink-dev` hosted project is configured and all seventeen current migrations are applied.

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

## Live end-to-end demonstration data not prepared

- **Blocked:** Proving UC01 through UC12 end to end with real Requester, Volunteer, Coordinator and Administrator sessions and retained demonstration records.
- **Why:** The hosted schema and policies are deployed, but the owner-selected password recovery and administrator-provisioned demo roles have not been completed.
- **Needed from owner:** When available, finish the same-computer password recovery once. Coordinator and Administrator accounts must be provisioned through the secured database workflow, never public registration.
- **Work that can continue:** Demo seeding scripts, source-to-artifact review, automated regression coverage and evaluation preparation that do not require owner credentials.
