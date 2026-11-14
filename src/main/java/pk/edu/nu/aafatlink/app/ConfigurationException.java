package pk.edu.nu.aafatlink.app;

/** Indicates an unsafe or incomplete desktop application configuration. */
public final class ConfigurationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }
}
