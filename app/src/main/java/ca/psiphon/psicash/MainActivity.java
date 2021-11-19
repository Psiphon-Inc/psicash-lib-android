package ca.psiphon.psicash;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.psiphon.psicashlib.PsiCashLib;

public class MainActivity extends AppCompatActivity {
    private PsiCashLib psiCashLib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.sample_text);
        tv.setText("Initial text");

        psiCashLib = new PsiCashLib();
        PsiCashLib.Error err = psiCashLib.init(getFilesDir().toString(), new PsiCashLibHelper(), false);
        if (err != null) {
            Log.e("PsiCashApp", err.message);
            return;
        }

        new NetworkTask().execute();
    }

    private class NetworkTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            Map<String, String> myMap = new HashMap<String, String>() {{
                put("a", "b");
                put("c", "d");
            }};
            PsiCashLib.Error error = psiCashLib.setRequestMetadataItems(myMap);
            if (error != null) {
                Log.e("PsiCashApp", error.message);
            }


            error = psiCashLib.setRequestMetadataItems(null); //erroneous
            if (error != null) {
                Log.e("PsiCashApp", error.message);
            }

            String accountSignupURL = psiCashLib.getAccountSignupURL();
            String accountManagementURL = psiCashLib.getAccountManagementURL();
            String accountForgotURL = psiCashLib.getAccountForgotURL();

            List<String> purchaseClasses = new ArrayList<>(Arrays.asList("speed-boost"));
            PsiCashLib.RefreshStateResult rsr = psiCashLib.refreshState(false, purchaseClasses);

            PsiCashLib.IsAccountResult isAccount = psiCashLib.isAccount();
            PsiCashLib.HasTokensResult htr = psiCashLib.hasTokens();
            PsiCashLib.BalanceResult b = psiCashLib.balance();
            PsiCashLib.GetPurchasePricesResult pp = psiCashLib.getPurchasePrices();
            PsiCashLib.GetPurchasesResult gpr = psiCashLib.getPurchases();
            PsiCashLib.ActivePurchasesResult vpr = psiCashLib.activePurchases();
            PsiCashLib.NextExpiringPurchaseResult ner = psiCashLib.nextExpiringPurchase();
            PsiCashLib.ExpirePurchasesResult epr = psiCashLib.expirePurchases();

            List<String> ids = new ArrayList<>(Arrays.asList("id1", "id2"));
            PsiCashLib.RemovePurchasesResult rpr = psiCashLib.removePurchases(ids);

            PsiCashLib.ModifyLandingPageResult mlpr = psiCashLib.modifyLandingPage("https://example.com/foo");
            PsiCashLib.GetRewardedActivityDataResult gradr = psiCashLib.getRewardedActivityData();
            PsiCashLib.GetDiagnosticInfoResult gdir = psiCashLib.getDiagnosticInfo(false);

            String encodedAuth = "eyJBdXRob3JpemF0aW9uIjp7IklEIjoiMFYzRXhUdmlBdFNxTGZOd2FpQXlHNHpaRUJJOGpIYnp5bFdNeU5FZ1JEZz0iLCJBY2Nlc3NUeXBlIjoic3BlZWQtYm9vc3QtdGVzdCIsIkV4cGlyZXMiOiIyMDE5LTAxLTE0VDE3OjIyOjIzLjE2ODc2NDEyOVoifSwiU2lnbmluZ0tleUlEIjoiUUNZTzV2clIvZGhjRDZ6M2FMQlVNeWRuZlJyZFNRL1RWYW1IUFhYeTd0TT0iLCJTaWduYXR1cmUiOiJQL2NrenloVUJoSk5RQ24zMnluM1VTdGpLencxU04xNW9MclVhTU9XaW9scXBOTTBzNVFSNURHVEVDT1FzQk13ODdQdTc1TGE1OGtJTHRIcW1BVzhDQT09In0=";
            PsiCashLib.DecodeAuthorizationResult authRes = PsiCashLib.decodeAuthorization(encodedAuth);

            PsiCashLib.NewExpiringPurchaseResult nep = psiCashLib.newExpiringPurchase(
                    "speed-boost",
                    "1hr",
                    100000000000L);

            return nep.status.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            s = (s == null ? "<null>" : s);

            TextView tv = findViewById(R.id.sample_text);
            tv.setText(s);
            Log.i("json", s);
        }
    }
}
