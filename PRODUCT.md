# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

AafatLink serves four roles in one disaster-response coordination organization:

- Requesters register, submit assistance requests, resubmit returned requests, and track their own cases.
- Volunteers register, maintain readiness information, wait for approval, and carry out assigned assistance work.
- Coordinators verify and prioritize requests, assign volunteers, review completed work, and run operational reports.
- Administrators provision privileged users and manage roles, permissions, account status, volunteer approval, and controlled reference data.

## Product Purpose

AafatLink centralizes disaster assistance requests and volunteer coordination that would otherwise be scattered across phone calls, messages, and manual records. Success means every mandatory use case is demonstrable, traceable to source code, secured at the database boundary, and understandable during a university evaluation or technical interview.

## Positioning

AafatLink combines requester self-service with coordinator-controlled verification, rule-based priority decisions, readiness-aware volunteer assignment, auditable completion review, and database-enforced workflow transitions in a focused desktop application.

## Operating Context

The application is a connected JavaFX desktop client. It communicates with Supabase Auth, PostgreSQL, Storage, and HTTPS APIs. The academic demonstration uses synthetic records and preconfigured demo accounts alongside public Requester and Volunteer registration.

## Capabilities and Constraints

- Java 17, JavaFX with FXML and CSS, Maven, Eclipse, Supabase PostgreSQL, Supabase Auth, Jackson, and JUnit 5.
- All 12 submitted use cases are mandatory but do not limit additional useful features.
- Priority is an attribute, never a `Prioritized` request status.
- Coordinator and Administrator roles are never publicly selectable.
- Service-role keys and database credentials never ship in the desktop client.
- Multi-record workflow changes use authorized PostgreSQL functions with concurrency protection.
- Evidence accepts JPG, PNG, or PDF files up to 5 MB.
- The project remains achievable and explainable as a university semester project.

## Brand Commitments

The product name is AafatLink. The interface is restrained, trustworthy, and operational. It uses a light workspace, deep navy navigation, muted teal interactive accents, explicit status labels, and minimal motion.

## Evidence on Hand

- `Deliverable/25I-0807_25I-0881_J_Delieverable3%20(2).pdf`: authoritative fully dressed use cases.
- `Deliverable/25i-0807_25i-0881_J_Deliverable4(1).pdf`: domain model and system sequence diagrams.
- `Deliverable/AafatLink_FINAL_REVIEWED_GRASP_Class_Diagram.drawio`: final GRASP class diagram.
- `Deliverable/AafatLink_FINAL_REVIEWED_12_Sequence_Diagrams.drawio`: 12 design sequence diagrams.
- No production claims, real disaster records, customer names, or operational benchmarks are available and none may be fabricated.

## Product Principles

1. Preserve and trace every documented use-case rule.
2. Enforce authorization and workflow integrity in PostgreSQL, not only in JavaFX.
3. Show success only after the remote operation is confirmed.
4. Prefer small, explicit classes over speculative framework layers.
5. Keep every screen readable, keyboard accessible, and useful under network failure.

## Accessibility & Inclusion

Use visible labels, keyboard navigation, strong focus indicators, WCAG AA color contrast, text plus icons for status, understandable errors, and layouts that remain usable when the desktop window is resized.
