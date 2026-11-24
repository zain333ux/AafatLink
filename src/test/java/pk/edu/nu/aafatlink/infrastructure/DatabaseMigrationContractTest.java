package pk.edu.nu.aafatlink.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class DatabaseMigrationContractTest {
    private static final Path DOMAIN = Path.of("supabase", "migrations",
            "20261115093122_create_aafatlink_domain.sql");
    private static final Path RPCS = Path.of("supabase", "migrations",
            "20261115093125_add_authorized_workflow_rpcs.sql");
    private static final Path STORAGE = Path.of("supabase", "migrations",
            "20261115093127_configure_evidence_storage.sql");
    private static final Path ONBOARDING = Path.of("supabase", "migrations",
            "20261115093129_add_account_onboarding.sql");
    private static final Path HARDENING = Path.of("supabase", "migrations",
            "20261115093320_harden_database_baseline.sql");
    private static final Path UC01_ALIGNMENT = Path.of("supabase", "migrations",
            "20261115094050_align_request_submission_with_uc01.sql");
    private static final Path REQUESTER_TRACKING = Path.of("supabase", "migrations",
            "20261120221719_allow_requester_progress_tracking.sql");
    private static final Path EVIDENCE_RECOVERY = Path.of("supabase", "migrations",
            "20261120221726_add_reliable_evidence_staging.sql");
    private static final Path UC04_ALIGNMENT = Path.of("supabase", "migrations",
            "20261120221733_align_priority_with_uc04.sql");
    private static final Path VOLUNTEER_COORDINATION = Path.of("supabase", "migrations",
            "20261120221740_add_volunteer_coordination_workflows.sql");
    private static final Path TASK_EXECUTION = Path.of("supabase", "migrations",
            "20261120232255_add_task_execution_and_closure_workflows.sql");
    private static final Path REWORK_VISIBILITY = Path.of("supabase", "migrations",
            "20261121070241_allow_volunteer_rework_visibility.sql");
    private static final Path REPORTING_ACCESS = Path.of("supabase", "migrations",
            "20261121075908_add_reporting_and_access_management.sql");
    private static final Path COORDINATOR_REVIEW = Path.of("supabase", "migrations",
            "20261121172140_align_coordinator_entry_and_review_details.sql");
    private static final Path REVIEW_PRIVILEGES = Path.of("supabase", "migrations",
            "20261121172313_minimize_request_review_privileges.sql");
    private static final Path COORDINATOR_OPERATIONS = Path.of("supabase", "migrations",
            "20261121173242_support_coordinator_request_operations.sql");
    private static final Path CONTROLLED_REFERENCES = Path.of("supabase", "migrations",
            "20261122072007_add_controlled_reference_management.sql");
    private static final Path REFERENCE_POLICY_OPTIMIZATION = Path.of("supabase", "migrations",
            "20261122072322_optimize_reference_audit_and_request_policy.sql");

    @Test
    void everyPublicTableEnablesRowLevelSecurity() throws IOException {
        String sql = normalized(DOMAIN);
        Matcher tables = Pattern.compile("create table public\\.([a-z_]+)").matcher(sql);
        Set<String> tableNames = new HashSet<>();
        while (tables.find()) {
            tableNames.add(tables.group(1));
        }

        assertTrue(tableNames.size() >= 20, "The domain migration should contain the documented relational model.");
        for (String table : tableNames) {
            assertTrue(sql.contains("alter table public." + table + " enable row level security"),
                    () -> "RLS is missing for public." + table);
        }
    }

    @Test
    void priorityRemainsSeparateFromRequestStatus() throws IOException {
        String sql = normalized(DOMAIN);

        assertFalse(sql.contains("'prioritized'"));
        assertTrue(sql.contains("priority public.request_priority"));
    }

    @Test
    void assignmentConflictsUseTimeOverlapAndConservativeUnscheduledRule() throws IOException {
        String sql = normalized(DOMAIN);

        assertTrue(sql.contains("exclude using gist (volunteer_account_id with =, active_window with &&)"));
        assertTrue(sql.contains("volunteer_one_unscheduled_active_assignment_idx"));
        assertFalse(sql.contains("unique (volunteer_account_id, status)"));
    }

    @Test
    void everyDefinerFunctionDerivesIdentityAndPinsSearchPath() throws IOException {
        String sql = normalized(DOMAIN) + "\n" + normalized(RPCS) + "\n" + normalized(ONBOARDING)
                + "\n" + normalized(VOLUNTEER_COORDINATION) + "\n" + normalized(TASK_EXECUTION)
                + "\n" + normalized(REPORTING_ACCESS) + "\n" + normalized(COORDINATOR_REVIEW)
                + "\n" + normalized(CONTROLLED_REFERENCES);
        String[] functions = sql.split("create or replace function ");
        int definerCount = 0;
        for (int index = 1; index < functions.length; index++) {
            String function = functions[index];
            if (!function.contains("security definer")) {
                continue;
            }
            definerCount++;
            assertTrue(function.contains("set search_path = ''"));
            boolean internalAuthTrigger = function.startsWith("private.handle_new_auth_user()");
            assertTrue(internalAuthTrigger ? function.contains("new.id") : function.contains("auth.uid()"));
        }
        assertTrue(definerCount >= 6);
        assertFalse(sql.contains("service_role"));
    }

    @Test
    void committedEvidenceCannotBeRemovedByCleanupPolicy() throws IOException {
        String sql = normalized(STORAGE);

        assertTrue(sql.contains("evidence_objects_delete_uncommitted_own"));
        assertTrue(sql.contains("not exists"));
        assertTrue(sql.contains("evidence.state = 'committed'"));
    }

    @Test
    void hostedBaselineClosesAdvisorFindings() throws IOException {
        String sql = normalized(HARDENING);

        assertTrue(sql.contains("alter extension btree_gist set schema extensions"));
        assertTrue(sql.contains("revoke execute on function public.rls_auto_enable() from public, anon, authenticated"));
        assertTrue(sql.contains("request_evidence_uploader_idx"));
        assertTrue(sql.contains("role_permissions_permission_idx"));
        assertTrue(sql.contains("volunteer_approval_volunteer_idx"));
        assertFalse(sql.contains("for all to authenticated"));
    }

    @Test
    void requestSubmissionSchemaMatchesUc01() throws IOException {
        String sql = normalized(UC01_ALIGNMENT);

        assertTrue(sql.contains("required_assistance"));
        assertTrue(sql.contains("affected_person_details"));
        assertTrue(sql.contains("possible_duplicate"));
        assertTrue(sql.contains("request_source"));
        assertTrue(sql.contains("5242880"), "UC01 limits each evidence file to 5 MB.");
        assertTrue(sql.contains("resubmit_assistance_request"));
        assertTrue(sql.contains("status = 'pending_verification'"));
    }

    @Test
    void requesterCanReadProgressOnlyThroughOwnedRequests() throws IOException {
        String sql = normalized(REQUESTER_TRACKING);

        assertTrue(sql.contains("drop policy if exists progress_select"));
        assertTrue(sql.contains("requester_account_id = (select auth.uid())"));
        assertTrue(sql.contains("assignment.request_id"));
        assertTrue(sql.contains("task_progress_updates.assignment_id"));
    }

    @Test
    void evidenceRecoveryLocksOperationAndRestrictsDefinerCleanup() throws IOException {
        String sql = normalized(EVIDENCE_RECOVERY);

        assertTrue(sql.contains("create function public.reconcile_request_operation"));
        assertTrue(sql.contains("where operation_id = p_operation_id for update"));
        assertTrue(sql.contains("create function public.abandon_request_operation"));
        assertTrue(sql.contains("security definer set search_path = ''"));
        assertTrue(sql.contains("operation.actor_account_id <> actor_id"));
        assertTrue(sql.contains("operation.operation_type not in ('submit_request', 'resubmit_request')"));
        assertTrue(sql.contains("state = 'orphan_candidate'"));
        assertTrue(sql.contains("revoke all on function public.abandon_request_operation(uuid) from public, anon"));
    }

    @Test
    void priorityMigrationAllowsOnlyDocumentedStatusesAndAuditsPreviousLevel() throws IOException {
        String sql = normalized(UC04_ALIGNMENT);

        assertTrue(sql.contains("previous_priority public.request_priority"));
        assertTrue(sql.contains("status not in ('verified', 'assigned', 'in_progress')"));
        assertTrue(sql.contains("request_record.priority, p_suggested_priority"));
        assertFalse(sql.contains("'prioritized'"));
    }

    @Test
    void volunteerCoordinationRechecksEligibilityAndUsesIdempotentAtomicWorkflows() throws IOException {
        String sql = normalized(VOLUNTEER_COORDINATION);

        assertTrue(sql.contains("security invoker"));
        assertTrue(sql.contains("create or replace function public.assign_volunteer_matching"));
        assertTrue(sql.contains("create or replace function public.respond_to_assignment"));
        assertTrue(sql.contains("create or replace function public.reassign_volunteer"));
        assertTrue(sql.contains("profile.approval_status <> 'approved'"));
        assertTrue(sql.contains("profile.availability_status <> 'available'"));
        assertTrue(sql.contains("volunteer no longer matches the required skill or service area"));
        assertTrue(sql.contains("volunteer has a conflicting active assignment"));
        assertTrue(sql.contains("operation.status = 'committed' then return operation.result"));
        assertTrue(sql.contains("set status = 'verified'"));
        assertTrue(sql.contains("revoke execute on function public.assign_volunteer"));
    }

    @Test
    void taskExecutionAndClosureMatchUc06Uc09AndUc10() throws IOException {
        String sql = normalized(TASK_EXECUTION);

        assertTrue(sql.contains("create or replace function public.record_task_progress"));
        assertTrue(sql.contains("length(btrim(p_note)) not between 1 and 500"));
        assertTrue(sql.contains("p_percentage not between 0 and 99"));
        assertTrue(sql.contains("assignment.status not in ('accepted', 'in_progress')"));
        assertTrue(sql.contains("create or replace function public.complete_assistance_task"));
        assertTrue(sql.contains("task_completions_assignment_attempt_idx"));
        assertTrue(sql.contains("task_completion_id = p_completion_id"));
        assertTrue(sql.contains("state = 'committed', committed_at = now()"));
        assertTrue(sql.contains("create or replace function public.review_request_closure"));
        assertTrue(sql.contains("target_assignment_status := 'completed'"));
        assertTrue(sql.contains("target_request_status := 'closed'"));
        assertTrue(sql.contains("target_assignment_status := 'in_progress'"));
        assertTrue(sql.contains("operation.status = 'committed' then return operation.result"));
        assertTrue(sql.contains("where id = p_assignment_id for update"));
        assertTrue(sql.contains("where id = p_request_id for update"));
    }

    @Test
    void returnedReworkReasonIsVisibleToAssignedVolunteer() throws IOException {
        String sql = normalized(REWORK_VISIBILITY);

        assertTrue(sql.contains("private.can_read_closure_review"));
        assertTrue(sql.contains("assignment.volunteer_account_id = auth.uid()"));
        assertTrue(sql.contains("request.requester_account_id = auth.uid()"));
        assertTrue(sql.contains("drop policy if exists closure_reviews_select"));
        assertTrue(sql.contains("set search_path = ''"));
    }

    @Test
    void reportingUsesFixedMetricsAndDoesNotExposePersonalDetails() throws IOException {
        String sql = normalized(REPORTING_ACCESS);

        assertTrue(sql.contains("report_request_status"));
        assertTrue(sql.contains("report_outstanding_requests"));
        assertTrue(sql.contains("report_volunteer_workload"));
        assertTrue(sql.contains("report_response_performance"));
        assertTrue(sql.contains("verified_at - submitted_at"));
        assertTrue(sql.contains("assigned_at - verified_at"));
        assertTrue(sql.contains("closed_at - submitted_at"));
        assertTrue(sql.contains("limit 5000"));
        assertFalse(sql.contains("affected_person_details"));
        assertFalse(sql.contains("requester_account_id"));
    }

    @Test
    void accessUpdateRechecksUc12SecurityRulesAndAuditsAtomically() throws IOException {
        String sql = normalized(REPORTING_ACCESS);

        assertTrue(sql.contains("create or replace function public.update_user_access"));
        assertTrue(sql.contains("private.current_app_role() <> 'administrator'"));
        assertTrue(sql.contains("actor_id = target.id and p_role <> target.role"));
        assertTrue(sql.contains("the last active administrator cannot be removed"));
        assertTrue(sql.contains("active assignments must be completed or reassigned first"));
        assertTrue(sql.contains("insert into public.access_change_audit"));
        assertTrue(sql.contains("insert into public.volunteer_approval_reviews"));
        assertTrue(sql.contains("where id = p_target_account_id for update"));
        assertTrue(sql.contains("target.version <> p_expected_version"));
        assertTrue(sql.contains("set search_path = ''"));
        assertTrue(sql.contains("revoke all on function public.update_user_access"));
    }

    @Test
    void coordinatorEntryAndVerificationDetailsMatchReviewedArtifacts() throws IOException {
        String sql = normalized(COORDINATOR_REVIEW);

        assertTrue(sql.contains("alter column requester_account_id drop not null"));
        assertTrue(sql.contains("requester_contact_name"));
        assertTrue(sql.contains("recorded_by_account_id"));
        assertTrue(sql.contains("'coordinator_entered'"));
        assertTrue(sql.contains("private.current_app_role() <> 'coordinator'"));
        assertTrue(sql.contains("create or replace function public.get_request_review_details"));
        assertTrue(sql.contains("evidence.state = 'committed'"));
        assertTrue(sql.contains("candidate.status not in ('closed', 'rejected')"));
        assertFalse(sql.contains("account.email"));
        assertTrue(sql.contains("set search_path = ''"));
        assertTrue(sql.contains("revoke all on function public.get_request_review_details"));
    }

    @Test
    void requestReviewReadUsesInvokerAfterContactSnapshot() throws IOException {
        String sql = normalized(REVIEW_PRIVILEGES);

        assertTrue(sql.contains("private.populate_request_contact_snapshot"));
        assertTrue(sql.contains("coalesce(account.phone, account.email)"));
        assertTrue(sql.contains("create or replace function public.get_request_review_details"));
        assertTrue(sql.contains("security invoker"));
        assertFalse(sql.contains("join public.accounts"));
    }

    @Test
    void coordinatorEntryUsesTheReliableEvidenceOperationLifecycle() throws IOException {
        String sql = normalized(COORDINATOR_OPERATIONS);

        assertTrue(sql.contains("'record_coordinator_request'"));
        assertTrue(sql.contains("create or replace function public.begin_request_operation"));
        assertTrue(sql.contains("create or replace function public.abandon_request_operation"));
        assertTrue(sql.contains("private.current_app_role() <> 'coordinator'"));
        assertTrue(sql.contains("if operation.status <> 'pending' then return false"));
        assertTrue(sql.contains("state = 'orphan_candidate'"));
    }

    @Test
    void controlledReferenceChangesAreVersionedAuditedAndNeverDeleteHistory() throws IOException {
        String sql = normalized(CONTROLLED_REFERENCES);

        assertTrue(sql.contains("create table if not exists public.disaster_types"));
        assertTrue(sql.contains("create table if not exists public.standard_decline_reasons"));
        assertTrue(sql.contains("create table if not exists public.reference_data_audit"));
        assertTrue(sql.contains("alter table public.disaster_types enable row level security"));
        assertTrue(sql.contains("create or replace function public.save_controlled_reference_value"));
        assertTrue(sql.contains("private.current_app_role() <> 'administrator'"));
        assertTrue(sql.contains("reference value changed; refresh and retry"));
        assertTrue(sql.contains("insert into public.reference_data_audit"));
        assertTrue(sql.contains("operation.status = 'committed' then return operation.result"));
        assertTrue(sql.contains("set search_path = ''"));
        assertTrue(sql.contains("revoke all on function public.save_controlled_reference_value"));
        assertFalse(sql.contains("delete from public.skills"));
        assertFalse(sql.contains("delete from public.service_areas"));
        assertFalse(sql.contains("delete from public.disaster_types"));
    }

    @Test
    void referenceAuditIsIndexedAndRequestReadRulesUseOnePolicy() throws IOException {
        String sql = normalized(REFERENCE_POLICY_OPTIMIZATION);

        assertTrue(sql.contains("reference_data_audit_actor_idx"));
        assertTrue(sql.contains("drop policy if exists requests_select_authorized"));
        assertTrue(sql.contains("drop policy if exists requests_select_assigned_volunteer"));
        assertTrue(sql.contains("requester_account_id = (select auth.uid())"));
        assertTrue(sql.contains("private.current_app_role()")
                && sql.contains("'coordinator', 'administrator'"));
        assertTrue(sql.contains("private.is_request_assigned_to_actor(id)"));
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).toLowerCase();
    }
}
