package pk.edu.nu.aafatlink.domain.request;

/** Auditable UC03 coordinator outcomes. */
public enum VerificationDecision {
    VERIFIED,
    ADDITIONAL_INFORMATION_REQUIRED,
    REJECTED;

    public String validateReason(String reason) {
        if (this == VERIFIED) {
            return null;
        }
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 5 || normalized.length() > 1000) {
            throw new IllegalArgumentException("Enter a reason containing 5 to 1000 characters.");
        }
        return normalized;
    }
}
