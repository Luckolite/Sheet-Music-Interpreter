// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original asymmetric carets and rival attacks, with no score assets or inferred pitch. */
public class DetachedCaretOriginalTest {
    static final int W = 2048, H = 1600;

    static byte[] paper() {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        return r;
    }

    static void line(byte[] r, int x0, int y0, int x1, int y1, int tone) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x0 + (x1 - x0) * (i / (float) n)),
                    y = Math.round(y0 + (y1 - y0) * (i / (float) n));
            r[y * W + x] = (byte) tone;
            r[(y + 1) * W + x] = (byte) tone;
        }
    }

    static void caret(byte[] r, int x, int left) {
        line(r, x - 12, 113, x, 100, left);
        line(r, x, 100, x + 12, 113, 75);
    }

    static void dot(byte[] r, int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                if (Math.abs(dx) + Math.abs(dy) <= 3) r[(y + dy) * W + x + dx] = 75;
    }

    static List<NoteArticulationDetector.Anchor> one() {
        return List.of(new NoteArticulationDetector.Anchor(107, 160, 14, 0));
    }

    static int[] detect(byte[] raw, List<NoteArticulationDetector.Anchor> notes) {
        return NoteArticulationDetector.detect(new byte[raw.length], raw, W, H, notes);
    }

    @Test
    public void completeFadedArmUsesActualPaperAndKeepsDarkControl() {
        byte[] r = paper();
        caret(r, 107, 195);
        assertEquals(2, detect(r, one())[0]);
        r = paper();
        caret(r, 107, 75);
        assertEquals(2, detect(r, one())[0]);
    }

    @Test
    public void potentialFarRivalRefusesWholeNewBody() {
        byte[] r = paper();
        caret(r, 107, 195);
        assertArrayEquals(
                new int[2],
                detect(
                        r,
                        List.of(
                                new NoteArticulationDetector.Anchor(107, 160, 14, 0),
                                new NoteArticulationDetector.Anchor(107, 205, 14, 1))));
    }

    @Test
    public void closerMarkedRivalCannotBeRemovedToRescueEmptyOwner() {
        byte[] r = paper();
        caret(r, 107, 195);
        dot(r, 107, 135);
        assertArrayEquals(
                new int[] {0, 4},
                detect(
                        r,
                        List.of(
                                new NoteArticulationDetector.Anchor(107, 165, 14, 0),
                                new NoteArticulationDetector.Anchor(107, 150, 14, 1))));
    }

    @Test
    public void anyOldChordBitPreservesEntireAttack() {
        byte[] r = paper();
        caret(r, 107, 195);
        dot(r, 107, 145);
        assertArrayEquals(
                new int[] {4, 4},
                detect(
                        r,
                        List.of(
                                new NoteArticulationDetector.Anchor(107, 160, 14, 0),
                                new NoteArticulationDetector.Anchor(107, 170, 14, 0))));
    }

    @Test
    public void unchangedLegacyFarBeamOwnedMarcatoPasses() {
        byte[] r = paper();
        caret(r, 107, 75);
        for (int y = 128; y <= 205; y++) r[y * W + 114] = 75;
        for (int y = 128; y <= 131; y++) for (int x = 114; x <= 150; x++) r[y * W + x] = 75;
        assertEquals(
                2, detect(r, List.of(new NoteArticulationDetector.Anchor(107, 205, 14, 0)))[0]);
    }

    @Test
    public void paperContrastMustCloseAndWhiteGapCannotBeFilled() {
        for (int tone : new int[] {226, 240}) {
            byte[] r = paper();
            caret(r, 107, tone);
            assertEquals(0, detect(r, one())[0]);
        }
        byte[] r = paper();
        caret(r, 107, 195);
        for (int y = 99; y <= 104; y++) for (int x = 103; x <= 111; x++) r[y * W + x] = (byte) 255;
        assertEquals(0, detect(r, one())[0]);
    }

    @Test
    public void lettersVAndSolidBodiesDoNotBecomeCarets() {
        for (int kind = 0; kind < 4; kind++) {
            byte[] r = paper();
            caret(r, 107, 195);
            if (kind == 0) line(r, 99, 108, 115, 108, 75);
            if (kind == 1) {
                line(r, 107, 100, 107, 118, 75);
                line(r, 95, 113, 119, 113, 75);
            }
            if (kind == 2) {
                Arrays.fill(r, (byte) 255);
                line(r, 95, 100, 107, 113, 195);
                line(r, 107, 113, 119, 100, 75);
            }
            if (kind == 3)
                for (int y = 100; y <= 114; y++) for (int x = 95; x <= 119; x++) r[y * W + x] = 75;
            assertEquals("kind=" + kind, 0, detect(r, one())[0] & 2);
        }
    }

    @Test
    public void returningCurveIsNotTwoStraightArms() {
        byte[] r = paper();
        for (int x = 95; x <= 119; x++) {
            int y = 100 + (int) Math.round(12 * Math.pow((x - 107) / 12., 2));
            r[y * W + x] = (byte) (x < 107 ? 195 : 75);
            r[(y + 1) * W + x] = (byte) (x < 107 ? 195 : 75);
        }
        assertEquals(0, detect(r, one())[0] & 2);
    }

    @Test
    public void belowHeadFallbackIsNotAnAboveHeadCaret() {
        byte[] r = paper();
        caret(r, 107, 195);
        assertEquals(
                0, detect(r, List.of(new NoteArticulationDetector.Anchor(107, 70, 14, 0)))[0] & 2);
    }

    @Test
    public void grayAndLabelsRemainLiteralAndUnmodified() {
        byte[] r = paper();
        caret(r, 107, 195);
        byte[] copy = r.clone(), labels = new byte[r.length], labelsCopy = labels.clone();
        NoteArticulationDetector.detect(labels, r, W, H, one());
        assertArrayEquals(copy, r);
        assertArrayEquals(labelsCopy, labels);
    }

    @Test
    public void lateAdmissionRefusalDiscardsEarlierNewMarksAndPreservesOld() {
        byte[] r = paper();
        caret(r, 107, 195);
        var single = one();
        assertEquals(
                2,
                NoteArticulationDetector.detectWithWordBodies(
                        new byte[r.length],
                        r,
                        W,
                        H,
                        single,
                        List.of(),
                        Long.MAX_VALUE,
                        1_000_000,
                        Long.MAX_VALUE)[0]);
        caret(r, 200, 195);
        dot(r, 350, 164);
        var notes =
                List.of(
                        new NoteArticulationDetector.Anchor(107, 160, 14, 0),
                        new NoteArticulationDetector.Anchor(200, 160, 14, 0),
                        new NoteArticulationDetector.Anchor(350, 180, 14, 1));
        assertArrayEquals(new int[] {2, 2, 4}, detect(r, notes));
        assertArrayEquals(
                new int[] {0, 0, 4},
                NoteArticulationDetector.detectWithWordBodies(
                        new byte[r.length],
                        r,
                        W,
                        H,
                        notes,
                        List.of(),
                        Long.MAX_VALUE,
                        1_000_000,
                        Long.MAX_VALUE));
    }

    @Test
    public void producerRefusalCannotEraseLegacyArticulations() {
        byte[] r = paper();
        caret(r, 107, 75);
        assertEquals(
                2,
                NoteArticulationDetector.detectWithWordBodies(
                        new byte[r.length], r, W, H, one(), List.of(), 0, 0, 0)[0]);
    }

    @Test
    public void staffCarrierCannotSupplyAClippedCaret() {
        byte[] r = paper();
        caret(r, 107, 195);
        for (int x = 40; x <= 220; x++) r[114 * W + x] = 75;
        assertEquals(0, detect(r, one())[0] & 2);
    }

    @Test
    public void aFadedPeakInsideAContinuousWordRetainsWordOwnership() {
        byte[] r = paper();
        caret(r, 107, 195);
        for (int left : new int[] {70, 82, 126, 138})
            for (int y = 103; y <= 114; y++)
                for (int x = left; x <= left + 6; x++) r[y * W + x] = 75;
        assertEquals(0, detect(r, one())[0] & 2);
    }
}
