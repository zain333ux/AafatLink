package pk.edu.nu.aafatlink.domain.request;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Request information available to its owner for UC02 tracking. */
public record AssistanceRequest(
        UUID id,
        AssistanceRequestDraft details,
        RequestStatus status,
        RequestPriority priority,
        boolean possibleDuplicate,
        Instant submittedAt,
        Instant resubmittedAt,
        long version,
        String coordinatorReason,
        String assignmentState,
        String latestProgress) {
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,5})\\b");

    public AssistanceRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** Whether UC06 may review this request for final closure. */
    public boolean canBeClosed() {
        return status == RequestStatus.AWAITING_CLOSURE_REVIEW;
    }

    public boolean canBeVerified() {
        return status == RequestStatus.PENDING_VERIFICATION;
    }

    public boolean canBePrioritized() {
        return status.canBePrioritized();
    }

    public boolean canBeAssigned() {
        return status == RequestStatus.VERIFIED && priority != null;
    }

    /** Returns an immutable preview; the authorized RPC remains the committed source of truth. */
    public AssistanceRequest setPriority(RequestPriority level) {
        if (!canBePrioritized()) {
            throw new IllegalStateException("This request cannot be prioritized in its current status.");
        }
        return copy(status, Objects.requireNonNull(level, "level"));
    }

    /** Returns an immutable preview of a legal lifecycle transition. */
    public AssistanceRequest transitionTo(RequestStatus target) {
        Objects.requireNonNull(target, "target");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Request cannot move from " + status.displayName()
                    + " to " + target.displayName() + ".");
        }
        return copy(target, target == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED ? null : priority);
    }

    /** Consistent UC04 suggestion derived from the facts owned by this request. */
    public RequestPriority suggestPriorityLevel(Instant assessedAt) {
        Objects.requireNonNull(assessedAt, "assessedAt");
        if (assessedAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException("Assessment time cannot be before request submission.");
        }
        String text = (details.description() + " " + details.requiredAssistance() + " "
                + details.affectedPersonDetails() + " " + details.disasterType().name())
                .toLowerCase(Locale.ROOT);
        int affected = largestNumber(details.affectedPersonDetails());

        RequestPriority suggestion;
        if (affected >= 20 || containsAny(text, "trapped", "unconscious", "life-threatening",
                "severe bleeding", "immediate medical", "immediate evacuation")) {
            suggestion = RequestPriority.CRITICAL;
        } else if (affected >= 10 || containsAny(text, "medical", "evacuation", "fire", "earthquake",
                "landslide", "elderly", "pregnant", "disability")) {
            suggestion = RequestPriority.HIGH;
        } else if (affected >= 3 || containsAny(text, "shelter", "food", "water", "flood")) {
            suggestion = RequestPriority.MEDIUM;
        } else {
            suggestion = RequestPriority.LOW;
        }
        return Duration.between(submittedAt, assessedAt).toHours() >= 24 ? escalate(suggestion) : suggestion;
    }

    private AssistanceRequest copy(RequestStatus newStatus, RequestPriority newPriority) {
        return new AssistanceRequest(id, details, newStatus, newPriority, possibleDuplicate, submittedAt,
                resubmittedAt, version, coordinatorReason, assignmentState, latestProgress);
    }

    private static int largestNumber(String text) {
        int largest = 0;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) largest = Math.max(largest, Integer.parseInt(matcher.group(1)));
        return largest;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    private static RequestPriority escalate(RequestPriority priority) {
        return switch (priority) {
            case LOW -> RequestPriority.MEDIUM;
            case MEDIUM -> RequestPriority.HIGH;
            case HIGH, CRITICAL -> RequestPriority.CRITICAL;
        };
    }
}
