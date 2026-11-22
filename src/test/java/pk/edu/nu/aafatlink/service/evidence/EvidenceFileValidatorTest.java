package pk.edu.nu.aafatlink.service.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;

class EvidenceFileValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsDocumentedEvidenceFormatsWithinFiveMegabytes() throws IOException {
        Path evidence = temporaryDirectory.resolve("damage-photo.JPG");
        Files.write(evidence, new byte[] { 1, 2, 3 });

        EvidenceAttachment attachment = new EvidenceFileValidator().validate(evidence);

        assertEquals("image/jpeg", attachment.contentType());
        assertEquals(3, attachment.sizeBytes());
        assertTrue(attachment.isValid());
    }

    @Test
    void rejectsUnsupportedFormats() throws IOException {
        Path evidence = temporaryDirectory.resolve("notes.txt");
        Files.writeString(evidence, "not supported");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EvidenceFileValidator().validate(evidence));

        assertEquals("Evidence must be a JPG, PNG or PDF file.", error.getMessage());
    }

    @Test
    void rejectsFilesOverFiveMegabytes() throws IOException {
        Path evidence = temporaryDirectory.resolve("oversized.pdf");
        try (var channel = Files.newByteChannel(evidence,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(EvidenceFileValidator.MAXIMUM_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] { 1 }));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EvidenceFileValidator().validate(evidence));

        assertEquals("Evidence files must not exceed 5 MB.", error.getMessage());
    }
}
