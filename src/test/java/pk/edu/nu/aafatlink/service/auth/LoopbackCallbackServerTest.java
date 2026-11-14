package pk.edu.nu.aafatlink.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LoopbackCallbackServerTest {

    @Test
    void acceptsOneMatchingCallbackOnTheIpv4LoopbackInterface() throws Exception {
        try (LoopbackCallbackServer callback = LoopbackCallbackServer.start()) {
            URI redirect = callback.redirectUri();
            URI returned = URI.create(redirect + "&code=4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a");

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(returned).GET().build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a",
                    callback.authCode().get(2, TimeUnit.SECONDS));
            assertEquals("127.0.0.1", redirect.getHost());
        }
    }

    @Test
    void timesOutRatherThanWaitingForever() throws Exception {
        try (LoopbackCallbackServer callback = LoopbackCallbackServer.start()) {
            callback.failAfter(Duration.ofMillis(25));
            callback.authCode().handle((value, error) -> error).get(2, TimeUnit.SECONDS);
        }
    }
}
