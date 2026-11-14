# AafatLink contributor rules

## Build and verification

- Use Java 17 language features only.
- Run `./mvnw.cmd clean test` and `./mvnw.cmd package` after significant increments.
- Run `./mvnw.cmd javafx:run` for visual changes and inspect both login and shell.
- Do not mark a use case complete until required behavior and tests exist.

## Architecture

- Keep JavaFX presentation controllers under `ui`.
- Keep GRASP application controllers under `application`; they must not depend on JavaFX.
- Put business rules in domain or service classes and persistence behind repository interfaces.
- Prefer small vertical slices over speculative abstractions or empty packages.

## Security

- Never commit passwords, access tokens, private data, service-role keys or `.env` files.
- Treat UI role checks as usability only. Enforce authorization through RLS and RPCs.
- Prefer `SECURITY INVOKER`. Harden and restrict every necessary `SECURITY DEFINER` function.
- Use idempotency operation IDs for retryable writes and verify uncertain commits before storage cleanup.
- Never delete a staged evidence object that a committed evidence row references.

## Product requirements

- Deliverable 3 is authoritative for UC01 through UC12.
- Priority is an attribute, never a request status.
- Preserve requester and recorder identities separately.
- Resubmission keeps the original request ID.
- Keep `docs/USE_CASE_TRACEABILITY.md`, `docs/WORK_LOG.md`, `docs/DECISIONS.md` and `docs/BLOCKERS.md` current.
- Never modify files under `Deliverable/`.
