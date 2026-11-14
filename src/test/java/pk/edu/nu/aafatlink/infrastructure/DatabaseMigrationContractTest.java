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

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).toLowerCase();
    }
}
