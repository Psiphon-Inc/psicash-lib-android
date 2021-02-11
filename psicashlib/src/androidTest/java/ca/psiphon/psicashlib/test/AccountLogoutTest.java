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
        PsiCashLib.AccountLoginResult loginResult = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);
        assertTrue(pcl.isAccount().isAccount);
        assertTrue(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(pcl.isAccount().isAccount);
        assertTrue(pcl.hasTokens().hasTokens);
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));

        // Logout
        PsiCashLib.AccountLogoutResult logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertFalse(logoutResult.reconnectRequired);
        assertTrue(pcl.isAccount().isAccount);
        assertFalse(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);

        // Good credentials with non-ASCII characters
        loginResult = pcl.accountLogin(TEST_ACCOUNT_UNICODE_USERNAME, TEST_ACCOUNT_UNICODE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);
        assertTrue(pcl.isAccount().isAccount);
        assertTrue(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);

        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(pcl.isAccount().isAccount);
        assertTrue(pcl.hasTokens().hasTokens);
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));

        // Logout
        logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertFalse(logoutResult.reconnectRequired);
        assertTrue(pcl.isAccount().isAccount);
        assertFalse(pcl.hasTokens().hasTokens);
        assertEquals(0, pcl.balance().balance);
    }

    @Test
    public void reconnectRequired() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Login
        PsiCashLib.AccountLoginResult loginResult = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Get some credit to spend
        err = pcl.testReward(2);
        assertNull(err);

        // Make purchase WITHOUT Authorization, so no reconnect required on logout
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        // Logout
        PsiCashLib.AccountLogoutResult logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertFalse(logoutResult.reconnectRequired);

        // Login again
        loginResult = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);
        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Make purchase WITH Authorization, requiring reconnect on logout
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_WITH_AUTHORIZATION_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        // Logout
        logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertTrue(logoutResult.reconnectRequired);
    }
}
