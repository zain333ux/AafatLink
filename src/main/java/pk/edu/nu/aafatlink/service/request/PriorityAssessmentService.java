package pk.edu.nu.aafatlink.service.request;

import java.time.Instant;
import java.util.Objects;

import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;

/** Applies the consistent UC04 suggestion rules while leaving the final decision to the coordinator. */
public final class PriorityAssessmentService {
    public RequestPriority suggestPriority(AssistanceRequest request, Instant assessedAt) {
        return Objects.requireNonNull(request, "request")
                .suggestPriorityLevel(Objects.requireNonNull(assessedAt, "assessedAt"));
    }

    public String validateSelection(RequestPriority suggested, RequestPriority selected, String justification) {
        Objects.requireNonNull(suggested, "suggested");
        Objects.requireNonNull(selected, "selected");
        if (suggested == selected) {
            return null;
        }
        String normalized = justification == null ? "" : justification.trim();
        if (normalized.length() < 10 || normalized.length() > 1000) {
            throw new IllegalArgumentException("Explain the priority override in 10 to 1000 characters.");
        }
        return normalized;
    }

}
