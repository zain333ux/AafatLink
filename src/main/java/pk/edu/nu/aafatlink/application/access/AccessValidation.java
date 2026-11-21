package pk.edu.nu.aafatlink.application.access;

import java.util.Objects;

/** Explainable result shown before UC12 confirmation. */
public record AccessValidation(boolean allowed, String reason) {
    public AccessValidation {
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static AccessValidation allowedResult() { return new AccessValidation(true, ""); }
    public static AccessValidation rejected(String reason) { return new AccessValidation(false, reason); }
}
