package ca.psiphon.psicashlib;

import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ca.psiphon.psicashlib.SecretTestValues.*;
import static org.junit.Assert.*;

public class RemovePurchasesTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper());
        assertNull(err);

        // Test with both null param and empty array, as the code path is different

        // Default value, before the first RefreshState
        // Null ID array
        PsiCashLib.RemovePurchasesResult rpr = pcl.removePurchases(null);
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
        // Empty ID array
        rpr = pcl.removePurchases(new ArrayList<>());
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        // Null ID array
        rpr = pcl.removePurchases(null);
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
        rpr = pcl.removePurchases(new ArrayList<>());
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        rpr = pcl.removePurchases(null);
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
        rpr = pcl.removePurchases(new ArrayList<>());
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
    }

    @Test
    public void withPurchases() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper());
        assertNull(err);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        err = pcl.testReward(3);
        assertNull(err);

        res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Make three purchases
        PsiCashLib.NewExpiringPurchaseResult newExprPurchRes = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(newExprPurchRes.error);
        assertEquals(PsiCashLib.Status.SUCCESS, newExprPurchRes.status);
        newExprPurchRes = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(newExprPurchRes.error);
        assertEquals(PsiCashLib.Status.SUCCESS, newExprPurchRes.status);
        newExprPurchRes = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(newExprPurchRes.error);
        assertEquals(PsiCashLib.Status.SUCCESS, newExprPurchRes.status);

        PsiCashLib.GetPurchasesResult gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertEquals(3, gpr.purchases.size());

        // Try removing a nonexistent purchase; expect no error but no change
        PsiCashLib.RemovePurchasesResult rpr = pcl.removePurchases(Arrays.asList("nonexistent"));
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertEquals(3, gpr.purchases.size());

        // Remove two of the purchases
        List<String> purchasesToRemove = Arrays.asList(gpr.purchases.get(0).id, gpr.purchases.get(2).id);
        String remainingPurchaseID = gpr.purchases.get(1).id;
        rpr = pcl.removePurchases(purchasesToRemove);
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(2, rpr.purchases.size());
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertEquals(1, gpr.purchases.size());
        assertEquals(remainingPurchaseID, gpr.purchases.get(0).id);

        // Try to remove those purchases again; expect no error but no change
        rpr = pcl.removePurchases(purchasesToRemove);
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(0, rpr.purchases.size());
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertEquals(1, gpr.purchases.size());
        assertEquals(remainingPurchaseID, gpr.purchases.get(0).id);

        // Remove the final purchase
        rpr = pcl.removePurchases(Arrays.asList(remainingPurchaseID));
        assertNull(conds(rpr.error, "message"), rpr.error);
        assertEquals(1, rpr.purchases.size());
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertEquals(0, gpr.purchases.size());
    }
}
