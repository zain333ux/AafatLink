package pk.edu.nu.aafatlink.application.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.account.AccessChanges;
import pk.edu.nu.aafatlink.domain.account.AccessChangeLog;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.Permission;
import pk.edu.nu.aafatlink.domain.account.Role;
import pk.edu.nu.aafatlink.domain.account.UserAccount;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;
import pk.edu.nu.aafatlink.repository.UserRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub;

class AccessControllerTest {
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final AuthSession SESSION = new AuthSession(ADMIN_ID, "admin@example.com", "access", "refresh",
            Instant.now().plusSeconds(3600));

    @Test
    void rejectsAnAdministratorChangingTheirOwnRole() {
        UserAccount admin = account(ADMIN_ID, UserRole.ADMINISTRATOR, null);
        FakeUsers users = new FakeUsers(admin, 2);
        AccessController controller = new AccessController(users, new Workflows(false));

        AccessValidation validation = controller.validateAccessChange(SESSION, admin,
                new AccessChanges(UserRole.COORDINATOR, AccountStatus.ACTIVE, null, "Rotation"));

        assertFalse(validation.allowed());
        assertEquals("Administrators cannot change their own role.", validation.reason());
    }

    @Test
    void rejectsRemovingTheLastActiveAdministrator() {
        UserAccount otherAdmin = account(UUID.randomUUID(), UserRole.ADMINISTRATOR, null);
        AccessController controller = new AccessController(new FakeUsers(otherAdmin, 1), new Workflows(false));

        AccessValidation validation = controller.validateAccessChange(SESSION, otherAdmin,
                new AccessChanges(UserRole.COORDINATOR, AccountStatus.ACTIVE, null, "Role changed"));

        assertFalse(validation.allowed());
        assertEquals("The last active Administrator cannot be removed.", validation.reason());
    }

    @Test
    void rejectsWithdrawingVolunteerApprovalDuringActiveWork() {
        UserAccount volunteer = account(UUID.randomUUID(), UserRole.VOLUNTEER,
                VolunteerApprovalStatus.APPROVED);
        AccessController controller = new AccessController(new FakeUsers(volunteer, 2), new Workflows(true));

        AccessValidation validation = controller.validateAccessChange(SESSION, volunteer,
                new AccessChanges(UserRole.VOLUNTEER, AccountStatus.ACTIVE,
                        VolunteerApprovalStatus.REJECTED, "Readiness review failed"));

        assertFalse(validation.allowed());
        assertEquals("Access cannot be restricted while the user has an active assignment.", validation.reason());
    }

    @Test
    void appliesAnApprovedVolunteerChangeThroughTheRepository() {
        UserAccount volunteer = account(UUID.randomUUID(), UserRole.VOLUNTEER,
                VolunteerApprovalStatus.PENDING);
        FakeUsers users = new FakeUsers(volunteer, 2);
        AccessController controller = new AccessController(users, new Workflows(false));
        AccessChanges changes = new AccessChanges(UserRole.VOLUNTEER, AccountStatus.ACTIVE,
                VolunteerApprovalStatus.APPROVED, "Verified documents");

        UserAccount updated = controller.updateAccess(SESSION, volunteer, changes);

        assertEquals(VolunteerApprovalStatus.APPROVED, updated.volunteerApprovalStatus().orElseThrow());
        assertEquals(volunteer.id(), users.savedTarget);
        assertEquals(ADMIN_ID, users.savedLog.administratorAccountId());
        assertEquals(VolunteerApprovalStatus.PENDING,
                users.savedLog.before().volunteerApprovalStatus());
        assertEquals(changes, users.savedLog.after());
    }

    @Test
    void exposesTheDocumentedRolePermissionConfigurations() {
        UserAccount administrator = account(UUID.randomUUID(), UserRole.ADMINISTRATOR, null);
        AccessController controller = new AccessController(new FakeUsers(administrator, 2), new Workflows(false));

        Role role = controller.listRoles(SESSION).get(0);

        assertEquals(UserRole.ADMINISTRATOR, role.roleId());
        assertEquals("access.manage", role.permissions().iterator().next().permissionId());
    }

    private static UserAccount account(UUID id, UserRole role, VolunteerApprovalStatus approval) {
        Role accessRole = new Role(role, role.displayName(),
                Set.of(new Permission("access.manage", "Manage access")));
        return new UserAccount(id, "User", "user@example.com", accessRole, AccountStatus.ACTIVE,
                approval, 0);
    }

    private static final class FakeUsers implements UserRepository {
        private final UserAccount target;
        private final int administratorCount;
        private UUID savedTarget;
        private AccessChangeLog savedLog;

        private FakeUsers(UserAccount target, int administratorCount) {
            this.target = target;
            this.administratorCount = administratorCount;
        }

        @Override public String findContactNumber(AuthSession session, UUID accountId) { return ""; }
        @Override public List<Role> findRoles(AuthSession session) { return List.of(target.accessRole()); }
        @Override public List<pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue> findReferenceValues(
                AuthSession session, pk.edu.nu.aafatlink.domain.account.ReferenceDataType type) { return List.of(); }
        @Override public pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue saveReferenceValue(
                AuthSession session, UUID operationId,
                pk.edu.nu.aafatlink.domain.account.ReferenceDataChange change) { throw new UnsupportedOperationException(); }
        @Override public List<UserAccount> findUsers(AuthSession session) { return List.of(target); }
        @Override public Optional<UserAccount> findUser(AuthSession session, UUID id) {
            return target.id().equals(id) ? Optional.of(target) : Optional.empty();
        }
        @Override public int countActiveAdministrators(AuthSession session) { return administratorCount; }
        @Override public UserAccount saveAccessWithAudit(AuthSession session, UUID operationId,
                UserAccount account, AccessChangeLog log, long expectedVersion) {
            savedTarget = account.id();
            savedLog = log;
            return account.applyAccessChanges(log.after(), expectedVersion + 1);
        }
    }

    private static final class Workflows extends WorkflowRepositoryStub {
        private final boolean active;
        private Workflows(boolean active) { this.active = active; }
        @Override public boolean hasActiveAssignment(AuthSession session, UUID accountId) { return active; }
    }
}
