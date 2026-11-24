# Ten-minute evaluation demonstration

## Preparing the evaluation data

Apply the repository migrations to the development project. Create four verified evaluation accounts and set the Volunteer to approved and available with a matching skill and service area. In a local copy of `supabase/demo/prepare_demo_data.sql`, replace the four example addresses and run the script as the development database owner. Then run `supabase/demo/verify_demo_data.sql`. A fresh setup has one pending verification, one verified awaiting priority, one verified with High priority, one awaiting closure review and three closed requests. Rerunning preparation preserves work already performed on those fixtures.

Prepare the live accounts and seeded records before evaluation. Demonstration mode is a read-only UI fallback and must never be presented as a successful database transaction.

1. **Architecture and login (45 seconds):** sign in, show the role-aware shell, then identify one FXML controller, its plain-Java application controller, domain object, repository interface and Supabase adapter.
2. **Requester (75 seconds):** submit a valid request with optional evidence (UC01), then open the same ID and its status timeline (UC02). Point out that returned-for-information resubmission keeps the ID.
3. **Coordinator intake (90 seconds):** verify the request (UC03), show the rule-based priority suggestion and justified override (UC04), then choose an eligible volunteer and mention overlap-based conflict protection (UC05).
4. **Volunteer (120 seconds):** show approval/readiness (UC07), accept the assignment (UC08), add progress (UC09), and complete with optional evidence (UC10).
5. **Coordinator closure (45 seconds):** inspect completion history and approve closure, or show the reasoned return-for-rework alternative (UC06).
6. **Reports (60 seconds):** generate a filtered report through the common `ReportGenerator` type and export the retained result to CSV (UC11).
7. **Administrator (60 seconds):** approve a volunteer or make one safe access change, show the audit record, then demonstrate one blocked unsafe change (UC12).
8. **Evidence (45 seconds):** open `docs/USE_CASE_TRACEABILITY.md`, show the latest automated-test total, and point to the RLS/RPC migration for one atomic workflow.

Keep one prepared record at each important workflow state so the demonstration is not delayed by email delivery or network-dependent setup. Clearly identify all demonstration identities and data.
