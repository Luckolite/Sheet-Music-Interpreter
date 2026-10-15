// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural two, beams and shafts; no source-score pixels or pitches. */
public class CompoundDupletTest {
    static final int W = 400, H = 500;

    static byte[] ink(boolean numeral, boolean beam, boolean broken) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (beam)
            for (int y = 167; y <= 173; y++) for (int x = 108; x <= 228; x++) g[y * W + x] = 0;
        for (int x : new int[] {108, 228})
            for (int y = 100; y <= 170; y++)
                if (!(broken && x == 228 && y >= 130 && y <= 140))
                    for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        if (numeral)
            for (int y = 0; y < 22; y++)
                for (int x = 0; x < 12; x++) {
                    int center = 10 - (y - 7) * 10 / 12;
                    if (y < 3 && x >= 2 && x <= 9
                            || y >= 3 && y < 8 && x >= 9
                            || y >= 8 && y < 19 && Math.abs(x - center) <= 1
                            || y >= 19) g[(190 + y) * W + 162 + x] = 0;
                }
        return g;
    }

    static ScoreNoteEvent eighth(float x) {
        return new ScoreNoteEvent(0, x, 0, 0, 1, .2f, false, 0, 1, 2, 0, 1).withStemDirection(-1);
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(
                eighth(.3f),
                eighth(.6f),
                new ScoreNoteEvent(0, .85f, 2, 0, 1, .2f, false, 1, 0, 2, 1, 1)
                        .withStemDirection(-1));
    }

    static List<ScoreNoteEvent> run(List<ScoreNoteEvent> n, byte[] g, List<ScoreMeterChange> m) {
        return TripletRhythmDetector.compoundDuplets(
                n, List.of(new MeasureRegion(0, 1, .05f, .37f)), m, g, W, H);
    }

    static List<ScoreMeterChange> meter() {
        return List.of(new ScoreMeterChange(0, 6, 8));
    }

    @Test
    public void printedDupletCompletesCompoundBar() {
        var n = notes();
        var r = run(n, ink(true, true, false), meter());
        assertEquals(n.get(0).withTupletRatio(2, 3), r.get(0));
        assertEquals(n.get(1).withTupletRatio(2, 3), r.get(1));
        assertEquals(n.get(2), r.get(2));
        assertEquals(3, r.stream().mapToDouble(ScoreNoteTiming::writtenDurationBeats).sum(), 1e-8);
    }

    @Test
    public void missingNumeralDoesNotInferFromShortClock() {
        var n = notes();
        assertEquals(n, run(n, ink(false, true, false), meter()));
    }

    @Test
    public void missingBeamDoesNotBorrowTwo() {
        var n = notes();
        assertEquals(n, run(n, ink(true, false, false), meter()));
    }

    @Test
    public void shaftGapCannotBridgeOwner() {
        var n = notes();
        assertEquals(n, run(n, ink(true, true, true), meter()));
    }

    @Test
    public void missingMeterAbstains() {
        var n = notes();
        assertEquals(n, run(n, ink(true, true, false), List.of()));
    }

    @Test
    public void simpleMeterCannotChooseCompoundRatio() {
        var n = notes();
        assertEquals(n, run(n, ink(true, true, false), List.of(new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void conflictingMeterAbstains() {
        var n = notes();
        assertEquals(
                n,
                run(
                        n,
                        ink(true, true, false),
                        List.of(new ScoreMeterChange(0, 6, 8), new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void incompleteRestOfBarCannotProveRatio() {
        var n = notes().subList(0, 2);
        assertEquals(n, run(n, ink(true, true, false), meter()));
    }

    @Test
    public void opposedShaftCannotBorrowNeighborBeam() {
        var n = new ArrayList<>(notes());
        n.set(1, n.get(1).withStemDirection(1));
        assertEquals(n, run(n, ink(true, true, false), meter()));
    }

    @Test
    public void callerRasterIsUnchanged() {
        var g = ink(true, true, false);
        var copy = g.clone();
        run(notes(), g, meter());
        assertArrayEquals(copy, g);
    }

    @Test
    public void explicitRatiosSurviveNoteEdits() {
        for (int[] r : new int[][] {{2, 3}, {4, 6}, {7, 6}}) {
            var n = eighth(.3f).withTupletRatio(r[0], r[1]);
            assertEquals(r[0], n.withArticulations(1).withStemDirection(1).tupletDivisor());
            assertEquals(r[1], n.withArticulations(1).withStemDirection(1).tupletNormalNotes());
        }
    }

    @Test
    public void legacyUnsupportedCountsKeepPreviousMeaning() {
        for (int actual : new int[] {2, 4}) {
            var n = new ScoreNoteEvent(0, .3f, 0, 0, 1, .2f, false, 0, 1, 2, 0, actual);
            assertEquals(1, n.tupletDivisor());
            assertEquals(1, n.tupletNormalNotes());
        }
    }

    @Test
    public void invalidExplicitRatioStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> eighth(.3f).withTupletRatio(2, 0));
        assertThrows(IllegalArgumentException.class, () -> eighth(.3f).withTupletRatio(1, 3));
    }

    static byte[] otherDigit(int digit) {
        var g = ink(false, true, false);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++) {
                boolean horizontal = (y < 3 || y >= 9 && y <= 12 || y >= 19) && x >= 1 && x <= 10;
                boolean right = x >= 9, left = x <= 2;
                boolean black =
                        switch (digit) {
                            case 3 -> horizontal || right;
                            case 5 -> horizontal || (y < 11 ? left : right);
                            case 8 -> horizontal || left || right;
                            default -> false;
                        };
                if (black) g[(190 + y) * W + 162 + x] = 0;
            }
        return g;
    }

    @Test
    public void printedThreeCannotBecomeDuplet() {
        var n = notes();
        assertEquals(n, run(n, otherDigit(3), meter()));
    }

    @Test
    public void printedFiveCannotBecomeDuplet() {
        var n = notes();
        assertEquals(n, run(n, otherDigit(5), meter()));
    }

    @Test
    public void printedEightCannotBecomeDuplet() {
        var n = notes();
        assertEquals(n, run(n, otherDigit(8), meter()));
    }

    @Test
    public void twoBesideFirstStemCannotOwnWholePair() {
        var n = notes();
        var g = ink(true, true, false);
        for (int y = 190; y < 212; y++)
            for (int x = 0; x < 12; x++) {
                g[y * W + 100 + x] = g[y * W + 162 + x];
                g[y * W + 162 + x] = (byte) 255;
            }
        assertEquals(n, run(n, g, meter()));
    }

    @Test
    public void laterMeterDoesNotApplyBackwards() {
        var n = notes();
        assertEquals(n, run(n, ink(true, true, false), List.of(new ScoreMeterChange(1, 6, 8))));
    }

    @Test
    public void explicitExistingRatioIsNeverOverwritten() {
        var n = new ArrayList<>(notes());
        n.set(0, n.get(0).withTupletRatio(3, 2));
        assertEquals(n, run(n, ink(true, true, false), meter()));
    }

    static byte[] mixedInk(boolean four) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x : new int[] {48, 88, 128, 168, 248, 328})
            for (int y = 100; y <= 170; y++) for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        for (int x = 48; x <= 168; x++)
            for (int y = 155; y <= 173; y++) if (y <= 161 || y >= 167) g[y * W + x] = 0;
        for (int x = 248; x <= 328; x++) for (int y = 167; y <= 173; y++) g[y * W + x] = 0;
        if (four)
            for (int y = 0; y < 22; y++)
                for (int x = 0; x < 12; x++)
                    if (x >= 9
                            || y >= 13 && y <= 15
                            || y < 14 && Math.abs(x - (8 - y * 8 / 14)) <= 1)
                        g[(190 + y) * W + 102 + x] = 0;
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++) {
                int center = 10 - (y - 7) * 10 / 12;
                if (y < 3 && x >= 2 && x <= 9
                        || y >= 3 && y < 8 && x >= 9
                        || y >= 8 && y < 19 && Math.abs(x - center) <= 1
                        || y >= 19) g[(190 + y) * W + 282 + x] = 0;
            }
        return g;
    }

    static List<ScoreNoteEvent> mixed() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {60, 100, 140, 180})
            n.add(
                    new ScoreNoteEvent(0, x / (float) W, 0, 0, 1, .2f, false, 0, 2, 2, 0, 1)
                            .withStemDirection(-1));
        n.add(eighth(.65f));
        n.add(eighth(.85f));
        return n;
    }

    @Test
    public void fourSixteenthsAndTwoEighthsFillOneCompoundBar() {
        var n = mixed();
        var r = run(n, mixedInk(true), meter());
        for (int i = 0; i < 4; i++) assertEquals(n.get(i).withTupletRatio(4, 6), r.get(i));
        for (int i = 4; i < 6; i++) assertEquals(n.get(i).withTupletRatio(2, 3), r.get(i));
        assertEquals(3, r.stream().mapToDouble(ScoreNoteTiming::writtenDurationBeats).sum(), 1e-8);
    }

    @Test
    public void missingFourCannotRescaleTheRemainingDupletToFillBar() {
        var n = mixed();
        assertEquals(n, run(n, mixedInk(false), meter()));
    }

    @Test
    public void extraAttackMakesMixedClockAmbiguous() {
        var n = new ArrayList<>(mixed());
        n.add(new ScoreNoteEvent(0, .95f, 2, 0, 1, .2f, false, 0, 2, 2, 0, 1));
        assertEquals(n, run(n, mixedInk(true), meter()));
    }
}
