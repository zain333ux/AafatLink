# SDA project rubric traceability

This file maps the evaluation rubric to evidence that can be shown in a ten-minute demonstration. It does not mark unfinished use cases complete; detailed status remains in `USE_CASE_TRACEABILITY.md`.

## Evaluation evidence

| Rubric area | Demonstrable implementation evidence |
|---|---|
| User interface | JavaFX FXML screens use one role-aware shell, reusable CSS, background network tasks, validation, loading, empty, success and error states. Each mandatory use case receives a real screen connected to an application controller. |
| Business logic | Plain-Java application controllers coordinate use cases. Domain objects validate legal state and input. PostgreSQL RPCs atomically enforce multi-record transitions and optimistic concurrency. |
| OOP principles | Private immutable state and controlled methods demonstrate encapsulation. Repository interfaces and report-generator contracts demonstrate abstraction. Four concrete report generators inherit from `ReportGenerator` and override report behavior; `ReportService` invokes them through the parent type without `if` or `instanceof` routing. |
| Database | PostgreSQL uses named domain tables, primary/foreign keys, checks, indexes, RLS, audit records, idempotent operations and restricted RPCs. Java adapters handle HTTP/database failures without false success messages. |
| Architecture | FXML/UI controllers remain under `ui`; GRASP application controllers under `application`; rules under `domain`/`service`; persistence contracts under `repository`; Supabase adapters under `infrastructure.supabase`. |
| Use-case implementation | `USE_CASE_TRACEABILITY.md` maps every UC01-UC12 to classes, methods, screens, database objects, tests and demonstration steps. |
| Design/code consistency | All 16 reviewed domain classes and six GRASP application controllers map to implementation classes. `DESIGN_TRACEABILITY.md` maps every SSD/design event and records the smallest security/persistence corrections; `DECISIONS.md` records their rationale. |
| GRASP and patterns | Controller: application controllers. Information Expert: request, volunteer profile and assignment domain types. Creator: controllers create operation IDs/results through repositories. Low Coupling/High Cohesion: UI, application, domain and infrastructure separation. Protected Variation: repository and storage interfaces. Strategy/polymorphism: report generators in UC11. |
| Documentation | README, architecture, database, decisions, blockers, work log, use-case traceability and this rubric map are maintained with verified command results. |

## Ten-minute demonstration order

1. Sign in and identify the role-aware dashboard and architecture separation in source.
2. Submit a request and show the same request in requester tracking (UC01-UC02).
3. Verify it, assign priority and assign an eligible volunteer (UC03-UC05).
4. Switch to the volunteer: show readiness, accept, record progress and complete the task (UC07-UC10).
5. Switch to the coordinator: approve closure (UC06).
6. Generate one report through the polymorphic report interface and export CSV (UC11).
7. Switch to the administrator: approve a volunteer or safely change access; show the audit record and one blocked unsafe action (UC12).
8. Close with the traceability table, automated test result and database security/transaction evidence.

The demo data must be clearly labelled, pre-seeded and arranged so the complete lifecycle can be shown without waiting for email or network-dependent setup during evaluation.
