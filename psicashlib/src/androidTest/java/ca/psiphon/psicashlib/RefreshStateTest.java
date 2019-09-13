package ca.psiphon.psicashlib;


import org.junit.*;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static ca.psiphon.psicashlib.SecretTestValues.TEST_DEBIT_TRANSACTION_CLASS;
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
        PsiCashLib.ValidTokenTypesResult vttr = pcl.validTokenTypes();
        assertNull(vttr.error);
        assertThat(vttr.validTokenTypes.size(), is(0));
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertEquals(0L, br.balance);
        PsiCashLib.GetPurchasePricesResult gppr = pcl.getPurchasePrices();
        assertEquals(0, gppr.purchasePrices.size());

        // First call, which gets tokens
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        vttr = pcl.validTokenTypes();
        assertNull(vttr.error);
        assertThat(vttr.validTokenTypes.size(), is(3));
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // Second call, which just refreshes
        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        vttr = pcl.validTokenTypes();
        assertNull(vttr.error);
        assertThat(vttr.validTokenTypes.size(), is(3));
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));
    }

    @Test
    public void balanceChange() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        PsiCashLib.IsAccountResult iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
        PsiCashLib.ValidTokenTypesResult vttr = pcl.validTokenTypes();
        assertNull(vttr.error);
        assertThat(vttr.validTokenTypes.size(), is(3));
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        long initialBalance = br.balance;
        err = pcl.testReward(1);
        assertNull(conds(err, "message"), err);

        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

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

        PsiCashLib.RefreshStateResult res = pcl.refreshState(Arrays.asList("speed-boost", TEST_DEBIT_TRANSACTION_CLASS));
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        gppr = pcl.getPurchasePrices();
        assertThat(gppr.purchasePrices.size(), greaterThan(0));
    }

    @Test
    public void serverErrors() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        pcl.setRequestMutators(Arrays.asList("Response:code=500", "Response:code=500", "Response:code=500"));
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SERVER_ERROR, res.status);

        pcl.setRequestMutator("Timeout:11");
        res = pcl.refreshState(null);
        assertNotNull(res.error);
        assertThat(res.error.message, either(containsString("timeout")).or(containsString("Timeout")));

        pcl.setRequestMutator("Response:code=666");
        res = pcl.refreshState(null);
        assertNotNull(res.error);
        assertThat(res.error.message, containsString("666"));
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
        PsiCashLib.RefreshStateResult res = pcl.refreshState(Arrays.asList("speed-boost"));
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
                PsiCashLib.RefreshStateResult res = pcl.refreshState(Arrays.asList("speed-boost"));
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
