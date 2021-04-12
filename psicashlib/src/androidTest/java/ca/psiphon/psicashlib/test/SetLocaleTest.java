package ca.psiphon.psicashlib.test;

import android.net.Uri;

import org.junit.Test;

import ca.psiphon.psicashlib.PsiCashLib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SetLocaleTest extends TestBase {
    @Test
    public void simpleSuccess() {
        PsiCashLibTester pcl = new PsiCashLibTester();
        PsiCashLib.Error err = pcl.init(getTempDir(), new PsiCashLibHelper(), false);
        assertNull(err);

        String testLocaleString = "TeST-lOCaLE-StRiNg";
        pcl.setLocale(testLocaleString);

        String localeParamName = "locale";

        Uri uri = Uri.parse(pcl.getAccountForgotURL());
        assertNotNull(uri);
        assertEquals(uri.getQueryParameter(localeParamName), testLocaleString);

        uri = Uri.parse(pcl.getAccountForgotURL());
        assertNotNull(uri);
        assertEquals(uri.getQueryParameter(localeParamName), testLocaleString);

        uri = Uri.parse(pcl.getAccountForgotURL());
        assertNotNull(uri);
        assertEquals(uri.getQueryParameter(localeParamName), testLocaleString);
    }
}
