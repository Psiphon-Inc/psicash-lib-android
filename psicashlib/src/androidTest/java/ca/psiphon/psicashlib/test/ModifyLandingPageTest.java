package ca.psiphon.psicashlib.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import ca.psiphon.psicashlib.PsiCashLib;

public class ModifyLandingPageTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        String url = "http://example.com/a/b";

        // Even with no credentials, the call shouldn't fail
        PsiCashLib.ModifyLandingPageResult mlpr = pcl.modifyLandingPage(url);
        assertNull(mlpr.error);
        assertTrue(mlpr.url.startsWith(url));
        assertNotEquals(url, mlpr.url);

        // First RefreshState, which creates the tracker
        PsiCashLib.RefreshStateResult res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        mlpr = pcl.modifyLandingPage(url);
        assertNull(mlpr.error);
        assertTrue(mlpr.url.startsWith(url));
        assertNotEquals(url, mlpr.url);

        // Second RefreshState, which just refreshes
        res = pcl.refreshState(false, null);
        assertNull(conds(res.error, "message"), res.error);
        assertEquals(PsiCashLib.Status.SUCCESS, res.status);

        mlpr = pcl.modifyLandingPage(url);
        assertNull(mlpr.error);
        assertTrue(mlpr.url.startsWith(url));
        assertNotEquals(url, mlpr.url);
        String noMetadataUrl = url;

        // Set some metadata
        Map<String, String> items = new HashMap<String, String>() {{
            put("mykey1", "myval1");
            put("mykey2", "myval2");
        }};

        err = pcl.setRequestMetadataItems(items);
        assertNull(err);
        mlpr = pcl.modifyLandingPage(url);
        assertNull(mlpr.error);
        assertTrue(mlpr.url.startsWith(url));
        assertNotEquals(url, mlpr.url);
        assertNotEquals(noMetadataUrl, mlpr.url);
    }
}
