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

#include <memory>
#include <functional>
#include "vendor/nlohmann/json.hpp"
#include "jnihelpers.hpp"

using namespace std;
using json = nlohmann::json;


bool g_testing = false;
jclass g_jGlueClass;
jmethodID g_makeHTTPRequestMID;


psicash::PsiCash& GetPsiCash() {
    static psicash::PsiCash psi_cash;
#ifndef NDEBUG
    static testing::PsiCashTester psi_cash_test;
    if (g_testing) {
        return psi_cash_test;
    }
#endif
    return psi_cash;
}

#ifndef NDEBUG
testing::PsiCashTester& GetPsiCashTester() {
    return static_cast<testing::PsiCashTester&>(GetPsiCash());
}
#endif


bool CheckJNIException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe(); // writes to logcat
        env->ExceptionClear();
        return true;
    }
    return false;
}

std::function<void(const char*)> StringUTFDeleter(JNIEnv* env, jstring j_param) {
    return [=](const char* s) { env->ReleaseStringUTFChars(j_param, s); };
}

template<typename T>
using deleted_unique_ptr = std::unique_ptr<T, std::function<void(T*)>>;

nonstd::optional<std::string> JStringToString(JNIEnv* env, jstring j_s) {
    if (!j_s) {
        return nonstd::nullopt;
    }

    deleted_unique_ptr<const char> s(env->GetStringUTFChars(j_s, NULL), StringUTFDeleter(env, j_s));
    return std::string(s.get());
}

string ErrorResponseFallback(const string& message) {
    return "{\"error\":{\"message\":\""s + message + "\", \"critical\":true}}";
}

string ErrorResponse(bool critical, const string& message,
                     const string& filename, const string& function, int line) {
    try {
        json j({{"error", nullptr}});
        if (!message.empty()) {
            j["error"]["message"] = error::Error(critical, message, filename, function, line).ToString();
            j["error"]["critical"] = critical;
        }
        return j.dump(-1, ' ', true);
    }
    catch (json::exception& e) {
        return ErrorResponseFallback(
                utils::Stringer("ErrorResponse json dump failed: ", e.what(), "; id:", e.id).c_str());
    }
}

string ErrorResponse(const error::Error& error, const string& message,
                     const string& filename, const string& function, int line) {
    try {
        json j({{"error", nullptr}});
        if (error) {
            j["error"]["message"] = error::Error(error).Wrap(message, filename, function, line).ToString();
            j["error"]["critical"] = error.Critical();
        }
        return j.dump(-1, ' ', true);
    }
    catch (json::exception& e) {
        return ErrorResponseFallback(
                utils::Stringer("ErrorResponse json dump failed: ", e.what(), "; id:", e.id).c_str());
    }
}

string SuccessResponse() {
    return SuccessResponse(nullptr);
}

psicash::MakeHTTPRequestFn GetHTTPReqFn(JNIEnv* env, jobject& this_obj) {
    psicash::MakeHTTPRequestFn http_req_fn = [env, &this_obj = this_obj](const psicash::HTTPParams& params) -> psicash::HTTPResult {
        psicash::HTTPResult error_result;
        error_result.code = psicash::HTTPResult::CRITICAL_ERROR;

        string params_json;
        try {
            json j = {
                {"scheme", params.scheme},
                {"hostname", params.hostname},
                {"port", params.port},
                {"method", params.method},
                {"path", params.path},
                {"headers", params.headers},
                {"query", params.query}};

            params_json = j.dump(-1, ' ', true);
        }
        catch (json::exception& e) {
            error_result.error = MakeCriticalError(utils::Stringer(
                "ErrorResponse json dump failed: ", e.what(), "; id:", e.id)).ToString();
            return error_result;
        }

        auto j_params = env->NewStringUTF(params_json.c_str());
        if (!j_params) {
            CheckJNIException(env);
            error_result.error = MakeCriticalError("NewStringUTF failed").ToString();
            return error_result;
        }

        auto j_result = (jstring)env->CallObjectMethod(this_obj, g_makeHTTPRequestMID, j_params);
        if (!j_result) {
            CheckJNIException(env);
            error_result.error = MakeCriticalError("CallObjectMethod failed").ToString();
            return error_result;
        }

        auto result_json = JStringToString(env, j_result);
        if (!result_json) {
            CheckJNIException(env);
            error_result.error = MakeCriticalError("JStringToString failed").ToString();
            return error_result;
        }

        try {
            auto j = json::parse(*result_json);

            psicash::HTTPResult result;
            result.code = j["code"].get<int>();

            if (!j["body"].is_null()) {
                result.body = j["body"].get<string>();
            }

            if (!j["date"].is_null()) {
                result.date = j["date"].get<string>();
            }

            if (!j["error"].is_null()) {
                result.error = j["error"].get<string>();
            }

            return result;
        }
        catch (json::exception& e) {
            error_result.error = MakeCriticalError(utils::Stringer(
                "json parse failed: ", e.what(), "; id:", e.id)).ToString();
            return error_result;
        }
    };

    return http_req_fn;
}
