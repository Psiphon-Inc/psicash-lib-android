package ca.psiphon.psicashlib.test;

import org.junit.*;

import java.util.Random;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class AccountLogoutTest extends TestBase {
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

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));

        // Logout
        err = pcl.accountLogout();
        assertNull(err);
        assertTrue(pcl.isAccount().isAccount);
        assertEquals(0, pcl.validTokenTypes().validTokenTypes.size());
        assertEquals(0, pcl.balance().balance);

        // Good credentials with non-ASCII characters
        alr = pcl.accountLogin(TEST_ACCOUNT_UNICODE_USERNAME, TEST_ACCOUNT_UNICODE_PASSWORD);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, alr.status);
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertEquals(0, pcl.balance().balance);

        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));

        // Logout
        err = pcl.accountLogout();
        assertNull(err);
        assertTrue(pcl.isAccount().isAccount);
        assertEquals(0, pcl.validTokenTypes().validTokenTypes.size());
        assertEquals(0, pcl.balance().balance);
    }
}
