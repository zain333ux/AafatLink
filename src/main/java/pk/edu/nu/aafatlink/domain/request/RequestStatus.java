package pk.edu.nu.aafatlink.domain.request;

import java.util.EnumSet;
import java.util.Set;

/** Documented assistance-request lifecycle. Priority is intentionally separate. */
public enum RequestStatus {
    PENDING_VERIFICATION,
    ADDITIONAL_INFORMATION_REQUIRED,
    VERIFIED,
    ASSIGNED,
    IN_PROGRESS,
    AWAITING_CLOSURE_REVIEW,
    CLOSED,
    REJECTED;

    public boolean canTransitionTo(RequestStatus target) {
        if (target == null) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    private Set<RequestStatus> allowedTargets() {
        return switch (this) {
            case PENDING_VERIFICATION -> EnumSet.of(VERIFIED, ADDITIONAL_INFORMATION_REQUIRED, REJECTED);
            case ADDITIONAL_INFORMATION_REQUIRED -> EnumSet.of(PENDING_VERIFICATION);
            case VERIFIED -> EnumSet.of(ASSIGNED);
            case ASSIGNED -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(AWAITING_CLOSURE_REVIEW);
            case AWAITING_CLOSURE_REVIEW -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED, REJECTED -> EnumSet.noneOf(RequestStatus.class);
        };
    }
}
