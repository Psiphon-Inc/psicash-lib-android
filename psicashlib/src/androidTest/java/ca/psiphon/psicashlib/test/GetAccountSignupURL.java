package ca.psiphon.psicashlib.test;

import org.junit.Test;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.*;

public class GetAccountSignupURL extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Even with no credentials, the call shouldn't fail
        String url = pcl.getAccountSignupURL();
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
    }
}
