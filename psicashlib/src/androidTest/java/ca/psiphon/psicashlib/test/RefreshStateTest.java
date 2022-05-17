package ca.psiphon.psicashlib.test;


import org.junit.*;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class RefreshStateTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Before first call, so default values
        PsiCashLib.IsAccountResult iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        PsiCashLib.HasTokensResult htr = pcl.hasTokens();
        assertNull(htr.error);
        assertFalse(htr.hasTokens);
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertEquals(0L, br.balance);
        PsiCashLib.GetPurchasePricesResult gppr = pcl.getPurchasePrices();
        assertEquals(0, gppr.purchasePrices.size());

        // First call, which gets tokens
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        htr = pcl.hasTokens();
        assertNull(htr.error);
        assertTrue(htr.hasTokens);
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // Second call, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        htr = pcl.hasTokens();
        assertNull(htr.error);
        assertTrue(htr.hasTokens);
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));
    }

    @Test
    public void balanceChange() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);
        PsiCashLib.IsAccountResult iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        PsiCashLib.HasTokensResult htr = pcl.hasTokens();
        assertNull(htr.error);
        assertTrue(htr.hasTokens);
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        long initialBalance = br.balance;
        err = pcl.testReward(1);
        assertNull(conds(err, "message"), err);

        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);

        br = pcl.balance();
        assertNull(br.error);
        assertEquals(initialBalance + SecretTestValues.ONE_TRILLION, br.balance);
    }

    @Test
    public void withPurchaseClasses() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        PsiCashLib.GetPurchasePricesResult gppr = pcl.getPurchasePrices();
        assertThat(gppr.purchasePrices.size(), is(0));

        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, Arrays.asList("speed-boost", TEST_DEBIT_TRANSACTION_CLASS));
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);

        gppr = pcl.getPurchasePrices();
        assertThat(gppr.purchasePrices.size(), greaterThan(0));
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
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Get some credit to spend
        err = pcl.testReward(2);
        assertNull(err);

        // Make purchase WITHOUT Authorization, so no reconnect required
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

        // Refresh will sync the purchase, but it doesn't require a reconnect
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertFalse(res.reconnectRequired);
        PsiCashLib.ActivePurchasesResult apr = pcl.activePurchases();
        assertNull(apr.error);
        assertNotNull(apr.purchases);
        assertThat(apr.purchases, hasSize(1));

        // Make purchase WITH Authorization, requiring reconnect on logout
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_WITH_AUTHORIZATION_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        // Logout
        logoutResult = pcl.accountLogout();
        assertNull(logoutResult.error);
        assertTrue(logoutResult.reconnectRequired);

        // Login again
        loginResult = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(loginResult.error);
        assertEquals(PsiCashLib.Status.SUCCESS, loginResult.status);

        // Refresh will sync both purchase, and now we require a reconnect
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(res.reconnectRequired);
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertNotNull(apr.purchases);
        assertThat(apr.purchases, hasSize(2));
    }

    @Test
    public void serverErrors() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        pcl.setRequestMutators(Arrays.asList("Response:code=500", "Response:code=500", "Response:code=500"));
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SERVER_ERROR, res.status);
        assertFalse(res.reconnectRequired);

        pcl.setRequestMutators(Arrays.asList("Timeout:11", "Timeout:11", "Timeout:11"));
        res = pcl.refreshState(false, null);
        assertNotNull(res.error);
        assertThat(res.error.message, either(containsString("timeout")).or(containsString("Timeout")));
        assertFalse(res.reconnectRequired);

        pcl.setRequestMutator("Response:code=666");
        res = pcl.refreshState(false, null);
        assertNotNull(res.error);
        assertThat(res.error.message, containsString("666"));
        assertFalse(res.reconnectRequired);
    }

    @Test
    public void concurrentNewTracker() {
        // Test https://github.com/Psiphon-Inc/psiphon-issues/issues/557

        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(conds(err, "message"), err);

        // Execute a lot of threads with few reps, to increase the chance of concurrency
        // on NewTracker.
        int threads = 10;
        CountDownLatch signal = new CountDownLatch(threads);
        int reps = 5;
        Executor exec = new ThreadPerTaskExecutor();
        for (int i = 0; i < threads; i++) {
            exec.execute(new ReqRunnable(pcl, signal, reps));
        }

        try {
            signal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void concurrentRefreshState() {
        // Test https://github.com/Psiphon-Inc/psiphon-issues/issues/557

        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(conds(err, "message"), err);

        // We'll do an initial RefreshState here to ensure tokens are in place
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, Arrays.asList("speed-boost"));
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        int threads = 3;
        CountDownLatch signal = new CountDownLatch(threads);
        int reps = 100;
        Executor exec = new ThreadPerTaskExecutor();
        for (int i = 0; i < threads; i++) {
            exec.execute(new ReqRunnable(pcl, signal, reps));
        }

        try {
            signal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class ReqRunnable implements Runnable {
        PsiCashLibTester pcl;
        CountDownLatch signal;
        int reps;

        ReqRunnable(PsiCashLibTester pcl, CountDownLatch signal, int reps) {
            this.pcl = pcl;
            this.signal = signal;
            this.reps = reps;
        }

        @Override
        public void run() {
            for (int i = 0; i < reps; i++) {
                PsiCashLib.RefreshStateResult res = pcl.refreshState(false, Arrays.asList("speed-boost"));
                assertNull(conds(res.error, "message"), res.error);
                assertEquals(PsiCashLib.Status.SUCCESS, res.status);
            }
            signal.countDown();
        }
    }
    class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }
}
