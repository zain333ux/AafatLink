package pk.edu.nu.aafatlink.domain.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VerificationRecordTest {
    @Test
    void requiresAReasonForARejectedRequest() {
        assertThrows(IllegalArgumentException.class, () -> new VerificationRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                VerificationDecision.REJECTED, " ", Instant.now()));
    }

    @Test
    void normalizesTheRecordedReason() {
        VerificationRecord record = new VerificationRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                VerificationDecision.ADDITIONAL_INFORMATION_REQUIRED,
                "  Upload a clearer image.  ", Instant.now());

        assertEquals("Upload a clearer image.", record.reason());
    }
}
