# Android OCR task ownership

These optional Apache-2.0 adapters protect Android text-recognition resources during
interruption and timeout. They are separate from the standalone Java interpreter;
the core build does not gain Android or ML Kit dependencies.

A timed-out or interrupted waiter does not mean the recognizer has stopped reading
its input. `OwnedBitmapTask` gives each submitted operation a private bitmap and
recycles that copy when the operation completes. The original stays owned by the
caller, which may recycle it after the waiter returns.

`DeferredTextRecognizer` owns the underlying recognizer's close operation. Closing
the wrapper immediately rejects new submissions, then closes the underlying client
after all previously submitted tasks complete. This matters because closing an ML
Kit client can cancel its public task before queued work has finished. Transfer
close ownership to the wrapper; do not also close the underlying client directly.

Use both adapters together:

```java
try (var recognizer = new DeferredTextRecognizer(
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS))) {
    Text text = OwnedBitmapTask.await(bitmap,
            copy -> recognizer.process(InputImage.fromBitmap(copy, 0)), 18);
}
```

Keep the application's existing cancellation and timeout policy. An interrupted
caller must retain its interrupt status and avoid publishing an incomplete OCR
result. These adapters change resource ownership, not recognized text, inference,
confidence thresholds, models, caching, or document timing.

## Integration and tests

The sources use package `io.github.luckolite.interpreter.android`. Include
`src/main/java` in an Android application or library, and `src/androidTest/java`
in its instrumentation test sources. The reviewed integration uses Android API 36,
minimum API 26, Java 17, `com.google.mlkit:text-recognition:16.0.1`, and its
`play-services-tasks:18.2.0` dependency. Tests use JUnit 4.13.2 and
`androidx.test:runner:1.6.2` with `AndroidJUnitRunner`.

Run the instrumentation tests on an emulator. They use original tiny bitmaps,
controlled tasks and fake recognizers; they do not invoke a recognition model,
read a score, access a personal library, or require a network connection. They
check interruption, timeout, terminal cleanup, deferred close, submission races
and API forwarding. Passing these controls establishes resource-lifetime behavior,
not recognition accuracy or device-wide performance.

No Android SDK, Google library binary, model, phone log, or score asset is included.
