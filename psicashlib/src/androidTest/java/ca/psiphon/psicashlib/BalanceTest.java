package ca.psiphon.psicashlib;

import org.junit.*;

import static ca.psiphon.psicashlib.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class BalanceTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Default value, before the first RefreshState
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // Another access, without a RefreshState
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
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Start with 0
        PsiCashLib.BalanceResult br = pcl.balance();
        assertNull(br.error);
        assertThat(br.balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        // Get a reward
        long initialBalance = br.balance;
        err = pcl.testReward(1);
        assertNull(conds(err, "message"), err);
        // ...and refresh
        res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        // ...and have a bigger balance
        br = pcl.balance();
        assertNull(br.error);
        assertEquals(initialBalance + SecretTestValues.ONE_TRILLION, br.balance);

        // Spend the balance
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        // ...the balance should have already updated
        br = pcl.balance();
        assertNull(br.error);
        assertEquals(initialBalance, br.balance);
        // ...also try refreshing
        res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        br = pcl.balance();
        assertNull(br.error);
        assertEquals(initialBalance, br.balance);
    }
}
