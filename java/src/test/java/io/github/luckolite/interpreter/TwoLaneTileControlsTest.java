// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Original synthetic owner/operation controls; no Android, LiteRT, tensors or model data. */
public final class TwoLaneTileControlsTest {
    private static int groups;

    private static final class Owner implements AutoCloseable {
        final int lane;
        final Thread createdOn = Thread.currentThread();
        final float[] input = new float[8];
        final AtomicInteger active = new AtomicInteger(), closes = new AtomicInteger();
        Throwable closeFailure;
        Thread closedOn;

        Owner(int lane) {
            this.lane = lane;
        }

        @Override
        public void close() throws Exception {
            check(active.get() == 0, "Retired while operation was active");
            closedOn = Thread.currentThread();
            check(closedOn == createdOn, "Retirement changed native owner thread");
            check(closes.incrementAndGet() == 1, "Double close");
            TwoLaneTileExecutor.rethrow(closeFailure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void waitFor(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "Control latch timed out");
    }

    private static void joined(Thread thread) throws Exception {
        thread.join(5000);
        check(!thread.isAlive(), "Control thread did not actually exit");
    }

    private static void passed(String name) {
        groups++;
        System.out.println("PASS " + name);
    }

    private static void expectSame(Throwable expected, RunnableThrows work) throws Exception {
        Throwable actual = null;
        try {
            work.run();
        } catch (Exception | Error failure) {
            actual = failure;
        }
        check(actual == expected, "Changed primary Throwable identity");
    }

    private interface RunnableThrows {
        void run() throws Exception;
    }

    private static Owner[] owners(TwoLaneTileExecutor<Owner> engine) {
        return new Owner[] {engine.idleOwner(0), engine.idleOwner(1)};
    }

    private static void retired(Owner[] owners) {
        for (Owner owner : owners) {
            check(owner.closes.get() == 1, "Missing retirement");
            check(!owner.createdOn.isAlive(), "Owner ThreadFactory finally was not joined");
        }
    }

    public static void main(String[] args) throws Exception {
        List<Integer> opening = Collections.synchronizedList(new ArrayList<>());
        var engine =
                new TwoLaneTileExecutor<Owner>(
                        lane -> {
                            opening.add(lane);
                            return new Owner(lane);
                        });
        Owner[] first = owners(engine);
        check(
                opening.size() == 2 && opening.contains(0) && opening.contains(1),
                "Factories must each open exactly once; parallel order may vary");
        check(
                first[0] != first[1] && first[0].input != first[1].input,
                "Shared native/input owner");
        check(first[0].createdOn != first[1].createdOn, "Shared native thread");
        engine.close();
        retired(first);
        passed("separate owners, parallel factories and actual retirement");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] parallel = owners(engine);
        CountDownLatch entered = new CountDownLatch(2),
                releaseFirst = new CountDownLatch(1),
                secondReturned = new CountDownLatch(1);
        var early =
                engine.submit(
                        0,
                        owner -> {
                            owner.active.incrementAndGet();
                            entered.countDown();
                            try {
                                waitFor(releaseFirst);
                                return 10;
                            } finally {
                                owner.active.decrementAndGet();
                            }
                        });
        var late =
                engine.submit(
                        1,
                        owner -> {
                            owner.active.incrementAndGet();
                            entered.countDown();
                            try {
                                secondReturned.countDown();
                                return 11;
                            } finally {
                                owner.active.decrementAndGet();
                            }
                        });
        try {
            waitFor(entered);
            waitFor(secondReturned);
            check(parallel[0].active.get() == 1, "No actual operation overlap");
            List<Integer> consumed = new ArrayList<>();
            releaseFirst.countDown();
            consumed.add(early.await());
            consumed.add(late.await());
            check(consumed.equals(List.of(10, 11)), "Out-of-order consumer output");
        } finally {
            releaseFirst.countDown();
            engine.close();
        }
        retired(parallel);
        passed("real overlap with ordered consumption after later return");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] bounded = owners(engine);
        CountDownLatch busyRelease = new CountDownLatch(1), busyEntered = new CountDownLatch(1);
        var busy =
                engine.submit(
                        0,
                        owner -> {
                            busyEntered.countDown();
                            waitFor(busyRelease);
                            return 1;
                        });
        try {
            waitFor(busyEntered);
            Throwable ownerFailure = null, submitFailure = null;
            try {
                engine.idleOwner(0);
            } catch (IllegalStateException expected) {
                ownerFailure = expected;
            }
            try {
                engine.submit(0, owner -> 2);
            } catch (IllegalStateException expected) {
                submitFailure = expected;
            }
            check(ownerFailure != null && submitFailure != null, "Unbounded same-lane lookahead");
            busyRelease.countDown();
            check(busy.await() == 1, "Wrong busy result");
            check(
                    engine.submit(0, owner -> 2).await() == 2,
                    "Lane unavailable after actual return");
        } finally {
            busyRelease.countDown();
            engine.close();
        }
        retired(bounded);
        passed("one outstanding request per lane and safe reuse");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] activeOwners = owners(engine);
        CountDownLatch activeEntered = new CountDownLatch(1),
                nativeReturn = new CountDownLatch(1),
                closeStarted = new CountDownLatch(1);
        engine.submit(
                0,
                owner -> {
                    owner.active.incrementAndGet();
                    activeEntered.countDown();
                    try {
                        waitFor(nativeReturn);
                        return 1;
                    } finally {
                        owner.active.decrementAndGet();
                    }
                });
        var closingEngine = engine;
        AtomicReference<Throwable> closeProblem = new AtomicReference<>();
        Thread closer =
                new Thread(
                        () -> {
                            closeStarted.countDown();
                            try {
                                closingEngine.close();
                            } catch (Throwable failure) {
                                closeProblem.set(failure);
                            }
                        });
        try {
            waitFor(activeEntered);
            closer.start();
            waitFor(closeStarted);
            check(
                    activeOwners[0].closes.get() == 0,
                    "Future cancellation masqueraded as native completion");
            nativeReturn.countDown();
            joined(closer);
            check(closeProblem.get() == null, "Pending operation close failed");
        } finally {
            nativeReturn.countDown();
            if (closer.isAlive()) joined(closer);
            closingEngine.close();
        }
        retired(activeOwners);
        passed("close waits actual operation exit before same-thread disposal");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Error operationError = new AssertionError("original native-shaped error");
        var failing =
                engine.submit(
                        0,
                        owner -> {
                            throw operationError;
                        });
        expectSame(operationError, failing::await);
        check(engine.submit(0, owner -> 3).await() == 3, "Operation error killed reusable worker");
        engine.close();
        passed("original Error identity and fresh request after failed operation");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] cleanupOwners = owners(engine);
        Exception cleanup0 = new Exception("close0");
        Error cleanup1 = new AssertionError("close1");
        cleanupOwners[0].closeFailure = cleanup0;
        cleanupOwners[1].closeFailure = cleanup1;
        var failedCleanupEngine = engine;
        expectSame(cleanup0, failedCleanupEngine::close);
        check(
                cleanup0.getSuppressed().length == 1 && cleanup0.getSuppressed()[0] == cleanup1,
                "Changed close failure order");
        retired(cleanupOwners);
        expectSame(cleanup0, failedCleanupEngine::close);
        passed("all retirement failures preserved, both exits joined, no double close");

        for (Throwable factoryFailure :
                List.of(new Exception("factory1"), new AssertionError("factory1Error"))) {
            AtomicReference<Owner> opened = new AtomicReference<>();
            expectSame(
                    factoryFailure,
                    () ->
                            new TwoLaneTileExecutor<Owner>(
                                    lane -> {
                                        if (lane == 1) {
                                            TwoLaneTileExecutor.rethrow(factoryFailure);
                                        }
                                        Owner owner = new Owner(lane);
                                        opened.set(owner);
                                        return owner;
                                    }));
            retired(new Owner[] {opened.get()});
        }
        Exception firstFactoryFailure = new Exception("factory0");
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicReference<Owner> openedOther = new AtomicReference<>();
        expectSame(
                firstFactoryFailure,
                () ->
                        new TwoLaneTileExecutor<Owner>(
                                lane -> {
                                    factoryCalls.incrementAndGet();
                                    if (lane == 0) {
                                        TwoLaneTileExecutor.rethrow(firstFactoryFailure);
                                    }
                                    Owner owner = new Owner(lane);
                                    openedOther.set(owner);
                                    return owner;
                                }));
        check(
                factoryCalls.get() == 2 && openedOther.get() != null && openedOther.get().lane == 1,
                "Primary acquisition failure must still observe the successful secondary");
        retired(new Owner[] {openedOther.get()});
        passed(
                "Exception/Error parallel factory failure preserves primary and retires successful other owner");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        CountDownLatch waitingEntered = new CountDownLatch(1),
                waitingRelease = new CountDownLatch(1),
                waiterStarted = new CountDownLatch(1);
        var held =
                engine.submit(
                        0,
                        owner -> {
                            waitingEntered.countDown();
                            waitFor(waitingRelease);
                            return 41;
                        });
        AtomicBoolean interruptedResult = new AtomicBoolean();
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter =
                new Thread(
                        () -> {
                            Thread.currentThread().interrupt();
                            waiterStarted.countDown();
                            try {
                                check(held.await() == 41, "Interrupted wait changed output");
                                interruptedResult.set(Thread.currentThread().isInterrupted());
                            } catch (Throwable failure) {
                                waiterFailure.set(failure);
                            }
                        });
        try {
            waitFor(waitingEntered);
            waiter.start();
            waitFor(waiterStarted);
            waiter.interrupt();
            waitingRelease.countDown();
            joined(waiter);
        } finally {
            waitingRelease.countDown();
            engine.close();
        }
        check(
                waiterFailure.get() == null && interruptedResult.get(),
                "Incoming/during-wait interrupt was lost");
        passed("incoming and newly delivered interruption retained after actual completion");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] interruptOwners = owners(engine);
        var interruptClosingEngine = engine;
        AtomicBoolean closeInterrupt = new AtomicBoolean();
        AtomicReference<Throwable> interruptCloseProblem = new AtomicReference<>();
        Thread interruptCloser =
                new Thread(
                        () -> {
                            Thread.currentThread().interrupt();
                            try {
                                interruptClosingEngine.close();
                                closeInterrupt.set(Thread.currentThread().isInterrupted());
                            } catch (Throwable failure) {
                                interruptCloseProblem.set(failure);
                            }
                        });
        interruptCloser.start();
        joined(interruptCloser);
        check(
                interruptCloseProblem.get() == null && closeInterrupt.get(),
                "Close lost interrupt or skipped joins");
        retired(interruptOwners);
        passed("interrupted retirement joins both real owners and restores flag");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        var selfEngine = engine;
        Throwable selfClose = null;
        try {
            engine.submit(
                            0,
                            owner -> {
                                selfEngine.close();
                                return 1;
                            })
                    .await();
        } catch (IllegalStateException expected) {
            selfClose = expected;
        }
        check(selfClose != null, "Self retirement would deadlock");
        check(engine.submit(0, owner -> 9).await() == 9, "Self-close rejection damaged owner");
        engine.close();
        Throwable afterClose = null;
        try {
            engine.submit(1, owner -> 9);
        } catch (IllegalStateException expected) {
            afterClose = expected;
        }
        check(afterClose != null, "Submitted after closed");
        passed("self-close rejected and closed submission fails");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] retainedOwners = owners(engine);
        List<Integer> ordered = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            for (int tile = 0; tile < 4; tile += 2) {
                int a = page * 100 + tile, b = a + 1;
                check(
                        engine.idleOwner(0) == retainedOwners[0]
                                && engine.idleOwner(1) == retainedOwners[1],
                        "Changed retained owner");
                var ta =
                        engine.submit(
                                0,
                                owner -> {
                                    owner.input[0] = a;
                                    return a;
                                });
                var tb =
                        engine.submit(
                                1,
                                owner -> {
                                    owner.input[0] = b;
                                    return b;
                                });
                ordered.add(ta.await());
                ordered.add(tb.await());
            }
        }
        check(
                ordered.equals(List.of(0, 1, 2, 3, 100, 101, 102, 103, 200, 201, 202, 203)),
                "Page/order isolation failed");
        engine.close();
        retired(retainedOwners);
        passed("serial A-B-A pages retain independent lane/input owners");

        engine = new TwoLaneTileExecutor<>(Owner::new);
        Owner[] primaryOwners = owners(engine);
        Exception op0 = new Exception("op0"),
                op1 = new Exception("op1"),
                retire0 = new Exception("retire0"),
                retire1 = new Exception("retire1");
        primaryOwners[0].closeFailure = retire0;
        primaryOwners[1].closeFailure = retire1;
        engine.submit(
                0,
                owner -> {
                    throw op0;
                });
        engine.submit(
                1,
                owner -> {
                    throw op1;
                });
        var primaryEngine = engine;
        expectSame(op0, primaryEngine::close);
        check(
                List.of(op0.getSuppressed()).equals(List.of(op1, retire0, retire1)),
                "Operation/retirement suppression order changed");
        retired(primaryOwners);
        passed("deterministic original primary plus later operation and both cleanup failures");

        check(groups == 12, "Wrong completed group count");
        System.out.println("TERMINAL PASS groups=" + groups + " models=0 Android=0 SDK=0");
    }

    /** Runs the original 12-group synthetic owner contract. */
    @org.junit.Test
    public void originalOwnerContract() throws Exception {
        main(new String[0]);
    }
}
