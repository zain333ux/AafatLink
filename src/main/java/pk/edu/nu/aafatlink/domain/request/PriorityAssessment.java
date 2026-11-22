package pk.edu.nu.aafatlink.domain.request;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable UC04 assessment that owns override-justification validation. */
public record PriorityAssessment(UUID assessmentId, UUID requestId, UUID assessorId,
        RequestPriority priorityLevel, RequestPriority suggestedLevel,
        String justification, Instant assessedAt) {
    public PriorityAssessment {
        Objects.requireNonNull(assessmentId, "assessmentId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(assessorId, "assessorId");
        Objects.requireNonNull(priorityLevel, "priorityLevel");
        Objects.requireNonNull(suggestedLevel, "suggestedLevel");
        Objects.requireNonNull(assessedAt, "assessedAt");
        if (priorityLevel == suggestedLevel) {
            justification = null;
        } else {
            String normalized = justification == null ? "" : justification.trim();
            if (normalized.length() < 10 || normalized.length() > 1000) {
                throw new IllegalArgumentException("Explain the priority override in 10 to 1000 characters.");
            }
            justification = normalized;
        }
    }

    public boolean requiresJustification() {
        return priorityLevel != suggestedLevel;
    }

    public boolean validateSelection() {
        return !requiresJustification() || justification != null;
    }
}
