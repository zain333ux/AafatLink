# AafatLink implementation architecture

## Runtime shape

AafatLink is a single Java 17 desktop process. JavaFX owns presentation. Plain Java application controllers coordinate use cases. Domain services enforce workflow rules. Repository interfaces isolate HTTPS, Supabase Auth, PostgREST, RPC and Storage adapters.

```text
FXML + CSS -> ui controllers -> application controllers -> domain services
                                                  |-> repository interfaces
                                                  `-> Supabase HTTPS adapters
```

The desktop client contains no privileged database credential. PostgreSQL RLS and authorized functions are the final enforcement boundary.

## Source packages

| Package | Responsibility |
|---|---|
| `pk.edu.nu.aafatlink` | JavaFX lifecycle and launcher |
| `app` | configuration, session and application-level infrastructure |
| `app.navigation` | role-aware presentation destinations |
| `ui.auth`, `ui.shell` | FXML presentation controllers |
| `application` | six GRASP use-case controllers, added with workflow slices |
| `domain` | entities, value objects, status enums and business rules |
| `service` | cross-entity business services |
| `repository` | persistence ports |
| `infrastructure.supabase` | Java HTTP Client adapters, DTOs and JSON mapping |
| `reporting` | report model and generator polymorphism |
| `validation` | shared validation contracts and errors |

Packages are created only when working behavior requires them.

## Phase 1 composition

`Launcher` starts `AafatLinkApplication`. Startup validates `AppConfiguration` and constructs a `ViewRouter`, `SessionManager` and `NavigationPolicy`. `ViewRouter` loads FXML and injects presentation dependencies. UI controllers do not query storage or implement workflow rules.

The project is intentionally nonmodular. Maven builds on the classpath and Surefire disables the module path. This avoids fragile FXML reflection configuration and is reliable in Eclipse while package-level separation remains explicit.

## Error handling and logging

Startup configuration failures produce an actionable dialog and a logged exception. A default uncaught-exception handler records unexpected failures through `java.util.logging`. Workflow increments will introduce typed domain and remote-operation failures rather than leaking HTTP exceptions into JavaFX controllers.

## Planned data boundary

Schema migrations live in `supabase/migrations`. Multi-record state transitions use RPCs with idempotency operation IDs and optimistic version checks. Functions remain `SECURITY INVOKER` unless a reviewed permission boundary requires definer rights.

The current public workflow RPCs require definer rights because desktop roles deliberately lack direct transition-table update grants. They are hardened with database-derived identity, explicit role checks, empty search paths and restricted execution. Details are in `docs/DATABASE.md`.
