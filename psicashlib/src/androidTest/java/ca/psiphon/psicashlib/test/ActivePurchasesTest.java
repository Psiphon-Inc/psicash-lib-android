package ca.psiphon.psicashlib.test;

import org.junit.*;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.junit.Assert.*;

public class ActivePurchasesTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Default value, before the first RefreshState
        PsiCashLib.ActivePurchasesResult apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());
    }

    @Test
    public void withPurchases() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        PsiCashLib.ActivePurchasesResult apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());

        err = pcl.testReward(3);
        assertNull(err);

        res = pcl.refreshState(false, null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Make purchase (ten-second validity); may be brittle with clock skew
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(1, apr.purchases.size());

        // Make another purchase (one-second validity)
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(2, apr.purchases.size());

        // Let those purchases expire (and expire them)
        sleep(15000);
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());
        PsiCashLib.ExpirePurchasesResult epr = pcl.expirePurchases();
        assertNull(epr.error);
        assertEquals(2, epr.purchases.size());
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertEquals(0, apr.purchases.size());
    }
}
