package pk.edu.nu.aafatlink.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.account.UserRole;

class SessionManagerTest {

    @Test
    void startsAndEndsAnExplicitlyMarkedDemonstrationSession() {
        SessionManager sessions = new SessionManager();

        sessions.startDemonstrationSession(UserRole.VOLUNTEER);

        SessionUser current = sessions.currentUser().orElseThrow();
        assertEquals(UserRole.VOLUNTEER, current.role());
        assertTrue(current.demonstrationAccount());

        sessions.logout();
        assertTrue(sessions.currentUser().isEmpty());
    }
}
