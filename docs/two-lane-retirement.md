# Native tile owner retirement

`TwoLaneTileExecutor` keeps two independent serial owners, with at most one
outstanding request per owner. Each owner closes on its creator thread after
accepted work completes. Constructor APIs and normal exception precedence are
unchanged; diagnostic recording during constructor cleanup and failure merging
is now best effort, so it cannot replace the primary or stop later cleanup.

Adapters that share a native resource family can pass the same `Admission` to
the constructor taking a factory and shutdown callback. They should also check
`requireRetirementAdmission()` before expensive model acquisition. The Android
tile adapter uses one shared family across analyzer instances.

An ordinary `CLOSING` cohort remains strongly owned and permits new admission.
After close reports a terminal unresolved outcome, that family rejects new
acquisition promptly. An admission check does not wait, cancel work, interrupt
workers, close native owners or retry shutdown. Already-admitted pairs remain
valid. The registry publishes the terminal outcome before releasing close
waiters or returning the original failure.

Later admission can reap a cohort only after its actual worker threads have
exited and every acquired owner's close returned normally. Reaping does not
rewrite the original `CloseState`, joined flags or close exception. If shutdown
never lets a worker exit, or an owner's close throws, the family can remain
blocked until genuine resource drain or process restart. No retry fabricates
successful retirement.

## Failed factory acquisition

A factory can fail before transferring an owner to the executor. Adapters must
prepare `Admission.PartialAcquisition` with the original holder before native
acquisition. If cleanup does not return normally, `holdUnconfirmed()` strongly
retains that holder and rejects later acquisitions for the same family. Repeating
the hold is idempotent. Preparing an unused token does not block a healthy family;
other families remain independent. A held partial acquisition cannot be reaped
merely because the factory worker joined. Its native cleanup is unconfirmed, so
the terminal hold remains until process restart.

The Android LiteRT adapter allocates its TileLane holder, Java input and token
before acquiring the Environment, model or tensor buffers. Acquisition failure
attempts the returned resources' cleanup independently on the creator thread.
Any unconfirmed cleanup publishes the hold before recording its diagnostic. The
portable helper contains the pure-JDK token and admission policy, not LiteRT
objects or an alternative standalone ONNX implementation.

The adapter publishes the partial hold before recording suppression. Helper
constructor cleanup and `merge` use the same best-effort diagnostic rule without
changing their existing ownership or cleanup order. They skip null/self
suppression and catch RuntimeException/Error only around `addSuppressed`.
Ordinary distinct diagnostics keep their order; failure to record one does not
replace the primary or interrupt later cleanup. No native close failure is
converted to success.

The original Apache-2.0 JUnit regression uses real JDK executors and synthetic
owners to cover healthy concurrent retirement, delayed actual drain, joined but
unconfirmed close, and a constructor that cannot return its live partial cohort.
It is discovered by `python scripts/test_java.py`; the existing startup,
lifecycle and shutdown-fault regressions retain their contracts. These controls
do not load models or establish native failure rates, pixel parity, phone memory,
elapsed performance or heat improvements.

`TwoLanePartialAcquisitionControlsTest` adds original synthetic real-thread
factory/cleanup cases for partial-holder retention and readmission, healthy
unheld tokens, independent families, and ordinary primary/diagnostic identity.
Separate Android host controls execute the actual Java TileLane/helper with
explicit LiteRT/Android provider doubles, including a factory AssertionError
and modeled cleanup Error/RuntimeException/Exception. They do not execute JNI
destructors, heap exhaustion or suppression-allocation failure. The best-effort
diagnostic-allocation path is reviewed in source, not claimed as an executed
allocation-failure test. Hidden resources inside a factory that throws without
returning them remain outside the returned-resource cleanup proof.

The standalone helper remains free of Android and inference-runtime dependencies.
The Android model settings, tile packing, inference and stitching are unchanged.
This resource-lifetime change does not alter model weights, decoder/audio record
layouts, recognition epochs, or the desktop ONNX adapter's execution policy.
The explicit desktop decoder/native-audio source selections exclude these two
classes. The standalone Java builder still fingerprints all main sources, so its
JAR identity changes with the portable helper. Matching Hub1.6.140/page-worker
gates, packaged capability checks and verified paired rollout remain required;
this source review does not establish live deployment or packaged smoke results.

## Distinguishing restart-required acquisition

`Admission.restartRequired()` identifies a retained partial-acquisition hold.
It is a non-mutating query: it does not wait for, close, cancel or retry owners.
New acquisition under that hold throws `Admission.RestartRequiredException`, an
`IllegalStateException` subtype, with the existing diagnostic text. The hold
remains terminal until process restart; reopening a viewer does not retire it.

This reason differs from returned cohorts whose workers can still drain.
`restartRequired()` remains false for ordinary `CLOSING` and returned unresolved
cohorts; `requireRetirementAdmission()` still applies their existing admission
and reaping rules. A false query does not mean all resources have retired.
Previously admitted healthy owners remain usable, and independent families are
unaffected.

`TwoLaneRestartAdmissionControlsTest` uses four original real-JDK-thread
schedules with synthetic owners: empty/healthy admission, permanent partial
rejection before factories while existing work completes, concurrent healthy
closing, and returned-unjoined retirement followed by actual drain/readmission.
It is discovered by the ordinary Java test runner and contains no models,
Android resources, native destruction, private inputs or allocation-failure test.

Android failure propagation and restart guidance remain app orchestration.
They distinguish new optical work from healthy loaded analyzers and cached
results, preserve ordinary recoverable retries and report terminal partial
ownership without killing the process. The standalone helper contains only the
portable typed reason and query. No model, inference, record-layout or audio
algorithm changes are part of this port. Public build/provenance checks and
matched Hub/page-worker qualification remain separate release gates.
