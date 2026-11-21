package pk.edu.nu.aafatlink.domain.request;

import java.util.List;
import java.util.Objects;

/** Complete UC03 review context: request, caller snapshot, evidence and duplicate candidates. */
public record RequestReviewDetails(AssistanceRequest request, String requesterName,
        String requesterContact, List<ReviewEvidence> evidence,
        List<PossibleDuplicateMatch> possibleDuplicates) {
    public RequestReviewDetails {
        Objects.requireNonNull(request, "request");
        requesterName = normalize(requesterName, "Requester name unavailable");
        requesterContact = normalize(requesterContact, "Contact unavailable");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        possibleDuplicates = List.copyOf(possibleDuplicates == null ? List.of() : possibleDuplicates);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
