1. go back to original code and try it with initial Refresh (current test code) -- if this succeeds, then there's probably a simultaneous-NewTracker problem in released code
2. go back to sync'd code and try it with initial Refresh (current test code)
3. Try with GetEnv and stored this_obj

* NewTracker is never going to be okay with multiple concurrent calls -- tokens get reset


---

There are three unrelated problems in the PsiCash library (core and Android) resulting from concurrent operations. We'll call them the "JNIEnv problem", the "NewTracker problem", and the "Datastore Pauser problem".

## Problem manifestation

Crashes that look like this:

```
pid: 7314, tid: 7336, name: Thread-3  >>> ca.psiphon.psicashlib.test <<<
signal 6 (SIGABRT), code -6 (SI_TKILL), fault addr --------
Abort message: 'java_vm_ext.cc:542] JNI DETECTED ERROR IN APPLICATION: thread Thread[16,tid=7336,Native,Thread*=0xdbfdb400,peer=0x175e2328,"Thread-3"] using JNIEnv* from thread Thread[17,tid=7337,Runnable,Thread*=0xdbfdba00,peer=0x175e2480,"Thread-4"]'
    eax 00000000  ebx 00001c92  ecx 00001ca8  edx 00000006
    edi 00001c92  esi 00000236
    ebp 000024e1  esp c9078228  eip e92fdb39
backtrace:
    #00 pc 00000b39  [vdso:e92fd000] (__kernel_vsyscall+9)
    #01 pc 0001fdf8  /system/lib/libc.so (syscall+40)
    #02 pc 00022ed3  /system/lib/libc.so (abort+115)
    #03 pc 004dc8a6  /system/lib/libart.so (art::Runtime::Abort(char const*)+1174)
    #04 pc 005cd833  /system/lib/libart.so (_ZNSt3__110__function6__funcIPFvPKcENS_9allocatorIS5_EES4_EclEOS3_+35)
    #05 pc 00007ccc  /system/lib/libbase.so (android::base::LogMessage::~LogMessage()+828)
    #06 pc 0031ab5f  /system/lib/libart.so (art::JavaVMExt::JniAbort(char const*, char const*)+1775)
    #07 pc 0031ad21  /system/lib/libart.so (art::JavaVMExt::JniAbortV(char const*, char const*, char*)+113)
    #08 pc 000d60f7  /system/lib/libart.so (art::(anonymous namespace)::ScopedCheck::AbortF(char const*, ...)+71)
    #09 pc 000d46fc  /system/lib/libart.so (art::(anonymous namespace)::ScopedCheck::CheckPossibleHeapValue(art::ScopedObjectAccess&, char, art::(anonymous namespace)::JniValueType)+364)
    #10 pc 000d3bdb  /system/lib/libart.so (art::(anonymous namespace)::ScopedCheck::Check(art::ScopedObjectAccess&, bool, char const*, art::(anonymous namespace)::JniValueType*)+811)
    #11 pc 000de1b4  /system/lib/libart.so (art::(anonymous namespace)::CheckJNI::GetStringCharsInternal(char const*, _JNIEnv*, _jstring*, unsigned char*, bool, bool)+916)
    #12 pc 000c951d  /system/lib/libart.so (art::(anonymous namespace)::CheckJNI::GetStringUTFChars(_JNIEnv*, _jstring*, unsigned char*)+45)
    #13 pc 000ddd14  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (_JNIEnv::GetStringUTFChars(_jstring*, unsigned char*)+100)
    #14 pc 000dd7d4  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (JStringToString(_JNIEnv*, _jstring*)+132)
    #15 pc 000e8013  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so
    #16 pc 000e47cc  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so
    #17 pc 000e4637  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so
    #18 pc 0016f1a5  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (std::__ndk1::function<psicash::HTTPResult (psicash::HTTPParams const&)>::operator()(psicash::HTTPParams const&) const+229)
    #19 pc 0016cf47  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (psicash::PsiCash::MakeHTTPRequestWithRetry(std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>> const&, std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>> const&, bool, std::__ndk1::vector<std::__ndk1::pair<std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>>, std::__ndk1::basic_
    #20 pc 00178986  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (psicash::PsiCash::RefreshState(std::__ndk1::vector<std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>>, std::__ndk1::allocator<std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>>>> const&, bool)+4662)
    #21 pc 00177734  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (psicash::PsiCash::RefreshState(std::__ndk1::vector<std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>>, std::__ndk1::allocator<std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>>>> const&)+84)
    #22 pc 000acebf  /data/app/ca.psiphon.psicashlib.test-DHndXih0EUfYYycM0w4HJQ==/lib/x86/libpsicash.so (Java_ca_psiphon_psicashlib_PsiCashLib_NativeRefreshState+1295)
    #23 pc 005f6b97  /system/lib/libart.so (art_quick_generic_jni_trampoline+71)
```

That is resulting from the JNIEnv problem. I only found the other problems when trying out JNIEnv problem reproduction and fixes.

## JNIEnv problem

This is the code flow for a PsiCash Android lib call that has a network request (like RefreshState).

```no-highlight
App Java                 App HTTP requester
   ⇅                             ⇅
Lib Java                     Lib Java
   ⇅                             ⇅
C++ JNI glue [1]              JNI glue [2]
   ⇅                             ⇅
C++ core lib        ⇄      need HTTP req
```

The "App HTTP Requester" is a helper function supplied and set by the app during initialization. Designing its use, I didn't realize that some scenarios (like Android) would require that state present at the beginning of the call (`[1]` above) would be needed when processing the request (`[2]` above), so I didn't provide a way to pass state through the (JNI-ignorant) C++ core code into the HTTP requester.

The state that's needed is the `JNIEnv` instance pointer and the `jobject` which is the `PsiCashLib` Java object instance. `jobject` is shareable between threads (I think, at this point), but `JNIEnv` is not.

I did the state passing in a [super dumb way](https://github.com/Psiphon-Inc/psicash-lib-android/blob/3865fc1860e2a106defcdbc0e150973ff3188d01/psicashlib/src/main/cpp/jniglue.cpp#L324-L329):
```cpp
extern "C" JNIEXPORT jstring
JNICALL
Java_ca_psiphon_psicashlib_PsiCashLib_NativeRefreshState(
        JNIEnv* env,
        jobject this_obj,
        jobjectArray j_purchase_classes) {

    ... j_purchase_classes --> purchase_classes

    GetPsiCash().SetHTTPRequestFn(GetHTTPReqFn(env, this_obj));

    auto result = GetPsiCash().RefreshState(purchase_classes);
    if (!result) {
        return JNI_(WRAP_ERROR(result.error()));
    }

    return JNI_s(SuccessResponse(*result));
}
```

That effectively creates an HTTP requester function that encloses the `JNIEnv` and `jobject` passed into the call, and sets it as _the_ requester function for the library.

But... if another RefreshState (or NewTransaction) call happens from another thread while the initial call is in progress... a different enclosing function is set as _the_ requester, enclosing the JNIEnv for a different thread. When an attempt is made to access the wrong JNIEnv from the wrong thread... the crash you see above occurs.

### Why didn't it manifest before?

When all the Java lib calls were `synchronized` or read-write-locked, there were no concurrent network calls. (Maybe I had that in mind when I wrote it so dumb? Maybe!)

This is the same answer to the same questions for the other problems as well.


## NewTracker problem

This should probably be called the "first RefreshState and so NewTracker is called" problem.

RefreshState's logic is something like this:
1. If there are no tokens yet, call NewTracker (`/tracker`) and recurse.
   - NewTracker gets new tokens, set them and clears the balance in the datastore.
2. Make a `/refresh-state` request to the server, passing tokens.
3. Get back the balance and purchase prices and set them in the datastore.

If different threads concurrently call RefreshState for the first time, then the Tracker may get created twice and different tokens used for the final `/refresh-state` req than were obtained from the final `/tracker` req.

### NewExpiringPurchase/NewTransaction variant

NewExpiringPurchase calls should also not be run concurrently with each other or with RefreshState. We could end up storing a balance that's from an earlier operation, whereas the true final balance is from a later operation.


## Datastore Pauser problem

This might be due to another concurrency effect here: datastore "write pausing". In order to avoid lots of disk writes for little bits of data that will arrive together, the datastore writing can be paused and resumed -- so, pause, set a bunch of values, then unpause to write them. The pauser is just a simple off/on mechanism, [like so](https://github.com/Psiphon-Inc/psicash-lib-core/blob/d9592e7549dace16ec21566f137f6bd4e81c7b1a/datastore.cpp#L48-L60):

```cpp
void Datastore::PauseWrites() {
    SYNCHRONIZE(mutex_);
    paused_ = true;
}

error::Error Datastore::UnpauseWrites() {
    SYNCHRONIZE(mutex_);
    if (!paused_) {
        return nullerr;
    }
    paused_ = false;
    return FileStore();
}
```

So if multiple threads try to pause and unpause writes at the same time... it's not great. It shouldn't actually cause bad behaviour, since this is what will happen:

1. Thread A pauses writes.
2. Thread B pauses writes.
3. Thread A sets XA, which doesn't go out to disk yet.
4. Thread A sets YA, which doesn't go out to disk yet.
5. Thread B sets XB, which doesn't go out to disk yet.
6. Thread A unpauses, causing write to happen.
7. Thread B sets YB, which writes to disk immediately.
8. Thread B unpauses, which returns an error, which is ignored by [`UserData::WritePauser` dtor](https://github.com/Psiphon-Inc/psicash-lib-core/blob/d9592e7549dace16ec21566f137f6bd4e81c7b1a/userdata.hpp#L57).

The disk thrashing that we wanted to avoid isn't avoided, but the data that ends up on disk isn't incorrect.

## Solution(s)

There are going to be multiple parts to this fix, and probably short and long term parts.

### NewTracker (and NewExpiringPurchase) problem

RefreshState and NewExpiringPurchase need to happen one-at-a-time. So, there needs to be a mutex controlling access. In the long term, I want that mutex to be in the core library, as this problem is shared across platforms. In the short term, maybe it can be in the Java or JNI glue.

### JNIEnv problem

In the short term, mutexing RefreshState and NewExpiringPurchase in Java or JNI negates this problem, as the enclosed values will be valid for the duration of the calls.

In the long term, this should be fixed properly. A few ideas:

1. If the jobject is okay to share between threads, then the JNIEnv for the current thread can be retrieved using `JavaVM::GetEnv` from within the HTTP requester and not much else needs to change.

2. The enclosed HTTP request function could be passed into RefreshState and NewExpiringPurchase for later use.

3. Support could be added for an opaque object (containing jobject and JNIEnv) to be passed through RefreshState and NewExpiringPurchase into the HTTP requester.

I think that's my preferred order.

### Datastore Pauser problem

In the short term, mutexing RefreshState and NewExpiringPurchase in Java or JNI negates this problem, as those are the only places write pausers are used.

In the long term... Maybe use a count up/down when pausing and unpausing? Except if they're happening simultaneously then there's something else wrong (like concurrent RefreshState calls). So maybe there's no point in a fix for this.

Maybe it should just assert if this condition is hit.

### Mutex usage elsewhere

In a previous rev, _all_ PsiCashLib methods were `synchronized`; in a rev after that, they _all_ used read/write locks.

We then rationalized that just mutexing the datastore reads and writes was sufficient, and that our thread-safety guarantees would only be "data won't be corrupt".

I think our new philosophy should be similar: "Writes are mutexed at the method level, reads have no special mutexes; all datastore read/writes are mutexed (to avoid corruption)". Methods that write are: init, setRequestMetadataItem, expirePurchases, removePurchases, refreshState, newExpiringPurchase.

### Short-term fix summary

Mutex the write operations in Java.

The easiest thing would be to just add `synchronized` to them. But Eugene has seen some ANR weirdness that suggests dangling locks. Which we can't explain, except waving our hands and saying "C++/JNI code throws a RuntimeException that bypasses a mutex unlock and is caught upstream somewhere and the app tries to keep running". (Which seems unlikely for about 3 reasons: 1. we haven't seen C++ code throw exceptions; 2. `synchronized` doesn't have an explicit unlock anyway; 3. we shouldn't be catching RuntimeExceptions and then not killing the app.)

So we're going to use a `ReentrantLock` and unlock it in `finally` blocks, so unlocking will definitely happen even if there's a RuntimeException.
