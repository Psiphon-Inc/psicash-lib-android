package ca.psiphon.psicashlib.test;

import org.junit.Test;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GetAccountForgotURL extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Even with no credentials, the call shouldn't fail
        String url = pcl.getAccountForgotURL();
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
    }
}
