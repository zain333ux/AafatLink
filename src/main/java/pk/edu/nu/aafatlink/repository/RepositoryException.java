package pk.edu.nu.aafatlink.repository;

/** Indicates a persistence or remote data-access failure. */
public final class RepositoryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
