// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Original fake-owner startup controls; no Android, native models, processes or timings. */
public final class TwoLaneStartupControlsTest {
    private static int groups;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "Synthetic control latch timed out");
    }

    private static void join(Thread thread) throws Exception {
        thread.join(5000);
        check(!thread.isAlive(), "Actual control thread did not exit");
    }

    private static void passed(String message) {
        groups++;
        System.out.println("PASS " + message);
    }

    private static final class Owner implements AutoCloseable {
        final Thread creator = Thread.currentThread();
        final AtomicInteger closes = new AtomicInteger();
        final Throwable closeFailure;

        Owner(Throwable closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() throws Exception {
            check(Thread.currentThread() == creator, "Owner retirement changed threads");
            check(closes.incrementAndGet() == 1, "Owner retired more than once");
            TwoLaneTileExecutor.rethrow(closeFailure);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(2);
        final CountDownLatch[] release = {new CountDownLatch(1), new CountDownLatch(1)};
        final CountDownLatch[] returned = {new CountDownLatch(1), new CountDownLatch(1)};
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReferenceArray<Owner> owners = new AtomicReferenceArray<>(2);
        final AtomicReferenceArray<Thread> factoryThreads = new AtomicReferenceArray<>(2);
        final AtomicReference<TwoLaneTileExecutor<Owner>> published = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean interruptedAtReturn = new AtomicBoolean();
        final Thread caller;

        Fixture(
                Throwable first,
                Throwable second,
                Throwable firstClose,
                Throwable secondClose,
                boolean nullOwners,
                boolean initiallyInterrupted,
                TwoLaneTileExecutor.Shutdown shutdown) {
            Throwable[] failures = {first, second}, closeFailures = {firstClose, secondClose};
            caller =
                    new Thread(
                            () -> {
                                if (initiallyInterrupted) Thread.currentThread().interrupt();
                                try {
                                    published.set(
                                            new TwoLaneTileExecutor<>(
                                                    lane -> {
                                                        factoryThreads.set(
                                                                lane, Thread.currentThread());
                                                        entered.countDown();
                                                        try {
                                                            await(release[lane]);
                                                            TwoLaneTileExecutor.rethrow(
                                                                    failures[lane]);
                                                            if (nullOwners) return null;
                                                            Owner owner =
                                                                    new Owner(closeFailures[lane]);
                                                            owners.set(lane, owner);
                                                            return owner;
                                                        } finally {
                                                            returned[lane].countDown();
                                                        }
                                                    },
                                                    shutdown));
                                } catch (Exception | Error problem) {
                                    failure.set(problem);
                                } finally {
                                    interruptedAtReturn.set(Thread.currentThread().isInterrupted());
                                    finished.countDown();
                                }
                            },
                            "SyntheticStartupCaller");
            caller.start();
        }

        Fixture(
                Throwable first,
                Throwable second,
                Throwable firstClose,
                Throwable secondClose,
                boolean nullOwners,
                boolean initiallyInterrupted) {
            this(
                    first,
                    second,
                    firstClose,
                    secondClose,
                    nullOwners,
                    initiallyInterrupted,
                    (executor, lane) -> executor.shutdown());
        }

        void finish() throws Exception {
            release[0].countDown();
            release[1].countDown();
            join(caller);
        }

        void requireRetired(int lane) {
            Owner owner = owners.get(lane);
            check(
                    owner != null && owner.closes.get() == 1,
                    "Successful owner was not retired once");
            check(!owner.creator.isAlive(), "Owner thread exit was not actually joined");
        }

        @Override
        public void close() throws Exception {
            finish();
            TwoLaneTileExecutor<Owner> engine = published.get();
            if (engine != null) engine.close();
            for (int lane = 0; lane < 2; lane++) {
                Thread thread = factoryThreads.get(lane);
                check(thread == null || !thread.isAlive(), "Factory thread remains alive");
            }
        }
    }

    private static void simultaneousAcquisitionAndOrderedUse() throws Exception {
        try (Fixture f = new Fixture(null, null, null, null, false, false)) {
            await(f.entered);
            check(
                    f.published.get() == null && f.finished.getCount() == 1,
                    "Constructor published while both factories remained active");
            f.release[1].countDown();
            await(f.returned[1]);
            check(f.published.get() == null, "Secondary completion published an incomplete pair");
            f.release[0].countDown();
            join(f.caller);
            check(f.failure.get() == null, "Successful parallel startup failed");
            var engine = f.published.get();
            check(
                    engine.idleOwner(0) == f.owners.get(0)
                            && engine.idleOwner(1) == f.owners.get(1),
                    "Lane identities changed with completion order");
            check(
                    f.factoryThreads.get(0) != f.factoryThreads.get(1),
                    "Acquisitions shared an owner");
            check(
                    engine.submit(0, owner -> 10).await() == 10
                            && engine.submit(1, owner -> 11).await() == 11,
                    "Work dispatch changed lanes");
            engine.close();
            f.requireRetired(0);
            f.requireRetired(1);
        }
        passed("both acquisitions overlap; publication waits; same-owner use and close");
    }

    private static void reversedFailuresKeepPrimaryIdentity() throws Exception {
        Error primary = new AssertionError("primary factory");
        Exception secondary = new Exception("secondary factory");
        try (Fixture f = new Fixture(primary, secondary, null, null, false, false)) {
            await(f.entered);
            f.release[1].countDown();
            await(f.returned[1]);
            check(f.finished.getCount() == 1, "Secondary failure skipped the actual primary wait");
            f.release[0].countDown();
            join(f.caller);
            check(f.failure.get() == primary, "Completion order replaced the primary Error");
            check(
                    primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == secondary,
                    "Secondary factory failure was lost or reordered");
        }
        passed("secondary fails first; primary Error identity and ordered suppression survive");
    }

    private static void primaryFailureDrainsAndClosesSecondary() throws Exception {
        Exception primary = new Exception("primary acquisition failed");
        Error cleanup = new AssertionError("secondary owner close");
        try (Fixture f = new Fixture(primary, null, null, cleanup, false, false)) {
            await(f.entered);
            f.release[0].countDown();
            await(f.returned[0]);
            check(
                    f.finished.getCount() == 1,
                    "Primary failure skipped actual secondary acquisition");
            f.release[1].countDown();
            join(f.caller);
            check(f.failure.get() == primary, "Cleanup masked the acquisition failure");
            check(
                    primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == cleanup,
                    "Successful secondary retirement failure was omitted");
            f.requireRetired(1);
        }
        passed("primary failure drains the successful secondary and preserves cleanup Error");
    }

    private static void secondaryFailureClosesPrimary() throws Exception {
        Exception secondary = new Exception("secondary acquisition failed");
        try (Fixture f = new Fixture(null, secondary, null, null, false, false)) {
            await(f.entered);
            f.finish();
            check(f.failure.get() == secondary, "Secondary failure identity changed");
            f.requireRetired(0);
        }
        passed("secondary acquisition failure retires the completed primary on its owner");
    }

    private static void nullOwnersAreRejectedAfterBothReturns() throws Exception {
        try (Fixture f = new Fixture(null, null, null, null, true, false)) {
            await(f.entered);
            f.release[0].countDown();
            await(f.returned[0]);
            check(f.finished.getCount() == 1, "Null primary skipped actual secondary return");
            f.release[1].countDown();
            join(f.caller);
            Throwable failure = f.failure.get();
            check(
                    failure instanceof IllegalStateException
                            && "Missing lane owner".equals(failure.getMessage()),
                    "Null owner was published");
            check(
                    failure.getSuppressed().length == 1
                            && failure.getSuppressed()[0] instanceof IllegalStateException,
                    "Second null-owner failure was lost");
        }
        passed("null owner rejection awaits both real factory returns");
    }

    private static void interruptionDoesNotCancelStartup() throws Exception {
        for (boolean incoming : new boolean[] {true, false}) {
            try (Fixture f = new Fixture(null, null, null, null, false, incoming)) {
                await(f.entered);
                if (!incoming) f.caller.interrupt();
                f.release[0].countDown();
                await(f.returned[0]);
                check(f.finished.getCount() == 1, "Interruption abandoned the secondary owner");
                f.release[1].countDown();
                join(f.caller);
                check(
                        f.failure.get() == null && f.interruptedAtReturn.get(),
                        "Interruption altered startup or was not restored at constructor return");
                f.published.get().close();
                f.requireRetired(0);
                f.requireRetired(1);
            }
        }
        passed(
                "incoming and in-flight caller interruption wait both acquisitions and restore flag");
    }

    private static void factoryFailurePrecedesShutdownFailure() throws Exception {
        Exception primary = new Exception("primary factory before shutdown");
        Error shutdown = new AssertionError("reported after actual secondary shutdown");
        try (Fixture f =
                new Fixture(
                        primary,
                        null,
                        null,
                        null,
                        false,
                        false,
                        (executor, lane) -> {
                            executor.shutdown();
                            if (lane == 1) throw shutdown;
                        })) {
            await(f.entered);
            f.finish();
            check(f.failure.get() == primary, "Shutdown masked the original factory failure");
            check(
                    primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == shutdown,
                    "Shutdown failure suppression changed");
            f.requireRetired(1);
        }
        passed("constructor failure keeps priority over actual shutdown-failure cleanup");
    }

    public static void main(String[] args) throws Exception {
        simultaneousAcquisitionAndOrderedUse();
        reversedFailuresKeepPrimaryIdentity();
        primaryFailureDrainsAndClosesSecondary();
        secondaryFailureClosesPrimary();
        nullOwnersAreRejectedAfterBothReturns();
        interruptionDoesNotCancelStartup();
        factoryFailurePrecedesShutdownFailure();
        check(groups == 7, "Unexpected startup-control group count");
        System.out.println("TERMINAL PASS startupGroups=7 nativeModels=0 SDK=0 timings=0");
    }

    /** Runs the original 7-group synthetic owner contract. */
    @org.junit.Test
    public void originalOwnerContract() throws Exception {
        main(new String[0]);
    }
}
