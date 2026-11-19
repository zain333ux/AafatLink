package pk.edu.nu.aafatlink.service.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Enforces the UC01 evidence type and size rules before a file is staged. */
public final class EvidenceFileValidator {
    public static final long MAXIMUM_BYTES = 5L * 1024L * 1024L;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf");

    public EvidenceAttachment validate(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Choose an evidence file that is available on this computer.");
        }
        String contentType = CONTENT_TYPES.get(extension(normalized));
        if (contentType == null) {
            throw new IllegalArgumentException("Evidence must be a JPG, PNG or PDF file.");
        }
        try {
            long size = Files.size(normalized);
            if (size > MAXIMUM_BYTES) {
                throw new IllegalArgumentException("Evidence files must not exceed 5 MB.");
            }
            return new EvidenceAttachment(normalized, contentType, size);
        } catch (IOException exception) {
            throw new IllegalArgumentException("AafatLink could not read the selected evidence file.", exception);
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
