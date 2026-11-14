# External blockers

## Development Supabase project unavailable

- **Blocked:** Applying migrations, exercising RLS with real JWT claims, testing Auth registration/email/recovery and verifying Storage policies.
- **Why:** No clearly designated development project URL, publishable key, database connection or test inbox is configured in this repository.
- **Needed from owner:** A disposable development Supabase project and its public client configuration. A database-owner connection is needed only for migrations, never inside the application.
- **Work that can continue:** SQL migration authoring, local unit tests, repository contracts, HTTP adapters, JavaFX workflows and fake integration tests.

No Phase 1 build or launch blocker remains.

## Local Supabase runtime unavailable

- **Blocked:** Parsing and executing migrations with `supabase db reset`, pgTAP policy tests and database advisors.
- **Why:** Docker is not installed or available on this machine.
- **Needed from owner:** Install and start Docker Desktop, or provide a disposable development Supabase project.
- **Work that can continue:** Static migration contract tests, Java domain/application work and HTTP adapter tests with local fakes.
