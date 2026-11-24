package pk.edu.nu.aafatlink.domain.request;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Display-safe committed evidence metadata available during UC03 review. */
public record ReviewEvidence(UUID id, String fileName, String contentType, long sizeBytes, Instant uploadedAt) {
    public ReviewEvidence {
        Objects.requireNonNull(id, "id");
        fileName = requireText(fileName, "fileName");
        contentType = requireText(contentType, "contentType");
        if (sizeBytes < 1 || sizeBytes > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Evidence size is outside the allowed limit.");
        }
        Objects.requireNonNull(uploadedAt, "uploadedAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
