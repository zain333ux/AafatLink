package pk.edu.nu.aafatlink.domain.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AssistanceRequestTest {
    @Test
    void ownsVerificationPrioritizationAndAssignmentEligibility() {
        AssistanceRequest pending = request(RequestStatus.PENDING_VERIFICATION, null,
                "Roof damaged", "Food and water", "One adult");
        AssistanceRequest verified = pending.transitionTo(RequestStatus.VERIFIED);

        assertTrue(pending.canBeVerified());
        assertFalse(pending.canBePrioritized());
        assertTrue(verified.canBePrioritized());
        assertFalse(verified.canBeAssigned());

        AssistanceRequest prioritized = verified.setPriority(RequestPriority.HIGH);
        assertTrue(prioritized.canBeAssigned());
        assertEquals(RequestPriority.HIGH, prioritized.priority());
        assertEquals(null, verified.priority(), "Domain snapshots remain immutable until the RPC commits.");
    }

    @Test
    void rejectsIllegalLifecycleAndPriorityChanges() {
        AssistanceRequest pending = request(RequestStatus.PENDING_VERIFICATION, null,
                "Roof damaged", "Food and water", "One adult");

        assertThrows(IllegalStateException.class, () -> pending.setPriority(RequestPriority.HIGH));
        assertThrows(IllegalStateException.class, () -> pending.transitionTo(RequestStatus.ASSIGNED));
    }

    @Test
    void suggestsPriorityFromRequestFactsAndWaitingTime() {
        AssistanceRequest urgent = request(RequestStatus.VERIFIED, null,
                "Two people are trapped with severe bleeding", "Immediate medical evacuation", "2 adults");
        AssistanceRequest waiting = request(RequestStatus.VERIFIED, null,
                "Roof damaged after rainfall", "Food and drinking water", "One adult");

        assertEquals(RequestPriority.CRITICAL,
                urgent.suggestPriorityLevel(Instant.parse("2026-11-20T01:00:00Z")));
        assertEquals(RequestPriority.HIGH,
                waiting.suggestPriorityLevel(Instant.parse("2026-11-21T01:00:00Z")));
    }

    private static AssistanceRequest request(RequestStatus status, RequestPriority priority,
            String description, String assistance, String people) {
        return new AssistanceRequest(UUID.randomUUID(), new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", description, assistance, people), status, priority, false,
                Instant.parse("2026-11-20T00:00:00Z"), null, 0, null, null, null);
    }
}
