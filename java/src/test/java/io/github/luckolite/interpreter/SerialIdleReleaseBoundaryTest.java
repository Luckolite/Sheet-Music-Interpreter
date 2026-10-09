// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** One original deterministic stale nonempty/cancel boundary with a fake resource. */
public final class SerialIdleReleaseBoundaryTest {
    public static final class HookQueue<T> extends ArrayBlockingQueue<T> {
        private final String worker;
        private final AtomicBoolean armed = new AtomicBoolean(), observed = new AtomicBoolean();
        public final CountDownLatch staleNonempty = new CountDownLatch(1),
                resume = new CountDownLatch(1);
        public volatile int observedSize;

        public HookQueue(int capacity, String worker) {
            super(capacity);
            this.worker = worker;
        }

        public void arm() {
            armed.set(true);
        }

        @Override
        public boolean isEmpty() {
            boolean empty = super.isEmpty();
            if (!empty
                    && armed.get()
                    && worker.equals(Thread.currentThread().getName())
                    && observed.compareAndSet(false, true)) {
                observedSize = super.size();
                staleNonempty.countDown();
                try {
                    if (!resume.await(3, TimeUnit.SECONDS))
                        throw new AssertionError("Boundary was not resumed");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return empty;
        }
    }

    public static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    /** The same schedule is bound to the portable helper and the actual Android owner. */
    public static void verify(
            ThreadPoolExecutor executor,
            HookQueue<?> queue,
            List<? extends Future<?>> jobs,
            Runnable request,
            CountDownLatch finishActive,
            AtomicInteger active,
            AtomicInteger closes,
            CountDownLatch closed)
            throws Exception {
        check(executor.getQueue().size() == 2, "Original queue must be full");
        request.run();
        check(closes.get() == 0 && active.get() == 1, "Trim closed an active resource");
        check(jobs.get(1).cancel(true), "First queued task was not cancelled");
        queue.arm();
        finishActive.countDown();
        jobs.get(0).get(2, TimeUnit.SECONDS);
        check(
                queue.staleNonempty.await(2, TimeUnit.SECONDS),
                "Worker missed the controlled nonempty observation");
        check(active.get() == 0 && closes.get() == 0, "Resource closed before serial idle");
        try {
            check(jobs.get(2).cancel(true), "Last queued task was not cancelled");
            check(closes.get() == 0, "Cancel caller closed the resource");
        } finally {
            queue.resume.countDown();
        }
        check(
                closed.await(1, TimeUnit.SECONDS),
                "Pending trim stranded after final queued removal");
        executor.shutdown();
        check(executor.awaitTermination(2, TimeUnit.SECONDS), "Private serial worker did not exit");
        check(closes.get() == 1, "Resource did not close exactly once");
        check(
                jobs.get(1).isCancelled() && jobs.get(2).isCancelled(),
                "Queued cancellation changed");
    }

    @Test
    public void finalQueuedCancellationReleasesOnTheWorker() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        String worker = "portable-idle-release-control";
        HookQueue<Runnable> queue = new HookQueue<>(2, worker);
        AtomicInteger active = new AtomicInteger(),
                closes = new AtomicInteger(),
                reads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1),
                finish = new CountDownLatch(1),
                closed = new CountDownLatch(1);
        AtomicReference<Thread> releaseThread = new AtomicReference<>();
        SerialIdleRelease[] owner = new SerialIdleRelease[1];
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        1, 1, 0, TimeUnit.SECONDS, queue, task -> new Thread(task, worker)) {
                    @Override
                    protected void afterExecute(Runnable task, Throwable failure) {
                        super.afterExecute(task, failure);
                        owner[0].afterTask();
                    }
                };
        owner[0] =
                new SerialIdleRelease(
                        executor,
                        () -> {
                            check(active.get() == 0, "Active fake resource closed");
                            releaseThread.set(Thread.currentThread());
                            closes.incrementAndGet();
                            closed.countDown();
                        });
        java.util.function.IntFunction<FutureTask<Integer>> task =
                value ->
                        new FutureTask<>(
                                () -> {
                                    reads.incrementAndGet();
                                    active.incrementAndGet();
                                    entered.countDown();
                                    try {
                                        check(
                                                finish.await(3, TimeUnit.SECONDS),
                                                "Active operation not resumed");
                                        return value;
                                    } finally {
                                        active.decrementAndGet();
                                    }
                                }) {
                            @Override
                            protected void done() {
                                if (isCancelled())
                                    owner[0].afterQueuedCancellation(executor.remove(this));
                            }
                        };
        var a = task.apply(11);
        var b = task.apply(22);
        var c = task.apply(33);
        try {
            executor.execute(a);
            check(entered.await(2, TimeUnit.SECONDS), "Active operation not entered");
            executor.execute(b);
            executor.execute(c);
            verify(
                    executor,
                    queue,
                    List.of(a, b, c),
                    () -> check(!owner[0].request(), "Full trim admission changed"),
                    finish,
                    active,
                    closes,
                    closed);
            check(a.get() == 11 && reads.get() == 1, "Accepted/cancelled operation order changed");
            check(
                    releaseThread.get().getName().equals(worker),
                    "Release escaped the serial worker");
            System.out.println("PASS one_original_stale_nonempty_final_cancel_boundary");
        } finally {
            queue.resume.countDown();
            finish.countDown();
            executor.shutdownNow();
            check(executor.awaitTermination(2, TimeUnit.SECONDS), "Private worker leaked");
        }
    }
}
