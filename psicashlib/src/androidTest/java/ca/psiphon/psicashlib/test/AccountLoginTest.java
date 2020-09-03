package ca.psiphon.psicashlib.test;

import org.junit.*;

import java.util.Random;

import ca.psiphon.psicashlib.PsiCashLib;

import static ca.psiphon.psicashlib.test.SecretTestValues.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.*;

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
        assertNotEquals(0, pcl.validTokenTypes().validTokenTypes.size());
        assertTrue(pcl.isAccount().isAccount);

        // Good credentials with non-ASCII characters
        alr = pcl.accountLogin(TEST_ACCOUNT_UNICODE_USERNAME, TEST_ACCOUNT_UNICODE_PASSWORD);
        assertNull(alr.error);
        assertEquals(PsiCashLib.Status.SUCCESS, alr.status);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertTrue(pcl.isAccount().isAccount);

        PsiCashLib.RefreshStateResult res = pcl.refreshState(null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        assertThat(pcl.validTokenTypes().validTokenTypes.size(), is(3));
        assertTrue(pcl.isAccount().isAccount);
        assertThat(pcl.balance().balance, greaterThan(MAX_STARTING_BALANCE));
    }
}
