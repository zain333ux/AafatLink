# AafatLink project handoff

This handoff is for the developer continuing the current repository. The mandatory academic implementation comes first. Do not spend time on optional portfolio polish until the minimum completion gate below passes.

## 1. Current verified state

- Repository: `https://github.com/zain333ux/AafatLink`
- Baseline commit: `afc5979` (`fix: harden JavaFX workflow interactions`)
- Stack: Java 17, JavaFX 21, FXML/CSS, Maven Wrapper, Supabase PostgreSQL/Auth/Storage and JUnit 5.
- Mandatory scope: UC01-UC12 all have JavaFX, application, domain, repository and database implementations.
- Database: 19 migrations are applied to Supabase project `fpgxsyvthfaxdeooezjs`.
- Verification at handoff: `124` tests passed; `mvnw.cmd package` succeeded; the Requester dashboard and request form were visually inspected at 1280 x 720.
- Git working tree was clean when this handoff was prepared.

Live evaluation accounts and a complete retained demonstration dataset are not prepared yet. That is the main difference between “implemented” and “ready for a ten-minute live evaluation.”

## 2. Read these files first

Read in this order before editing:

1. `AGENTS.md` — build, architecture and security rules.
2. `docs/USE_CASE_TRACEABILITY.md` — source-to-UC01-UC12 map and demonstration steps.
3. `docs/DESIGN_TRACEABILITY.md` — domain model, SSD and GRASP design realization.
4. `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` — actual structure and justified UML corrections.
5. `docs/BLOCKERS.md` and the latest section of `docs/WORK_LOG.md` — external dependencies and continuation point.
6. `docs/DEMO_GUIDE.md` and `docs/RUBRIC_TRACEABILITY.md` — the ten-minute evaluation path.
7. Deliverable 3 under `Deliverable/` when behavior is unclear. Never modify anything under `Deliverable/`.

## 3. Establish the baseline

Use JDK 17 or newer. From the repository root:

```powershell
git status --short
.\mvnw.cmd clean test
.\mvnw.cmd package
.\mvnw.cmd javafx:run
```

Expected baseline: 124 tests, zero failures/errors/skips, a packaged JAR and a working JavaFX launch. Configure connected mode only through environment variables:

```text
AAFATLINK_SUPABASE_URL
AAFATLINK_SUPABASE_PUBLISHABLE_KEY
```

Never use or commit a service-role key, database password, account password or `.env` file.

## 4. Minimum completion plan — do this first

### M1. Fix public Volunteer registration

This is the first code change. The registration UI currently allows Requester or Volunteer selection, but `RegistrationViewController` calls `AuthenticationController.register` without the selected type. `SupabaseAuthClient` therefore sends only `display_name`, and the auth trigger creates every account as `REQUESTER`.

Implement the smallest secure correction:

1. Add a `UserRole accountType` argument through `AuthService.register`, `AuthenticationController.register` and `SupabaseAuthClient.register`.
2. Pass `accountTypeBox.getValue()` from `RegistrationViewController`.
3. Reject every public value except `REQUESTER` and `VOLUNTEER` in Java.
4. Send a narrowly named auth metadata value such as `account_type`.
5. Add a migration that updates `private.handle_new_auth_user`:
   - map only the exact value `VOLUNTEER` to the Volunteer role;
   - default every missing, malformed or privileged value to Requester, or reject it;
   - create a `volunteer_profiles` row with `PENDING` approval for a Volunteer;
   - never accept Coordinator or Administrator from metadata;
   - keep `SECURITY DEFINER`, `search_path = ''` and revoked execution privileges.
6. Add tests for Requester, Volunteer and attempted Administrator/Coordinator registration.
7. Apply the migration to the designated development project and verify the resulting account/profile rows.

Acceptance criteria:

- A Requester self-registers and receives Requester navigation.
- A Volunteer self-registers, sees pending approval and can maintain readiness.
- Public input cannot create Coordinator or Administrator access.
- Existing 124 tests and the new tests all pass.

Estimated focused time: 3-5 hours including hosted verification.

### M2. Prepare real evaluation identities

Prepare separate, clearly labelled test identities:

- Requester — public registration.
- Volunteer — public registration after M1, then administrator approval.
- Coordinator — administrator-provisioned only.
- Administrator — securely bootstrapped/provisioned, never public registration.

Use test inbox aliases controlled by the owner. Do not commit addresses or passwords. The owner must choose passwords and complete email verification personally. Record only role, purpose and setup state in a private checklist.

Acceptance criteria:

- All four roles can sign in separately.
- Each role sees only its authorized navigation.
- Database rows show Active status; the Volunteer profile is Approved and Available.
- A frontend request cannot elevate its own role.

Estimated focused time: 2-4 hours plus email-delivery time.

### M3. Add repeatable demonstration data setup

Create `supabase/demo/prepare_demo_data.sql` and `supabase/demo/verify_demo_data.sql`. These are owner-run development scripts, not production migrations.

Requirements:

- Look up existing verified test accounts by replaceable email parameters; never create auth users or store passwords.
- Abort if any required identity is missing, unverified or has the wrong role.
- Mark every display value clearly with `[DEMO]`.
- Be idempotent and safe to rerun.
- Use a transaction and deterministic operation/request IDs or a dedicated manifest.
- Prepare useful historical report data and records at important states without blocking the Volunteer needed for the live workflow.
- Include a verification query that reports account roles, request states, assignment states and expected queue counts.
- If cleanup is provided, make it a separate, explicit script and protect non-demo data.

Recommended prepared records:

- one Pending Verification request;
- one Verified request awaiting priority;
- one prioritized Verified request ready for assignment;
- one Awaiting Closure Review task;
- several Closed historical requests for all four UC11 reports.

Acceptance criteria:

- Running the preparation twice does not duplicate data.
- Every prepared record is visible only to the correct role under RLS.
- Reports return meaningful non-zero results.
- No real personal data is present.

Estimated focused time: 4-6 hours.

### M4. Run a connected UC01-UC12 smoke test

Use the real JavaFX application, not direct SQL, for the workflow being demonstrated:

1. Requester submits and tracks a request; also prove same-ID resubmission (UC01-UC02).
2. Coordinator returns one request for information, verifies another and records priority with override justification when applicable (UC03-UC04).
3. Coordinator assigns an approved matching Volunteer and demonstrates a conflict rejection without blocking a valid nonconflicting case (UC05).
4. Volunteer maintains readiness, accepts/declines assignments, records progress and submits completion (UC07-UC10).
5. Coordinator returns one completion for rework and approves another closure (UC06).
6. Coordinator generates all four report types and exports CSV (UC11).
7. Administrator approves a Volunteer, makes one allowed access/reference change and demonstrates one protected unsafe change (UC12).

After every failure, verify that no misleading success message appears and the current server state is refreshed.

Acceptance criteria:

- Every UC is demonstrated through its screen and leaves the expected database state.
- RLS rejects wrong-owner and wrong-role access.
- Status transitions exactly match the documented request and assignment lifecycles.
- Priority remains a separate attribute.
- Evidence retry/reconciliation never deletes a committed object.

Estimated focused time: 1-2 days including fixes discovered during testing.

### M5. Finish authentication recovery proof

Run password recovery on the same computer that initiated it. The owner must choose and submit the new password and click the newest one-use email link within ten minutes. Do not open the link on a phone, reuse an expired link or share the password.

Acceptance criteria:

- The loopback PKCE callback is accepted once.
- The password update succeeds.
- The user signs in with the new password.
- Expired, reused and wrong-flow callbacks show accurate errors.

Estimated focused time: 30-60 minutes when the owner is present.

### M6. Freeze the evaluation build

1. Run `mvnw.cmd clean test`, `mvnw.cmd package` and `mvnw.cmd javafx:run`.
2. Visually inspect login and all four role workspaces at 1280 x 720 and one larger size.
3. Rehearse `docs/DEMO_GUIDE.md` and keep the demonstration below ten minutes.
4. Update test totals, traceability statuses, blockers and the work log.
5. Commit and tag the tested evaluation build. Do not make optional changes after the freeze without rerunning the full checklist.

Minimum completion gate:

- public Volunteer registration is secure and working;
- all four live roles exist;
- UC01-UC12 pass through the connected application;
- repeatable demo data exists;
- password recovery is proven;
- clean tests/package/visual launch succeed;
- the ten-minute demo is rehearsed.

Estimated total for the minimum gate: 2.5-3.5 focused development days, depending on live integration defects and owner availability.

## 5. Maximum-quality plan — only after the minimum gate

Work in this order:

1. **Automated integration environment:** install Docker Desktop or use a disposable Supabase branch; run migrations from zero and add pgTAP/RLS tests for cross-role denial and concurrent RPCs.
2. **CI pipeline:** add GitHub Actions for Java 17 compile, tests, packaging, migration contract checks and secret scanning.
3. **Session resilience:** automatically refresh expiring Supabase tokens, handle offline/reconnect states and force a clean sign-in when refresh is rejected.
4. **Evidence operations:** add an owner-run orphan-evidence audit/cleanup job that reconciles operation IDs before deletion and reports retained committed references.
5. **Accessibility pass:** keyboard navigation, visible focus, screen-reader labels, contrast and scaling checks across every FXML screen.
6. **UI refinement:** replace static dashboard counts with authorized live summaries, improve table pagination/filter persistence and capture final screenshots. Do not redesign working mandatory screens.
7. **Distribution:** produce a Windows runtime image/installer with `jpackage`, version metadata and a tested configuration guide. Never bundle credentials.
8. **Portfolio presentation:** architecture diagram, curated screenshots, short demo video, code-coverage report and an interview walkthrough explaining GRASP, OOP polymorphism, RLS and idempotent RPCs.

Estimated additional time: 3-6 focused days. These items improve reliability and presentation but must not delay the academic minimum.

## 6. Rules the next developer must preserve

- Deliverable 3 is authoritative for UC01-UC12 behavior.
- Do not modify `Deliverable/`.
- Keep JavaFX presentation controllers in `ui`; application controllers must remain JavaFX-free.
- Keep business rules in domain/services and persistence behind repository interfaces.
- Request resubmission keeps the same request ID.
- Requester and recorder identities remain separate.
- Priority is never a request status.
- Authorization is enforced in RLS/RPCs, not trusted to UI role checks.
- Prefer `SECURITY INVOKER`; harden every required definer function.
- Use operation IDs for retryable writes and reconcile uncertain commits before evidence cleanup.
- Never delete staged evidence referenced by a committed database row.
- Update `USE_CASE_TRACEABILITY.md`, `WORK_LOG.md`, `DECISIONS.md` and `BLOCKERS.md` with every completed slice.
- Never claim completion from a compile alone: run tests, package, visually launch and verify the resulting database state.

## 7. Suggested commits

Use small verified commits:

```text
fix: complete secure volunteer self-registration
test: cover public registration role boundaries
chore: add repeatable evaluation dataset
test: verify connected UC01-UC12 workflows
docs: freeze evaluation handoff and demo guide
```

Push only after the associated checks pass and never commit secrets or private evaluation credentials.
