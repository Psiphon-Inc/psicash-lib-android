package ca.psiphon.psicashlib.test;

import org.junit.*;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.*;

public class GetRewardedActivityDataTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Default value, before the first RefreshState; is error because tokens are required
        PsiCashLib.GetRewardedActivityDataResult gradr = pcl.getRewardedActivityData();
        assertNotNull(gradr.error);

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        gradr = pcl.getRewardedActivityData();
        assertNull(gradr.error);
        assertNotNull(gradr.data);
        assertNotEquals(0, gradr.data.length());

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        gradr = pcl.getRewardedActivityData();
        assertNull(gradr.error);
        assertNotNull(gradr.data);
        assertNotEquals(0, gradr.data.length());
    }
}
