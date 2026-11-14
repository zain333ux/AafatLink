package pk.edu.nu.aafatlink.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AppConfigurationTest {

    @Test
    void usesDemonstrationModeWhenPublicSupabaseSettingsAreAbsent() {
        AppConfiguration configuration = AppConfiguration.from(Map.of());

        assertFalse(configuration.isRemoteConfigured());
        assertTrue(configuration.modeLabel().contains("Demonstration"));
    }

    @Test
    void acceptsACompleteHttpsSupabaseConfiguration() {
        AppConfiguration configuration = AppConfiguration.from(Map.of(
                "AAFATLINK_SUPABASE_URL", " https://example.supabase.co ",
                "AAFATLINK_SUPABASE_PUBLISHABLE_KEY", " sb_publishable_example "));

        assertTrue(configuration.isRemoteConfigured());
        assertTrue(configuration.supabaseUrl().orElseThrow().toString().startsWith("https://"));
    }

    @Test
    void rejectsPartialOrInsecureRemoteConfiguration() {
        assertThrows(ConfigurationException.class,
                () -> AppConfiguration.from(Map.of("AAFATLINK_SUPABASE_URL", "https://example.supabase.co")));
        assertThrows(ConfigurationException.class, () -> AppConfiguration.from(Map.of(
                "AAFATLINK_SUPABASE_URL", "http://example.supabase.co",
                "AAFATLINK_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_example")));
    }

    @Test
    void refusesServiceRoleSecretsInDesktopConfiguration() {
        assertThrows(ConfigurationException.class,
                () -> AppConfiguration.from(Map.of("AAFATLINK_SUPABASE_SERVICE_ROLE_KEY", "never-embed-this")));
    }
}
