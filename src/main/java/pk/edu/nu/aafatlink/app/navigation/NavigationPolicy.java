package pk.edu.nu.aafatlink.app.navigation;

import java.util.List;
import java.util.Objects;

import pk.edu.nu.aafatlink.domain.account.UserRole;

/** Defines visible destinations only. Database authorization remains authoritative. */
public final class NavigationPolicy {
    public List<NavigationItem> itemsFor(UserRole role) {
        Objects.requireNonNull(role, "role");
        return switch (role) {
            case REQUESTER -> List.of(NavigationItem.DASHBOARD, NavigationItem.SUBMIT_REQUEST,
                    NavigationItem.MY_REQUESTS);
            case VOLUNTEER -> List.of(NavigationItem.DASHBOARD, NavigationItem.READINESS_PROFILE,
                    NavigationItem.ASSIGNMENTS, NavigationItem.TASK_PROGRESS);
            case COORDINATOR -> List.of(NavigationItem.DASHBOARD, NavigationItem.VERIFICATION_QUEUE,
                    NavigationItem.PRIORITY_QUEUE, NavigationItem.VOLUNTEER_ASSIGNMENT,
                    NavigationItem.CLOSURE_REVIEW, NavigationItem.REPORTS);
            case ADMINISTRATOR -> List.of(NavigationItem.DASHBOARD, NavigationItem.ACCESS_MANAGEMENT,
                    NavigationItem.REPORTS);
        };
    }
}
