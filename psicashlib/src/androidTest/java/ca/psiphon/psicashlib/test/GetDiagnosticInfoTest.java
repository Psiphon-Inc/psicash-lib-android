package ca.psiphon.psicashlib.test;

import org.junit.*;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.*;

public class GetDiagnosticInfoTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        // Default value, before the first RefreshState
        PsiCashLib.GetDiagnosticInfoResult gdir = pcl.getDiagnosticInfo(false);
        assertNull(gdir.error);
        assertNotNull(gdir.jsonString);
        assertNotEquals(0, gdir.jsonString.length());
        String firstResult = gdir.jsonString;

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        gdir = pcl.getDiagnosticInfo(false);
        assertNull(gdir.error);
        assertNotNull(gdir.jsonString);
        assertNotEquals(0, gdir.jsonString.length());
        assertNotEquals(firstResult, gdir.jsonString);

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);
        gdir = pcl.getDiagnosticInfo(false);
        assertNull(gdir.error);
        assertNotNull(gdir.jsonString);
        assertNotEquals(0, gdir.jsonString.length());
    }
}
