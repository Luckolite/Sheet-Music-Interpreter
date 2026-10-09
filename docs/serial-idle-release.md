<!-- Copyright 2026 Luckolite
SPDX-License-Identifier: Apache-2.0 -->

# Serial idle release

`SerialIdleRelease` retains a release request until accepted work on one serial
worker has drained. It is a pure-JDK ownership helper; it does not load models or
interpret score data. The Android `PortablePageOcr` adapter uses it for requested
idle reclamation. The earlier [page OCR overlap review](android-page-ocr-overlap.md)
remains historical evidence with its original source and format identities.

The executor must retain one worker and an aborting rejection policy.
`CallerRunsPolicy` is unsupported: submitting or cancellation threads must never
close the resource. Invoke `afterTask` only on the serial worker after an accepted
task returns, normally from `ThreadPoolExecutor.afterExecute`. The release callback
runs there only after the queue is observed empty.

`request` retains intent and admits a worker wake. Its boolean result describes
wake admission, not successful release. If the bounded queue rejects that wake,
intent remains for an accepted task's completion. When cancellation successfully
removes a queued task, pass that result to `afterQueuedCancellation`; a removed
task cannot supply its own completion hook. A pending request then admits another
bounded wake, preventing a stale nonempty observation from leaving an idle worker
asleep with unreclaimed resources. Neither method directly closes on its caller.

The Android owner retains its capacity-two queue and standard `AbortPolicy`.
Cleanup wakes occupy existing slots and may consume a slot freed by cancellation;
the helper does not increase capacity or promise unchanged admission under trim
contention. New work can arrive after an idle observation and reopen an owner
later. There is no global stop-admission or permanent-close guarantee.

Shutdown rejects new wakes. A retained request with no remaining accepted task
does not itself guarantee final shutdown cleanup; the owner must provide its
shutdown lifecycle. The helper consumes intent before invoking the release
callback and does not wrap or automatically retry callback failures. The Android
adapter preserves its existing close exception and reference-clear behavior.

The original `SerialIdleReleaseBoundaryTest` uses the full helper, a real JDK
executor, a fake resource and a deterministic latch-controlled cancellation
boundary. Existing `scripts/test_java.py` discovers its JUnit entry point. The app
test is the same source after package replacement. The regression proves worker
ownership and idle release; it does not establish native OCR completion, pixels,
model output, memory savings or phone elapsed improvement.

No models, tensor/CTC/confidence logic, timed await behavior, OCR outputs, geometry,
record layouts, epochs or dependencies change. The current explicit 221-source
native decoder list excludes both Android owner and idle-release helper.
