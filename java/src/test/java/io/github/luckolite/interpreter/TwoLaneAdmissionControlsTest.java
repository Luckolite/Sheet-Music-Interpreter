// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Original real-thread ownership controls with synthetic owners and explicit native families. */
public final class TwoLaneAdmissionControlsTest {
    private static final AtomicInteger checks = new AtomicInteger();
    private static int groups;

    private interface Throwing {
        void run() throws Exception;
    }

    private static final class Owner implements AutoCloseable {
        final Thread creator = Thread.currentThread();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch entered;
        final CountDownLatch allow;
        final RuntimeException failure;

        Owner(CountDownLatch entered, CountDownLatch allow, RuntimeException failure) {
            this.entered = entered;
            this.allow = allow;
            this.failure = failure;
        }

        @Override
        public void close() throws Exception {
            check(Thread.currentThread() == creator, "retirement uses the original owner thread");
            if (entered != null) entered.countDown();
            if (allow != null) awaitRetirement(allow);
            check(closes.incrementAndGet() == 1, "owner close is attempted exactly once");
            if (failure != null) throw failure;
        }
    }

    private static void awaitRetirement(CountDownLatch allow) {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true)
                try {
                    long remaining = Math.max(0, deadline - System.nanoTime());
                    check(allow.await(remaining, TimeUnit.NANOSECONDS), "retirement is released");
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks.incrementAndGet();
    }

    private static Throwable capture(Throwing work) {
        try {
            work.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void join(Thread thread) throws Exception {
        thread.join(5_000);
        check(!thread.isAlive(), "actual worker exits");
    }

    private static void requireBlocked(TwoLaneTileExecutor.Admission admission) {
        check(
                capture(admission::requireRetirementAdmission) instanceof IllegalStateException,
                "terminal unresolved native family blocks admission");
        AtomicInteger acquisitions = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            check(
                    capture(
                                    () ->
                                            new TwoLaneTileExecutor<Owner>(
                                                    lane -> {
                                                        acquisitions.incrementAndGet();
                                                        return new Owner(null, null, null);
                                                    },
                                                    (executor, lane) -> executor.shutdown(),
                                                    admission))
                            instanceof IllegalStateException,
                    "constructor rejects before any owner acquisition");
            check(
                    Thread.currentThread().isInterrupted(),
                    "rejected admission preserves interruption");
            check(acquisitions.get() == 0, "blocked constructor starts no owners");
        } finally {
            Thread.interrupted();
        }
    }

    private static void acceptedPair(TwoLaneTileExecutor.Admission admission) throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        try (var pair =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            acquisitions.incrementAndGet();
                            return new Owner(null, null, null);
                        },
                        (executor, lane) -> executor.shutdown(),
                        admission)) {
            check(acquisitions.get() == 2, "admitted pair acquires both owners");
            check(pair.submit(0, owner -> 37).await() == 37, "admitted owner accepts work");
        }
    }

    private static void healthyClosingDoesNotBlock() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var entered = new CountDownLatch(2);
        var allow = new CountDownLatch(1);
        Owner[] owners = new Owner[2];
        var pair =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            owners[lane] = new Owner(entered, allow, null);
                            return owners[lane];
                        },
                        (executor, lane) -> executor.shutdown(),
                        admission);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closer = new Thread(() -> closeFailure.set(capture(pair::close)), "healthy-close");
        closer.start();
        try {
            check(entered.await(5, TimeUnit.SECONDS), "both owners enter normal retirement");
            check(
                    pair.closeState() == TwoLaneTileExecutor.CloseState.CLOSING,
                    "ordinary retirement remains in progress");
            admission.requireRetirementAdmission();
            acceptedPair(admission);
            check(closer.isAlive(), "new admission does not wait for the healthy retiring pair");
        } finally {
            allow.countDown();
            join(closer);
        }
        check(closeFailure.get() == null, "normal close succeeds");
        for (Owner owner : owners) check(owner.closes.get() == 1, "both original owners retired");
        admission.requireRetirementAdmission();
        groups++;
        System.out.println("PASS healthy CLOSING permits another pair without caller waiting");
    }

    private static void terminalUnjoinedReapsAfterActualDrain() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var entered = new CountDownLatch(2);
        var allow = new CountDownLatch(1);
        Owner[] owners = new Owner[2];
        var denied = new SecurityException("shutdown changed state then failed");
        var pair =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            owners[lane] = new Owner(entered, allow, null);
                            return owners[lane];
                        },
                        (executor, lane) -> {
                            executor.shutdown();
                            throw denied;
                        },
                        admission);
        try {
            check(capture(pair::close) == denied, "original shutdown failure remains primary");
            check(entered.getCount() == 0, "actual owners entered close but have not returned");
            check(
                    pair.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "bounded failure observation reports unresolved ownership");
            for (int lane = 0; lane < 2; lane++) {
                check(
                        !pair.laneJoined(lane) && !pair.ownerCloseConfirmed(lane),
                        "terminal observations do not claim retirement");
                check(owners[lane].creator.isAlive(), "original owner is actually alive");
            }
            requireBlocked(admission);
        } finally {
            allow.countDown();
            for (Owner owner : owners) join(owner.creator);
        }
        admission.requireRetirementAdmission();
        acceptedPair(admission);
        check(
                pair.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                "later reaping does not rewrite historical close state");
        check(
                !pair.laneJoined(0) && !pair.laneJoined(1),
                "later actual exits are not fabricated historical joins");
        check(capture(pair::close) == denied, "repeat close preserves its original outcome");
        groups++;
        System.out.println("PASS terminal unresolved admission reopens only after actual drain");
    }

    private static void joinedUnconfirmedOwnerRemainsBlocked() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var failure = new IllegalArgumentException("owner close did not return normally");
        Owner[] owners = new Owner[2];
        var pair =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            owners[lane] = new Owner(null, null, lane == 0 ? failure : null);
                            return owners[lane];
                        },
                        (executor, lane) -> executor.shutdown(),
                        admission);
        check(capture(pair::close) == failure, "unconfirmed close keeps original primary");
        check(
                pair.closeState() == TwoLaneTileExecutor.CloseState.FAILED_JOINED,
                "threads joined despite unconfirmed owner retirement");
        check(pair.laneJoined(0) && pair.laneJoined(1), "both actual joins are retained");
        check(
                !pair.ownerCloseConfirmed(0) && pair.ownerCloseConfirmed(1),
                "normal close return is distinguished from thread exit");
        for (Owner owner : owners) check(!owner.creator.isAlive(), "owner threads really exited");
        requireBlocked(admission);
        check(capture(pair::close) == failure, "no synthetic retirement retry masks failure");
        groups++;
        System.out.println("PASS joined but unconfirmed owner remains blocked without close retry");
    }

    private static void lostConstructorCohortRemainsOwned() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var startup = new IllegalArgumentException("first owner acquisition failed");
        var shutdown = new SecurityException("constructor cleanup shutdown denied");
        ThreadPoolExecutor[] executors = new ThreadPoolExecutor[2];
        Thread[] threads = new Thread[2];
        Owner[] owners = new Owner[2];
        try {
            Throwable result =
                    capture(
                            () ->
                                    new TwoLaneTileExecutor<Owner>(
                                            lane -> {
                                                threads[lane] = Thread.currentThread();
                                                if (lane == 0) throw startup;
                                                owners[lane] = new Owner(null, null, null);
                                                return owners[lane];
                                            },
                                            (executor, lane) -> {
                                                executors[lane] = executor;
                                                throw shutdown;
                                            },
                                            admission));
            check(result == startup, "constructor retains acquisition primary identity");
            check(
                    startup.getSuppressed().length == 1 && startup.getSuppressed()[0] == shutdown,
                    "constructor cleanup remains suppressed in original order");
            check(
                    owners[1].creator.isAlive() && owners[1].closes.get() == 0,
                    "partially acquired owner remains actually live");
            Field retained = TwoLaneTileExecutor.Admission.class.getDeclaredField("retiring");
            retained.setAccessible(true);
            check(retained.get(admission) != null, "family strongly retains the unreturned cohort");
            requireBlocked(admission);
        } finally {
            // Independent fixture cleanup, not a production retry or a fabricated helper join.
            for (ThreadPoolExecutor executor : executors) if (executor != null) executor.shutdown();
            for (Thread thread : threads) if (thread != null) join(thread);
        }
        admission.requireRetirementAdmission();
        acceptedPair(admission);
        check(owners[1].closes.get() == 1, "partial owner really retired once");
        groups++;
        System.out.println(
                "PASS failed constructor cohort stays owned until real cleanup completes");
    }

    public static void main(String[] args) throws Exception {
        healthyClosingDoesNotBlock();
        terminalUnjoinedReapsAfterActualDrain();
        joinedUnconfirmedOwnerRemainsBlocked();
        lostConstructorCohortRemainsOwned();
        check(groups == 4, "four independent native-family schedules completed");
        System.out.println(
                "TWO_LANE_ADMISSION_PASS groups="
                        + groups
                        + " checks="
                        + checks.get()
                        + " actualThreads=true nativeModels=0 Android=false");
    }

    @org.junit.Test
    public void nativeFamilyRetirementAdmission() throws Exception {
        main(new String[0]);
    }
}
