// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original source/ownership/resource controls for the pure optional proof only. */
public class ClosedCounterRestInkTest {
    static final int W = 400, H = 240;

    static byte[] bowl() {
        return PartialBowlRestOriginalTest.bowl(true);
    }

    static List<ScoreNoteEvent> notes(byte[] r) {
        var n =
                new ScoreNoteEvent(
                        0,
                        .8f,
                        0,
                        0,
                        1,
                        44f / H,
                        true,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_NATURAL,
                        2f);
        var runs = new ArrayList<int[]>();
        for (int y = 40; y <= 48; y++)
            for (int x = 313; x <= 327; x++) {
                double q = (x - 320) * (x - 320) / 49.0 + (y - 44) * (y - 44) / 16.0;
                if (.4 <= q && q <= 1) runs.add(new int[] {y, x, x});
            }
        var o = RestFigureOrigins.HeadOrigin.accepted(new Object(), r, W, H, runs);
        assertNotNull(o);
        return RestFigureOrigins.bind(List.of(n), Map.of(n, o));
    }

    static RestFigureOrigins.ProposalOrigin proposal(
            byte[] scan, byte[] view, int l, int r, int t, int b) {
        return new RestFigureOrigins.ProposalOrigin(
                scan, view, W, H, 80, 140, new boolean[61], l, r, t, b);
    }

    static boolean proof(byte[] r, List<ScoreNoteEvent> n, ClosedCounterRestInk.Budget b) {
        var f = RestFigureOrigins.originalFrame(n, r, W, H, b);
        return ClosedCounterRestInk.owns(
                f, n, r, W, H, proposal(r, r, 140, 147, 90, 110), 140, 147, 90, 110, 10, b);
    }

    @Test
    public void completeOriginalPartialBowlHasNonRestCertificate() throws Exception {
        var r = bowl();
        assertTrue(PartialBowlRestOriginalTest.inspect(r, 140, 147).isEmpty());
        assertTrue(proof(r, notes(r), new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
    }

    @Test
    public void exactReservationAndOneUnderRefuseAtomically() {
        var r = bowl();
        var n = notes(r);
        var big = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        assertTrue(proof(r, n, big));
        var exact = new ClosedCounterRestInk.Budget(big.workUsed, big.bytesUsed);
        assertTrue(proof(r, n, exact));
        assertEquals(big.workUsed, exact.workUsed);
        assertEquals(big.bytesUsed, exact.bytesUsed);
        for (int axis = 0; axis < 2; axis++) {
            var low =
                    new ClosedCounterRestInk.Budget(
                            big.workUsed - (axis == 0 ? 1 : 0),
                            big.bytesUsed - (axis == 1 ? 1 : 0));
            assertFalse(proof(r, n, low));
            assertTrue(low.failed);
            assertTrue(low.workUsed <= low.workLimit);
            assertTrue(low.bytesUsed <= low.byteLimit);
        }
    }

    @Test
    public void noDefaultFunding() {
        var r = bowl();
        assertNull(RestFigureOrigins.originalFrame(notes(r), r, W, H, null));
        assertFalse(
                ClosedCounterRestInk.owns(
                        null, notes(r), r, W, H, null, 140, 147, 90, 110, 10, null));
    }

    @Test
    public void missingAndClonedOriginsRefuse() {
        var r = bowl();
        var n = notes(r);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        assertNull(RestFigureOrigins.originalFrame(List.copyOf(n), r, W, H, b));
        assertNull(RestFigureOrigins.originalFrame(n, r.clone(), W, H, b));
    }

    @Test
    public void registeredPhotometricViewResolvesOnlyOriginalPlane() {
        var raw = bowl();
        var original = notes(raw);
        var view = raw.clone();
        var n = RestFigureOrigins.photometricView(original, raw, view, W, H);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var f = RestFigureOrigins.originalFrame(n, view, W, H, b);
        assertNotNull(f);
        assertSame(raw, f.source);
        assertTrue(
                ClosedCounterRestInk.owns(
                        f,
                        n,
                        view,
                        W,
                        H,
                        proposal(view, view, 140, 147, 90, 110),
                        140,
                        147,
                        90,
                        110,
                        10,
                        b));
    }

    @Test
    public void conflictingProducerOriginalsRefuse() {
        var a = bowl();
        var b = a.clone();
        var view = a.clone();
        var na = notes(a);
        var nb = notes(b);
        var va = RestFigureOrigins.photometricView(na, a, view, W, H);
        var vb = RestFigureOrigins.photometricView(nb, b, view, W, H);
        var n1 = va.get(0);
        var n2 = vb.get(0);
        var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
        map.put(n1, RestFigureOrigins.origin(va, n1, view, W, H));
        map.put(n2, RestFigureOrigins.origin(vb, n2, view, W, H));
        var mixed = RestFigureOrigins.bind(List.of(n1, n2), map);
        assertNull(
                RestFigureOrigins.originalFrame(
                        mixed, view, W, H, new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
    }

    @Test
    public void exactRegisteredRectifierMappingIsFunded() {
        var raw = bowl();
        var n = notes(raw);
        var flat = raw.clone();
        var fnotes =
                RestFigureOrigins.rectified(
                        n, List.copyOf(n), List.copyOf(n), raw, W, H, flat, H, new float[W], 0, 0);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var f = RestFigureOrigins.originalFrame(fnotes, flat, W, H, b);
        assertNotNull(f);
        assertSame(flat, f.source);
        assertTrue(
                ClosedCounterRestInk.owns(
                        f,
                        fnotes,
                        flat,
                        W,
                        H,
                        proposal(flat, flat, 140, 147, 90, 110),
                        140,
                        147,
                        90,
                        110,
                        10,
                        b));
        assertTrue(b.workUsed > 0);
    }

    @Test
    public void unregisteredClassificationCopyCannotOwnSource() {
        var raw = bowl();
        var n = notes(raw);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var f = RestFigureOrigins.originalFrame(n, raw, W, H, b);
        assertFalse(
                ClosedCounterRestInk.owns(
                        f,
                        n,
                        raw,
                        W,
                        H,
                        proposal(raw.clone(), raw, 140, 147, 90, 110),
                        140,
                        147,
                        90,
                        110,
                        10,
                        b));
    }

    @Test
    public void genuineQuaverDoesNotBecomeNonRest() {
        var r = bowl();
        for (int y = 85; y < 115; y++) for (int x = 135; x < 155; x++) r[y * W + x] = (byte) 255;
        for (int y = 89; y <= 95; y++)
            for (int x = 139; x <= 147; x++)
                if ((x - 143) * (x - 143) / 16.0 + (y - 92) * (y - 92) / 9.0 <= 1) r[y * W + x] = 0;
        for (int y = 90; y <= 110; y++) {
            int x = 148 - (y - 90) * 8 / 20;
            r[y * W + x] = 0;
            r[y * W + x + 1] = 0;
        }
        var n = notes(r);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var f = RestFigureOrigins.originalFrame(n, r, W, H, b);
        assertFalse(
                ClosedCounterRestInk.owns(
                        f,
                        n,
                        r,
                        W,
                        H,
                        proposal(r, r, 139, 149, 89, 110),
                        139,
                        149,
                        89,
                        110,
                        10,
                        b));
    }

    @Test
    public void brokenCounterAndForeignProposalInkRefuse() {
        var r = bowl();
        for (int y = 92; y <= 93; y++) for (int x = 145; x <= 147; x++) r[y * W + x] = (byte) 255;
        assertFalse(proof(r, notes(r), new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
        r = bowl();
        r[108 * W + 147] = 0;
        assertFalse(proof(r, notes(r), new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
    }

    @Test
    public void knownHeadOriginAndLedgerPreserveAmbiguity() {
        var r = bowl();
        var held = notes(r);
        var head =
                new ScoreNoteEvent(
                        0,
                        .36f,
                        0,
                        0,
                        1,
                        93f / H,
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_NATURAL,
                        2f);
        var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
        map.put(held.get(0), RestFigureOrigins.origin(held, held.get(0), r, W, H));
        map.put(
                head,
                RestFigureOrigins.HeadOrigin.accepted(
                        new Object(), r, W, H, List.of(new int[] {91, 140, 146})));
        var n = RestFigureOrigins.bind(List.of(held.get(0), head), map);
        assertFalse(proof(r, n, new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
        r = bowl();
        for (int x = 120; x <= 170; x++) r[100 * W + x] = 0;
        assertFalse(proof(r, notes(r), new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
    }

    @Test
    public void sourceAndNotesArePreserved() {
        var r = bowl();
        var before = r.clone();
        var n = notes(r);
        var copy = List.copyOf(n);
        assertTrue(proof(r, n, new ClosedCounterRestInk.Budget(10_000_000, 1_000_000)));
        assertArrayEquals(before, r);
        assertEquals(copy, n);
    }

    @Test
    public void wrongThreadAndDimensionsRefuse() throws Exception {
        var r = bowl();
        var n = notes(r);
        var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var f = RestFigureOrigins.originalFrame(n, r, W, H, b);
        boolean[] result = {true};
        Thread thread =
                new Thread(
                        () ->
                                result[0] =
                                        ClosedCounterRestInk.owns(
                                                f,
                                                n,
                                                r,
                                                W,
                                                H,
                                                proposal(r, r, 140, 147, 90, 110),
                                                140,
                                                147,
                                                90,
                                                110,
                                                10,
                                                new ClosedCounterRestInk.Budget(
                                                        10_000_000, 1_000_000)));
        thread.start();
        thread.join();
        assertFalse(result[0]);
        assertFalse(
                ClosedCounterRestInk.owns(
                        f,
                        n,
                        r,
                        H,
                        W,
                        proposal(r, r, 140, 147, 90, 110),
                        140,
                        147,
                        90,
                        110,
                        10,
                        b));
    }

    @Test
    public void invalidGeometryAndOverflowRefuseWithoutAllocation() {
        var r = bowl();
        var n = notes(r);
        for (float gap : new float[] {0, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
            var b = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
            var f = RestFigureOrigins.originalFrame(n, r, W, H, b);
            assertFalse(
                    ClosedCounterRestInk.owns(
                            f,
                            n,
                            r,
                            W,
                            H,
                            proposal(r, r, 140, 147, 90, 110),
                            140,
                            147,
                            90,
                            110,
                            gap,
                            b));
            assertEquals(128, b.bytesUsed);
        }
        var b = new ClosedCounterRestInk.Budget(Long.MAX_VALUE, Long.MAX_VALUE);
        b.take(Long.MAX_VALUE, Long.MAX_VALUE);
        try {
            b.take(1, 1);
            fail();
        } catch (ClosedCounterRestInk.Refusal expected) {
        }
        assertEquals(Long.MAX_VALUE, b.workUsed);
        assertEquals(Long.MAX_VALUE, b.bytesUsed);
    }

    @Test
    public void unrelatedParentFailureIsNotSwallowed() {
        var failure = new RuntimeException("parent");
        try {
            ClosedCounterRestInk.owns(
                    null,
                    List.of(),
                    new byte[0],
                    0,
                    0,
                    null,
                    0,
                    0,
                    0,
                    0,
                    10,
                    (w, b) -> {
                        throw failure;
                    });
            fail();
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }
        var arithmetic = new ArithmeticException("parent arithmetic");
        try {
            ClosedCounterRestInk.owns(
                    null,
                    List.of(),
                    new byte[0],
                    0,
                    0,
                    null,
                    0,
                    0,
                    0,
                    0,
                    10,
                    (w, b) -> {
                        throw arithmetic;
                    });
            fail();
        } catch (ArithmeticException actual) {
            assertSame(arithmetic, actual);
        }
    }
}
