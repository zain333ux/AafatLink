package pk.edu.nu.aafatlink.domain.assignment;

import java.util.EnumSet;
import java.util.Set;

/** Documented volunteer-assignment lifecycle. */
public enum AssignmentStatus {
    PENDING_ACCEPTANCE,
    ACCEPTED,
    IN_PROGRESS,
    AWAITING_CLOSURE_REVIEW,
    COMPLETED,
    DECLINED,
    REASSIGNED;

    public boolean canTransitionTo(AssignmentStatus target) {
        if (target == null) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    private Set<AssignmentStatus> allowedTargets() {
        return switch (this) {
            case PENDING_ACCEPTANCE -> EnumSet.of(ACCEPTED, DECLINED, REASSIGNED);
            case ACCEPTED -> EnumSet.of(IN_PROGRESS, REASSIGNED);
            case IN_PROGRESS -> EnumSet.of(AWAITING_CLOSURE_REVIEW, REASSIGNED);
            case AWAITING_CLOSURE_REVIEW -> EnumSet.of(COMPLETED, IN_PROGRESS);
            case COMPLETED, DECLINED, REASSIGNED -> EnumSet.noneOf(AssignmentStatus.class);
        };
    }
}
