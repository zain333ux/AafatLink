package pk.edu.nu.aafatlink.domain.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VolunteerAssignmentTest {
    @Test
    void assignedVolunteerCanAcceptPendingAssignment() {
        UUID volunteerId = UUID.randomUUID();
        VolunteerAssignment assignment = assignment(volunteerId);

        assignment.accept(volunteerId, Instant.parse("2026-11-21T08:00:00Z"));

        assertEquals(AssignmentStatus.ACCEPTED, assignment.status());
        assertTrue(assignment.responseReason().isEmpty());
    }

    @Test
    void declineRequiresReason() {
        VolunteerAssignment assignment = assignment(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> assignment.decline(assignment.volunteerAccountId(), " ", Instant.now()));
    }

    @Test
    void differentVolunteerOrFinalAssignmentCannotRespond() {
        UUID volunteerId = UUID.randomUUID();
        VolunteerAssignment assignment = assignment(volunteerId);

        assertThrows(IllegalArgumentException.class,
                () -> assignment.accept(UUID.randomUUID(), Instant.now()));
        assignment.decline(volunteerId, "Unavailable during the response window.", Instant.now());
        assertThrows(IllegalStateException.class, () -> assignment.accept(volunteerId, Instant.now()));
    }

    private static VolunteerAssignment assignment(UUID volunteerId) {
        return new VolunteerAssignment(UUID.randomUUID(), UUID.randomUUID(), volunteerId,
                AssignmentStatus.PENDING_ACCEPTANCE, Instant.parse("2026-11-21T07:00:00Z"),
                null, null, null, null, 2);
    }
}
