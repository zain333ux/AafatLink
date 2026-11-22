package pk.edu.nu.aafatlink.app.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.account.UserRole;

class NavigationPolicyTest {

    private final NavigationPolicy policy = new NavigationPolicy();

    @Test
    void requesterNavigationContainsRequestWorkflowsOnly() {
        List<NavigationItem> items = policy.itemsFor(UserRole.REQUESTER);

        assertTrue(items.contains(NavigationItem.SUBMIT_REQUEST));
        assertTrue(items.contains(NavigationItem.MY_REQUESTS));
        assertFalse(items.contains(NavigationItem.ACCESS_MANAGEMENT));
        assertFalse(items.contains(NavigationItem.VERIFICATION_QUEUE));
    }

    @Test
    void volunteerNavigationContainsReadinessAndAssignmentWorkflows() {
        List<NavigationItem> items = policy.itemsFor(UserRole.VOLUNTEER);

        assertTrue(items.contains(NavigationItem.READINESS_PROFILE));
        assertTrue(items.contains(NavigationItem.ASSIGNMENTS));
        assertFalse(items.contains(NavigationItem.ACCESS_MANAGEMENT));
    }

    @Test
    void coordinatorNavigationContainsOperationalQueuesAndReports() {
        List<NavigationItem> items = policy.itemsFor(UserRole.COORDINATOR);

        assertTrue(items.contains(NavigationItem.VERIFICATION_QUEUE));
        assertTrue(items.contains(NavigationItem.VOLUNTEER_ASSIGNMENT));
        assertTrue(items.contains(NavigationItem.REPORTS));
        assertFalse(items.contains(NavigationItem.ACCESS_MANAGEMENT));
    }

    @Test
    void administratorNavigationContainsAccessManagement() {
        List<NavigationItem> items = policy.itemsFor(UserRole.ADMINISTRATOR);

        assertTrue(items.contains(NavigationItem.ACCESS_MANAGEMENT));
        assertTrue(items.contains(NavigationItem.REFERENCE_DATA));
        assertTrue(items.contains(NavigationItem.REPORTS));
    }
}
