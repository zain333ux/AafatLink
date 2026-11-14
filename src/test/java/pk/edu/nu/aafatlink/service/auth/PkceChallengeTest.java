package pk.edu.nu.aafatlink.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PkceChallengeTest {

    @Test
    void matchesTheRfc7636S256Example() {
        PkceChallenge challenge = PkceChallenge.fromVerifier(
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge.codeChallenge());
    }

    @Test
    void generatesUrlSafeHighEntropyVerifier() {
        PkceChallenge challenge = PkceChallenge.generate();

        assertTrue(challenge.codeVerifier().length() >= 43);
        assertTrue(challenge.codeVerifier().matches("[A-Za-z0-9_-]+"));
    }
}
