# AafatLink foundation design

## Scope

Phase 1 establishes the build, startup, safe configuration boundary, reusable JavaFX theme, login surface, role-aware application shell, initial tests, and durable documentation. It does not connect to Supabase or implement the 12 workflows.

## Architecture

`AafatLinkApplication` owns JavaFX startup. `ApplicationContext` composes dependencies. `ViewRouter` loads FXML and switches the primary scene. JavaFX controllers contain presentation behavior only. Later GRASP application controllers will live under `application` and will not depend on JavaFX.

Configuration reads `AAFATLINK_SUPABASE_URL` and `AAFATLINK_SUPABASE_PUBLISHABLE_KEY` from environment variables or JVM system properties. Missing remote configuration does not block Phase 1 demonstration mode. Secret and service-role keys are explicitly rejected.

## Visual direction

The application is an Operate surface. A dark navy sidebar anchors a light working canvas. Muted teal identifies primary actions. Status and role information always use text, not color alone. The shell uses one spacing scale, one radius system, visible keyboard focus, restrained shadows, and no decorative animation.

## Initial behavior

- The login screen clearly labels its Phase 1 demonstration state.
- A user can select a demonstration role and enter the application shell.
- The shell exposes only navigation items allowed for that role.
- Logout clears the demonstration session and returns to login.
- Missing or malformed configuration produces actionable validation results without exposing secrets.

## Security refinements carried into Phase 2

- UC05 conflict rules will be derived from the authoritative requirement: block a conflicting active assignment, not every additional assignment. The exact conflict definition will be documented with database tests.
- Evidence cleanup must query by operation ID after an uncertain RPC result. A staged object is deleted only after the database confirms that no committed evidence row references it.
- RPC functions use `SECURITY INVOKER` unless elevated rights are required. Every `SECURITY DEFINER` function will pin an empty search path, derive identity from `auth.uid()`, check authorization, and receive explicit execute grants.
- Password recovery will be implemented only after a JavaFX loopback PKCE proof verifies the complete redirect and token exchange flow.

## Verification

Phase 1 requires `mvn clean test`, `mvn package`, and a real `mvn javafx:run` startup observation. A graphical process starting without an inspectable window is reported as limited verification, not full visual proof.
