# AafatLink

AafatLink is a Java desktop system for disaster assistance requests, volunteer coordination, accountable field work and response reporting. It is being built as a FAST NUCES Software Design and Analysis semester project and as a portfolio-quality engineering project.

## Current status

Phase 1 is complete. The Phase 2 database baseline is deployed to the designated development Supabase project with RLS, storage policy and advisor verification. Phase 3 includes tested Supabase Auth, account-profile adapters, connected sign-in and registration UI, and a same-computer PKCE recovery flow awaiting final live proof. UC01 and UC02 now have working requester form and tracking screens connected to their application controller, including latest assignment/progress mapping. The newest requester-progress RLS migration is locally verified but not yet deployed because the Supabase connector is temporarily unavailable. Evidence Storage staging and a live authenticated request transaction remain before those use cases can be marked complete.

Demonstration accounts are read-only and never imitate successful remote writes. All operational access still depends on a real Supabase session and database policies.

## Technology

- Java 17 source target
- JavaFX 21.0.8 with FXML and external CSS
- Maven Wrapper 3.10.0 and Eclipse m2e metadata
- Jackson 2.21.8
- Ikonli 12.4.0
- JUnit 5.14.4
- Supabase PostgreSQL and Auth, introduced through migrations and HTTPS adapters

Dependency versions are pinned in `pom.xml` for repeatable builds.

## Run locally

Requirements: a JDK 17 or newer. The project includes Maven Wrapper scripts.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run
```

Without Supabase settings, AafatLink opens in clearly labelled demonstration mode. For connected mode, define `AAFATLINK_SUPABASE_URL` and `AAFATLINK_SUPABASE_PUBLISHABLE_KEY` in the launch environment. Never provide a secret or service-role key to the desktop client.

## Eclipse import

1. Open Eclipse and select **File > Import > Maven > Existing Maven Projects**.
2. Select this repository folder and import the detected `pom.xml`.
3. Confirm the project JRE is Java 17 or newer.
4. Run `pk.edu.nu.aafatlink.Launcher` as a Java application, or run the Maven goal `javafx:run`.

The application intentionally uses the classpath instead of `module-info.java`. This keeps JavaFX FXML controller loading and Eclipse imports straightforward while preserving package boundaries in code.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Authentication](docs/AUTHENTICATION.md)
- [Database](docs/DATABASE.md)
- [Engineering decisions](docs/DECISIONS.md)
- [Use-case traceability](docs/USE_CASE_TRACEABILITY.md)
- [Work log](docs/WORK_LOG.md)
- [Known blockers](docs/BLOCKERS.md)

## Security

Only the Supabase project URL and publishable client key belong in the desktop environment. Authorization must be enforced by PostgreSQL Row Level Security and authorized RPC functions. Secrets, service-role keys, real user data and local environment files must never be committed.

## Academic source material

The original reviewed deliverables are preserved unchanged under the local `Deliverable/` folder. They are ignored by Git because they contain academic identity material; publish only an owner-reviewed, redacted copy. Deliverable 3 remains the functional authority for UC01 through UC12.
