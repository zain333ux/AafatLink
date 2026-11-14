package pk.edu.nu.aafatlink.application.auth;

/** Indicates that Auth succeeded but application access is not currently allowed. */
public final class AccountAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AccountAccessException(String message) {
        super(message);
    }
}
