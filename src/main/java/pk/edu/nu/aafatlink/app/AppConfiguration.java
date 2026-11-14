package pk.edu.nu.aafatlink.app;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Public configuration that may safely be supplied to the desktop client.
 * Privileged Supabase keys are deliberately rejected.
 */
public record AppConfiguration(Optional<URI> supabaseUrl, Optional<String> publishableKey) {
    public static final String URL_VARIABLE = "AAFATLINK_SUPABASE_URL";
    public static final String KEY_VARIABLE = "AAFATLINK_SUPABASE_PUBLISHABLE_KEY";
    private static final String SERVICE_ROLE_VARIABLE = "AAFATLINK_SUPABASE_SERVICE_ROLE_KEY";

    public AppConfiguration {
        Objects.requireNonNull(supabaseUrl, "supabaseUrl");
        Objects.requireNonNull(publishableKey, "publishableKey");
    }

    public static AppConfiguration fromEnvironment() {
        return from(System.getenv());
    }

    public static AppConfiguration from(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        if (hasText(environment.get(SERVICE_ROLE_VARIABLE))) {
            throw new ConfigurationException("A service-role key must never be exposed to the desktop application.");
        }

        String rawUrl = trimmed(environment.get(URL_VARIABLE));
        String rawKey = trimmed(environment.get(KEY_VARIABLE));
        if (rawUrl.isEmpty() && rawKey.isEmpty()) {
            return new AppConfiguration(Optional.empty(), Optional.empty());
        }
        if (rawUrl.isEmpty() || rawKey.isEmpty()) {
            throw new ConfigurationException("Both the Supabase URL and publishable key are required.");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException("The Supabase URL is invalid.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new ConfigurationException("The Supabase URL must use HTTPS.");
        }
        return new AppConfiguration(Optional.of(uri), Optional.of(rawKey));
    }

    public boolean isRemoteConfigured() {
        return supabaseUrl.isPresent() && publishableKey.isPresent();
    }

    public String modeLabel() {
        return isRemoteConfigured() ? "Connected configuration" : "Demonstration mode";
    }

    private static boolean hasText(String value) {
        return !trimmed(value).isEmpty();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
