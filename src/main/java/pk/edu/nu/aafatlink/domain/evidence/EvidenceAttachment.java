package pk.edu.nu.aafatlink.domain.evidence;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Local evidence value object that owns the documented type and size rules. */
public record EvidenceAttachment(Path path, String contentType, long sizeBytes) {
    public static final long MAXIMUM_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    public EvidenceAttachment {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Evidence must be a JPG, PNG or PDF file.");
        }
        if (sizeBytes < 0 || sizeBytes > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Evidence files must not exceed 5 MB.");
        }
    }

    public String fileName() {
        return path.getFileName().toString();
    }

    public boolean isValid() {
        return !fileName().isBlank() && SUPPORTED_CONTENT_TYPES.contains(contentType)
                && sizeBytes >= 0 && sizeBytes <= MAXIMUM_BYTES;
    }
}
