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

nonstd::optional<std::string> JStringToString(JNIEnv* env, jstring j_s) {
    if (!j_s) {
        return nonstd::nullopt;
    }

    // JNI's GetStringUTFChars doesn't really give UTF-8 but "modified UTF-8". We don't
    // want to be sending that through to the core library and the server, so we'll need
    // to make some special effort. For details, see: https://stackoverflow.com/a/32215302

    const jclass stringClass = env->GetObjectClass(j_s);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");

    const jstring charsetName = env->NewStringUTF("UTF-8");
    const jbyteArray stringJbytes = (jbyteArray)env->CallObjectMethod(j_s, getBytes, charsetName);
    env->DeleteLocalRef(charsetName);

    const jsize length = env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string res((char*)pBytes, length);

    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);
    env->DeleteLocalRef(stringJbytes);

    return res;
}

jstring JNIify(JNIEnv* env, const char* str) {
    return str ? env->NewStringUTF(str) : nullptr;
}

jstring JNIify(JNIEnv* env, const std::string& str) {
    return !str.empty() ? env->NewStringUTF(str.c_str()) : nullptr;
}

string ErrorResponseFallback(const string& message) {
    return "{\"error\":{\"message\":\""s + message + "\", \"critical\":true}}";
}

string ErrorResponse(bool critical, const string& message,
                     const string& filename, const string& function, int line) {
    try {
        json j({{"error", nullptr}});
        if (!message.empty()) {
            j["error"]["message"] = psicash::error::Error(critical, message, filename, function, line).ToString();
            j["error"]["critical"] = critical;
        }
        return j.dump(-1, ' ', true);
    }
    catch (json::exception& e) {
        return ErrorResponseFallback(
                utils::Stringer("ErrorResponse json dump failed: ", e.what(), "; id:", e.id).c_str());
    }
}

string ErrorResponse(const psicash::error::Error& error, const string& message,
                     const string& filename, const string& function, int line) {
    try {
        json j({{"error", nullptr}});
        if (error) {
            j["error"]["message"] = psicash::error::Error(error).Wrap(message, filename, function, line).ToString();
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
                {"query", params.query},
                {"body", params.body}};

            params_json = j.dump(-1, ' ', true);
        }
        catch (json::exception& e) {
            error_result.error = psicash::error::MakeCriticalError(utils::Stringer(
                "ErrorResponse json dump failed: ", e.what(), "; id:", e.id)).ToString();
            return error_result;
        }

        auto j_params = env->NewStringUTF(params_json.c_str());
        if (!j_params) {
            CheckJNIException(env);
            error_result.error = psicash::error::MakeCriticalError("NewStringUTF failed").ToString();
            return error_result;
        }

        auto j_result = (jstring)env->CallObjectMethod(this_obj, g_makeHTTPRequestMID, j_params);
        if (!j_result) {
            CheckJNIException(env);
            error_result.error = psicash::error::MakeCriticalError("CallObjectMethod failed").ToString();
            return error_result;
        }

        auto result_json = JStringToString(env, j_result);
        if (!result_json) {
            CheckJNIException(env);
            error_result.error = psicash::error::MakeCriticalError("JStringToString failed").ToString();
            return error_result;
        }

        try {
            auto j = json::parse(*result_json);

            psicash::HTTPResult result;
            result.code = j["code"].get<int>();

            if (!j["body"].is_null()) {
                result.body = j["body"].get<string>();
            }

            if (!j["headers"].is_null()) {
                result.headers = j["headers"].get<map<string, vector<string>>>();
            }

            if (!j["error"].is_null()) {
                result.error = j["error"].get<string>();
            }

            return result;
        }
        catch (json::exception& e) {
            error_result.error = psicash::error::MakeCriticalError(utils::Stringer(
                "json parse failed: ", e.what(), "; id:", e.id)).ToString();
            return error_result;
        }
    };

    return http_req_fn;
}
