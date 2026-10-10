// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual helper/thread behavior with fake owners; this is not an observed JNI incident. */
public final class TwoLaneRestartAdmissionControlsTest {
    private static final AtomicInteger checks = new AtomicInteger();
    private static int groups;

    private interface Action {
        void run() throws Exception;
    }

    private static final class Owner implements AutoCloseable {
        final Thread creator = Thread.currentThread();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch closing;
        final CountDownLatch release;

        Owner(CountDownLatch closing, CountDownLatch release) {
            this.closing = closing;
            this.release = release;
        }

        @Override
        public void close() throws Exception {
            check(Thread.currentThread() == creator, "close stays on actual owner thread");
            check(closes.incrementAndGet() == 1, "owner close attempted once");
            if (closing != null) closing.countDown();
            if (release != null) awaitOwnerRelease(release, "fixture releases owner close");
        }
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
        checks.incrementAndGet();
    }

    private static void await(CountDownLatch latch, String why) throws Exception {
        check(latch.await(8, TimeUnit.SECONDS), why);
    }

    // Executor shutdown may interrupt an idle worker as its wrapper enters owner cleanup.
    // Keep the fake close pending until its release, with the same deadline and interrupt flag.
    private static void awaitOwnerRelease(CountDownLatch latch, String why) throws Exception {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        boolean released = false;
        try {
            while (true) {
                try {
                    released =
                            latch.await(
                                    Math.max(0, deadline - System.nanoTime()),
                                    TimeUnit.NANOSECONDS);
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                    if (System.nanoTime() >= deadline) break;
                }
            }
            check(released, why);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread thread) throws Exception {
        thread.join(8_000);
        check(!thread.isAlive(), "actual thread exited");
    }

    private static Throwable failure(Action action) {
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static TwoLaneTileExecutor<Owner> pair(
            TwoLaneTileExecutor.Admission admission, Owner[] owners) throws Exception {
        return new TwoLaneTileExecutor<>(
                lane -> {
                    Owner owner = new Owner(null, null);
                    if (owners != null) owners[lane] = owner;
                    return owner;
                },
                (executor, lane) -> executor.shutdown(),
                admission);
    }

    private static void healthy(TwoLaneTileExecutor.Admission admission) throws Exception {
        Owner[] owners = new Owner[2];
        try (var pair = pair(admission, owners)) {
            check(pair.submit(0, owner -> 37).await() == 37, "healthy owner result retained");
            check(!admission.restartRequired(), "healthy admission does not require restart");
        }
        for (Owner owner : owners) {
            check(owner.closes.get() == 1, "healthy owner actually retired");
            check(!owner.creator.isAlive(), "healthy owner thread actually joined");
        }
    }

    private static void emptyAndHealthy() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        check(!admission.restartRequired(), "empty family requires no restart");
        admission.preparePartialAcquisition(new Object());
        check(!admission.restartRequired(), "unused accounting token requires no restart");
        admission.requireRetirementAdmission();
        healthy(admission);
        check(!admission.restartRequired(), "completed healthy family requires no restart");
        groups++;
        System.out.println("PASS empty/unused-token/healthy restartRequired=false");
    }

    private static void partialHoldAndExistingWork() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        Owner[] owners = new Owner[2];
        var current = pair(admission, owners);
        var entered = new CountDownLatch(2);
        var allow = new CountDownLatch(1);
        var first =
                current.submit(
                        0,
                        owner -> {
                            entered.countDown();
                            await(allow, "first pending request released");
                            return 11;
                        });
        var second =
                current.submit(
                        1,
                        owner -> {
                            entered.countDown();
                            await(allow, "second pending request released");
                            return 19;
                        });
        try {
            await(entered, "both pre-existing requests actually started");
            // Explicit fake unconfirmed partial owner: no native incident or reset is fabricated.
            Object partialOwner = new Object();
            var token = admission.preparePartialAcquisition(partialOwner);
            token.holdUnconfirmed();
            token.holdUnconfirmed();
            check(admission.restartRequired(), "retained partial token requires restart");
            check(
                    failure(admission::requireRetirementAdmission)
                            instanceof TwoLaneTileExecutor.Admission.RestartRequiredException,
                    "direct admission reports typed permanent hold");
            AtomicInteger factoryCalls = new AtomicInteger();
            Throwable blocked =
                    failure(
                            () ->
                                    new TwoLaneTileExecutor<Owner>(
                                            lane -> {
                                                factoryCalls.incrementAndGet();
                                                return new Owner(null, null);
                                            },
                                            (executor, lane) -> executor.shutdown(),
                                            admission));
            check(
                    blocked instanceof TwoLaneTileExecutor.Admission.RestartRequiredException,
                    "new factory rejected with typed reason");
            check(factoryCalls.get() == 0, "blocked new factory never called");
            var independent = new TwoLaneTileExecutor.Admission();
            healthy(independent);
            check(!independent.restartRequired(), "independent family unaffected");
            allow.countDown();
            check(
                    first.await() == 11 && second.await() == 19,
                    "pre-existing pending requests still complete");
        } finally {
            allow.countDown();
            current.close();
        }
        check(
                current.closeState() == TwoLaneTileExecutor.CloseState.RETIRED,
                "pre-existing executor closes normally despite separate hold");
        for (Owner owner : owners)
            check(
                    owner.closes.get() == 1 && !owner.creator.isAlive(),
                    "pre-existing owners actually retired");
        check(admission.restartRequired(), "healthy retirement never clears partial hold");
        groups++;
        System.out.println(
                "PASS typed partial hold/factory0; independent family and pre-existing pending work preserved");
    }

    private static void returnedClosing() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var entered = new CountDownLatch(2);
        var allow = new CountDownLatch(1);
        var current =
                new TwoLaneTileExecutor<Owner>(
                        lane -> new Owner(entered, allow),
                        (executor, lane) -> executor.shutdown(),
                        admission);
        var closeFailure = new AtomicReference<Throwable>();
        Thread closer =
                new Thread(
                        () -> closeFailure.set(failure(current::close)), "behavior-healthy-closer");
        closer.start();
        try {
            await(entered, "returned owners both enter close");
            check(
                    current.closeState() == TwoLaneTileExecutor.CloseState.CLOSING,
                    "returned cohort is still draining");
            check(
                    !admission.restartRequired(),
                    "returned draining cohort is not permanent partial hold");
            admission.requireRetirementAdmission();
            healthy(admission);
            check(closer.isAlive(), "healthy new pair does not wait for returned cohort");
        } finally {
            allow.countDown();
            join(closer);
        }
        check(closeFailure.get() == null, "healthy returned cohort closes successfully");
        admission.requireRetirementAdmission();
        check(!admission.restartRequired(), "healthy drain requires no restart");
        groups++;
        System.out.println(
                "PASS returned CLOSING stays restartRequired=false and preserves concurrent admission");
    }

    private static void genericUnjoinedRecovery() throws Exception {
        var admission = new TwoLaneTileExecutor.Admission();
        var entered = new CountDownLatch(2);
        var allow = new CountDownLatch(1);
        var denied = new SecurityException("modeled shutdown throws after actual shutdown");
        Owner[] owners = new Owner[2];
        var current =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            owners[lane] = new Owner(entered, allow);
                            return owners[lane];
                        },
                        (executor, lane) -> {
                            executor.shutdown();
                            throw denied;
                        },
                        admission);
        try {
            check(failure(current::close) == denied, "original shutdown failure retained");
            check(entered.getCount() == 0, "returned owners are actually in close");
            check(
                    current.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                    "actual unresolved returned cohort recorded");
            check(
                    !admission.restartRequired(),
                    "generic unresolved returned cohort requires no permanent restart");
            Throwable blocked = failure(admission::requireRetirementAdmission);
            check(
                    blocked != null && blocked.getClass() == IllegalStateException.class,
                    "existing generic admission rejection remains distinct");
            AtomicInteger factoryCalls = new AtomicInteger();
            Throwable constructor =
                    failure(
                            () ->
                                    new TwoLaneTileExecutor<Owner>(
                                            lane -> {
                                                factoryCalls.incrementAndGet();
                                                return new Owner(null, null);
                                            },
                                            (executor, lane) -> executor.shutdown(),
                                            admission));
            check(
                    constructor != null
                            && constructor.getClass() == IllegalStateException.class
                            && factoryCalls.get() == 0,
                    "generic returned-cohort admission still blocks before factory");
        } finally {
            allow.countDown();
            for (Owner owner : owners) join(owner.creator);
        }
        admission.requireRetirementAdmission();
        healthy(admission);
        check(!admission.restartRequired(), "actual returned-owner exit reopens generic admission");
        check(
                current.closeState() == TwoLaneTileExecutor.CloseState.FAILED_UNJOINED,
                "reaping does not rewrite historical outcome");
        check(failure(current::close) == denied, "repeat close retains prior generic failure");
        groups++;
        System.out.println(
                "PASS generic unjoined returned cohort stays restartRequired=false and reopens after actual drain");
    }

    @org.junit.Test
    public void syntheticRestartAdmissionContracts() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        emptyAndHealthy();
        partialHoldAndExistingWork();
        returnedClosing();
        genericUnjoinedRecovery();
        System.out.println(
                "RESULT groups="
                        + groups
                        + " assertions="
                        + checks.get()
                        + " modeledOwners=true JNI=false reflection=false");
    }
}
