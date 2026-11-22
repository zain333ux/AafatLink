package pk.edu.nu.aafatlink.domain.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PriorityAssessmentTest {
    @Test
    void requiresJustificationOnlyWhenOverridingTheSuggestion() {
        PriorityAssessment acceptedSuggestion = assessment(RequestPriority.HIGH, RequestPriority.HIGH, null);
        PriorityAssessment override = assessment(RequestPriority.CRITICAL, RequestPriority.HIGH, "Children trapped");

        assertFalse(acceptedSuggestion.requiresJustification());
        assertTrue(acceptedSuggestion.validateSelection());
        assertTrue(override.requiresJustification());
        assertTrue(override.validateSelection());
    }

    @Test
    void rejectsAnUnjustifiedOverride() {
        assertThrows(IllegalArgumentException.class,
                () -> assessment(RequestPriority.CRITICAL, RequestPriority.HIGH, " "));
    }

    private static PriorityAssessment assessment(RequestPriority selected, RequestPriority suggested,
            String justification) {
        return new PriorityAssessment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                selected, suggested, justification, Instant.now());
    }
}
