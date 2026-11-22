package pk.edu.nu.aafatlink.domain.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class RoleTest {
    @Test
    void grantsOnlyPermissionsAssignedToTheRole() {
        Permission verify = new Permission("request.verify", "Verify assistance requests");
        Role coordinator = new Role(UserRole.COORDINATOR, "Coordinator", Set.of(verify));

        assertTrue(coordinator.grants(new Permission("request.verify", "Updated display text")));
        assertFalse(coordinator.grants(new Permission("access.manage", "Manage access")));
    }
}
