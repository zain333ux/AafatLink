package pk.edu.nu.aafatlink.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LoopbackCallbackServerTest {
    private static final int TEST_PORT = 49153;

    @Test
    void acceptsOneMatchingCallbackOnTheIpv4LoopbackInterface() throws Exception {
        try (LoopbackCallbackServer callback = LoopbackCallbackServer.start(TEST_PORT)) {
            URI redirect = callback.redirectUri();
            URI returned = URI.create(redirect + "&code=4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a");

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(returned).GET().build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a",
                    callback.authCode().get(2, TimeUnit.SECONDS));
            assertEquals("127.0.0.1", redirect.getHost());
            assertEquals(TEST_PORT, redirect.getPort());
        }
    }

    @Test
    void timesOutRatherThanWaitingForever() throws Exception {
        try (LoopbackCallbackServer callback = LoopbackCallbackServer.start(TEST_PORT)) {
            callback.failAfter(Duration.ofMillis(25));
            callback.authCode().handle((value, error) -> error).get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void explainsAnExpiredSupabaseLinkInsteadOfReportingInvalidCallback() throws Exception {
        try (LoopbackCallbackServer callback = LoopbackCallbackServer.start(TEST_PORT)) {
            URI redirect = callback.redirectUri();
            URI returned = URI.create(redirect
                    + "&error=access_denied&error_code=otp_expired"
                    + "&error_description=Email+link+is+invalid+or+has+expired");

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(returned).GET().build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("expired"));
            assertTrue(callback.authCode().handle((value, error) -> error).get(2, TimeUnit.SECONDS) != null);
        }
    }
}
