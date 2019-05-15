package ca.psiphon.psicashlib;

import org.junit.*;

import static ca.psiphon.psicashlib.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class GetAuthorizationsTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new TestBase.PsiCashLibHelper());
        assertNull(err);

        // Default value, before the first RefreshState
        PsiCashLib.GetAuthorizationsResult aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
    }

    @Test
    public void withPurchases() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper());
        assertNull(err);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
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

        res = pcl.refreshState(null);
        assertNull(res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        // Make purchase (ten-second validity) WITHOUT Authorization; may be brittle with clock skew
        PsiCashLib.NewExpiringPurchaseResult nepr = pcl.newExpiringPurchase(TEST_DEBIT_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertThat(apr.purchases, hasSize(1));
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(0));

        // Make purchase (ten-second validity) WITH Authorization; may be brittle with clock skew
        nepr = pcl.newExpiringPurchase(TEST_DEBIT_WITH_AUTHORIZATION_TRANSACTION_CLASS, TEST_ONE_TRILLION_TEN_SECOND_DISTINGUISHER, ONE_TRILLION);
        assertNull(nepr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, nepr.status);

        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertThat(apr.purchases, hasSize(2));
        aar = pcl.getAuthorizations(false);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(1));
        aar = pcl.getAuthorizations(true);
        assertNull(aar.error);
        assertThat(aar.authorizations, hasSize(1));

        // Let those purchases expire (and expire them)
        sleep(15000);
        apr = pcl.activePurchases();
        assertNull(apr.error);
        assertThat(apr.purchases, hasSize(0));
        aar = pcl.getAuthorizations(false);
        assertThat(aar.authorizations, hasSize(1));
        aar = pcl.getAuthorizations(true);
        assertThat(aar.authorizations, hasSize(0));
        PsiCashLib.ExpirePurchasesResult epr = pcl.expirePurchases();
        assertNull(epr.error);
        assertThat(epr.purchases, hasSize(2));
        aar = pcl.getAuthorizations(false);
        assertThat(aar.authorizations, hasSize(0));
        aar = pcl.getAuthorizations(true);
        assertThat(aar.authorizations, hasSize(0));
    }
}
