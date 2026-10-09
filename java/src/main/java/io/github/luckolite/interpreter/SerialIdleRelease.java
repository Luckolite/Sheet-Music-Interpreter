// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Remembers release intent until accepted work on a single worker has drained. */
public final class SerialIdleRelease {
    private final ThreadPoolExecutor executor;
    private final Runnable release;
    private final AtomicBoolean requested = new AtomicBoolean();

    /**
     * The executor must retain one worker and an aborting rejection policy.
     *
     * {@link ThreadPoolExecutor.CallerRunsPolicy} is unsupported because release must never run on a
     * submitting thread. Invoke {@link #afterTask()} only on the serial worker.
     */
    public SerialIdleRelease(ThreadPoolExecutor executor, Runnable release) {
        this.executor = Objects.requireNonNull(executor);
        this.release = Objects.requireNonNull(release);
        if (executor.getMaximumPoolSize() != 1)
            throw new IllegalArgumentException("Release requires a serial executor");
    }

    /** Request release without closing on the requesting thread; false means wake admission failed. */
    public boolean request() {
        requested.set(true);
        return wake();
    }

    /** Invoke only on the serial worker after an accepted task has returned. */
    public void afterTask() {
        if (!executor.getQueue().isEmpty() || !requested.getAndSet(false)) return;
        release.run();
    }

    /** A removed queued task cannot provide its own completion hook, so wake the worker. */
    public boolean afterQueuedCancellation(boolean removed) {
        return removed && requested.get() && wake();
    }

    private boolean wake() {
        try {
            executor.execute(this::afterTask);
            return true;
        } catch (RejectedExecutionException busy) {
            // Keep intent; an accepted task's completion will try the idle boundary again.
            return false;
        }
    }
}
