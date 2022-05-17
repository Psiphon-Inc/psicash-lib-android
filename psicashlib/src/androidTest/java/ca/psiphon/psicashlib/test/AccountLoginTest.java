package ca.psiphon.psicashlib.test;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static ca.psiphon.psicashlib.test.SecretTestValues.TEST_ACCOUNT_ONE_PASSWORD;
import static ca.psiphon.psicashlib.test.SecretTestValues.TEST_ACCOUNT_ONE_USERNAME;
import static ca.psiphon.psicashlib.test.SecretTestValues.TEST_ACCOUNT_UNICODE_PASSWORD;
import static ca.psiphon.psicashlib.test.SecretTestValues.TEST_ACCOUNT_UNICODE_USERNAME;

import org.junit.Test;

import java.util.Random;

import ca.psiphon.psicashlib.PsiCashLib;

public class AccountLoginTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Incorrect credentials
        Random rand = new Random();
        String randString = Long.toString(rand.nextLong());
        PsiCashLib.AccountLoginResult alr = pcl.accountLogin(randString, randString);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.INVALID_CREDENTIALS, alr.status);

        // Good credentials
        alr = pcl.accountLogin(TEST_ACCOUNT_ONE_USERNAME, TEST_ACCOUNT_ONE_PASSWORD);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, alr.status);
        assertTrue(pcl.hasTokens().hasTokens);
        assertTrue(pcl.isAccount().isAccount);

        // Good credentials with non-ASCII characters
        alr = pcl.accountLogin(TEST_ACCOUNT_UNICODE_USERNAME, TEST_ACCOUNT_UNICODE_PASSWORD);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, alr.status);
        assertTrue(pcl.hasTokens().hasTokens);
        assertTrue(pcl.isAccount().isAccount);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertTrue(pcl.hasTokens().hasTokens);
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));
    }
}
