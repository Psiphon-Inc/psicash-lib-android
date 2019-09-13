package ca.psiphon.psicashlib;

import android.support.annotation.Nullable;

import java.util.List;

public class PsiCashLibTester extends PsiCashLib {
    @Override
    public Error init(String fileStoreRoot, HTTPRequester httpRequester, boolean forceReset) {
        return init(fileStoreRoot, httpRequester, forceReset, true);
    }

    @Nullable
    public Error testReward(int trillions) {
        for (int i = 0; i < trillions; i++) {
            if (i != 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }

            String err = NativeTestReward(SecretTestValues.TEST_CREDIT_TRANSACTION_CLASS, SecretTestValues.TEST_ONE_TRILLION_ONE_MICROSECOND_DISTINGUISHER);
            if (err != null) {
                return new Error(err);
            }
        }
        return null;
    }

    public boolean mutatorsEnabled() {
        return this.NativeTestSetRequestMutators(null);
    }

    public void setRequestMutators(List<String> mutators) {
        this.NativeTestSetRequestMutators(mutators.toArray(new String[0]));
    }

    public void setRequestMutator(String mutator) {
        this.NativeTestSetRequestMutators(new String[]{mutator});
    }
}
