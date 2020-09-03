package ca.psiphon.psicashlib.test;

import org.junit.*;

import java.util.Random;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class ResetUserTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Login
        PsiCashLib.AccountLoginResult alr = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, alr.status);
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertEquals(0, pcl.balance().balance);

        // Logout
        err = pcl.accountLogout();
        assertNull(err);
        assertTrue(pcl.isAccount().isAccount);
        assertEquals(0, pcl.validTokenTypes().validTokenTypes.size());
        assertEquals(0, pcl.balance().balance);

        // Reset user state
        err = pcl.resetUser();
        assertNull(err);
        assertFalse(pcl.isAccount().isAccount); // no longer an account
        assertEquals(0, pcl.validTokenTypes().validTokenTypes.size());
        assertEquals(0, pcl.balance().balance);
    }
}
