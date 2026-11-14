package pk.edu.nu.aafatlink.application.auth;

/** Indicates that a desktop password-recovery flow could not finish safely. */
public final class PasswordRecoveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PasswordRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
