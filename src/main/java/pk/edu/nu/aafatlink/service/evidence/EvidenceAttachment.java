package pk.edu.nu.aafatlink.service.evidence;

import java.nio.file.Path;
import java.util.Objects;

/** A locally validated evidence file that is safe to offer for staging. */
public record EvidenceAttachment(Path path, String contentType, long sizeBytes) {
    public EvidenceAttachment {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }

    public String fileName() {
        return path.getFileName().toString();
    }
}
