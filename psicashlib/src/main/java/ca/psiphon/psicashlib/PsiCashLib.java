/*
 * Copyright (c) 2018, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.psicashlib;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * The PsiCash library interface. It provides a wrapper around the C++ core.
 */
public class PsiCashLib {
    private final Lock writeLock = new ReentrantLock();
    private boolean initialized = false;

    /**
     * The library user must implement this interface. It provides HTTP request
     * functionality to the library.
     * It is up to the implementer to decide which thread the request should be executed
     * on, and whether the request should be proxied, etc.
     */
    public interface HTTPRequester {
        /**
         * The HTTP requester. Must take care of TLS, proxying, etc.
         */
        Result httpRequest(ReqParams reqParams);

        /**
         * The input to the HTTP requester.
         */
        class ReqParams {
            public String method;
            public Uri uri;
            public Map<String, String> headers;
            public String body;
        }

        /**
         * The output from the HTTP requester.
         */
        class Result {
            public static final int CRITICAL_ERROR = -2;
            public static final int RECOVERABLE_ERROR = -1;

            // On successful request: 200, 404, etc.
            // If unable to reach server (or some other probably-recoverable error): RECOVERABLE_ERROR
            // On critical error (e.g., programming fault or out-of-memory): CRITICAL_ERROR
            public int code = CRITICAL_ERROR;
            public String body;
            public Map<String, List<String>> headers;
            public String error;

            String toJSON() {
                JSONObject json = new JSONObject();
                try {
                    json.put("code", this.code);
                    json.put("body", this.body);
                    json.put("error", this.error);

                    if (this.headers != null) {
                        JSONObject headers = new JSONObject();
                        for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
                            if (entry.getKey() == null) {
                                // The Java headers object puts the first HTTP line under a null key
                                continue;
                            }
                            headers.put(entry.getKey(), new JSONArray(entry.getValue()));
                        }
                        json.put("headers", headers);
                    }
                    else {
                        json.put("headers", null);
                    }

                    return json.toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // Should never happen, and no sane recovery.
                return null;
            }
        }
    }

    private HTTPRequester httpRequester;

    // Common fields in the JNI glue messages.
    private static final String kErrorKey = "error";
    private static final String kErrorMessageKey = "message";
    private static final String kErrorCriticalKey = "critical";
    private static final String kResultKey = "result";
    private static final String kStatusKey = "status";

    /**
     * Possible status values for many of the API methods. Specific meanings will be
     * described in the method comments.
     */
    public enum Status {
        INVALID(-1),
        SUCCESS(0),
        EXISTING_TRANSACTION(1),
        INSUFFICIENT_BALANCE(2),
        TRANSACTION_AMOUNT_MISMATCH(3),
        TRANSACTION_TYPE_NOT_FOUND(4),
        INVALID_TOKENS(5),
        INVALID_CREDENTIALS(6),
        BAD_REQUEST(7),
        SERVER_ERROR(8);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public static Status fromCode(int code) {
            for (Status s : Status.values()) {
                if (s.code == code) return s;
            }
            throw new IllegalArgumentException("Status not found");
        }

        public boolean equals(int code) {
            return this.code == code;
        }
    }

    /**
     * Error structure returned by many API methods.
     */
    public static class Error {
        @NonNull // If Error is set, it must have a message
        public String message;
        public boolean critical;

        public Error() {
        }

        public Error(String message, boolean critical) {
            this.message = message;
            this.critical = critical;
        }

        public Error(String message) {
            this(message, false);
        }

        @Nullable // if null, there's no error in json
        static Error fromJSON(JSONObject json) {
            // We don't know for sure that the JSON contains an Error at this point.

            JSONObject errorObj = JSON.nullableObject(json, kErrorKey);
            if (errorObj == null) {
                // The object will be absent if this isn't actually an error
                return null;
            }

            Error error = new Error();
            error.message = JSON.nullableString(errorObj, kErrorMessageKey);
            if (error.message == null) {
                // Message is required for this to be considered an error
                return null;
            }

            Boolean critical = JSON.nullableBoolean(errorObj, kErrorCriticalKey);
            error.critical = critical != null && critical;

            return error;
        }
    }

    /**
     * The possible token types.
     */
    public enum TokenType {
        EARNER("earner"),
        SPENDER("spender"),
        INDICATOR("indicator"),
        ACCOUNT("account");

        private final String name;

        TokenType(String name) {
            this.name = name;
        }

        public static TokenType fromName(String name) {
            for (TokenType tt : TokenType.values()) {
                if (tt.name.equals(name)) return tt;
            }
            throw new IllegalArgumentException("TokenType not found");
        }

        public boolean equals(String name) {
            return this.name.equals(name);
        }
    }

    /**
     * Purchase price information.
     */
    public static class PurchasePrice {
        public String transactionClass;
        public String distinguisher;
        public long price;

        static PurchasePrice fromJSON(JSONObject json) throws JSONException {
            if (json == null) {
                return null;
            }
            PurchasePrice pp = new PurchasePrice();
            pp.transactionClass = JSON.nonnullString(json, "class");
            pp.distinguisher = JSON.nonnullString(json, "distinguisher");
            pp.price = JSON.nonnullLong(json, "price");
            return pp;
        }
    }

    /**
     * Purchase information.
     */
    public static class Purchase {
        public String id;
        public String transactionClass;
        public String distinguisher;
        public Date expiry;
        public Authorization authorization;

        static Purchase fromJSON(JSONObject json) throws JSONException {
            if (json == null) {
                return null;
            }
            Purchase p = new Purchase();
            p.id = JSON.nonnullString(json, "id");
            p.transactionClass = JSON.nonnullString(json, "class");
            p.distinguisher = JSON.nonnullString(json, "distinguisher");
            p.expiry = JSON.nullableDate(json, "localTimeExpiry");

            JSONObject authJSON = JSON.nullableObject(json, "authorization");
            if (authJSON != null) {
                p.authorization = Authorization.fromJSON(authJSON);
            }
            return p;
        }
    }

    /**
     * Authorization information.
     */
    public static class Authorization {
        public String id;
        public String accessType;
        public Date expires;
        public String encoded;

        static Authorization fromJSON(JSONObject json) throws JSONException {
            if (json == null) {
                return null;
            }
            Authorization auth = new Authorization();
            auth.id = JSON.nonnullString(json, "ID");
            auth.accessType = JSON.nonnullString(json, "AccessType");
            auth.expires = JSON.nonnullDate(json, "Expires");
            auth.encoded = JSON.nonnullString(json, "Encoded");
            return auth;
        }
    }

    /*
     * Begin methods
     */

    public PsiCashLib() {
    }

    /**
     * Initializes the library. Must be called before any other methods are invoked.
     * @param fileStoreRoot The directory where the library will store its data. Must exist.
     * @param httpRequester Helper used to make HTTP requests.
     * @param forceReset If true, the PsiCash datastore will be reset.
     * @return null if no error; Error otherwise.
     */
    @Nullable
    public Error init(String fileStoreRoot, HTTPRequester httpRequester, boolean forceReset) {
        Error res;
        writeLock.lock();
        try {
            res = init(fileStoreRoot, httpRequester, forceReset, false);
        }
        finally {
            writeLock.unlock();
        }
        if (res == null) {
            this.initialized = true;
        }
        return res;
    }

    /**
     * Indicates if the library has been successfully initialized.
     * @return true if initialized, false otherwise.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Used internally for testing.
     * @param fileStoreRoot The directory where the library will store its data. Must exist.
     * @param httpRequester Helper used to make HTTP requests.
     * @param forceReset If true, the PsiCash datastore will be reset.
     * @param test Should be true if testing mode (and server) is to be used.
     * @return null if no error; Error otherwise.
     */
    @Nullable
    protected Error init(String fileStoreRoot, HTTPRequester httpRequester, boolean forceReset, boolean test) {
        this.httpRequester = httpRequester;
        writeLock.lock();
        String jsonStr;
        try {
            jsonStr = this.NativeObjectInit(fileStoreRoot, forceReset, test);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.ErrorOnly res = new JNI.Result.ErrorOnly(jsonStr);
        return res.error;
    }

    /**
     * Resets the current user data. See psicash.hpp for full description.
     * @return error
     */
    @Nullable
    public Error resetUser() {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeResetUser();
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.ErrorOnly res = new JNI.Result.ErrorOnly(jsonStr);
        return res.error;
    }

    /**
     * Set values that will be included in the request metadata. This includes
     * client_version, client_region, sponsor_id, and propagation_channel_id.
     * @return null if no error; Error otherwise.
     */
    @Nullable
    public Error setRequestMetadataItems(Map<String, String> items) {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeSetRequestMetadataItems(items);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.ErrorOnly res = new JNI.Result.ErrorOnly(jsonStr);
        return res.error;
    }

    /**
     * Set locale string that will be included with user site URLs
     * @return null if no error; Error otherwise.
     */
    @Nullable
    public Error setLocale(@NonNull String locale) {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeSetLocale(locale);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.ErrorOnly res = new JNI.Result.ErrorOnly(jsonStr);
        return res.error;
    }

    /*
     * HasTokens
     */

    /**
     * Returns true if there are sufficient tokens for this library to function on behalf
     * of a user. False otherwise.
     * If this is false and `IsAccount()` is true, then the user is a logged-out account
     * and needs to log in to continue. If this is false and `IsAccount()` is false,
     * `RefreshState()` needs to be called to get new Tracker tokens.
     */
    @NonNull
    public HasTokensResult hasTokens() {
        String jsonStr = this.NativeHasTokens();
        JNI.Result.HasTokens res = new JNI.Result.HasTokens(jsonStr);
        return new HasTokensResult(res);
    }

    public static class HasTokensResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        public boolean hasTokens;

        HasTokensResult(JNI.Result.HasTokens res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.hasTokens = res.hasTokens;
        }
    }

    /**
     * Retrieve the stored info about whether the user is a Tracker or an Account.
     */
    @NonNull
    public IsAccountResult isAccount() {
        String jsonStr = this.NativeIsAccount();
        JNI.Result.IsAccount res = new JNI.Result.IsAccount(jsonStr);
        return new IsAccountResult(res);
    }

    public static class IsAccountResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        public boolean isAccount;

        IsAccountResult(JNI.Result.IsAccount res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.isAccount = res.isAccount;
        }
    }

    /**
     * Retrieve the stored user balance.
     */
    @NonNull
    public BalanceResult balance() {
        String jsonStr = this.NativeBalance();
        JNI.Result.Balance res = new JNI.Result.Balance(jsonStr);
        return new BalanceResult(res);
    }

    public static class BalanceResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        public long balance;

        BalanceResult(JNI.Result.Balance res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.balance = res.balance;
        }
    }

    /**
     * Retrieves the stored purchase prices.
     * @return List will be empty if there are no available purchase prices.
     */
    @NonNull
    public GetPurchasePricesResult getPurchasePrices() {
        String jsonStr = this.NativeGetPurchasePrices();
        JNI.Result.GetPurchasePrices res = new JNI.Result.GetPurchasePrices(jsonStr);
        return new GetPurchasePricesResult(res);
    }

    public static class GetPurchasePricesResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null iff error (which is not expected).
        public List<PurchasePrice> purchasePrices;

        GetPurchasePricesResult(JNI.Result.GetPurchasePrices res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchasePrices = res.purchasePrices;
        }
    }

    /**
     * Retrieves the set of all (active or expired) purchases, if any.
     * @return List will be empty if there are no purchases.
     */
    @NonNull
    public GetPurchasesResult getPurchases() {
        String jsonStr = this.NativeGetPurchases();
        JNI.Result.GetPurchases res = new JNI.Result.GetPurchases(jsonStr);
        return new GetPurchasesResult(res);
    }

    public static class GetPurchasesResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null iff error (which is not expected).
        public List<Purchase> purchases;

        GetPurchasesResult(JNI.Result.GetPurchases res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchases = res.purchases;
        }
    }

    /**
     * Retrieves the set of active purchases that are not expired, if any.
     * @return List will be empty if there are no valid purchases.
     */
    @NonNull
    public ActivePurchasesResult activePurchases() {
        String jsonStr = this.NativeActivePurchases();
        JNI.Result.ActivePurchases res = new JNI.Result.ActivePurchases(jsonStr);
        return new ActivePurchasesResult(res);
    }

    public static class ActivePurchasesResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null iff error (which is not expected).
        public List<Purchase> purchases;

        ActivePurchasesResult(JNI.Result.ActivePurchases res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchases = res.purchases;
        }
    }

    /**
     * Returns all purchase authorizations. If activeOnly is true, only authorizations
     * for non-expired purchases will be returned.
     * @return List of authorizations, possibly empty.
     */
    @NonNull
    public GetAuthorizationsResult getAuthorizations(boolean activeOnly) {
        String jsonStr = this.NativeGetAuthorizations(activeOnly);
        JNI.Result.GetAuthorizations res = new JNI.Result.GetAuthorizations(jsonStr);
        return new GetAuthorizationsResult(res);
    }

    public static class GetAuthorizationsResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null iff error (which is not expected).
        public List<Authorization> authorizations;

        GetAuthorizationsResult(JNI.Result.GetAuthorizations res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.authorizations = res.authorizations;
        }
    }

    /**
     * Returns all purchases that match the given set of Authorization IDs.
     * @param authorizationIDs List of authorization IDs for which purchases should be
     *                         retrieved.
     * @return List of purchases containing the given authorizations.
     */
    @Nullable
    public GetPurchasesByAuthorizationIDResult getPurchasesByAuthorizationID(List<String> authorizationIDs) {
        String[] idsArray = null;
        if (authorizationIDs != null) {
            idsArray = authorizationIDs.toArray(new String[0]);
        }
        String jsonStr = this.NativeGetPurchasesByAuthorizationID(idsArray);
        JNI.Result.GetPurchasesByAuthorizationID res = new JNI.Result.GetPurchasesByAuthorizationID(jsonStr);
        return new GetPurchasesByAuthorizationIDResult(res);

    }

    public static class GetPurchasesByAuthorizationIDResult {
        // Null if storage writing problem or glue problem.
        public Error error;
        // Null iff error (which is not expected). Contains the removed purchases.
        public List<Purchase> purchases;

        GetPurchasesByAuthorizationIDResult(JNI.Result.GetPurchasesByAuthorizationID res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchases = res.purchases;
        }
    }

    /**
     * Decodes and deserializes the given encoded Authorization, returning the result.
     * @return The decoded authorization.
     */
    @NonNull
    public static DecodeAuthorizationResult decodeAuthorization(String encodedAuthorization) {
        String jsonStr = NativeDecodeAuthorization(encodedAuthorization);
        JNI.Result.DecodeAuthorization res = new JNI.Result.DecodeAuthorization(jsonStr);
        return new DecodeAuthorizationResult(res);
    }

    public static class DecodeAuthorizationResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null iff error (which is not expected).
        public Authorization authorization;

        DecodeAuthorizationResult(JNI.Result.DecodeAuthorization res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.authorization = res.authorization;
        }
    }

    /**
     * Get the next expiring purchase (with local_time_expiry populated).
     * @return If there is no expiring purchase, the returned purchase will be null.
     * The returned purchase may already be expired.
     */
    @NonNull
    public NextExpiringPurchaseResult nextExpiringPurchase() {
        String jsonStr = this.NativeNextExpiringPurchase();
        JNI.Result.NextExpiringPurchase res = new JNI.Result.NextExpiringPurchase(jsonStr);
        return new NextExpiringPurchaseResult(res);
    }

    public static class NextExpiringPurchaseResult {
        // Expected to be null; indicates glue problem.
        public Error error;
        // Null if error, or if there is no such purchase.
        public Purchase purchase;

        NextExpiringPurchaseResult(JNI.Result.NextExpiringPurchase res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchase = res.purchase;
        }
    }

    /**
     * Clear out expired purchases. Return the ones that were expired, if any.
     * @return List will be empty if there are no expired purchases.
     */
    @NonNull
    public ExpirePurchasesResult expirePurchases() {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeExpirePurchases();
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.ExpirePurchases res = new JNI.Result.ExpirePurchases(jsonStr);
        return new ExpirePurchasesResult(res);
    }

    public static class ExpirePurchasesResult {
        // Null if storage writing problem or glue problem.
        public Error error;
        // Null iff error (which is not expected). Empty if there were no expired purchases.
        public List<Purchase> purchases;

        ExpirePurchasesResult(JNI.Result.ExpirePurchases res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchases = res.purchases;
        }
    }

    /**
     * Force removal of purchases with the given transaction IDs.
     * This is to be called when the Psiphon server indicates that a purchase has
     * expired (even if the local clock hasn't yet indicated it).
     * @param transactionIDs List of transaction IDs of purchases to remove. IDs not being
     *                       found does _not_ result in an error.
     * @return List will contain the purchases that were removed. Passing IDs that don't
     *         exist does not result in an error.
     */
    @Nullable
    public RemovePurchasesResult removePurchases(List<String> transactionIDs) {
        String[] idsArray = null;
        if (transactionIDs != null) {
            idsArray = transactionIDs.toArray(new String[0]);
        }
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeRemovePurchases(idsArray);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.RemovePurchases res = new JNI.Result.RemovePurchases(jsonStr);
        return new RemovePurchasesResult(res);

    }

    public static class RemovePurchasesResult {
        // Null if storage writing problem or glue problem.
        public Error error;
        // Null iff error (which is not expected). Contains the removed purchases.
        public List<Purchase> purchases;

        RemovePurchasesResult(JNI.Result.RemovePurchases res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.purchases = res.purchases;
        }
    }

    /**
     * Utilizes stored tokens and metadata to craft a landing page URL.
     * @param url URL of landing page to modify.
     * @return Error if modification is impossible. (In that case the error should be
     * logged -- and added to feedback -- and home page opening should proceed
     * with the original URL.)
     */
    @NonNull
    public ModifyLandingPageResult modifyLandingPage(String url) {
        String jsonStr = this.NativeModifyLandingPage(url);
        JNI.Result.ModifyLandingPage res = new JNI.Result.ModifyLandingPage(jsonStr);
        return new ModifyLandingPageResult(res);
    }

    public static class ModifyLandingPageResult {
        public Error error;
        // Null iff error.
        public String url;

        ModifyLandingPageResult(JNI.Result.ModifyLandingPage res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.url = res.url;
        }
    }

    /**
     * Retrieves the PsiCash account signup page URL.
     * @return The URL of the signup page.
     */
    @NonNull
    public String getAccountSignupURL() {
        return this.NativeGetAccountSignupURL();
    }

    /**
     * Retrieves the PsiCash account forgot credentials page URL.
     * @return The URL of the forgot credentials page.
     */
    @NonNull
    public String getAccountForgotURL() {
        return this.NativeGetAccountForgotURL();
    }

    /**
     * Retrieves the PsiCash account management page URL.
     * @return The URL of the management page.
     */
    @NonNull
    public String getAccountManagementURL() {
        return this.NativeGetAccountManagementURL();
    }

    /**
     * Retrieves the PsiCash account username for a logged-in account.
     * @return The username.
     */
    @NonNull
    public AccountUsername getAccountUsername() {
        String jsonStr = this.NativeGetAccountUsername();
        JNI.Result.AccountUsername res = new JNI.Result.AccountUsername(jsonStr);
        return new AccountUsername(res);
    }

    public static class AccountUsername {
        public Error error;
        // Can be null even on success (error==null), if not an account or logged out.
        public String username;

        AccountUsername(JNI.Result.AccountUsername res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.username = res.username;
        }
    }

    /**
     * Creates a data package that should be included with a webhook for a user
     * action that should be rewarded (such as watching a rewarded video).
     * NOTE: The resulting string will still need to be encoded for use in a URL.
     * Returns an error if there is no earner token available and therefore the
     * reward cannot possibly succeed. (Error may also result from a JSON
     * serialization problem, but that's very improbable.)
     * So, the library user may want to call this _before_ showing the rewarded
     * activity, to perhaps decide _not_ to show that activity. An exception may be
     * if the Psiphon connection attempt and subsequent RefreshClientState may
     * occur _during_ the rewarded activity, so an earner token may be obtained
     * before it's complete.
     */
    @NonNull
    public GetRewardedActivityDataResult getRewardedActivityData() {
        String jsonStr = this.NativeGetRewardedActivityData();
        JNI.Result.GetRewardedActivityData res = new JNI.Result.GetRewardedActivityData(jsonStr);
        return new GetRewardedActivityDataResult(res);
    }

    public static class GetRewardedActivityDataResult {
        public Error error;
        // Can be null even on success (error==null), if there is no data.
        public String data;

        GetRewardedActivityDataResult(JNI.Result.GetRewardedActivityData res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.data = res.data;
        }
    }

    /**
     *
     * @param lite If true the returned diagnostic JSON size doesn't include purchase
     *             prices and about 200 bytes in size, otherwise the returned JSON is
     *             about 1000 bytes.
     * @return JSON object suitable for serializing that can be included in a feedback
     *       diagnostic data package.
     */
    @NonNull
    public GetDiagnosticInfoResult getDiagnosticInfo(boolean lite) {
        String jsonStr = this.NativeGetDiagnosticInfo(lite);
        JNI.Result.GetDiagnosticInfo res = new JNI.Result.GetDiagnosticInfo(jsonStr);
        return new GetDiagnosticInfoResult(res);
    }

    public static class GetDiagnosticInfoResult {
        public Error error;
        // Null iff error.
        public String jsonString;

        GetDiagnosticInfoResult(JNI.Result.GetDiagnosticInfo res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.jsonString = res.jsonString;
        }
    }

    /**
     * Refresh the local state (and obtain tokens, if necessary).
     * See psicash.hpp for full description.
     * @param purchaseClasses The purchase class names for which prices should be
     *                        retrieved, like `{"speed-boost"}`. If null or empty, no
     *                        purchase prices will be retrieved.
     * @return Error or request status. Even if error isn't set, request may have failed
     * for the reason indicated by the status.
     */
    @NonNull
    public RefreshStateResult refreshState(boolean localOnly, List<String> purchaseClasses) {
        if (purchaseClasses == null) {
            purchaseClasses = new ArrayList<>();
        }
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeRefreshState(localOnly, purchaseClasses.toArray(new String[0]));
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.RefreshState res = new JNI.Result.RefreshState(jsonStr);
        return new RefreshStateResult(res);
    }

    public static class RefreshStateResult {
        // Indicates catastrophic inability to make request.
        public Error error;
        // Null iff error.
        public Status status;
        // True when a tunnel reconnect is required as a result of this logout.
        public boolean reconnectRequired;

        RefreshStateResult(JNI.Result.RefreshState res) {
            this.error = res.error;
            this.reconnectRequired = res.reconnectRequired;
            if (this.error != null) {
                return;
            }
            this.status = res.status;
        }
    }

    /**
     * Makes a new transaction for an "expiring-purchase" class, such as "speed-boost".
     * See psicash.hpp for full description.
     * @param transactionClass The class name of the desired purchase. (Like "speed-boost".)
     * @param distinguisher    The distinguisher for the desired purchase. (Like "1hr".)
     * @param expectedPrice    The expected price of the purchase (previously obtained by
     *                         RefreshState). The transaction will fail if the
     *                         expectedPrice does not match the actual price.
     * @return Error or request status. Even if error isn't set, request may have failed
     * for the reason indicated by the status.
     */
    @NonNull
    public NewExpiringPurchaseResult newExpiringPurchase(
            String transactionClass, String distinguisher, long expectedPrice) {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeNewExpiringPurchase(transactionClass, distinguisher, expectedPrice);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.NewExpiringPurchase res = new JNI.Result.NewExpiringPurchase(jsonStr);
        return new NewExpiringPurchaseResult(res);
    }

    public static class NewExpiringPurchaseResult {
        // Indicates catastrophic inability to make request.
        public Error error;
        // Null iff error.
        public Status status;
        // Will be non-null on status==SUCCESS, but null for all other statuses.
        public Purchase purchase;

        NewExpiringPurchaseResult(JNI.Result.NewExpiringPurchase res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.status = res.status;
            this.purchase = res.purchase;
        }
    }

    /**
     * Logs the user into an account.
     * See psicash.hpp for full description.
     */
    @NonNull
    public AccountLogoutResult accountLogout() {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeAccountLogout();
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.AccountLogout res = new JNI.Result.AccountLogout(jsonStr);
        return new AccountLogoutResult(res);
    }

    public static class AccountLogoutResult {
        // Indicates catastrophic inability to make request.
        public Error error;
        // True when a tunnel reconnect is required as a result of this logout.
        public boolean reconnectRequired;

        AccountLogoutResult(JNI.Result.AccountLogout res) {
            this.error = res.error;
            this.reconnectRequired = res.reconnectRequired;
        }
    }

    /**
     * Logs the user into an account.
     * See psicash.hpp for full description.
     */
    @NonNull
    public AccountLoginResult accountLogin(String username, String password) {
        String jsonStr;
        writeLock.lock();
        try {
            jsonStr = this.NativeAccountLogin(username, password);
        }
        finally {
            writeLock.unlock();
        }
        JNI.Result.AccountLogin res = new JNI.Result.AccountLogin(jsonStr);
        return new AccountLoginResult(res);
    }

    public static class AccountLoginResult {
        // Indicates catastrophic inability to make request.
        public Error error;
        // Null iff error.
        public Status status;
        // Will be non-null iff tracker tokens were present to attempt to merge.
        public Boolean lastTrackerMerge;

        AccountLoginResult(JNI.Result.AccountLogin res) {
            this.error = res.error;
            if (this.error != null) {
                return;
            }
            this.status = res.status;
            this.lastTrackerMerge = res.lastTrackerMerge;
        }
    }

    //
    // END API ////////////////////////////////////////////////////////////////
    ///

    @SuppressWarnings("unused") // used as a native callback
    public String makeHTTPRequest(String jsonReqParams) {
        HTTPRequester.Result result = new HTTPRequester.Result();

        try {
            HTTPRequester.ReqParams reqParams = new HTTPRequester.ReqParams();
            Uri.Builder uriBuilder = new Uri.Builder();
            reqParams.headers = new HashMap<>();

            try {
                JSONObject json = new JSONObject(jsonReqParams);
                uriBuilder.scheme(JSON.nonnullString(json, "scheme"));
                String hostname = JSON.nonnullString(json, "hostname");
                Integer port = JSON.nullableInteger(json, "port");
                if (port != null) {
                    hostname += ":" + port;
                }

                uriBuilder.encodedAuthority(hostname);
                reqParams.method = JSON.nonnullString(json, "method");
                uriBuilder.encodedPath(JSON.nonnullString(json, "path"));

                JSONObject jsonHeaders = JSON.nullableObject(json, "headers");
                if (jsonHeaders != null) {
                    Iterator<?> headerKeys = jsonHeaders.keys();
                    while (headerKeys.hasNext()) {
                        String key = (String)headerKeys.next();
                        String value = JSON.nonnullString(jsonHeaders, key);
                        reqParams.headers.put(key, value);
                    }
                }

                // Query params are an array of arrays of 2 strings.
                JSONArray jsonQueryParams = JSON.nullableArray(json, "query");
                if (jsonQueryParams != null) {
                    for (int i = 0; i < jsonQueryParams.length(); i++) {
                        JSONArray param = JSON.nonnullArray(jsonQueryParams, i);
                        String key = JSON.nullableString(param, 0);
                        String value = JSON.nullableString(param, 1);
                        uriBuilder.appendQueryParameter(key, value);
                    }
                }

                reqParams.body = JSON.nullableString(json, "body");
            } catch (JSONException e) {
                result.error = "Parsing request object failed: " + e.toString();
                return result.toJSON();
            }

            reqParams.uri = uriBuilder.build();

            result = httpRequester.httpRequest(reqParams);

            // Check for consistency in the result.
            // Ensure sanity if there's an error: code must be negative iff there's an error message
            if ((result.code < 0) != (result.error != null && !result.error.isEmpty())) {
                result.code = HTTPRequester.Result.CRITICAL_ERROR;
                result.error = "Request result is not in sane error state: " + result.toString();
            }
        }
        catch (Throwable throwable) {
            // A runtime exception got thrown, probably from the requester. This can happen
            // if called from the main thread, for example.
            result.code = HTTPRequester.Result.CRITICAL_ERROR;
            result.error = "httpRequester threw runtime exception: " + throwable.getMessage();
        }

        return result.toJSON();
    }

    //
    // JNI helpers class
    //

    private static class JNI {

        private static class Result {

            private static abstract class Base {
                @Nullable
                Error error; // Null iff there's no error

                public Base(String jsonStr) {
                    if (jsonStr == null) {
                        this.error = new Error("Base: got null JSON string", true);
                        return;
                    }

                    JSONObject json;
                    try {
                        json = new JSONObject(jsonStr);
                    } catch (JSONException e) {
                        this.error = new Error("Base: Overall JSON parse failed: " + e.getMessage(), true);
                        return;
                    }

                    this.error = Error.fromJSON(json);
                    if (this.error != null) {
                        // The JSON encoded an error
                        return;
                    }

                    // There's no error, so let's extract the result.
                    try {
                        this.fromJSON(json, kResultKey);
                    } catch (JSONException e) {
                        this.error = new Error("Base: Result JSON parse failed: " + e.getMessage(), true);
                        return;
                    }
                }

                // Will be called iff there's no error, so must produce a value (except for ErrorOnly)
                // or throw an exception.
                abstract void fromJSON(JSONObject json, String key) throws JSONException;
            }

            private static class ErrorOnly extends Base {
                ErrorOnly(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    // There's no result besides error or not-error
                }
            }

            private static class IsAccount extends Base {
                boolean isAccount;

                public IsAccount(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.isAccount = JSON.nonnullBoolean(json, key);
                }
            }

            private static class HasTokens extends Base {
                boolean hasTokens;

                public HasTokens(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.hasTokens = JSON.nonnullBoolean(json, key);
                }
            }

            private static class Balance extends Base {
                long balance;

                public Balance(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.balance = JSON.nonnullLong(json, key);
                }
            }

            private static class GetPurchasePrices extends Base {
                List<PurchasePrice> purchasePrices;

                public GetPurchasePrices(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchasePrices = JSON.nullableList(
                            PsiCashLib.PurchasePrice.class, json, key, PsiCashLib.PurchasePrice::fromJSON, true);
                }
            }

            private static class GetPurchases extends Base {
                List<Purchase> purchases;

                public GetPurchases(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchases = JSON.nullableList(
                            PsiCashLib.Purchase.class, json, key, PsiCashLib.Purchase::fromJSON, true);
                }
            }

            private static class ActivePurchases extends Base {
                List<Purchase> purchases;

                public ActivePurchases(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchases = JSON.nullableList(
                            PsiCashLib.Purchase.class, json, key, PsiCashLib.Purchase::fromJSON, true);
                }
            }

            private static class GetAuthorizations extends Base {
                List<Authorization> authorizations;

                public GetAuthorizations(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.authorizations = JSON.nullableList(
                            PsiCashLib.Authorization.class, json, key, PsiCashLib.Authorization::fromJSON, true);
                }
            }

            private static class GetPurchasesByAuthorizationID extends Base {
                List<Purchase> purchases;

                public GetPurchasesByAuthorizationID(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchases = JSON.nullableList(
                            PsiCashLib.Purchase.class, json, key, PsiCashLib.Purchase::fromJSON, true);
                }
            }

            private static class DecodeAuthorization extends Base {
                Authorization authorization;

                public DecodeAuthorization(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    json = JSON.nonnullObject(json, key);
                    this.authorization = PsiCashLib.Authorization.fromJSON(json);
                }
            }

            private static class NextExpiringPurchase extends Base {
                PsiCashLib.Purchase purchase;

                public NextExpiringPurchase(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    // Even a valid result may give a null value (iff no existing expiring purchases)                    json = JSON.nullableObject(json, key);
                    json = JSON.nullableObject(json, key);
                    if (json == null) {
                        return;
                    }
                    this.purchase = PsiCashLib.Purchase.fromJSON(json);
                }
            }

            private static class ExpirePurchases extends Base {
                List<Purchase> purchases;

                public ExpirePurchases(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchases = JSON.nullableList(
                            PsiCashLib.Purchase.class, json, key, PsiCashLib.Purchase::fromJSON, true);
                }
            }

            private static class RemovePurchases extends Base {
                List<Purchase> purchases;

                public RemovePurchases(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    this.purchases = JSON.nullableList(
                            PsiCashLib.Purchase.class, json, key, PsiCashLib.Purchase::fromJSON, true);
                }
            }

            private static class ModifyLandingPage extends Base {
                String url;

                public ModifyLandingPage(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.url = JSON.nonnullString(json, key);
                }
            }

            private static class AccountUsername extends Base {
                String username;

                public AccountUsername(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.username = JSON.nullableString(json, key);
                }
            }

            private static class GetRewardedActivityData extends Base {
                String data;

                public GetRewardedActivityData(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) {
                    // Can be null even on success
                    this.data = JSON.nullableString(json, key);
                }
            }

            private static class GetDiagnosticInfo extends Base {
                String jsonString;

                public GetDiagnosticInfo(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    this.jsonString = JSON.nonnullString(json, key);
                }
            }

            private static class RefreshState extends Base {
                public Status status;
                public boolean reconnectRequired;

                public RefreshState(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    json = JSON.nonnullObject(json, key);
                    this.status = Status.fromCode(JSON.nonnullInteger(json, kStatusKey));
                    this.reconnectRequired = JSON.nonnullBoolean(json, "reconnect_required");
                }
            }

            private static class NewExpiringPurchase extends Base {
                public Status status;
                public Purchase purchase;

                public NewExpiringPurchase(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    json = JSON.nonnullObject(json, key);

                    this.status = Status.fromCode(JSON.nonnullInteger(json, kStatusKey));

                    // Allow for null purchase, as it will only be populated on status==success.
                    this.purchase = Purchase.fromJSON(JSON.nullableObject(json, "purchase"));

                    if (this.status == Status.SUCCESS && this.purchase == null) {
                        // Not a sane state.
                        throw new JSONException("NewExpiringPurchase.fromJSON got SUCCESS but no purchase object");
                    }
                }
            }

            private static class AccountLogout extends Base {
                public boolean reconnectRequired;

                public AccountLogout(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    json = JSON.nonnullObject(json, key);
                    this.reconnectRequired = JSON.nonnullBoolean(json, "reconnect_required");
                }
            }

            private static class AccountLogin extends Base {
                public Status status;
                public Boolean lastTrackerMerge;

                public AccountLogin(String jsonStr) {
                    super(jsonStr);
                }

                @Override
                public void fromJSON(JSONObject json, String key) throws JSONException {
                    json = JSON.nonnullObject(json, key);

                    this.status = Status.fromCode(JSON.nonnullInteger(json, kStatusKey));
                    this.lastTrackerMerge = JSON.nullableBoolean(json, "last_tracker_merge");
                }
            }
        }

    }

    //
    // JSON helpers class
    //

    private static class JSON {
        // The standard org.json.JSONObject does some unpleasant coercing of null into values, so
        // we're going to provide a bunch of helpers that behave sanely and consistently.
        // Please use these instead of the standard methods. (And consider adding more if any are missing.)

        @Nullable
        private static Boolean nullableBoolean(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optBoolean(key, false);
        }

        private static boolean nonnullBoolean(JSONObject json, String key) throws JSONException {
            Boolean v = nullableBoolean(json, key);
            if (v == null) {
                throw new JSONException("nonnullBoolean can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static Double nullableDouble(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optDouble(key, 0.0);
        }

        private static double nonnullDouble(JSONObject json, String key) throws JSONException {
            Double v = nullableDouble(json, key);
            if (v == null) {
                throw new JSONException("nonnullDouble can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static Integer nullableInteger(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optInt(key, 0);
        }

        private static int nonnullInteger(JSONObject json, String key) throws JSONException {
            Integer v = nullableInteger(json, key);
            if (v == null) {
                throw new JSONException("nonnullInteger can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static Long nullableLong(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optLong(key, 0L);
        }

        private static long nonnullLong(JSONObject json, String key) throws JSONException {
            Long v = nullableLong(json, key);
            if (v == null) {
                throw new JSONException("nonnullLong can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static String nullableString(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optString(key, null);
        }

        @Nullable
        private static String nullableString(JSONArray json, int index) {
            if (index >= json.length() || json.isNull(index)) {
                return null;
            }
            return json.optString(index, null);
        }

        @NonNull
        private static String nonnullString(JSONArray json, int index) throws JSONException {
            String v = nullableString(json, index);
            if (v == null) {
                throw new JSONException("nonnullString can't find non-null index: " + index);
            }
            return v;
        }

        @NonNull
        private static String nonnullString(JSONObject json, String key) throws JSONException {
            String v = nullableString(json, key);
            if (v == null) {
                throw new JSONException("nonnullString can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static JSONObject nullableObject(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optJSONObject(key);
        }

        @NonNull
        private static JSONObject nonnullObject(JSONObject json, String key) throws JSONException {
            JSONObject v = nullableObject(json, key);
            if (v == null) {
                throw new JSONException("nonnullObject can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static JSONObject nullableObject(JSONArray json, int index) {
            if (index >= json.length() || json.isNull(index)) {
                return null;
            }
            return json.optJSONObject(index);
        }

        @NonNull
        private static JSONObject nonnullObject(JSONArray json, int index) throws JSONException {
            JSONObject v = nullableObject(json, index);
            if (v == null) {
                throw new JSONException("nonnullObject can't find non-null index: " + index);
            }
            return v;
        }

        @Nullable
        private static JSONArray nullableArray(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }
            return json.optJSONArray(key);
        }

        @NonNull
        private static JSONArray nonnullArray(JSONObject json, String key) throws JSONException {
            JSONArray v = nullableArray(json, key);
            if (v == null) {
                throw new JSONException("nonnullArray can't find non-null key: " + key);
            }
            return v;
        }

        @Nullable
        private static JSONArray nullableArray(JSONArray json, int index) {
            if (index >= json.length() || json.isNull(index)) {
                return null;
            }
            return json.optJSONArray(index);
        }

        @NonNull
        private static JSONArray nonnullArray(JSONArray json, int index) throws JSONException {
            JSONArray v = nullableArray(json, index);
            if (v == null) {
                throw new JSONException("nonnullArray can't find non-null index: " + index);
            }
            return v;
        }

        // This function throws if the JSON field is present, but cannot be converted to a Date.
        @Nullable
        private static Date nullableDate(JSONObject json, String key) throws JSONException {
            String dateString = nullableString(json, key);
            if (dateString == null) {
                return null;
            }

            Date date;

            // We need to try different formats depending on the presence of milliseconds.
            // Note that we are setting parsing mode to strict (`setLenient(false)`) since
            // lenient parsing may produce incorrect output if the input date string is
            // not formatted exactly as expected.
            SimpleDateFormat isoFormatWithMS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoFormatWithMS.setTimeZone(TimeZone.getTimeZone("UTC"));
            isoFormatWithMS.setLenient(false);
            try {
                date = isoFormatWithMS.parse(dateString);
            } catch (ParseException e1) {
                SimpleDateFormat isoFormatWithoutMS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                isoFormatWithMS.setTimeZone(TimeZone.getTimeZone("UTC"));
                isoFormatWithMS.setLenient(false);
                try {
                    date = isoFormatWithoutMS.parse(dateString);
                } catch (ParseException e2) {
                    // Should not happen. No way to recover.
                    throw new JSONException("Failed to parse date with key " + key + "; error: " + e2);
                }
            }

            return date;
        }

        @NonNull
        private static Date nonnullDate(JSONObject json, String key) throws JSONException {
            Date v = nullableDate(json, key);
            if (v == null) {
                throw new JSONException("nonnullDate can't find non-null key: " + key);
            }
            return v;
        }

        // To be used for JSON-primitive types (String, boolean, etc.).
        @Nullable
        private static <T> List<T> nullableList(Class<T> clazz, JSONObject json, String key) {
            JSONArray jsonArray = nullableArray(json, key);
            if (jsonArray == null) {
                return null;
            }

            ArrayList<T> result = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(nullable(clazz, jsonArray, i));
            }

            return result;
        }

        interface Deserializer {
            Object fromJSON(JSONObject json) throws JSONException;
        }

        // Deserialize a list using an object deserializer method. If supplyDefault is
        // true, an empty list will be returned rather than null.
        @Nullable
        private static <T> List<T> nullableList(Class<T> clazz, JSONObject json, String key, Deserializer deserializer, boolean supplyDefault) {
            JSONArray jsonArray = nullableArray(json, key);
            if (jsonArray == null) {
                return supplyDefault ? new ArrayList<>() : null;
            }

            ArrayList<T> result = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject j = JSON.nullableObject(jsonArray, i);
                if (j == null) {
                    continue;
                }
                try {
                    result.add(cast(clazz, deserializer.fromJSON(j)));
                } catch (JSONException e) {
                    continue;
                }
            }

            return result;
        }

        // Helper; should (probably) not be used directly
        @Nullable
        private static <T> T cast(Class<T> clazz, Object o) {
            if (o == null) {
                return null;
            }

            assert clazz.isAssignableFrom(o.getClass());

            return clazz.cast(o);
        }

        // Helper; should (probably) not be used directly
        @Nullable
        private static <T> T nullable(Class<T> clazz, JSONArray json, int key) {
            Object o = json.opt(key);
            return cast(clazz, o);
        }
    }

    /*
     * Expose native (C++) functions.
     * NOTE: Full descriptions of what these methods do are in psicash.hpp
     * and will not be repeated here.
     */

    /*
    All String return values have this basic JSON structure:
        {
            "error": {      null or absent iff no error
                "message":  string; nonempty (if error object present)
                "critical": boolean; true iff error is 'critical' and probably unrecoverable
            }

            "result":       type varies; actual result of the call
        }
    Any field may be absent or null if not applicable, but either "error" or "result" must be present.
    */

    static {
        // Load the C++ library.
        System.loadLibrary("psicash");

        // Call the C++ init function each time the library loads.
        if (!NativeStaticInit()) {
            // This shouldn't happen, unless the apk is misconfigured.
            throw new AssertionError("psicash library init failed");
        }
    }

    private static native boolean NativeStaticInit();

    private native String NativeObjectInit(String fileStoreRoot, boolean forceReset, boolean test);

    /**
     * @return { "error": {...} }
     */
    private native String NativeResetUser();

    /**
     * @return { "error": {...} }
     */
    private native String NativeSetRequestMetadataItems(Map<String, String> items);

    /**
     * @return { "error": {...} }
     */
    private native String NativeSetLocale(String locale);

    /**
     * @return {
     * "error": {...},
     * "result": boolean
     * }
     */
    private native String NativeHasTokens();

    /**
     * @return {
     * "error": {...},
     * "result": boolean
     * }
     */
    private native String NativeIsAccount();

    /**
     * @return {
     * "error": {...},
     * "result": long
     * }
     */
    private native String NativeBalance();

    /**
     * @return {
     * "error": {...},
     * "result": [ ... PurchasePrices ... ]
     * }
     */
    private native String NativeGetPurchasePrices();

    /**
     * @return {
     * "error": {...},
     * "result": [ ... Purchase ... ]
     * }
     */
    private native String NativeActivePurchases();

    /**
     * @return {
     * "error": {...},
     * "result": [ ... Authorization ... ]
     * }
     */
    private native String NativeGetAuthorizations(boolean active_only);

    /**
     * @return {
     * "error": {...},
     * "result": [ ... Purchase ... ]
     * }
     */
    private native String NativeGetPurchasesByAuthorizationID(String[] authorization_ids);

    /**
     * @return {
     * "error": {...},
     * "result": Authorization
     * }
     */
    private native static String NativeDecodeAuthorization(String encoded_authorization);

    /**
     * @return {
     * "error": {...},
     * "result": [ ... Purchases ... ]
     * }
     */
    private native String NativeGetPurchases();

    /**
     * @return {
     * "error": {...},
     * "result": Purchase or null
     * }
     */
    private native String NativeNextExpiringPurchase();

    /**
     * @return {
     * "error": {...},
     * "result": [ ... Purchases ... ]
     * }
     */
    private native String NativeExpirePurchases();

    /**
     * @return {
     * "error": {...}
     * }
     */
    private native String NativeRemovePurchases(String[] transaction_ids);

    /**
     * @return {
     * "error": {...}
     * "result": modified url string
     * }
     */
    private native String NativeModifyLandingPage(String url);

    /**
     * @return {
     * "error": {...}
     * "result": url string
     * }
     */
    private native String NativeGetAccountSignupURL();

    /**
     * @return {
     * "error": {...}
     * "result": url string
     * }
     */
    private native String NativeGetAccountForgotURL();

    /**
     * @return {
     * "error": {...}
     * "result": url string
     * }
     */
    private native String NativeGetAccountManagementURL();

    /**
     * @return {
     * "error": {...}
     * "result": account username
     * }
     */
    private native String NativeGetAccountUsername();

    /**
     * @return {
     * "error": {...}
     * "result": string encoded data
     * }
     */
    private native String NativeGetRewardedActivityData();

    /**
     * @return {
     * "error": {...}
     * "result": diagnostic JSON as string
     * }
     */
    private native String NativeGetDiagnosticInfo(boolean lite);

    /**
     * @return {
     * "error": {...},
     * "result": Status
     * "reconnect_required": boolean
     * }
     */
    private native String NativeRefreshState(boolean localOnly, String[] purchaseClasses);

    /**
     * @return {
     * "error": {...},
     * "result": {
     *   "status": Status,
     *   "purchase": Purchase
     * }
     * }
     */
    private native String NativeNewExpiringPurchase(String transactionClass, String distinguisher, long expectedPrice);

    /**
     * @return {
     * "error": {...},
     * "reconnect_required": boolean
     * }
     */
    private native String NativeAccountLogout();

    /**
     * @return { "error": {...} }
     */
    private native String NativeAccountLogin(String username, String password);

    /*
     * TEST ONLY Native functions
     * It doesn't seem possible to have these declared in the PsiCashLibTester subclass.
     */

    protected native String NativeTestReward(String transactionClass, String distinguisher);

    protected native boolean NativeTestSetRequestMutators(String[] mutators);
}
