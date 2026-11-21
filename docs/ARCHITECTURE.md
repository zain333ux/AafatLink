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
| `ui.auth`, `ui.shell`, `ui.request`, `ui.volunteer`, `ui.assignment` | FXML presentation controllers and reusable shell content |
| `application` | GRASP use-case controllers, grouped by workflow while preserving the six reviewed controller responsibilities |
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

## Data boundary

Schema migrations live in `supabase/migrations`. Multi-record state transitions use RPCs with idempotency operation IDs and optimistic version checks. Functions remain `SECURITY INVOKER` unless a reviewed permission boundary requires definer rights.

The current public workflow RPCs require definer rights because desktop roles deliberately lack direct transition-table update grants. They are hardened with database-derived identity, explicit role checks, empty search paths and restricted execution. Details are in `docs/DATABASE.md`.

## Authentication boundary

`SupabaseAuthClient` handles GoTrue requests; `SupabaseAccountRepository` loads the RLS-protected account; `AuthenticationController` coordinates both and starts the local session only after role/status retrieval succeeds. JavaFX controllers only collect input, run the controller on a background task and present the outcome. Password recovery is isolated in a PKCE coordinator and loopback callback component.

## Requester workspace boundary

`AppShellController` owns presentation navigation and loads child FXML into one reusable content host. `RequestFormViewController` and `RequestListViewController` depend on the plain-Java `RequestController`; they never call Supabase directly. Remote work runs on daemon JavaFX tasks so network latency does not block the interface. Demonstration sessions use the same screens but cannot submit or retrieve operational records because they contain no authenticated token.

The request form keeps entered data after remote failure and validates mandatory text fields through `AssistanceRequestDraft`. `EvidenceFileValidator` accepts local JPG, PNG and PDF evidence up to 5 MB. `SupabaseEvidenceStorage` stages private objects under the authenticated actor and operation path; `RequestController` registers the staged row, submits with the same operation ID, reconciles uncertain results under a database row lock, and requests cleanup only after the operation is safely abandoned.

`RequestReviewController` owns the UC03 and UC04 application operations without depending on JavaFX. `RequestReviewViewController` presents the two coordinator queue modes and loads a `RequestReviewDetails` projection containing the requester snapshot, committed evidence and possible duplicate matches. The repository supplies owner-independent coordinator queries under RLS and invokes atomic, version-checked verification and priority RPCs.

## Volunteer coordination boundary

`VolunteerController` coordinates UC07 readiness and UC08 assignment response. It depends on `UserRepository` for account contact, `VolunteerRepository` for readiness/reference data and `WorkflowRepository` for operational assignment state, matching the reviewed UML dependencies. `AssignmentController` coordinates UC05 candidate search, assignment and reassignment for coordinators. Neither controller imports JavaFX.

`VolunteerProfile` is the Information Expert for approval, availability and capability matching. `VolunteerAssignment` encapsulates the legal response states. Repository interfaces provide Protected Variation around PostgREST and RPC details. The presentation controllers run these operations off the JavaFX thread and expose explicit loading, empty, validation, success and failure states.

## Task execution and closure boundary

`VolunteerController` also coordinates UC09 and UC10. `ProgressUpdate` and `TaskCompletion` validate the volunteer's input, while `VolunteerAssignment` owns legal progress, completion, closure and rework transitions. Optional completion evidence reuses the staged-storage protocol and operation UUID used by request submission.

`RequestReviewController` coordinates UC06 without importing JavaFX. `TaskContext` provides the request, assignment, chronological progress, latest completion attempt, evidence and prior closure decision required by both presentation screens. Atomic RPCs lock the request and assignment, validate expected versions, append audit records and commit both states together. Uncertain results are reconciled through `workflow_operations` before any evidence cleanup.

## Reporting and access boundary

`ReportController` delegates UC11 to `ReportService`, which validates filters and selects one of four `ReportGenerator` strategies through a `Map<ReportType, ReportGenerator>`. The concrete generators override their repository query, columns and summaries; callers use the parent type without `if` or `instanceof` dispatch. `ReportRepository` keeps committed reporting queries outside JavaFX, while CSV export retains the already generated result if the file write fails.

`AccessController` coordinates UC12 through `UserRepository` and the reviewed `WorkflowRepository` dependency. The `UserAccount` aggregate encapsulates access state. Java validation gives immediate feedback, while `update_user_access` repeats all authorization, own-role, last-administrator, active-assignment, compatibility and optimistic-version checks inside one atomic transaction and writes the access audit record before success is returned.
