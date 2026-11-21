package pk.edu.nu.aafatlink.domain.assignment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClosureReviewTest {
    @Test
    void approvedReviewAllowsOptionalRemarks() {
        ClosureReview review = review(ClosureDecision.APPROVED, null);
        assertTrue(review.remarks().isEmpty());
    }

    @Test
    void reworkReviewRequiresReason() {
        assertThrows(IllegalArgumentException.class,
                () -> review(ClosureDecision.RETURNED_FOR_REWORK, " "));
    }

    private static ClosureReview review(ClosureDecision decision, String remarks) {
        return new ClosureReview(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                decision, remarks, Instant.parse("2026-11-21T10:00:00Z"));
    }
}
