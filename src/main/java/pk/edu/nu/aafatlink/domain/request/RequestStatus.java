package pk.edu.nu.aafatlink.domain.request;

import java.util.EnumSet;
import java.util.Set;

/** Documented assistance-request lifecycle. Priority is intentionally separate. */
public enum RequestStatus {
    PENDING_VERIFICATION("Pending Verification"),
    ADDITIONAL_INFORMATION_REQUIRED("Additional Information Required"),
    VERIFIED("Verified"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    AWAITING_CLOSURE_REVIEW("Awaiting Closure Review"),
    CLOSED("Closed"),
    REJECTED("Rejected");

    private final String displayName;

    RequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canRequesterResubmit() {
        return this == ADDITIONAL_INFORMATION_REQUIRED;
    }

    public boolean canBePrioritized() {
        return this == VERIFIED || this == ASSIGNED || this == IN_PROGRESS;
    }

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
            case ASSIGNED -> EnumSet.of(IN_PROGRESS, AWAITING_CLOSURE_REVIEW);
            case IN_PROGRESS -> EnumSet.of(AWAITING_CLOSURE_REVIEW);
            case AWAITING_CLOSURE_REVIEW -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED, REJECTED -> EnumSet.noneOf(RequestStatus.class);
        };
    }
}
