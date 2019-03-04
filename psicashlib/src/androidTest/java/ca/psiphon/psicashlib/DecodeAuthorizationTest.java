package ca.psiphon.psicashlib;

import org.junit.*;

import java.util.Date;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DecodeAuthorizationTest extends TestBase {
    // Known-good encoded Authorizations
    final String encodedAuth1 = "eyJBdXRob3JpemF0aW9uIjp7IklEIjoiMFYzRXhUdmlBdFNxTGZOd2FpQXlHNHpaRUJJOGpIYnp5bFdNeU5FZ1JEZz0iLCJBY2Nlc3NUeXBlIjoic3BlZWQtYm9vc3QtdGVzdCIsIkV4cGlyZXMiOiIyMDE5LTAxLTE0VDE3OjIyOjIzLjE2ODc2NDEyOVoifSwiU2lnbmluZ0tleUlEIjoiUUNZTzV2clIvZGhjRDZ6M2FMQlVNeWRuZlJyZFNRL1RWYW1IUFhYeTd0TT0iLCJTaWduYXR1cmUiOiJQL2NrenloVUJoSk5RQ24zMnluM1VTdGpLencxU04xNW9MclVhTU9XaW9scXBOTTBzNVFSNURHVEVDT1FzQk13ODdQdTc1TGE1OGtJTHRIcW1BVzhDQT09In0=";
    final String encodedAuth2 = "eyJBdXRob3JpemF0aW9uIjp7IklEIjoibFRSWnBXK1d3TFJqYkpzOGxBUFVaQS8zWnhmcGdwNDFQY0dkdlI5a0RVST0iLCJBY2Nlc3NUeXBlIjoic3BlZWQtYm9vc3QtdGVzdCIsIkV4cGlyZXMiOiIyMDE5LTAxLTE0VDIxOjQ2OjMwLjcxNzI2NTkyNFoifSwiU2lnbmluZ0tleUlEIjoiUUNZTzV2clIvZGhjRDZ6M2FMQlVNeWRuZlJyZFNRL1RWYW1IUFhYeTd0TT0iLCJTaWduYXR1cmUiOiJtV1Z5Tm9ZU0pFRDNXU3I3bG1OeEtReEZza1M5ZWlXWG1lcDVvVWZBSHkwVmYrSjZaQW9WajZrN3ZVTDNrakIreHZQSTZyaVhQc3FzWENRNkx0eFdBQT09In0=";

    @Test
    public void simpleSuccess() {
        PsiCashLib.DecodeAuthorizationResult decodeRes1 = PsiCashLib.decodeAuthorization(encodedAuth1);
        assertNull(decodeRes1.error);
        assertNotNull(decodeRes1.authorization);
        assertNotNull(decodeRes1.authorization.id);
        assertThat(decodeRes1.authorization.id.length(), greaterThan(0));
        assertThat(decodeRes1.authorization.accessType, equalTo("speed-boost-test"));
        assertTrue(decodeRes1.authorization.expires.before(new Date()));
        assertThat(decodeRes1.authorization.encoded, equalTo(encodedAuth1));

        PsiCashLib.DecodeAuthorizationResult decodeRes2 = PsiCashLib.decodeAuthorization(encodedAuth2);
        assertNull(decodeRes2.error);
        assertNotNull(decodeRes2.authorization);
        assertNotNull(decodeRes2.authorization.id);
        assertThat(decodeRes2.authorization.id.length(), greaterThan(0));
        assertThat(decodeRes2.authorization.accessType, equalTo("speed-boost-test"));
        assertTrue(decodeRes2.authorization.expires.before(new Date()));
        assertThat(decodeRes2.authorization.encoded, equalTo(encodedAuth2));

        assertThat(decodeRes1.authorization.id, not(equalTo(decodeRes2.authorization.id)));
    }

    @Test
    public void badInput() {
        // Null input
        PsiCashLib.DecodeAuthorizationResult decodeRes = PsiCashLib.decodeAuthorization(null);
        assertNotNull(decodeRes.error);
        assertTrue(decodeRes.error.critical);

        // Invalid base64 input
        decodeRes = PsiCashLib.decodeAuthorization("$@%^%^^%&====");
        assertNotNull(decodeRes.error);
        assertTrue(decodeRes.error.critical);

        // Valid base64 but invalid JSON input
        decodeRes = PsiCashLib.decodeAuthorization("dGhpcyBpcyBjZXJ0YWlubHkgbm90IEpTT04=");
        assertNotNull(decodeRes.error);
        assertTrue(decodeRes.error.critical);

        // Valid base64 and JSON, but incorrect JSON structure
        decodeRes = PsiCashLib.decodeAuthorization("eyJJIGFtIjogInZhbGlkIEpTT04ifQ==");
        assertNotNull(decodeRes.error);
        assertTrue(decodeRes.error.critical);
    }
}
