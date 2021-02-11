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
        PsiCashLib.AccountLoginResult loginResult = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);
        assertTrue(pcl.isAccount().isAccount);
        assertTrue(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);

        // Logout
        PsiCashLib.AccountLogoutResult logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertTrue(pcl.isAccount().isAccount);
        assertFalse(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);

        // Reset user state
        err = pcl.resetUser();
        assertNull(err);
        assertFalse(pcl.isAccount().isAccount); // no longer an account
        assertFalse(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);
    }
}
