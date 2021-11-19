package ca.psiphon.psicashlib.test;

import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import ca.psiphon.psicashlib.PsiCashLib;

public class SetRequestMetadataItemTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), null, false);
        assertNull(err);

        Map<String, String> items = new HashMap<String, String>() {{
            put("mykey", "myval");
        }};
        err = pcl.setRequestMetadataItems(items);
        assertNull(err);

        // Same again
        err = pcl.setRequestMetadataItems(items);
        assertNull(err);

        // New one
        items.clear();
        items.put("mykey2", "myval2");
        err = pcl.setRequestMetadataItems(items);
        assertNull(err);
    }
}
