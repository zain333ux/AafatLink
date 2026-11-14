# AafatLink Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a tested, Eclipse-compatible Java 17 and JavaFX foundation with safe configuration, a polished login surface, a role-aware shell, and initial use-case traceability.

**Architecture:** A modular Maven application loads FXML views through a small router and composes dependencies in an application context. Presentation controllers stay under `ui`; future GRASP controllers, domain classes, and Supabase adapters have separate packages.

**Tech Stack:** Java 17, JavaFX 21.0.8, FXML, CSS, Maven, Jackson 2.21.8, JUnit 5.14.4, Ikonli 12.4.0.

## Global Constraints

- Product name is `AafatLink`.
- Preserve all files under `Deliverable/` without modification.
- Do not implement the 12 use cases in Phase 1.
- Do not embed secret or service-role keys.
- Demonstration data must be labeled as demonstration data.
- JavaFX controllers must not contain persistence or business workflows.

---

### Task 1: Build and repository foundation

**Files:** `pom.xml`, `.gitignore`, `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd`, `.project`, `.classpath`, `src/main/java/module-info.java`

- [ ] Initialize Git on `phase1-foundation`.
- [ ] Configure Java 17, JavaFX, Jackson, Ikonli, and JUnit with pinned versions.
- [ ] Add Maven Wrapper and Eclipse Maven nature.
- [ ] Verify dependency resolution with a test compilation.

### Task 2: Configuration contract

**Files:** `AppConfigurationTest.java`, `AppConfiguration.java`, `ConfigurationException.java`, `.env.example`

- [ ] Write failing tests for blank values, safe demonstration mode, and rejected secret-key names.
- [ ] Run the focused tests and confirm the expected failures.
- [ ] Implement the smallest configuration loader that passes.
- [ ] Run the focused and full test suites.

### Task 3: Role-aware navigation model

**Files:** `NavigationPolicyTest.java`, `UserRole.java`, `NavigationItem.java`, `NavigationPolicy.java`

- [ ] Write failing tests for each role's allowed navigation.
- [ ] Confirm the tests fail because the model is absent.
- [ ] Implement immutable role and navigation definitions.
- [ ] Run focused tests and refactor only while green.

### Task 4: JavaFX application shell

**Files:** startup classes, FXML controllers, FXML files, CSS, and image resources

- [ ] Add startup and routing code around the tested configuration and navigation model.
- [ ] Create the login surface, role selector, shell, sidebar, top bar, content placeholder, and logout behavior.
- [ ] Apply reusable semantic CSS classes and clear demonstration labels.
- [ ] Validate FXML loading through the application launch.

### Task 5: Documentation and verification

**Files:** `README.md`, `docs/ARCHITECTURE.md`, `docs/USE_CASE_TRACEABILITY.md`, `docs/DEMO_GUIDE.md`

- [ ] Document Phase 1 architecture and all 12 planned traceability entries.
- [ ] Run `mvn clean test`.
- [ ] Run `mvn package`.
- [ ] Run `mvn javafx:run` and observe startup.
- [ ] Inspect the resulting Git diff and commit verified Phase 1 work.
