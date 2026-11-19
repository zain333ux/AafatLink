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
            "20261114231131_create_aafatlink_domain.sql");
    private static final Path RPCS = Path.of("supabase", "migrations",
            "20261114231516_add_authorized_workflow_rpcs.sql");
    private static final Path STORAGE = Path.of("supabase", "migrations",
            "20261114231716_configure_evidence_storage.sql");
    private static final Path ONBOARDING = Path.of("supabase", "migrations",
            "20261114232825_add_account_onboarding.sql");
    private static final Path HARDENING = Path.of("supabase", "migrations",
            "20261115093254_harden_database_baseline.sql");
    private static final Path UC01_ALIGNMENT = Path.of("supabase", "migrations",
            "20261115093854_align_request_submission_with_uc01.sql");
    private static final Path REQUESTER_TRACKING = Path.of("supabase", "migrations",
            "20261119235500_allow_requester_progress_tracking.sql");
    private static final Path EVIDENCE_RECOVERY = Path.of("supabase", "migrations",
            "20261120090000_add_reliable_evidence_staging.sql");

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
        String sql = normalized(DOMAIN) + "\n" + normalized(RPCS) + "\n" + normalized(ONBOARDING);
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

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).toLowerCase();
    }
}
