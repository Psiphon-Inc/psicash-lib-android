[![Build Status](https://travis-ci.org/Psiphon-Inc/psicash-lib-android.svg?branch=master)](https://travis-ci.org/Psiphon-Inc/psicash-lib-android)

# Android PsiCash Library

This is a C++ core library wrapped in Java/JNI glue for Android. Eventually the C++ will
be split out into its own repository (and become a git subtree in this repo).

## Usage

The main API entry point is [`psicashlib/.../PsiCashLib.java`](https://github.com/Psiphon-Inc/psicash-lib-android/blob/master/psicashlib/src/main/java/ca/psiphon/psicashlib/PsiCashLib.java). An example usage of it can be found in the included [sample app](https://github.com/Psiphon-Inc/psicash-lib-android/tree/master/app/src/main/java/ca/psiphon/psicash).

The trickiest part is the implementation of the [`HTTPRequester` interface](https://github.com/Psiphon-Inc/psicash-lib-android/blob/master/psicashlib/src/main/java/ca/psiphon/psicashlib/PsiCashLib.java#L51). The sample app has [an implementation](https://github.com/Psiphon-Inc/psicash-lib-android/blob/master/app/src/main/java/ca/psiphon/psicash/PsiCashLibHelper.java#L16) of it, but that should only be viewed as a rough guide -- it is not intended to be used as-is.

**NOTE**: If in-app PsiCash functionality is to coexist with browser-only mode, then the `HTTPRequester` implementation will need to support proxying when the tunnel is connected.

**NOTE**: The PsiCash rule about no automatic untunneled requests remains in effect.

### Thread considerations

All library methods are `synchronized`. All methods are synchronous/blocking.

Network requests will be made on the same thread the method is called on.

## Glue exchange formats

### Consideration: Everything can be an error

Almost every JNI glue function has the potential to produce an error
(JSON parsing or dumping, JNI string ops). So anything that isn't one of
`jint, jbyte, jshort, jlong, jfloat, jdouble, jchar, jboolean` could
produce an error while marshaling.

There are two categories of errors:
* External to the library. Bad input, unreachable server, etc. These will
  typically be recoverable.
* Internal to the library. Incorrect exchange format, JSON marshal
  failure, storage write error, etc. These will typically be unrecoverable.

### Standard structure: JSON

```no-highlight
{
    // If not null or absent, an error resulted and "result" should not
    // be considered valid.
    "error": {
        "message": string; nonempty (if error object present),

        // If true, the error probably resulted from programmer error.
        // Logging and reporting should be handled differently.
        "critical": boolean; true iff error is 'critical' and probably unrecoverable
    }

    "result": type varies; actual result of the call
}
```

The authoritative definition of the return format for each API call will
above the call stub in `PsiCashLib.java`. Description of the arguments
to the call will also be there.

### Possibilities considered

JSON is kind of a clunky choice for exchanging data across the JNI
boundary. It also doesn't give compile-time checks for
type/structure-matching, and therefore errors can be introduced that
aren't discovered until too late.

So here are some other things that were considered:

We could have written [glue code](http://www.ntu.edu.sg/home/ehchua/programming/java/javanativeinterface.html#zz-6.)
to exchange objects. But in addition to that being a lot of painful code,
it _still_ doesn't provide compile-time assurances that the types match
(because it relies on runtime lookup of thing -- check the sample code
at that link).

[SWIG](http://www.swig.org/) seems like the "best" JNI glue generator.
But the interface definition is also kind of... incomprehensible. If we
used it, when someone comes along a year later to update it, they won't
know what they're looking at.

So I decided that writing the glue in a language we can understand (and
are already using, and using the support libraries for) was preferable.
The runtime-error problem can be mitigated with a test suite.
