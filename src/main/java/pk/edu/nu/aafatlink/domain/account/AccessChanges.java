package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;

import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;

/** Proposed UC12 role, account-state and volunteer-approval configuration. */
public record AccessChanges(UserRole role, AccountStatus status,
        VolunteerApprovalStatus volunteerApprovalStatus, String reason) {
    public AccessChanges {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        if (status == AccountStatus.SUSPENDED) {
            throw new IllegalArgumentException("UC12 uses Active or Deactivated account status.");
        }
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        if (volunteerApprovalStatus == VolunteerApprovalStatus.REJECTED && reason == null) {
            throw new IllegalArgumentException("A volunteer rejection requires a reason.");
        }
    }
}
