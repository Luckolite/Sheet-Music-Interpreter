// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Original fake-owner controls; no Android APIs, native resources, models or timings. */
public final class TwoLaneShutdownFaultControlsTest {
    private static final class Owner implements AutoCloseable {
        final Thread creator = Thread.currentThread();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch allowClose;

        Owner(CountDownLatch allowClose) {
            this.allowClose = allowClose;
        }

        @Override
        public void close() throws Exception {
            check(Thread.currentThread() == creator, "owner must close on its native lane");
            if (allowClose != null) await(allowClose);
            check(closes.incrementAndGet() == 1, "owner must close once");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Owner[] owners = new Owner[2];
        final ThreadPoolExecutor[] actualExecutors = new ThreadPoolExecutor[2];
        final AtomicInteger[] attempts = {new AtomicInteger(), new AtomicInteger()};
        final CountDownLatch allowRetirement;
        final TwoLaneTileExecutor<Owner> lanes;

        Fixture(
                int failingLane,
                boolean afterShutdown,
                Throwable failure,
                CountDownLatch shutdownEntered,
                CountDownLatch allowShutdown,
                CountDownLatch allowRetirement)
                throws Exception {
            this.allowRetirement = allowRetirement;
            lanes =
                    new TwoLaneTileExecutor<>(
                            lane -> {
                                Owner owner = new Owner(allowRetirement);
                                owners[lane] = owner;
                                return owner;
                            },
                            (executor, lane) -> {
                                actualExecutors[lane] = executor;
                                attempts[lane].incrementAndGet();
                                boolean fail = failingLane == lane || failingLane == 2;
                                if (fail && shutdownEntered != null) {
                                    shutdownEntered.countDown();
                                    await(allowShutdown);
                                }
                                if (!fail || afterShutdown) executor.shutdown();
                                if (fail) throwUnchecked(failure);
                            });
        }

        @Override
        public void close() throws Exception {
            // Independent fixture cleanup only. It is not reported as helper retirement.
            if (allowRetirement != null) allowRetirement.countDown();
            for (ThreadPoolExecutor executor : actualExecutors)
                if (executor != null) executor.shutdown();
            for (Owner owner : owners)
                if (owner != null) {
                    owner.creator.join(5_000);
                    check(
                            !owner.creator.isAlive(),
                            "fixture cleanup must join actual owner threads");
                    check(owner.closes.get() == 1, "fixture cleanup must close both owners once");
                }
        }
    }

    private static void beforeShutdownFailure(int lane) throws Exception {
        Throwable failure =
                lane == 0
                        ? new SecurityException("first shutdown denied")
                        : new AssertionError("second shutdown failed");
        try (Fixture f = new Fixture(lane, false, failure, null, null, null)) {
            check(capture(f.lanes::close) == failure, "original shutdown failure identity");
            bothAttempted(f);
            check(
                    f.lanes.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "nonshutdown lane remains explicitly unjoined");
            check(!f.lanes.laneJoined(lane), "failed lane cannot be called joined");
            check(!f.lanes.ownerCloseConfirmed(lane), "held owner cannot be called retired");
            check(f.owners[lane].closes.get() == 0, "caller cannot close held native owner");
            check(f.lanes.laneJoined(lane ^ 1), "other successful lane really exits");
            check(f.lanes.ownerCloseConfirmed(lane ^ 1), "other owner close really returns");
            check(capture(f.lanes::close) == failure, "repeated close returns stored failure");
            bothAttempted(f);
        }
    }

    private static void afterStateChangeFailure(int lane) throws Exception {
        Error failure = new AssertionError("shutdown threw after changing state");
        CountDownLatch permitRetirement = new CountDownLatch(1);
        try (Fixture f = new Fixture(lane, true, failure, null, null, permitRetirement)) {
            check(capture(f.lanes::close) == failure, "post-state failure identity");
            bothAttempted(f);
            check(
                    f.actualExecutors[0].isShutdown() && f.actualExecutors[1].isShutdown(),
                    "both states actually changed");
            check(
                    f.lanes.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "shutdown state does not prove blocked owner-close returned");
            for (int i = 0; i < 2; i++) {
                check(!f.lanes.laneJoined(i), "live retirement cannot be called joined");
                check(!f.lanes.ownerCloseConfirmed(i), "live retirement cannot be called closed");
                check(f.owners[i].creator.isAlive(), "owner retirement really remains alive");
            }
            Thread.currentThread().interrupt();
            check(capture(f.lanes::close) == failure, "interrupted repeated close remains finite");
            check(Thread.interrupted(), "incoming interruption is restored");
        }
    }

    private static void operationPrecedesBothShutdownFailures() throws Exception {
        Error operation = new AssertionError("earlier native operation");
        RuntimeException shutdown = new IllegalStateException("both shutdowns");
        try (Fixture f = new Fixture(2, false, shutdown, null, null, null)) {
            f.lanes.submit(
                    0,
                    owner -> {
                        throw operation;
                    });
            f.lanes.submit(1, owner -> "later successful operation");
            check(capture(f.lanes::close) == operation, "original operation retains precedence");
            check(
                    operation.getSuppressed().length == 2
                            && operation.getSuppressed()[0] == shutdown
                            && operation.getSuppressed()[1] == shutdown,
                    "both actual shutdown failures follow the original operation");
            bothAttempted(f);
            check(
                    f.lanes.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "both idle owners are held after failed shutdown");
            for (int i = 0; i < 2; i++) {
                check(
                        !f.lanes.laneJoined(i) && !f.lanes.ownerCloseConfirmed(i),
                        "held ownership is reported honestly");
                check(f.owners[i].closes.get() == 0, "no caller-side disposal");
            }
            check(capture(f.lanes::close) == operation, "repeated close preserves primary");
            check(operation.getSuppressed().length == 2, "repeated close adds no suppression");
        }
    }

    private static void shutdownFailureWithActualRetirement() throws Exception {
        SecurityException failure = new SecurityException("reported after actual shutdown");
        try (Fixture f = new Fixture(0, true, failure, null, null, null)) {
            check(capture(f.lanes::close) == failure, "joined failure must still be reported");
            bothAttempted(f);
            check(
                    f.lanes.closeState() == TwoLaneTileExecutor.CloseState.FAILED_JOINED,
                    "actual exits are distinguished from held owners");
            for (int i = 0; i < 2; i++) {
                check(
                        f.lanes.laneJoined(i) && f.lanes.ownerCloseConfirmed(i),
                        "retirement is confirmed only after real same-owner close and join");
                check(f.owners[i].closes.get() == 1, "actual owner close returned once");
            }
            check(capture(f.lanes::close) == failure, "joined repeated close keeps actual failure");
            bothAttempted(f);
        }
    }

    private static void concurrentCloseWaiterReceivesTerminalFailure() throws Exception {
        SecurityException failure = new SecurityException("concurrent denied shutdown");
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicReference<Throwable> first = new AtomicReference<>(),
                second = new AtomicReference<>();
        AtomicReference<Boolean> secondInterrupt = new AtomicReference<>(false);
        CountDownLatch secondEntered = new CountDownLatch(1);
        try (Fixture f = new Fixture(0, false, failure, entered, proceed, null)) {
            Thread a = new Thread(() -> first.set(capture(f.lanes::close)), "CloseFaultFirst");
            Thread b =
                    new Thread(
                            () -> {
                                Thread.currentThread().interrupt();
                                secondEntered.countDown();
                                second.set(capture(f.lanes::close));
                                secondInterrupt.set(Thread.interrupted());
                            },
                            "CloseFaultWaiter");
            a.start();
            try {
                check(entered.await(5, TimeUnit.SECONDS), "first close reaches shutdown phase");
                b.start();
                check(secondEntered.await(5, TimeUnit.SECONDS), "second close starts");
            } finally {
                proceed.countDown();
            }
            a.join(5_000);
            b.join(5_000);
            check(!a.isAlive() && !b.isAlive(), "both close callers reach finite disposition");
            check(first.get() == failure && second.get() == failure, "both observe same primary");
            check(secondInterrupt.get(), "concurrent wait restores interruption");
            bothAttempted(f);
            check(
                    f.lanes.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "concurrent waiter does not fabricate retirement");
        } finally {
            proceed.countDown();
        }
    }

    private static void bothAttempted(Fixture f) {
        check(
                f.attempts[0].get() == 1 && f.attempts[1].get() == 1,
                "both shutdowns attempted once");
    }

    private interface Throwing {
        void run() throws Exception;
    }

    private static Throwable capture(Throwing operation) {
        try {
            operation.run();
            return null;
        } catch (Exception | Error failure) {
            return failure;
        }
    }

    private static void await(CountDownLatch latch) {
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

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        throw new AssertionError("fault must be unchecked", failure);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        beforeShutdownFailure(0);
        beforeShutdownFailure(1);
        afterStateChangeFailure(0);
        afterStateChangeFailure(1);
        operationPrecedesBothShutdownFailures();
        shutdownFailureWithActualRetirement();
        concurrentCloseWaiterReceivesTerminalFailure();
        System.out.println(
                "TWO_LANE_SHUTDOWN_FAULT_CONTROLS_PASS groups=7 models=0 Android=0 SDK=0");
    }

    /** Runs the original 7-group synthetic owner contract. */
    @org.junit.Test
    public void originalOwnerContract() throws Exception {
        main(new String[0]);
    }
}
