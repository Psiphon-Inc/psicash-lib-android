package ca.psiphon.psicashlib.test;

import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class GetPurchasesByAuthorizationIDTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Test with both null param and empty array, as the code path is different

        // Default value, before the first RefreshState
        // Null ID array
        PsiCashLib.GetPurchasesByAuthorizationIDResult grbaidr = pcl.getPurchasesByAuthorizationID(null);
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());
        // Empty ID array
        grbaidr = pcl.getPurchasesByAuthorizationID(new ArrayList<>());
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        // Null ID array
        grbaidr = pcl.getPurchasesByAuthorizationID(null);
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());
        grbaidr = pcl.getPurchasesByAuthorizationID(new ArrayList<>());
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        grbaidr = pcl.getPurchasesByAuthorizationID(null);
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());
        grbaidr = pcl.getPurchasesByAuthorizationID(new ArrayList<>());
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());
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
        assertThat(apr.purchases, hasSize(0));
        PsiCashLib.GetAuthorizationsResult aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));

        // Get some credit to spend
        err = pcl.testReward(3);
        assertNull(err);

        res = pcl.refreshState(false, null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Make purchase (ten-second validity) WITHOUT Authorization; may be brittle with clock skew
        PsiCashLib.NewExpiringPurchaseResult neprNoAuth = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(neprNoAuth.error);
        assertEquals(PsiCashLib.Status.SUCCESS, neprNoAuth.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertThat(apr.purchases, hasSize(1));
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));

        // Make a couple purchases WITH Authorization
        PsiCashLib.NewExpiringPurchaseResult neprOneSec = pcl.newExpiringPurchase(TEST_DEBIT_WITH_AUTHORIZATION_TRANSACTION_CLASS, TEST_ONE_TRILLION_ONE_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(neprOneSec.error);
        assertEquals(PsiCashLib.Status.SUCCESS, neprOneSec.status);
        PsiCashLib.NewExpiringPurchaseResult neprTenSec = pcl.newExpiringPurchase(TEST_DEBIT_WITH_AUTHORIZATION_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(neprTenSec.error);
        assertEquals(PsiCashLib.Status.SUCCESS, neprTenSec.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertThat(apr.purchases, hasSize(3));
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(2));

        // Check that we can get the purchase from the authorization
        PsiCashLib.GetPurchasesByAuthorizationIDResult grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprOneSec.purchase.authorization.id)); // one purchase
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(1, grbaidr.purchases.size());
        assertEquals(grbaidr.purchases.get(0).id, neprOneSec.purchase.id);
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprTenSec.purchase.authorization.id)); // the other purchase
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(1, grbaidr.purchases.size());
        assertEquals(grbaidr.purchases.get(0).id, neprTenSec.purchase.id);
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprOneSec.purchase.authorization.id, neprTenSec.purchase.authorization.id)); // both purchases
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(2, grbaidr.purchases.size());
        assertThat(grbaidr.purchases.get(0).id, isOneOf(neprOneSec.purchase.id, neprTenSec.purchase.id));
        assertThat(grbaidr.purchases.get(1).id, isOneOf(neprOneSec.purchase.id, neprTenSec.purchase.id));

        // Check that we'll still get an empty result for empty input
        grbaidr = pcl.getPurchasesByAuthorizationID(new ArrayList<>());
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());

        // Check that we get an empty result for non-matchable input
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList("invalid-auth-id"));
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());


        // Let the shorter-life purchase expire, then see what we have
        sleep(2000);
        PsiCashLib.ExpirePurchasesResult epr = pcl.expirePurchases();
        assertNull(epr.error);
        assertThat(epr.purchases, hasSize(1));
        aar = pcl.getAuthorizations(false);
        assertThat(aar.authorizations, hasSize(1));
        // Do we still get the short one? (shouldn't)
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprOneSec.purchase.authorization.id));
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(0, grbaidr.purchases.size());
        // Do we still get the long one? (should)
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprTenSec.purchase.authorization.id));
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(1, grbaidr.purchases.size());
        assertEquals(grbaidr.purchases.get(0).id, neprTenSec.purchase.id);
        // Ask for both long and short (should get one)
        grbaidr = pcl.getPurchasesByAuthorizationID(Arrays.asList(neprOneSec.purchase.authorization.id, neprTenSec.purchase.authorization.id));
        assertNull(conds(grbaidr.error, "message"), grbaidr.error);
        assertEquals(1, grbaidr.purchases.size());
        assertEquals(grbaidr.purchases.get(0).id, neprTenSec.purchase.id);
    }
}
