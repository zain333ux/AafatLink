package pk.edu.nu.aafatlink.infrastructure.supabase;

/** A safe, user-presentable failure returned by Supabase Auth. */
public final class SupabaseAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int statusCode;

    public SupabaseAuthException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public SupabaseAuthException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}
