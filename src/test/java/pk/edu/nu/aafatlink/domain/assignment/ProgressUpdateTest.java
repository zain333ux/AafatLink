package pk.edu.nu.aafatlink.domain.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProgressUpdateTest {
    @Test
    void acceptsOptionalPercentageFromZeroThroughNinetyNine() {
        ProgressUpdate withoutPercentage = update(null, "Initial assessment completed.");
        ProgressUpdate atUpperBoundary = update(99, "Final checks are still pending.");

        assertTrue(withoutPercentage.percentage().isEmpty());
        assertEquals(99, atUpperBoundary.completionPercent());
        assertTrue(withoutPercentage.isValid());
        assertTrue(atUpperBoundary.isValid());
    }

    @Test
    void rejectsMissingLongOrCompletionPercentageNotes() {
        assertThrows(IllegalArgumentException.class, () -> update(50, " "));
        assertThrows(IllegalArgumentException.class, () -> update(100, "Finished"));
        assertThrows(IllegalArgumentException.class, () -> update(50, "x".repeat(501)));
    }

    private static ProgressUpdate update(Integer percent, String note) {
        return new ProgressUpdate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), note, percent,
                Instant.parse("2026-11-21T09:00:00Z"));
    }
}
