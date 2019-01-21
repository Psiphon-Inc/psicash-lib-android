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

#ifndef PSICASHLIB_PSICASH_H
#define PSICASHLIB_PSICASH_H

#include <string>
#include <functional>
#include <vector>
#include <memory>
#include "vendor/nonstd/optional.hpp"
#include "vendor/nlohmann/json.hpp"
#include "datetime.hpp"
#include "error.hpp"


namespace psicash {

// Forward declarations
class UserData;


//
// HTTP Requester-related types
//
// The string param that MakeHTTPRequestFn takes is a JSON-encoding of this structure:
// {
//    "scheme": "https",
//    "hostname": "api.psi.cash",
//    "port": 443,
//    "method": "POST", // or "GET", etc.
//    "path": "/v1/tracker",
//    "headers": { "User-Agent": "value", ...etc. },
//    "query": [ ["class", "speed-boost"], ["expectedAmount", "-10000"], ... ] // name-value pairs
// }
// The string param that it returns is a JSON-encoding of this structure:
struct HTTPResult {
    // 200, 404, etc. -1 if unable to talk to server (or other catastrophe).
    int code;

    // The contents of the response body, if any.
    std::string body;

    // The value of the response Date header.
    std::string date;

    // Any error message relating to an unsuccessful network attempt;
    // must be empty if the request succeeded (regardless of status code).
    std::string error;

    HTTPResult() : code(-1) {}
};
// This is the signature for the HTTP Requester callback provided by the native consumer.
using MakeHTTPRequestFn = std::function<std::string(const std::string&)>;

// These are the possible token types.
extern const char* const kEarnerTokenType;
extern const char* const kSpenderTokenType;
extern const char* const kIndicatorTokenType;
extern const char* const kAccountTokenType;

using TokenTypes = std::vector<std::string>;

struct PurchasePrice {
    std::string transaction_class;
    std::string distinguisher;
    int64_t price;

    friend bool operator==(const PurchasePrice& lhs, const PurchasePrice& rhs);
    friend void to_json(nlohmann::json& j, const PurchasePrice& pp);
    friend void from_json(const nlohmann::json& j, PurchasePrice& pp);
};

using PurchasePrices = std::vector<PurchasePrice>;

using TransactionID = std::string;
extern const char* const kTransactionIDZero; // The "zero value" for a TransactionID

struct Purchase {
    TransactionID id;
    std::string transaction_class;
    std::string distinguisher;
    nonstd::optional<datetime::DateTime> server_time_expiry;
    nonstd::optional<datetime::DateTime> local_time_expiry;
    nonstd::optional<std::string> authorization;

    friend bool operator==(const Purchase& lhs, const Purchase& rhs);
    friend void to_json(nlohmann::json& j, const Purchase& p);
    friend void from_json(const nlohmann::json& j, Purchase& p);
};

using Purchases = std::vector<Purchase>;

// Possible API method result statuses. Which are possible and what they mean will
// be described for each method.
enum class Status {
    Invalid = -1, // Should never be used if well-behaved
    Success = 0,
    ExistingTransaction,
    InsufficientBalance,
    TransactionAmountMismatch,
    TransactionTypeNotFound,
    InvalidTokens,
    ServerError
};

class PsiCash {
public:
    PsiCash();
    virtual ~PsiCash();

    PsiCash(const PsiCash&) = delete;
    PsiCash& operator=(PsiCash const&) = delete;

    /// Must be called once, before any other methods (or behaviour is undefined).
    /// `make_http_request_fn` may be null and set later with SetHTTPRequestFn.
    /// Returns false if there's an unrecoverable error (such as an inability to use the
    /// filesystem).
    /// If `test` is true, then the test server will be used, and other testing interfaces
    /// will be available. Should only be used for glue code testing.
    error::Error Init(const char* user_agent, const char* file_store_root,
                      MakeHTTPRequestFn make_http_request_fn, bool test = false);

    /// Can be used for updating the HTTP requester function pointer.
    void SetHTTPRequestFn(MakeHTTPRequestFn make_http_request_fn);

    /// Set values that will be included in the request metadata. This includes
    /// client_version, client_region, sponsor_id, and propagation_channel_id.
    error::Error SetRequestMetadataItem(const std::string& key, const std::string& value);

    //
    // Stored info accessors
    //

    /// Returns the stored valid token types. Like ["spender", "indicator"].
    /// Will be empty if no tokens are available.
    TokenTypes ValidTokenTypes() const;

    /// Returns the stored info about whether the user is a Tracker or an Account.
    bool IsAccount() const;

    /// Returns the stored user balance.
    int64_t Balance() const;

    /// Returns the stored purchase prices.
    /// Will be empty if no purchase prices are available.
    PurchasePrices GetPurchasePrices() const;

    /// Returns the set of active purchases, if any.
    Purchases GetPurchases() const;

    /// Returns the set of active purchases that are not expired, if any.
    Purchases ValidPurchases() const;

    /// Get the next expiring purchase (with local_time_expiry populated).
    /// The returned optional will false if there is no outstanding expiring purchase (or
    /// no outstanding purchases at all). The returned purchase may already be expired.
    nonstd::optional<Purchase> NextExpiringPurchase() const;

    /// Clear out expired purchases. Return the ones that were expired, if any.
    error::Result<Purchases> ExpirePurchases();

    /// Force removal of purchases with the given transaction IDs.
    /// This is to be called when the Psiphon server indicates that a purchase has
    /// expired (even if the local clock hasn't yet indicated it).
    error::Error RemovePurchases(const std::vector<TransactionID>& ids);

    /// Utilizes stored tokens and metadata to craft a landing page URL.
    /// Returns an error if modification is impossible. (In that case the error
    /// should be logged -- and added to feedback -- and home page opening should
    /// proceed with the original URL.)
    error::Result<std::string> ModifyLandingPage(const std::string& url) const;

    /// Creates a data package that should be included with a webhook for a user
    /// action that should be rewarded (such as watching a rewarded video).
    /// NOTE: The resulting string will still need to be encoded for use in a URL.
    /// Returns an error if there is no earner token available and therefore the
    /// reward cannot possibly succeed. (Error may also result from a JSON
    /// serialization problem, but that's very improbable.)
    /// So, the library user may want to call this _before_ showing the rewarded
    /// activity, to perhaps decide _not_ to show that activity. An exception may be
    /// if the Psiphon connection attempt and subsequent RefreshClientState may
    /// occur _during_ the rewarded activity, so an earner token may be obtained
    /// before it's complete.
    error::Result<std::string> GetRewardedActivityData() const;

    // TODO: This return value might be a problem for direct C++ consumers (vs glue).
    /// Returns a JSON object suitable for serializing that can be included in a
    /// feedback diagnostic data package.
    nlohmann::json GetDiagnosticInfo() const;

    //
    // API Server Requests
    //

    /**
    Refreshes the client state. Retrieves info about whether the user has an
    Account (vs Tracker), balance, valid token types, and purchase prices. After a
    successful request, the retrieved values can be accessed with the accessor
    methods.

    If there are no tokens stored locally (e.g., if this is the first run), then
    new Tracker tokens will obtained.

    If the user is/has an Account, then it is possible some tokens will be invalid
    (they expire at different rates). Login may be necessary before spending, etc.
    (It's even possible that validTokenTypes is empty -- i.e., there are no valid
    tokens.)

    If there is no valid indicator token, then balance and purchase prices will not
    be retrieved, but there may be stored (possibly stale) values that can be used.

    Input parameters:

    • purchase_classes: The purchase class names for which prices should be
      retrieved, like `{"speed-boost"}`. If null or empty, no purchase prices will be retrieved.

    Result fields:

    • error: If set, the request failed utterly and no other params are valid.

    • status: Request success indicator. See below for possible values.

    Possible status codes:

    • Success: Call was successful. Tokens may now be available (depending on if
      IsAccount is true, ValidTokenTypes should be checked, as a login may be required).

    • ServerError: The server returned 500 error response. Note that the request has
      already been retried internally and any further retry should not be immediate.

    • InvalidTokens: Should never happen (indicates something like
      local storage corruption). The local user state will be cleared.
    */
    error::Result<Status> RefreshState(const std::vector<std::string>& purchase_classes);

    /**
    Makes a new transaction for an "expiring-purchase" class, such as "speed-boost".

    Input parameters:

    • transaction_class: The class name of the desired purchase. (Like
      "speed-boost".)

    • distinguisher: The distinguisher for the desired purchase. (Like "1hr".)

    • expected_price: The expected price of the purchase (previously obtained by RefreshState).
      The transaction will fail if the expected_price does not match the actual price.

    Result fields:

    • error: If set, the request failed utterly and no other params are valid.

    • status: Request success indicator. See below for possible values.

    • purchase: The resulting purchase. Null if purchase was not successful (i.e., if
      the `status` is anything except `Status.Success`).

    Possible status codes:

    • Success: The purchase transaction was successful. The `purchase` field will be non-null.

    • ExistingTransaction: There is already a non-expired purchase that prevents this
      purchase from proceeding.

    • InsufficientBalance: The user does not have sufficient credit to make the requested
      purchase. Stored balance will be updated and UI should be refreshed.

    • TransactionAmountMismatch: The actual purchase price does not match expectedPrice,
      so the purchase cannot proceed. The price list should be updated immediately.

    • TransactionTypeNotFound: A transaction type with the given class and distinguisher
      could not be found. The price list should be updated immediately, but it might also
      indicate an out-of-date app.

    • InvalidTokens: The current auth tokens are invalid.
      TODO: Figure out how to handle this. It shouldn't be a factor for Trackers or MVP.

    • ServerError: An error occurred on the server. Probably report to the user and try
      again later. Note that the request has already been retried internally and any
      further retry should not be immediate.
    */
    struct NewExpiringPurchaseResponse {
        Status status;
        nonstd::optional<Purchase> purchase;
    };

    error::Result<NewExpiringPurchaseResponse> NewExpiringPurchase(
            const std::string& transaction_class,
            const std::string& distinguisher,
            const int64_t expected_price);

protected:
    // See implementation for descriptions of non-public methods.

    error::Result<HTTPResult> MakeHTTPRequestWithRetry(
            const std::string& method, const std::string& path, bool include_auth_tokens,
            const std::vector<std::pair<std::string, std::string>>& query_params);

    virtual error::Result<std::string> BuildRequestParams(
            const std::string& method, const std::string& path, bool include_auth_tokens,
            const std::vector<std::pair<std::string, std::string>>& query_params, int attempt,
            const std::map<std::string, std::string>& additional_headers) const;

    error::Result<Status> NewTracker();

    error::Result<Status>
    RefreshState(const std::vector<std::string>& purchase_classes, bool allow_recursion);

protected:
    std::string user_agent_;
    std::string server_scheme_;
    std::string server_hostname_;
    int server_port_;
    // This is a pointer rather than an instance to avoid including userdata.h (TODO: worthwhile?)
    std::unique_ptr<UserData> user_data_;
    MakeHTTPRequestFn make_http_request_fn_;
};

} // namespace psicash

#endif //PSICASHLIB_PSICASH_H
