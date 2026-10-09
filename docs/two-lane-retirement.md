# Native tile owner retirement

`TwoLaneTileExecutor` keeps two independent serial owners, with at most one
outstanding request per owner. Each owner closes on its creator thread after
accepted work completes. The existing constructors retain their behavior.

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

The original Apache-2.0 JUnit regression uses real JDK executors and synthetic
owners to cover healthy concurrent retirement, delayed actual drain, joined but
unconfirmed close, and a constructor that cannot return its live partial cohort.
It is discovered by `python scripts/test_java.py`; the existing startup,
lifecycle and shutdown-fault regressions retain their contracts. These controls
do not load models or establish native failure rates, pixel parity, phone memory,
elapsed performance or heat improvements.

The standalone helper remains free of Android and inference-runtime dependencies.
The Android model settings, tile packing, inference and stitching are unchanged.
This resource-lifetime change does not alter model weights, decoder/audio record
layouts, recognition epochs, or the desktop ONNX adapter's execution policy.
