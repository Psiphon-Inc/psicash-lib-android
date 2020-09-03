package ca.psiphon.psicashlib.test;

import android.support.test.filters.FlakyTest;

import org.junit.*;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class InitTest extends TestBase {
    @Test
    public void simpleSuccess() {
        {
            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(getTempDir(), null, false);
            assertNull(err);
        }
        {
            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
            assertNull(err);
        }
    }

    @Test
    public void error() {
        {
            // Null file store root
            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(null, new PsiCashLibHelper(), false);
            assertNotNull(err);
        }
        {
            // Make sure we can still succeed with good params
            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
            assertNull(err);
        }
        {
            // Bad file store root
            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init("/a:%$*&/b/c/d/e/f", new PsiCashLibHelper(), false);
            assertNotNull(err);
        }
    }

    @Test
    public void initWithReset() {
        String tempDir = getTempDir();
        long rewardBalance;
        {
            // Create a tracker with balance.

            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(tempDir, new PsiCashLibHelper(), false);
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
            rewardBalance = initialBalance + SecretTestValues.ONE_TRILLION;
        }
        {
            // Make sure the balance persists

            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(tempDir, new PsiCashLibHelper(), false);
            assertNull(err);

            PsiCashLib.BalanceResult br = pcl.balance();
            assertNull(br.error);
            assertEquals(rewardBalance, br.balance);
        }
        {
            // Reset the datastore

            PsiCashLibTester pcl = new PsiCashLibTester();
            PsiCashLib.Error err = pcl.init(tempDir, new PsiCashLibHelper(), true);
            assertNull(err);

            PsiCashLib.BalanceResult br = pcl.balance();
            assertNull(br.error);
            assertThat(br.balance, equalTo(0L)); // should be zero now
        }
    }
}
