// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/** Original synthetic factory ownership controls; no native runtime, model, or score input. */
public final class TwoLanePartialAcquisitionControlsTest {
    private static final AtomicInteger checks = new AtomicInteger();
    private static int groups;

    private interface Throwing {
        void run() throws Exception;
    }

    private static final class Owner implements AutoCloseable {
        final Thread creator = Thread.currentThread();
        final AtomicInteger closes = new AtomicInteger();
        final Throwable failure;

        Owner(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws Exception {
            check(Thread.currentThread() == creator, "cleanup stays on the owner thread");
            check(closes.incrementAndGet() == 1, "cleanup is attempted once");
            TwoLaneTileExecutor.rethrow(failure);
        }
    }

    private static final class PartialOwner {
        Owner acquired;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks.incrementAndGet();
    }

    private static Throwable capture(Throwing action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void healthyPair(TwoLaneTileExecutor.Admission admission) throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        Owner[] owners = new Owner[2];
        TwoLaneTileExecutor<Owner> pair =
                new TwoLaneTileExecutor<>(
                        lane -> {
                            acquisitions.incrementAndGet();
                            owners[lane] = new Owner(null);
                            return owners[lane];
                        },
                        (executor, lane) -> executor.shutdown(),
                        admission);
        try {
            check(pair.submit(0, owner -> 29).await() == 29, "healthy result is unchanged");
        } finally {
            pair.close();
        }
        check(acquisitions.get() == 2, "healthy family acquires both owners");
        check(owners[0].closes.get() == 1 && owners[1].closes.get() == 1, "both owners retire");
        check(pair.closeState() == TwoLaneTileExecutor.CloseState.RETIRED, "actual retirement");
        admission.requireRetirementAdmission();
    }

    private static void blockedBeforeAcquisition(TwoLaneTileExecutor.Admission admission) {
        AtomicInteger acquisitions = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            Throwable failure =
                    capture(
                            () ->
                                    new TwoLaneTileExecutor<Owner>(
                                            lane -> {
                                                acquisitions.incrementAndGet();
                                                return new Owner(null);
                                            },
                                            (executor, lane) -> executor.shutdown(),
                                            admission));
            check(failure instanceof IllegalStateException, "held family rejects readmission");
            check(acquisitions.get() == 0, "rejection precedes either factory");
            check(Thread.currentThread().isInterrupted(), "rejection preserves caller interrupt");
        } finally {
            Thread.interrupted();
        }
    }

    private static void partialFactoryRetainsOriginalOwner() throws Exception {
        TwoLaneTileExecutor.Admission admission = new TwoLaneTileExecutor.Admission();
        PartialOwner partialOwner = new PartialOwner();
        TwoLaneTileExecutor.Admission.PartialAcquisition token =
                admission.preparePartialAcquisition(partialOwner);
        AssertionError primary = new AssertionError("synthetic factory error");
        AssertionError cleanup = new AssertionError("synthetic cleanup failure");
        Owner[] other = new Owner[1];
        Throwable actual =
                capture(
                        () ->
                                new TwoLaneTileExecutor<Owner>(
                                        lane -> {
                                            if (lane == 0) {
                                                // Accounting exists before this procedural
                                                // acquisition.
                                                partialOwner.acquired = new Owner(cleanup);
                                                try {
                                                    throw primary;
                                                } catch (AssertionError failure) {
                                                    try {
                                                        partialOwner.acquired.close();
                                                    } catch (AssertionError closeFailure) {
                                                        token.holdUnconfirmed();
                                                        failure.addSuppressed(closeFailure);
                                                    }
                                                    throw failure;
                                                }
                                            }
                                            other[0] = new Owner(null);
                                            return other[0];
                                        },
                                        (executor, lane) -> executor.shutdown(),
                                        admission));
        check(actual == primary, "original factory Error is returned");
        check(primary.getSuppressed().length == 1, "ordinary cleanup diagnostic retained");
        check(primary.getSuppressed()[0] == cleanup, "cleanup identity is unchanged");
        check(
                partialOwner.acquired.closes.get() == 1,
                "partial resource cleanup was attempted once");
        check(other[0].closes.get() == 1, "returned owner cleanup was attempted once");
        check(
                !partialOwner.acquired.creator.isAlive(),
                "partial acquisition worker actually joined");
        check(!other[0].creator.isAlive(), "other worker actually joined");
        check(field(admission, "partialAcquisitions") == token, "family retains actual token");
        check(
                field(token, "owner") == partialOwner,
                "token strongly retains original partial owner");
        token.holdUnconfirmed();
        check(field(token, "next") == null, "repeat hold does not create a cycle");
        blockedBeforeAcquisition(admission);
        groups++;
    }

    private static void preparedButUnusedTokenAllowsHealthyReadmission() throws Exception {
        TwoLaneTileExecutor.Admission admission = new TwoLaneTileExecutor.Admission();
        Object owner = new Object();
        admission.preparePartialAcquisition(owner);
        healthyPair(admission);
        healthyPair(admission);
        check(field(admission, "partialAcquisitions") == null, "preparation alone creates no hold");
        groups++;
    }

    private static void failedFamilyDoesNotBlockIndependentFamily() throws Exception {
        TwoLaneTileExecutor.Admission blocked = new TwoLaneTileExecutor.Admission();
        blocked.preparePartialAcquisition(new Object()).holdUnconfirmed();
        blockedBeforeAcquisition(blocked);
        healthyPair(new TwoLaneTileExecutor.Admission());
        blockedBeforeAcquisition(blocked);
        groups++;
    }

    private static void startupAndCloseKeepPrimaryAndDiagnosticOrder() throws Exception {
        AssertionError primary = new AssertionError("first factory");
        RuntimeException second = new RuntimeException("second factory");
        Throwable actual =
                capture(
                        () ->
                                new TwoLaneTileExecutor<Owner>(
                                        lane -> {
                                            if (lane == 0) throw primary;
                                            throw second;
                                        }));
        check(actual == primary, "first lane remains primary regardless of completion order");
        check(primary.getSuppressed().length == 1, "second factory remains one diagnostic");
        check(primary.getSuppressed()[0] == second, "second factory identity is retained");

        AssertionError first = new AssertionError("one factory");
        AssertionError laterCleanup = new AssertionError("other owner's cleanup");
        Owner[] returned = new Owner[1];
        Throwable withCleanup =
                capture(
                        () ->
                                new TwoLaneTileExecutor<Owner>(
                                        lane -> {
                                            if (lane == 0) throw first;
                                            returned[0] = new Owner(laterCleanup);
                                            return returned[0];
                                        }));
        check(withCleanup == first, "constructor cleanup does not replace factory Error");
        check(returned[0].closes.get() == 1, "other returned owner receives cleanup attempt");
        check(first.getSuppressed().length == 1, "cleanup is retained once");
        check(first.getSuppressed()[0] == laterCleanup, "cleanup Error identity is retained");
        groups++;
    }

    @org.junit.Test
    public void syntheticPartialAcquisitionContracts() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        checks.set(0);
        groups = 0;
        partialFactoryRetainsOriginalOwner();
        preparedButUnusedTokenAllowsHealthyReadmission();
        failedFamilyDoesNotBlockIndependentFamily();
        startupAndCloseKeepPrimaryAndDiagnosticOrder();
        System.out.println(
                "TwoLanePartialAcquisitionControls: "
                        + groups
                        + " groups, "
                        + checks.get()
                        + " assertions");
    }
}
