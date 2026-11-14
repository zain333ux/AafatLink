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
