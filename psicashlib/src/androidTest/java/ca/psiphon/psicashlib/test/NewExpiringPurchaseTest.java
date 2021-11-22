package ca.psiphon.psicashlib.test;

import org.junit.*;

import java.util.Arrays;
import java.util.Date;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class NewExpiringPurchaseTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Get credit
        err = pcl.testReward(2);
        assertNull(conds(err, "message"), err);

        // Make a purchase
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);
        assertNotEquals(0, nepr.purchase.id.length());
        assertNotNull(nepr.purchase.expiry);
        assertNull(nepr.purchase.authorization);
        assertEquals(nepr.purchase.transactionClass, TEST_DEBIT_TRANSACTION_CLASS);
        assertEquals(nepr.purchase.distinguisher, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER);
        // Ensure the expiry time is near now (tests fix for https://github.com/Psiphon-Inc/psiphon-issues/issues/504)
        // This is brittle, as it depends on server-local clock skew, and the time zone
        // in which the test is run. If run in EST and time zone setting is broken, the
        // difference will be in the millions.
        assertThat(nepr.purchase.expiry.getTime() - new Date().getTime(), allOf(greaterThan(-10000L), lessThan(10000L)));

        // Make another purchase
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);
        assertNotEquals(0, nepr.purchase.id.length());
        assertNotNull(nepr.purchase.expiry);
        assertNull(nepr.purchase.authorization);
        assertEquals(nepr.purchase.transactionClass, TEST_DEBIT_TRANSACTION_CLASS);
        assertEquals(nepr.purchase.distinguisher, TEST_ONE_TRILLION_TEN_MICROSECOND_DISTINGUISHER);
    }

    @Test
    public void failureNoTokens() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // We start out with no tokens to use
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase("invalid", "invalid", 1);
        assertNotNull(nepr.error);
    }

    @Test
    public void failureNotFound() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Bad class
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase("invalid", TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.TRANSACTION_TYPE_NOT_FOUND, nepr.status);
        assertNull(nepr.purchase);

        // Bad distinguisher
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, "invalid", ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.TRANSACTION_TYPE_NOT_FOUND, nepr.status);
        assertNull(nepr.purchase);
    }

    @Test
    public void failurePriceMismatch() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Incorrect expected price
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, 12345);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.TRANSACTION_AMOUNT_MISMATCH, nepr.status);
        assertNull(nepr.purchase);
    }

    @Test
    public void failureInsufficientBalance() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertThat(pcl.balance().balance, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(MAX_STARTING_BALANCE)));

        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.INSUFFICIENT_BALANCE, nepr.status);
        assertNull(nepr.purchase);
    }

    @Test
    public void failureExistingTransaction() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Get credit
        err = pcl.testReward(2);
        assertNull(conds(err, "message"), err);

        // Make a long-lived purchase
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);

        // Try to make the same purchase again
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.EXISTING_TRANSACTION, nepr.status);
        assertNull(nepr.purchase);
    }

    @Test
    public void failureServerErrors() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Force server error
        pcl.setRequestMutators(Arrays.asList("Response:code=500", "Response:code=500", "Response:code=500"));
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SERVER_ERROR, nepr.status);
        assertNull(nepr.purchase);

        // Force timeout
        pcl.setRequestMutators(Arrays.asList("Timeout:11", "Timeout:11", "Timeout:11"));
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNotNull(nepr.error);
        assertThat(nepr.error.message, either(containsString("timeout")).or(containsString("Timeout")));

        // Force unexpected response code
        pcl.setRequestMutator("Response:code=666");
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER, ONE_TRILLION);
        assertNotNull(nepr.error);
        assertThat(nepr.error.message, containsString("666"));
    }

    @Test
    public void usePurchasePrices() {
        // Other tests use hardcoded purchase class, distinguisher, and price. Here we'll use
        // retrieved values.

        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use -- and our test purchase info
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, Arrays.asList(TEST_DEBIT_TRANSACTION_CLASS, "speed-boost"));
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        PsiCashLib.GetPurchasePricesResult gppr = pcl.getPurchasePrices();
        assertNotEquals(0, gppr.purchasePrices.size());
        assertThat(gppr.purchasePrices.size(), greaterThan(0));

        // We'll test with an "insufficient balance" response, since we don't know how much credit
        // we'll need, and that's enough to indicate that the purchase price values were good.
        // And we'll just try to buy everything.
        for (PsiCashLib.PurchasePrice pp : gppr.purchasePrices) {
            PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(pp.transactionClass, pp.distinguisher, pp.price);
            assertNull(nepr.error);
            assertEquals(PsiCashLib.Status.INSUFFICIENT_BALANCE, nepr.status);
            assertNull(nepr.purchase);
        }
    }

    @Test
    public void checkLocalPurchase() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Get tokens to use
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Get credit
        err = pcl.testReward(3);
        assertNull(conds(err, "message"), err);

        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MINUTE_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);
        PsiCashLib.GetPurchasesResult gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertThat(gpr.purchases.size(), is(1));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MINUTE_DISTINGUISHER));

        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertThat(gpr.purchases.size(), is(2));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MINUTE_DISTINGUISHER));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER));

        nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);
        assertNotNull(nepr.purchase);
        gpr = pcl.getPurchases();
        assertNull(gpr.error);
        assertThat(gpr.purchases.size(), is(3));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_MINUTE_DISTINGUISHER));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER));
        assertThat(gpr.purchases, containsPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_SECOND_DISTINGUISHER));
    }
}
