package pk.edu.nu.aafatlink.application.access;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.account.AccessChanges;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataChange;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataType;
import pk.edu.nu.aafatlink.domain.account.Role;
import pk.edu.nu.aafatlink.domain.account.UserAccount;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;
import pk.edu.nu.aafatlink.repository.UserRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

/** GRASP application controller for UC12. Database rules recheck every decision atomically. */
public final class AccessController {
    private final UserRepository users;
    private final WorkflowRepository workflows;

    public AccessController(UserRepository users, WorkflowRepository workflows) {
        this.users = Objects.requireNonNull(users, "users");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
    }

    public List<UserAccount> listUsers(AuthSession session) {
        return users.findUsers(Objects.requireNonNull(session, "session"));
    }

    public List<Role> listRoles(AuthSession session) {
        return users.findRoles(Objects.requireNonNull(session, "session"));
    }

    public List<ControlledReferenceValue> listReferenceValues(AuthSession session, ReferenceDataType type) {
        return users.findReferenceValues(Objects.requireNonNull(session, "session"),
                Objects.requireNonNull(type, "type"));
    }

    public ControlledReferenceValue saveReferenceValue(AuthSession session, ReferenceDataChange change) {
        return users.saveReferenceValue(Objects.requireNonNull(session, "session"), UUID.randomUUID(),
                Objects.requireNonNull(change, "change"));
    }

    public Optional<UserAccount> getAccessConfiguration(AuthSession session, UUID userId) {
        return users.findUser(Objects.requireNonNull(session, "session"), Objects.requireNonNull(userId, "userId"));
    }

    public AccessValidation validateAccessChange(AuthSession session, UserAccount target, AccessChanges changes) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(changes, "changes");
        if (!target.canApplyAccessChanges(changes)) {
            return AccessValidation.rejected("The selected role and volunteer approval are incompatible.");
        }
        if (session.userId().equals(target.id()) && changes.role() != target.role()) {
            return AccessValidation.rejected("Administrators cannot change their own role.");
        }
        boolean removesActiveAdministrator = target.role() == UserRole.ADMINISTRATOR && target.isActive()
                && (changes.role() != UserRole.ADMINISTRATOR || changes.status() != AccountStatus.ACTIVE);
        if (removesActiveAdministrator && users.countActiveAdministrators(session) <= 1) {
            return AccessValidation.rejected("The last active Administrator cannot be removed.");
        }
        boolean restrictsAccess = changes.role() != target.role() || changes.status() != AccountStatus.ACTIVE
                || (target.role() == UserRole.VOLUNTEER
                    && changes.volunteerApprovalStatus() != VolunteerApprovalStatus.APPROVED);
        if (restrictsAccess && workflows.hasActiveAssignment(session, target.id())) {
            return AccessValidation.rejected("Access cannot be restricted while the user has an active assignment.");
        }
        return AccessValidation.allowedResult();
    }

    public UserAccount updateAccess(AuthSession session, UserAccount selected, AccessChanges changes) {
        Objects.requireNonNull(selected, "selected");
        UserAccount fresh = users.findUser(Objects.requireNonNull(session, "session"), selected.id())
                .orElseThrow(() -> new IllegalArgumentException("The selected account no longer exists."));
        AccessValidation validation = validateAccessChange(session, fresh, changes);
        if (!validation.allowed()) throw new IllegalArgumentException(validation.reason());
        return users.saveAccessWithAudit(session, UUID.randomUUID(), fresh, changes, fresh.version());
    }
}
