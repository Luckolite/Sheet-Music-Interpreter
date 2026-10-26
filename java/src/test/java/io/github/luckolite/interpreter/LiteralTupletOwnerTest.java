// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original procedural engravings only. */
public class LiteralTupletOwnerTest {
    static final int W = InStaffPrimaryBeamContextTest.W, H = InStaffPrimaryBeamContextTest.H;

    static byte[] faint() throws Exception {
        byte[] g = InStaffPrimaryBeamContextTest.ink(true, true, 3);
        for (int cx : new int[] {180, 245, 310})
            for (int y = 120; y < 166; y++)
                for (int x = cx - 11; x <= cx - 9; x++)
                    if ((g[y * W + x] & 255) < 165 && y != 140 && y != 156)
                        g[y * W + x] = (byte) 180;
        return g;
    }

    static PrintedTupletBeamOwner.LiteralProof proof(
            byte[] g, List<ScoreNoteEvent> n, PrintedTupletBeamOwner.LiteralWork w) {
        return PrintedTupletBeamOwner.LiteralProof.create(
                g, W, H, n, n.size(), InStaffPrimaryBeamContextTest.BARS, 0, 1, 2, 16, w);
    }

    static float distance(PrintedTupletBeamOwner.LiteralProof p) {
        return p == null ? Float.POSITIVE_INFINITY : p.distance(229, 187, 244, 209);
    }

    @Test
    public void connectedFaintShaftsAreNotAThresholdReplacement() throws Exception {
        var g = faint();
        var before = g.clone();
        var n = InStaffPrimaryBeamContextTest.notes();
        assertFalse(PrintedTupletBeamOwner.connectedHeads(g, W, H, 180, 110, 245, 110, 16, -1));
        var work = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        var p = proof(g, n, work);
        assertNotNull(p);
        assertTrue(Float.isFinite(distance(p)));
        assertFalse(work.exhausted);
        assertArrayEquals(before, g);
    }

    @Test
    public void whiteGapCannotBeFilled() throws Exception {
        var g = faint();
        for (int y = 130; y <= 132; y++) for (int x = 168; x <= 172; x++) g[y * W + x] = (byte) 255;
        assertFalse(
                Float.isFinite(
                        distance(
                                proof(
                                        g,
                                        InStaffPrimaryBeamContextTest.notes(),
                                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)))));
    }

    @Test
    public void detachedHeadCannotSupplyAuthority() throws Exception {
        var g = faint();
        for (int y = 115; y <= 122; y++) for (int x = 160; x <= 190; x++) g[y * W + x] = (byte) 255;
        assertFalse(
                Float.isFinite(
                        distance(
                                proof(
                                        g,
                                        InStaffPrimaryBeamContextTest.notes(),
                                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)))));
    }

    @Test
    public void extraCurrentOccurrenceIsNotMergedByValue() throws Exception {
        var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.add(n.get(1));
        assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
    }

    @Test
    public void rivalCensusIncludesHeadBodiesAndTheNumeralWindow() throws Exception {
        for (float[] point : new float[][] {{156, 110}, {245, 238}}) {
            var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
            n.add(
                    new ScoreNoteEvent(
                                    0, point[0] / W, 12, 0, 1, point[1] / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(-1));
            assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        }
    }

    @Test
    public void wrongDirectionAndGraceRefuse() throws Exception {
        var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.set(1, n.get(1).withStemDirection(1));
        assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.set(1, n.get(1).withArticulations(NoteOrnament.GRACE));
        assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
    }

    @Test
    public void crossStaffAndVirtualRestOwnersRefuse() throws Exception {
        var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.set(1, n.get(1).withCrossStaffBeam());
        assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.set(1, n.get(1).withKind(ScoreNoteEvent.Kind.UNPITCHED));
        assertNull(proof(faint(), n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
    }

    @Test
    public void paperRailAndLongerBeamCannotOwnACompleteGroup() throws Exception {
        var g = faint();
        for (int y = 120; y < 166; y++) for (int x = 150; x <= 330; x++) g[y * W + x] = (byte) 225;
        assertFalse(
                Float.isFinite(
                        distance(
                                proof(
                                        g,
                                        InStaffPrimaryBeamContextTest.notes(),
                                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)))));
        g = faint();
        InStaffPrimaryBeamContextTest.line(g, 150, 170, 330, 170, 5);
        assertFalse(
                Float.isFinite(
                        distance(
                                proof(
                                        g,
                                        InStaffPrimaryBeamContextTest.notes(),
                                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)))));
    }

    @Test
    public void exactAndOneUnderPrechargesRemainAtomic() throws Exception {
        var g = faint();
        var n = InStaffPrimaryBeamContextTest.notes();
        var full = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        assertTrue(Float.isFinite(distance(proof(g, n, full))));
        var exact = new PrintedTupletBeamOwner.LiteralWork(full.used);
        assertTrue(Float.isFinite(distance(proof(g, n, exact))));
        assertEquals(full.used, exact.used);
        var under = new PrintedTupletBeamOwner.LiteralWork(full.used - 1);
        assertFalse(Float.isFinite(distance(proof(g, n, under))));
        assertTrue(under.exhausted);
        assertTrue(under.used <= under.limit);
        var zero = new PrintedTupletBeamOwner.LiteralWork(0);
        assertNull(proof(g, n, zero));
        assertEquals(0, zero.used);
    }

    @Test
    public void changedRawShapeAndUnboundedWorkRefuse() throws Exception {
        var n = InStaffPrimaryBeamContextTest.notes();
        assertNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        faint(),
                        W - 1,
                        H,
                        n,
                        3,
                        InStaffPrimaryBeamContextTest.BARS,
                        0,
                        1,
                        2,
                        16,
                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrintedTupletBeamOwner.LiteralWork(8_000_001));
    }

    @Test
    public void independentWindowHasExactAndOneUnderControls() throws Exception {
        var g = FaintTupletCallerTest.oneLevel(3);
        var n = InStaffPrimaryBeamContextTest.notes();
        var work = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        var p = proof(g, n, work);
        assertTrue(Float.isFinite(distance(p)));
        assertNotNull(p.numeralWindow(229, 187, 243, 209));
        long used = work.used;
        var exact = new PrintedTupletBeamOwner.LiteralWork(used);
        p = proof(g, n, exact);
        assertTrue(Float.isFinite(distance(p)));
        assertNotNull(p.numeralWindow(229, 187, 243, 209));
        assertEquals(used, exact.used);
        var under = new PrintedTupletBeamOwner.LiteralWork(used - 1);
        p = proof(g, n, under);
        assertTrue(Float.isFinite(distance(p)));
        assertNull(p.numeralWindow(229, 187, 243, 209));
        assertTrue(under.exhausted);
        assertTrue(under.used <= under.limit);
    }

    @Test
    public void invalidWindowAndUnsupportedFutureGapRefuse() throws Exception {
        var g = FaintTupletCallerTest.oneLevel(3);
        var n = InStaffPrimaryBeamContextTest.notes();
        var p = proof(g, n, new PrintedTupletBeamOwner.LiteralWork(8_000_000));
        assertNull(p.numeralWindow(-1, 187, 243, 209));
        assertNull(p.numeralWindow(229, 187, 243, 1000));
        assertNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        g,
                        W,
                        H,
                        n,
                        3,
                        InStaffPrimaryBeamContextTest.BARS,
                        0,
                        1,
                        2,
                        65,
                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
    }

    @Test
    public void independentWindowRetainsClippedContextAndRefusesAnotherClosedGlyph()
            throws Exception {
        var g = FaintTupletCallerTest.oneLevel(3);
        g[180 * W + 221] = 0;
        var p =
                proof(
                        g,
                        InStaffPrimaryBeamContextTest.notes(),
                        new PrintedTupletBeamOwner.LiteralWork(8_000_000));
        var window = p.numeralWindow(229, 187, 243, 209);
        assertNotNull(window);
        assertEquals(0, window.pixels()[window.width()]);
        g = FaintTupletCallerTest.oneLevel(3);
        g[182 * W + 247] = 0;
        p =
                proof(
                        g,
                        InStaffPrimaryBeamContextTest.notes(),
                        new PrintedTupletBeamOwner.LiteralWork(8_000_000));
        assertNull(p.numeralWindow(229, 187, 243, 209));
    }

    @Test
    public void typedFullMeasureAndLiteralRestRivalsRemainVisible() throws Exception {
        var notes = InStaffPrimaryBeamContextTest.notes();
        var g = faint();
        for (var rest :
                List.of(
                        ScoreRestEvent.fullMeasure(0, .95f, .95f, .03f, 0, 1),
                        new ScoreRestEvent(0, 245f / W, 110f / H, .03f, 0, 1, .5)))
            assertNull(
                    PrintedTupletBeamOwner.LiteralProof.create(
                            g,
                            W,
                            H,
                            notes,
                            3,
                            InStaffPrimaryBeamContextTest.BARS,
                            0,
                            1,
                            2,
                            16,
                            new PrintedTupletBeamOwner.LiteralWork(8_000_000),
                            List.of(rest)));
        var full = ScoreRestEvent.fullMeasure(0, .95f, .95f, .03f, 0, 1);
        var result =
                TripletRhythmDetector.withRests(
                        notes, List.of(full), InStaffPrimaryBeamContextTest.BARS, g, W, H);
        assertEquals(notes, result.notes());
        assertEquals(List.of(full), result.rests());
        result =
                TripletRhythmDetector.withRests(
                        notes,
                        List.of(full),
                        InStaffPrimaryBeamContextTest.BARS,
                        InStaffPrimaryBeamContextTest.ink(true, true, 3),
                        W,
                        H);
        for (var note : result.notes()) assertEquals(3, note.tupletDivisor());
    }

    @Test
    public void actualRestCensusHasAnAtomicPrechargeAndPreservesUnrelatedRows() throws Exception {
        var notes = InStaffPrimaryBeamContextTest.notes();
        var g = faint();
        var outside = new ScoreRestEvent(0, .95f, .95f, .03f, 0, 1, .5);
        var rests = List.of(outside);
        var work = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        assertNotNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        g,
                        W,
                        H,
                        notes,
                        3,
                        InStaffPrimaryBeamContextTest.BARS,
                        0,
                        1,
                        2,
                        16,
                        work,
                        rests));
        var exact = new PrintedTupletBeamOwner.LiteralWork(work.used);
        assertNotNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        g,
                        W,
                        H,
                        notes,
                        3,
                        InStaffPrimaryBeamContextTest.BARS,
                        0,
                        1,
                        2,
                        16,
                        exact,
                        rests));
        var under = new PrintedTupletBeamOwner.LiteralWork(work.used - 1);
        assertNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        g,
                        W,
                        H,
                        notes,
                        3,
                        InStaffPrimaryBeamContextTest.BARS,
                        0,
                        1,
                        2,
                        16,
                        under,
                        rests));
        assertTrue(under.exhausted);
        assertTrue(under.used <= under.limit);
        var result =
                TripletRhythmDetector.withRests(
                        notes, rests, InStaffPrimaryBeamContextTest.BARS, g, W, H);
        for (var note : result.notes()) assertEquals(3, note.tupletDivisor());
        assertEquals(rests, result.rests());
    }

    @Test
    public void nullRowsAndInvalidStaffDeclarationsCannotCreateAContext() throws Exception {
        var g = faint();
        for (int index : new int[] {0, 1, 3}) {
            var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
            if (index == 3) n.add(null);
            else n.set(index, null);
            assertNull(proof(g, n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        }
        var n = new ArrayList<>(InStaffPrimaryBeamContextTest.notes());
        n.set(
                0,
                new ScoreNoteEvent(0, .3f, 12, 1, 1, 110f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(-1));
        assertNull(proof(g, n, new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
        assertNull(
                PrintedTupletBeamOwner.LiteralProof.create(
                        g,
                        W,
                        H,
                        InStaffPrimaryBeamContextTest.notes(),
                        3,
                        Arrays.asList((MeasureRegion) null),
                        0,
                        1,
                        2,
                        16,
                        new PrintedTupletBeamOwner.LiteralWork(8_000_000)));
    }

    @Test
    public void oldSuccessAndFailureHavePriorityEvenWhenNewPoolRefuses() throws Exception {
        var method =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "apply",
                        List.class,
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        PrintedTupletBeamOwner.LiteralWork.class,
                        List.class);
        method.setAccessible(true);
        var notes = InStaffPrimaryBeamContextTest.notes();
        for (byte[] g :
                List.of(
                        InStaffPrimaryBeamContextTest.ink(true, true, 3),
                        FaintTupletCallerTest.oneLevel(3))) {
            Object old =
                    method.invoke(
                            null,
                            notes,
                            InStaffPrimaryBeamContextTest.BARS,
                            g,
                            W,
                            H,
                            notes.size(),
                            null,
                            List.of());
            Object refused =
                    method.invoke(
                            null,
                            notes,
                            InStaffPrimaryBeamContextTest.BARS,
                            g,
                            W,
                            H,
                            notes.size(),
                            new PrintedTupletBeamOwner.LiteralWork(0),
                            List.of());
            assertEquals(old, refused);
        }
    }

    @Test
    public void pendingStorageHeaderIsPrepaidAtomically() throws Exception {
        var g = FaintTupletCallerTest.oneLevel(3);
        var notes = InStaffPrimaryBeamContextTest.notes();
        var base = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        assertNotNull(proof(g, notes, base));
        var exact = new PrintedTupletBeamOwner.LiteralWork(base.used + 64);
        var p = proof(g, notes, exact);
        assertTrue(p.reserve(64));
        assertEquals(exact.limit, exact.used);
        var under = new PrintedTupletBeamOwner.LiteralWork(base.used + 63);
        p = proof(g, notes, under);
        assertFalse(p.reserve(64));
        assertEquals(base.used, under.used);
        assertTrue(under.exhausted);
    }

    static Object fallback(PrintedTupletBeamOwner.LiteralProof proof) throws Exception {
        Class<?> glyph = Class.forName("io.github.luckolite.interpreter.TripletRhythmDetector$Glyph");
        var constructor = glyph.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        Object pending = java.lang.reflect.Array.newInstance(glyph, 1);
        java.lang.reflect.Array.set(pending, 0, constructor.newInstance(229, 187, 243, 209));
        var method =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "literalFallback",
                        PrintedTupletBeamOwner.LiteralProof.class,
                        pending.getClass(),
                        int[].class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class);
        method.setAccessible(true);
        return method.invoke(
                null,
                proof,
                pending,
                new int[] {210},
                1,
                180f,
                310f,
                110f,
                110f,
                16f,
                true,
                Float.NaN,
                Float.NaN,
                3,
                false);
    }

    @Test
    public void wholeDeferredFallbackHasAnExactAndOneUnderBoundary() throws Exception {
        var g = FaintTupletCallerTest.oneLevel(3);
        var notes = InStaffPrimaryBeamContextTest.notes();
        var full = new PrintedTupletBeamOwner.LiteralWork(8_000_000);
        assertNotNull(fallback(proof(g, notes, full)));
        var exact = new PrintedTupletBeamOwner.LiteralWork(full.used);
        assertNotNull(fallback(proof(g, notes, exact)));
        assertEquals(full.used, exact.used);
        var under = new PrintedTupletBeamOwner.LiteralWork(full.used - 1);
        assertNull(fallback(proof(g, notes, under)));
        assertTrue(under.exhausted);
        assertTrue(under.used <= under.limit);
    }

    @Test
    public void fullLegacyCallerGetsPhysicalRatioWithoutRawMutation() throws Exception {
        var g = faint();
        var before = g.clone();
        var result =
                TripletRhythmDetector.apply(
                        InStaffPrimaryBeamContextTest.notes(),
                        InStaffPrimaryBeamContextTest.BARS,
                        g,
                        W,
                        H);
        for (var n : result) assertEquals(3, n.tupletDivisor());
        assertArrayEquals(before, g);
    }
}
