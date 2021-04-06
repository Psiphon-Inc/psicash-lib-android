package ca.psiphon.psicashlib.test;

import org.junit.*;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.*;

public class IsAccountTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Default value, before the first RefreshState
        PsiCashLib.IsAccountResult iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        iar = pcl.isAccount();
        assertNull(iar.error);
        assertFalse(iar.isAccount);
    }
}
