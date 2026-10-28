// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original finite pen envelopes, independent long authority, paper and fringe companions. */
public final class CompleteOpposedHairpinPairTest {
    static final int W = 700, H = 320;
    static final float G = 14.25f;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(100, 157, G, 0, 1);

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final int[] shortBox, longBox;
        final PlayingTechniqueDetector.Staff staff;

        Fixture(boolean mirror, boolean compact) {
            Arrays.fill(raw, (byte) 255);
            staff = compact ? new PlayingTechniqueDetector.Staff(100, 156, 14, 0, 1) : STAFF;
            shortBox = new int[] {100, compact ? 141 : 174, 180, compact ? 197 : 198};
            longBox =
                    new int[] {compact ? 193 : 202, compact ? 397 : 334, compact ? 179 : 180, 198};
            wedge(raw, shortBox, mirror ? -1 : 1);
            wedge(raw, longBox, mirror ? 1 : -1);
        }

        List<CompleteOpposedHairpinPair.Shape> run() {
            return run(new CompleteOpposedHairpinPair.Work(2_000_000, 1_000_000), staff);
        }

        List<CompleteOpposedHairpinPair.Shape> run(
                CompleteOpposedHairpinPair.Work budget, PlayingTechniqueDetector.Staff neighbor) {
            var c = new CompleteOpposedHairpinPair.Collector(raw, W, H, budget);
            add(c, raw, staff, shortBox, false);
            add(c, raw, neighbor, longBox, true);
            return c.finish();
        }
    }

    static void line(byte[] raw, int x0, int y0, int x1, int y1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x0 + (x1 - x0) * i / (float) n);
            int y = Math.round(y0 + (y1 - y0) * i / (float) n);
            raw[y * W + x] = 0;
        }
    }

    static void wedge(byte[] raw, int[] b, int direction) {
        int mid = (b[2] + b[3]) / 2;
        if (direction > 0) {
            line(raw, b[0], mid, b[1], b[2]);
            line(raw, b[0], mid, b[1], b[3]);
        } else {
            line(raw, b[0], b[2], b[1], mid);
            line(raw, b[0], b[3], b[1], mid);
        }
    }

    static void add(
            CompleteOpposedHairpinPair.Collector c,
            byte[] raw,
            PlayingTechniqueDetector.Staff staff,
            int[] b,
            boolean acceptedLong) {
        int[] u = new int[b[1] - b[0] + 1], l = new int[u.length];
        Arrays.fill(u, Integer.MAX_VALUE);
        Arrays.fill(l, Integer.MIN_VALUE);
        int count = 0;
        for (int x = b[0]; x <= b[1]; x++)
            for (int y = b[2]; y <= b[3]; y++)
                if ((raw[y * W + x] & 255) < 145) {
                    u[x - b[0]] = Math.min(u[x - b[0]], y);
                    l[x - b[0]] = Math.max(l[x - b[0]], y);
                    count++;
                }
        if (acceptedLong) c.acceptedLong(staff, b[0], b[1], b[2], b[3], count, u, l);
        else c.candidate(staff, b[0], b[1], b[2], b[3], count, u, l);
    }

    @Test
    public void opposedPairAndMirrorHaveOneIndependentShort() {
        for (boolean mirror : new boolean[] {false, true}) {
            var f = new Fixture(mirror, false);
            assertEquals(1, f.run().size());
            assertEquals(mirror ? -1 : 1, f.run().get(0).direction());
        }
    }

    @Test
    public void completeCompactEnvelopeUsesPairRatherThanCoarseAspect() {
        var f = new Fixture(false, true);
        assertTrue(42 < 18 * 2.5);
        assertEquals(1, f.run().size());
    }

    @Test
    public void isolatedShortAndTwoShortTextOperatorsCannotBootstrap() {
        var f = new Fixture(false, false);
        var c = new CompleteOpposedHairpinPair.Collector(f.raw, W, H);
        add(c, f.raw, STAFF, f.shortBox, false);
        assertTrue(c.finish().isEmpty());
        int[] other = {202, 243, 180, 198};
        Arrays.fill(f.raw, (byte) 255);
        wedge(f.raw, f.shortBox, 1);
        wedge(f.raw, other, -1);
        c = new CompleteOpposedHairpinPair.Collector(f.raw, W, H);
        add(c, f.raw, STAFF, f.shortBox, false);
        add(c, f.raw, STAFF, other, true);
        assertTrue(c.finish().isEmpty());
    }

    @Test
    public void ordinarySmallAccentCannotAcquireTheLongNeighbor() {
        var f = new Fixture(false, false);
        int[] accent = {100, 116, 180, 188};
        Arrays.fill(f.raw, (byte) 255);
        wedge(f.raw, accent, 1);
        wedge(f.raw, f.longBox, -1);
        var c = new CompleteOpposedHairpinPair.Collector(f.raw, W, H);
        add(c, f.raw, STAFF, accent, false);
        add(c, f.raw, STAFF, f.longBox, true);
        assertTrue(c.finish().isEmpty());
    }

    @Test
    public void sameDirectionDoesNotSupplyOpposedRole() {
        var f = new Fixture(false, false);
        Arrays.fill(f.raw, (byte) 255);
        wedge(f.raw, f.shortBox, 1);
        wedge(f.raw, f.longBox, 1);
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void anotherPhysicalStaffDoesNotSupplyRole() {
        var f = new Fixture(false, false);
        assertTrue(
                f.run(
                                new CompleteOpposedHairpinPair.Work(2_000_000, 1_000_000),
                                new PlayingTechniqueDetector.Staff(200, 257, G, 0, 1))
                        .isEmpty());
    }

    @Test
    public void shiftedAxisDoesNotSupplyRole() {
        var f = new Fixture(false, false);
        Arrays.fill(f.raw, (byte) 255);
        wedge(f.raw, f.shortBox, 1);
        f.longBox[2] += 5;
        f.longBox[3] += 5;
        wedge(f.raw, f.longBox, -1);
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void literalMonotoneAntialiasFringeRetainsPaperSeparator() {
        var f = new Fixture(false, false);
        f.raw[180 * W + 175] = (byte) 170;
        f.raw[180 * W + 176] = (byte) 210;
        byte[] before = f.raw.clone();
        assertEquals(1, f.run().size());
        assertArrayEquals(before, f.raw);
    }

    @Test
    public void detachedSpeckInGapCannotBorrowEdgeOwnership() {
        var f = new Fixture(false, false);
        f.raw[189 * W + 188] = (byte) 180;
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void attachedNonmonotoneBranchIsNotAntialias() {
        var f = new Fixture(false, false);
        f.raw[180 * W + 175] = (byte) 200;
        f.raw[180 * W + 176] = (byte) 170;
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void verticalAttachedBranchCannotWalkAnEightConnectedMask() {
        var f = new Fixture(false, false);
        for (int y = 180; y <= 198; y++) f.raw[y * W + 175] = (byte) 170;
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void bridgedOperatorAndTextInGapRefuse() {
        var f = new Fixture(false, false);
        for (int x = 175; x < 202; x++) f.raw[189 * W + x] = 0;
        assertTrue(f.run().isEmpty());
        f = new Fixture(false, false);
        line(f.raw, 185, 185, 191, 193);
        line(f.raw, 185, 193, 191, 185);
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void filledTriangleAndInteriorArmRefuse() {
        var f = new Fixture(false, false);
        for (int x = 100; x <= 174; x++) {
            int half = Math.round((x - 100) * 9 / 74f);
            for (int y = 189 - half; y <= 189 + half; y++) f.raw[y * W + x] = 0;
        }
        assertTrue(f.run().isEmpty());
        f = new Fixture(false, false);
        for (int y = 183; y <= 195; y++) f.raw[y * W + 150] = 0;
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void brokenArmCannotUseRemainingRail() {
        var f = new Fixture(false, false);
        int x = 160, y = Math.round(189 + (180 - 189) * (x - 100) / 74f);
        f.raw[y * W + x] = (byte) 255;
        assertTrue(f.run().isEmpty());
    }

    @Test
    public void twoEquidistantEligibleNeighborsRemainAmbiguous() {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        int[] s = {250, 324, 180, 198}, a = {89, 221, 180, 198}, b = {353, 485, 180, 198};
        wedge(raw, s, 1);
        wedge(raw, a, -1);
        wedge(raw, b, -1);
        var c = new CompleteOpposedHairpinPair.Collector(raw, W, H);
        add(c, raw, STAFF, s, false);
        add(c, raw, STAFF, a, true);
        add(c, raw, STAFF, b, true);
        assertTrue(c.finish().isEmpty());
    }

    @Test
    public void exactBudgetAndOneUnderFailClosed() {
        var f = new Fixture(false, false);
        var measured = new CompleteOpposedHairpinPair.Work(2_000_000, 1_000_000);
        assertEquals(1, f.run(measured, STAFF).size());
        long work = 2_000_000 - measured.work, bytes = 1_000_000 - measured.bytes;
        assertEquals(1, f.run(new CompleteOpposedHairpinPair.Work(work, bytes), STAFF).size());
        var less = new CompleteOpposedHairpinPair.Work(work - 1, bytes);
        assertTrue(f.run(less, STAFF).isEmpty());
        assertTrue(less.failed);
        less = new CompleteOpposedHairpinPair.Work(work, bytes - 1);
        assertTrue(f.run(less, STAFF).isEmpty());
        assertTrue(less.failed);
    }

    @Test
    public void shortOnEitherSideRetainsBothDirectionOrders() {
        for (int direction : new int[] {-1, 1}) {
            byte[] raw = new byte[W * H];
            Arrays.fill(raw, (byte) 255);
            int[] a = {100, 232, 180, 198}, b = {260, 334, 180, 198};
            wedge(raw, a, direction);
            wedge(raw, b, -direction);
            var c = new CompleteOpposedHairpinPair.Collector(raw, W, H);
            add(c, raw, STAFF, b, false);
            add(c, raw, STAFF, a, true);
            assertEquals(1, c.finish().size());
        }
    }

    @Test
    public void scaledAndPhasedCompleteCompactPairsRemainLiteral() {
        for (int scale : new int[] {1, 2})
            for (int phase = 0; phase <= 2; phase++) {
                byte[] raw = new byte[W * H];
                Arrays.fill(raw, (byte) 255);
                int[] a = {
                    phase + 100,
                    phase + 100 + 42 * scale - 1,
                    180 + phase,
                    180 + phase + 18 * scale - 1
                };
                int[] b = {
                    phase + 100 + 93 * scale,
                    phase + 100 + 298 * scale - 1,
                    180 + phase - scale,
                    180 + phase + 19 * scale - 1
                };
                wedge(raw, a, 1);
                wedge(raw, b, -1);
                var frame =
                        new PlayingTechniqueDetector.Staff(50, 50 + 56 * scale, 14 * scale, 0, 1);
                var c = new CompleteOpposedHairpinPair.Collector(raw, W, H);
                add(c, raw, frame, a, false);
                add(c, raw, frame, b, true);
                assertEquals(1, c.finish().size());
            }
    }

    static List<CompleteOpposedHairpinPair.Shape> twoShorts(CompleteOpposedHairpinPair.Work work) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        int[] a = {100, 174, 180, 198}, b = {202, 334, 180, 198}, d = {362, 436, 180, 198};
        wedge(raw, a, 1);
        wedge(raw, b, -1);
        wedge(raw, d, 1);
        var c = new CompleteOpposedHairpinPair.Collector(raw, W, H, work);
        add(c, raw, STAFF, a, false);
        add(c, raw, STAFF, d, false);
        add(c, raw, STAFF, b, true);
        return c.finish();
    }

    @Test
    public void laterExhaustionRefusesTheCompleteNewSet() {
        var measured = new CompleteOpposedHairpinPair.Work(2_000_000, 1_000_000);
        assertEquals(2, twoShorts(measured).size());
        var under =
                new CompleteOpposedHairpinPair.Work(
                        2_000_000 - measured.work - 1, 1_000_000 - measured.bytes);
        assertTrue(twoShorts(under).isEmpty());
        assertTrue(under.failed);
    }

    static ScoreNoteEvent note(int x) {
        return new ScoreNoteEvent(
                0,
                x / (float) W,
                4,
                0,
                1,
                130f / H,
                false,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    @Test
    public void originalCallerPreservesLongAndAddsOnlyTheShort() {
        for (boolean mirror : new boolean[] {false, true}) {
            var f = new Fixture(mirror, false);
            byte[] longOnly = f.raw.clone();
            for (int x = f.shortBox[0]; x <= f.shortBox[1]; x++)
                for (int y = f.shortBox[2]; y <= f.shortBox[3]; y++)
                    longOnly[y * W + x] = (byte) 255;
            var notes = List.of(note(100), note(174), note(202), note(334), note(500));
            var measures = List.of(new MeasureRegion(0, 1, 0, 1));
            var old =
                    ScoreDynamicsDetector.detectWithEvidence(
                            List.of(), List.of(STAFF), measures, notes, longOnly, W, H);
            byte[] before = f.raw.clone();
            var both =
                    ScoreDynamicsDetector.detectWithEvidence(
                            List.of(), List.of(STAFF), measures, notes, f.raw, W, H);
            assertEquals(1, old.changes().size());
            assertEquals(2, both.changes().size());
            assertTrue(both.changes().containsAll(old.changes()));
            assertEquals(old.events(), both.events());
            assertArrayEquals(before, f.raw);
        }
    }

    @Test
    public void existingFaintCrescendoKeepsItsCompleteEndpointAtSevenPhases() {
        for (int center = 212; center <= 236; center += 4) {
            var f = new Fixture(false, false);
            for (int x = 100; x <= 212; x++) {
                double d = x - 100;
                int a = (int) Math.round(center - .085 * d);
                int b = (int) Math.round(center + .085 * d);
                int tone = x >= 177 ? 80 : 238;
                for (int k = -1; k <= 1; k++) {
                    f.raw[(a + k) * W + x] = (byte) tone;
                    f.raw[(b + k) * W + x] = (byte) tone;
                }
            }
            var notes = List.of(note(100), note(174), note(202), note(334), note(500));
            var actual =
                    ScoreDynamicsDetector.detectWithEvidence(
                            List.of(),
                            List.of(STAFF),
                            List.of(new MeasureRegion(0, 1, 0, 1)),
                            notes,
                            f.raw,
                            W,
                            H);
            var retainedCrescendo =
                    new ScoreDynamicChange(0, 100f / W, 0, 1, 0, 202f / W, 0, 1, false);
            var retainedDiminuendo =
                    new ScoreDynamicChange(0, 202f / W, 0, 1, 0, 334f / W, 0, -1, false);
            assertEquals(
                    "phase " + center,
                    List.of(retainedCrescendo, retainedDiminuendo),
                    actual.changes());
            assertTrue(actual.events().isEmpty());
        }
    }

    @Test
    public void normalizedRecoveryUsesItsExistingSixPercentClaimWindow() {
        var f = new Fixture(false, false);
        for (int x = 130; x <= 242; x++) {
            double d = x - 130;
            int a = (int) Math.round(224 - .085 * d);
            int b = (int) Math.round(224 + .085 * d);
            int tone = x >= 177 ? 80 : 238;
            for (int k = -1; k <= 1; k++) {
                f.raw[(a + k) * W + x] = (byte) tone;
                f.raw[(b + k) * W + x] = (byte) tone;
            }
        }
        var notes = List.of(note(100), note(174), note(202), note(334), note(500));
        var actual =
                ScoreDynamicsDetector.detectWithEvidence(
                        List.of(),
                        List.of(STAFF),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        notes,
                        f.raw,
                        W,
                        H);
        assertTrue(30f / W > .025f);
        assertTrue(30f / W < .06f);
        assertEquals(
                List.of(
                        new ScoreDynamicChange(0, 130f / W, 0, 1, 0, 242f / W, 0, 1, false),
                        new ScoreDynamicChange(0, 202f / W, 0, 1, 0, 334f / W, 0, -1, false)),
                actual.changes());
        assertTrue(actual.events().isEmpty());
    }
}
