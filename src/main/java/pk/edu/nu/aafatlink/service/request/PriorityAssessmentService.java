package pk.edu.nu.aafatlink.service.request;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;

/** Applies the consistent UC04 suggestion rules while leaving the final decision to the coordinator. */
public final class PriorityAssessmentService {
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,5})\\b");

    public RequestPriority suggestPriority(AssistanceRequest request, Instant assessedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(assessedAt, "assessedAt");
        String text = (request.details().description() + " " + request.details().requiredAssistance() + " "
                + request.details().affectedPersonDetails() + " " + request.details().disasterType().name())
                .toLowerCase(Locale.ROOT);
        int affected = largestNumber(request.details().affectedPersonDetails());

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

        if (Duration.between(request.submittedAt(), assessedAt).toHours() >= 24) {
            suggestion = escalate(suggestion);
        }
        return suggestion;
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

    private static int largestNumber(String text) {
        int largest = 0;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            largest = Math.max(largest, Integer.parseInt(matcher.group(1)));
        }
        return largest;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
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
