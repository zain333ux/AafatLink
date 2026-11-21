package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;

/** Encapsulated access-control aggregate used by UC12. */
public final class UserAccount {
    private final UUID id;
    private final String displayName;
    private final String email;
    private final UserRole role;
    private final AccountStatus status;
    private final VolunteerApprovalStatus volunteerApprovalStatus;
    private final long version;
    private final Set<String> permissions;

    public UserAccount(UUID id, String displayName, String email, UserRole role, AccountStatus status,
            VolunteerApprovalStatus volunteerApprovalStatus, long version, Set<String> permissions) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = requireText(displayName, "displayName");
        this.email = requireText(email, "email");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.volunteerApprovalStatus = volunteerApprovalStatus;
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.permissions = Set.copyOf(permissions);
    }

    public UUID id() { return id; }
    public String displayName() { return displayName; }
    public String email() { return email; }
    public UserRole role() { return role; }
    public AccountStatus status() { return status; }
    public Optional<VolunteerApprovalStatus> volunteerApprovalStatus() {
        return Optional.ofNullable(volunteerApprovalStatus);
    }
    public long version() { return version; }
    public Set<String> permissions() { return permissions; }
    public boolean isActive() { return status == AccountStatus.ACTIVE; }

    public boolean canApplyAccessChanges(AccessChanges changes) {
        Objects.requireNonNull(changes, "changes");
        return changes.role() == UserRole.VOLUNTEER
                ? changes.volunteerApprovalStatus() != null
                : changes.volunteerApprovalStatus() == null;
    }

    public UserAccount withAccess(AccessChanges changes, long newVersion) {
        if (!canApplyAccessChanges(changes)) {
            throw new IllegalArgumentException("Volunteer approval is compatible only with the Volunteer role.");
        }
        return new UserAccount(id, displayName, email, changes.role(), changes.status(),
                changes.volunteerApprovalStatus(), newVersion, permissions);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
