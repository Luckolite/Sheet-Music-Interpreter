// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Two serial native owners, with one outstanding request per owner and no cancellation fiction. */
final class TwoLaneTileExecutor<L extends AutoCloseable> implements AutoCloseable {
    interface Factory<L> {
        L open(int lane) throws Exception;
    }

    interface Work<L, T> {
        T run(L owner) throws Exception;
    }

    // Package-private fault seam; the ordinary constructor invokes actual executor.shutdown().
    interface Shutdown {
        void run(ThreadPoolExecutor executor, int lane);
    }

    enum CloseState {
        OPEN,
        CLOSING,
        RETIRED,
        FAILED_JOINED,
        FAILED_UNJOINED
    }

    private static final long FAILED_SHUTDOWN_JOIN_NANOS = TimeUnit.SECONDS.toNanos(2);

    /** Admission for one native resource family; unrelated generic owners need not share it. */
    static final class Admission {
        private TwoLaneTileExecutor<?> retiring;
        private PartialAcquisition partialAcquisitions;

        /** Preallocates ownership accounting before acquiring any native resources. */
        PartialAcquisition preparePartialAcquisition(Object owner) {
            if (owner == null) throw new NullPointerException("owner");
            return new PartialAcquisition(this, owner);
        }

        /**
         * A failed factory owns resources that were never transferred to the executor. Native
         * destruction that did not return normally cannot be retried or reported as retirement.
         * This terminal hold intentionally blocks this family until the process restarts.
         */
        static final class PartialAcquisition {
            private final Admission admission;
            private final Object owner;
            private PartialAcquisition next;
            private boolean retained;

            private PartialAcquisition(Admission admission, Object owner) {
                this.admission = admission;
                this.owner = owner;
            }

            /** Retains the original partial owner without allocating or invoking native cleanup. */
            void holdUnconfirmed() {
                admission.retainPartial(this);
            }
        }

        private synchronized void retainPartial(PartialAcquisition acquisition) {
            if (acquisition.retained) return;
            acquisition.retained = true;
            acquisition.next = partialAcquisitions;
            partialAcquisitions = acquisition;
        }

        /** Rejects unresolved terminal retirement without waiting for native work or closing it. */
        synchronized void requireRetirementAdmission() {
            reap();
            if (partialAcquisitions != null)
                throw new IllegalStateException(
                        "Previous partial native tile acquisition has not retired");
            for (TwoLaneTileExecutor<?> cohort = retiring;
                    cohort != null;
                    cohort = cohort.nextRetiring) {
                if (cohort.retirementTerminal)
                    throw new IllegalStateException("Previous native tile owners have not retired");
            }
        }

        private synchronized void retain(TwoLaneTileExecutor<?> cohort) {
            if (cohort.retirementRegistered) return;
            cohort.retirementRegistered = true;
            cohort.nextRetiring = retiring;
            retiring = cohort;
        }

        private synchronized void finished(TwoLaneTileExecutor<?> cohort) {
            cohort.retirementTerminal = true;
            reap();
        }

        private void reap() {
            TwoLaneTileExecutor<?> previous = null;
            TwoLaneTileExecutor<?> cohort = retiring;
            while (cohort != null) {
                TwoLaneTileExecutor<?> next = cohort.nextRetiring;
                if (cohort.actuallyRetired()) {
                    if (previous == null) retiring = next;
                    else previous.nextRetiring = next;
                    cohort.nextRetiring = null;
                } else previous = cohort;
                cohort = next;
            }
        }
    }

    private final ThreadPoolExecutor[] executors = new ThreadPoolExecutor[2];
    private final Thread[] threads = new Thread[2];
    private final Object[] owners = new Object[2];
    private final Object[] pending = new Object[2];
    private final Throwable[] retirementFailures = new Throwable[2];
    private final CountDownLatch closeFinished = new CountDownLatch(1);
    private final Shutdown shutdown;
    private final Admission admission;
    // Accessed only under the family's Admission monitor, never while invoking native code.
    private TwoLaneTileExecutor<?> nextRetiring;
    private boolean retirementRegistered;
    private boolean retirementTerminal;
    private final boolean[] joined = new boolean[2];
    private final boolean[] retirementReturned = new boolean[2];
    private CloseState closeState = CloseState.OPEN;
    private boolean closing;
    private boolean closed;
    private Throwable closeFailure;

    TwoLaneTileExecutor(Factory<L> factory) throws Exception {
        this(factory, (executor, lane) -> executor.shutdown());
    }

    TwoLaneTileExecutor(Factory<L> factory, Shutdown shutdown) throws Exception {
        this(factory, shutdown, null);
    }

    TwoLaneTileExecutor(Factory<L> factory, Shutdown shutdown, Admission admission)
            throws Exception {
        if (shutdown == null) throw new NullPointerException("shutdown");
        this.shutdown = shutdown;
        this.admission = admission;
        if (admission != null) admission.requireRetirementAdmission();
        boolean interrupted = Thread.interrupted();
        try {
            Future<?>[] opening = new Future<?>[2];
            Throwable[] startupFailures = new Throwable[2];
            // Submit both real acquisitions before awaiting either. Invocation order may vary.
            for (int lane = 0; lane < 2; lane++) {
                try {
                    final int index = lane;
                    executors[lane] =
                            new ThreadPoolExecutor(
                                    1,
                                    1,
                                    0,
                                    TimeUnit.MILLISECONDS,
                                    new ArrayBlockingQueue<>(1),
                                    task -> {
                                        Thread thread =
                                                new Thread(
                                                        () -> {
                                                            try {
                                                                task.run();
                                                            } finally {
                                                                AutoCloseable owner =
                                                                        (AutoCloseable)
                                                                                owners[index];
                                                                if (owner != null)
                                                                    try {
                                                                        owner.close();
                                                                        retirementReturned[index] =
                                                                                true;
                                                                    } catch (Exception
                                                                            | Error failure) {
                                                                        retirementFailures[index] =
                                                                                failure;
                                                                    }
                                                            }
                                                        },
                                                        "PrivateOMRTileLane" + index);
                                        thread.setDaemon(true);
                                        threads[index] = thread;
                                        return thread;
                                    });
                    // Each factory owns its partial acquisition and runs on its native owner.
                    opening[lane] =
                            executors[lane].submit(
                                    () -> {
                                        L owner = factory.open(index);
                                        if (owner == null)
                                            throw new IllegalStateException("Missing lane owner");
                                        owners[index] = owner;
                                        return owner;
                                    });
                } catch (Exception | Error failure) {
                    startupFailures[lane] = failure;
                }
            }
            // Always observe both actual results, even if an earlier submission/open failed.
            // Hold caller interruption until both waits and constructor cleanup have finished.
            for (int lane = 0; lane < 2; lane++) {
                if (opening[lane] == null) continue;
                try {
                    while (true)
                        try {
                            opening[lane].get();
                            break;
                        } catch (InterruptedException ignored) {
                            interrupted = true;
                        } catch (ExecutionException failure) {
                            rethrow(failure.getCause());
                            throw new AssertionError();
                        }
                } catch (Exception | Error failure) {
                    startupFailures[lane] = failure;
                }
            }
            // Deterministic primary-first delivery; native completion order does not choose it.
            rethrow(merge(startupFailures[0], startupFailures[1]));
        } catch (Exception | Error failure) {
            try {
                close();
            } catch (Exception | Error cleanup) {
                bestEffortSuppressed(failure, cleanup);
            }
            throw failure;
        } finally {
            if (Thread.interrupted() || interrupted) Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    synchronized L idleOwner(int lane) {
        checkLane(lane);
        if (closing || closed) throw new IllegalStateException("Tile lanes are closed");
        if (pending[lane] != null) throw new IllegalStateException("Tile lane is busy");
        return (L) owners[lane];
    }

    synchronized <T> Ticket<T> submit(int lane, Work<L, T> operation) {
        L owner = idleOwner(lane);
        FutureTask<T> future = new FutureTask<>(() -> operation.run(owner));
        Ticket<T> ticket = new Ticket<>(lane, future);
        pending[lane] = ticket;
        try {
            executors[lane].execute(future);
        } catch (RuntimeException | Error failure) {
            pending[lane] = null;
            throw failure;
        }
        return ticket;
    }

    @SuppressWarnings("unchecked")
    void drain(int lane) throws Exception {
        Ticket<?> ticket;
        synchronized (this) {
            checkLane(lane);
            ticket = (Ticket<?>) pending[lane];
        }
        if (ticket != null) ticket.await();
    }

    final class Ticket<T> {
        private final int lane;
        private final Future<T> future;

        private Ticket(int lane, Future<T> future) {
            this.lane = lane;
            this.future = future;
        }

        T await() throws Exception {
            try {
                return awaitActual(future);
            } finally {
                // awaitActual never exits merely because its caller was interrupted.
                synchronized (TwoLaneTileExecutor.this) {
                    if (pending[lane] == this) pending[lane] = null;
                }
            }
        }
    }

    // These are terminal-close observations, not a claim about an unjoined worker's later exit.
    synchronized CloseState closeState() {
        return closeState;
    }

    synchronized boolean laneJoined(int lane) {
        checkLane(lane);
        return closed && joined[lane];
    }

    synchronized boolean ownerCloseConfirmed(int lane) {
        checkLane(lane);
        return closed && joined[lane] && (owners[lane] == null || retirementReturned[lane]);
    }

    @Override
    public void close() throws Exception {
        if (Thread.currentThread() == threads[0] || Thread.currentThread() == threads[1])
            throw new IllegalStateException("A native lane cannot retire itself");
        boolean first;
        synchronized (this) {
            first = !closing && !closed;
            if (first) {
                closing = true;
                closeState = CloseState.CLOSING;
            }
        }
        if (!first) {
            awaitLatch(closeFinished);
            synchronized (this) {
                rethrow(closeFailure);
            }
            return;
        }

        if (admission != null) admission.retain(this);

        Throwable firstOperation = null, secondOperation = null;
        Throwable firstShutdown = null, secondShutdown = null;
        Throwable firstRetirement, secondRetirement;
        try {
            // Do not cancel Futures: wait for the original operation, including native readback.
            try {
                drain(0);
            } catch (Exception | Error failure) {
                firstOperation = failure;
            }
            try {
                drain(1);
            } catch (Exception | Error failure) {
                secondOperation = failure;
            }
        } finally {
            // Always attempt both shutdowns. A failed shutdown is not proof of worker exit.
            try {
                if (executors[0] != null) shutdown.run(executors[0], 0);
            } catch (RuntimeException | Error failure) {
                firstShutdown = failure;
            }
            try {
                if (executors[1] != null) shutdown.run(executors[1], 1);
            } catch (RuntimeException | Error failure) {
                secondShutdown = failure;
            }
            if (firstShutdown == null && secondShutdown == null) {
                // The successful path keeps its original actual, unbounded retirement joins.
                for (int lane = 0; lane < 2; lane++) {
                    joinActual(threads[lane]);
                    joined[lane] = true;
                }
            } else {
                // One shared bounded observation budget; never cancel or interrupt a native owner.
                long deadline = System.nanoTime() + FAILED_SHUTDOWN_JOIN_NANOS;
                for (int lane = 0; lane < 2; lane++)
                    joined[lane] = joinActualUntil(threads[lane], deadline);
            }
            // Join establishes visibility. Still-live owners remain held, not reported retired.
            firstRetirement = joined[0] ? retirementFailures[0] : null;
            secondRetirement = joined[1] ? retirementFailures[1] : null;
            Throwable failure = null;
            try {
                failure = merge(failure, firstOperation);
                failure = merge(failure, secondOperation);
                failure = merge(failure, firstShutdown);
                failure = merge(failure, secondShutdown);
                failure = merge(failure, firstRetirement);
                failure = merge(failure, secondRetirement);
            } finally {
                synchronized (this) {
                    closeFailure = failure;
                    closed = true;
                    closeState =
                            !joined[0] || !joined[1]
                                    ? CloseState.FAILED_UNJOINED
                                    : failure == null
                                            ? CloseState.RETIRED
                                            : CloseState.FAILED_JOINED;
                }
                // Publish unresolved ownership before any caller observes terminal close failure.
                if (admission != null) admission.finished(this);
                // Release concurrent/repeated close even when ownership is intentionally held.
                closeFinished.countDown();
            }
        }
        synchronized (this) {
            rethrow(closeFailure);
        }
    }

    private boolean actuallyRetired() {
        for (int lane = 0; lane < 2; lane++) {
            Thread thread = threads[lane];
            // Observing actual termination also makes the owner's close-return visible.
            if (thread != null && thread.isAlive()) return false;
            if (owners[lane] != null && !retirementReturned[lane]) return false;
        }
        return true;
    }

    private static void checkLane(int lane) {
        if (lane < 0 || lane >= 2) throw new IllegalArgumentException("Invalid lane");
    }

    private static Throwable merge(Throwable primary, Throwable later) {
        if (primary == null) return later;
        bestEffortSuppressed(primary, later);
        return primary;
    }

    private static void bestEffortSuppressed(Throwable primary, Throwable cleanup) {
        if (primary == null || cleanup == null || primary == cleanup) return;
        try {
            primary.addSuppressed(cleanup);
        } catch (RuntimeException | Error ignored) {
            // Diagnostic recording must not replace the primary or stop cleanup.
        }
    }

    static <T> T awaitActual(Future<T> future) throws Exception {
        boolean interrupted = Thread.interrupted();
        try {
            while (true)
                try {
                    return future.get();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (ExecutionException failure) {
                    rethrow(failure.getCause());
                    throw new AssertionError();
                }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        boolean interrupted = Thread.interrupted();
        try {
            while (true)
                try {
                    latch.await();
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void joinActual(Thread thread) {
        if (thread == null) return;
        boolean interrupted = Thread.interrupted();
        try {
            while (true)
                try {
                    thread.join();
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static boolean joinActualUntil(Thread thread, long deadline) {
        if (thread == null) return true;
        boolean interrupted = Thread.interrupted();
        try {
            while (thread.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static void rethrow(Throwable failure) throws Exception {
        if (failure == null) return;
        if (failure instanceof Error error) throw error;
        if (failure instanceof Exception error) throw error;
        throw new IllegalStateException(failure);
    }
}
