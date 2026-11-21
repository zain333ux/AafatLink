package pk.edu.nu.aafatlink.domain.assignment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;

/** Auditable UC10 completion details created by the assigned volunteer. */
public final class TaskCompletion {
    private final UUID completionId;
    private final UUID assignmentId;
    private final UUID volunteerAccountId;
    private final String remarks;
    private final Instant completedAt;
    private final List<EvidenceAttachment> evidence = new ArrayList<>();

    public TaskCompletion(UUID completionId, UUID assignmentId, UUID volunteerAccountId,
            String remarks, Instant completedAt) {
        this.completionId = Objects.requireNonNull(completionId, "completionId");
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        this.volunteerAccountId = Objects.requireNonNull(volunteerAccountId, "volunteerAccountId");
        this.remarks = normalizeRemarks(remarks);
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    public void linkEvidence(EvidenceAttachment attachment) {
        evidence.add(Objects.requireNonNull(attachment, "attachment"));
    }

    public boolean hasValidRemarks() {
        return !remarks.isBlank();
    }

    public UUID completionId() { return completionId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID volunteerAccountId() { return volunteerAccountId; }
    public String remarks() { return remarks; }
    public Instant completedAt() { return completedAt; }
    public List<EvidenceAttachment> evidence() { return List.copyOf(evidence); }

    private static String normalizeRemarks(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Completion remarks are required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 4_000) {
            throw new IllegalArgumentException("Completion remarks must not exceed 4000 characters.");
        }
        return normalized;
    }
}
