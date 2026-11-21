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

    @Test
    void recordsProgressOnlyForAssignedVolunteerAndActiveState() {
        UUID volunteerId = UUID.randomUUID();
        VolunteerAssignment assignment = assignment(volunteerId);
        assignment.accept(volunteerId, Instant.now());

        ProgressUpdate update = assignment.recordProgress(volunteerId, "Supplies delivered to the shelter.", 45,
                Instant.parse("2026-11-21T09:00:00Z"));

        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.status());
        assertEquals(45, update.completionPercent());
        assertThrows(IllegalStateException.class,
                () -> assignment.recordProgress(UUID.randomUUID(), "Unauthorized", 50, Instant.now()));
    }

    @Test
    void completesAcceptedTaskAndSupportsClosureOrRework() {
        UUID volunteerId = UUID.randomUUID();
        VolunteerAssignment assignment = assignment(volunteerId);
        assignment.accept(volunteerId, Instant.now());

        TaskCompletion completion = assignment.createCompletion(volunteerId,
                "Family received shelter and emergency supplies.", Instant.now());

        assertTrue(completion.hasValidRemarks());
        assertEquals(AssignmentStatus.AWAITING_CLOSURE_REVIEW, assignment.status());
        assertThrows(IllegalArgumentException.class, () -> assignment.returnForRework(" "));
        assignment.returnForRework("Add a signed delivery note.");
        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.status());
    }

    private static VolunteerAssignment assignment(UUID volunteerId) {
        return new VolunteerAssignment(UUID.randomUUID(), UUID.randomUUID(), volunteerId,
                AssignmentStatus.PENDING_ACCEPTANCE, Instant.parse("2026-11-21T07:00:00Z"),
                null, null, null, null, 2);
    }
}
