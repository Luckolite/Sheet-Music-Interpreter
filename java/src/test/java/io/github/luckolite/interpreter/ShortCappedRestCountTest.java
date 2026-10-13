// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric glyphs and H-rests; no score pixels or model inference. */
public class ShortCappedRestCountTest {
    private static final int W = 600, H = 300;
    private static final MeasureRegion REGION = new MeasureRegion(.15f, .95f, .35f, .80f);

    private static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page(boolean leftCap, boolean rightCap) {
            Arrays.fill(gray, (byte) 255);
            for (int y = 130; y <= 194; y += 16) rect(70, y, 580, y, 4);
            rect(270, 156, 330, 166, 1);
            if (leftCap) rect(270, 149, 272, 173, 1);
            if (rightCap) rect(328, 149, 330, 173, 1);
        }

        void rect(int l, int t, int r, int b, int label) {
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = (byte) label;
                }
        }

        void seven(int x, int y, int label) {
            rect(x, y, x + 19, y + 3, label);
            for (int dy = 4; dy < 32; dy++) {
                int center = x + 18 - (dy - 4) / 2;
                rect(center - 2, y + dy, center + 1, y + dy, label);
            }
        }

        void eight(int x, int y, int label) {
            for (int dy : new int[] {0, 14, 29}) rect(x + 2, y + dy, x + 17, y + dy + 2, label);
            rect(x, y + 2, x + 3, y + 29, label);
            rect(x + 16, y + 2, x + 19, y + 29, label);
        }

        void six(int x, int y, int label, boolean curvedTerminal) {
            if (curvedTerminal) {
                rect(x + 10, y, x + 18, y + 2, label);
                for (int dy = 3; dy <= 13; dy++) {
                    int center = x + 12 - dy;
                    rect(Math.max(x, center), y + dy, Math.max(x, center) + 3, y + dy, label);
                }
            } else rect(x, y, x + 3, y + 13, label);
            rect(x, y + 12, x + 3, y + 29, label);
            rect(x + 3, y + 14, x + 16, y + 17, label);
            rect(x + 15, y + 16, x + 18, y + 29, label);
            rect(x + 3, y + 28, x + 16, y + 31, label);
        }

        List<MeasureNumberReconciler.NumberToken> detect(
                List<MeasureNumberReconciler.NumberToken> tokens) {
            return MultiMeasureRestDetector.detect(labels, gray, W, H, List.of(REGION), tokens);
        }
    }

    @Test
    public void shortCappedSevenExpandsItsOriginalVisualRegion() {
        Page p = new Page(true, true);
        p.seven(290, 80, 5);
        var counts = p.detect(List.of());
        assertEquals(1, counts.size());
        assertEquals(7, counts.get(0).value());
        var expanded = MeasureNumberReconciler.reconcile(List.of(REGION), List.of(), counts);
        assertEquals(7, expanded.size());
        assertTrue(expanded.stream().allMatch(REGION::equals));
    }

    @Test
    public void shortCappedEightRejectsAnOcrMeterInsideTheStaff() {
        Page p = new Page(true, true);
        p.eight(290, 80, 5);
        var meter = new MeasureNumberReconciler.NumberToken(4, .47f, .43f, .50f, .50f);
        var counts = p.detect(List.of(meter));
        assertEquals(1, counts.size());
        assertEquals(8, counts.get(0).value());
    }

    @Test
    public void lowerBowlCountIsSixRatherThanFour() {
        Page p = new Page(true, true);
        p.six(290, 80, 5, true);
        var counts = p.detect(List.of());
        assertEquals(1, counts.size());
        assertEquals(6, counts.get(0).value());
    }

    @Test
    public void sixShapeRecoveryDoesNotDependOnTheBarWidthFallback() {
        Page p = new Page(false, false);
        p.six(290, 80, 5, true);
        var count =
                MultiMeasureRestDetector.standaloneCount(
                        p.gray, W, H, new MultiMeasureRestDetector.RestBarCandidate(0, REGION));
        assertNotNull(count);
        assertEquals(6, count.value());
    }

    @Test
    public void sevenShapeRecoveryDoesNotDependOnCallerOcr() {
        Page p = new Page(false, false);
        p.seven(290, 80, 5);
        var count =
                MultiMeasureRestDetector.standaloneCount(
                        p.gray, W, H, new MultiMeasureRestDetector.RestBarCandidate(0, REGION));
        assertNotNull(count);
        assertEquals(7, count.value());
    }

    @Test
    public void mislabeledSevenDoesNotVetoAProvedRest() {
        Page p = new Page(true, true);
        p.seven(290, 80, 2);
        var counts = p.detect(List.of());
        assertEquals(1, counts.size());
        assertEquals(7, counts.get(0).value());
    }

    @Test
    public void actualNoteElsewhereInTheMeasureStillVetoesExpansion() {
        Page p = new Page(true, true);
        p.seven(290, 80, 2);
        p.rect(430, 170, 444, 180, 2);
        p.rect(444, 134, 445, 180, 1);
        assertTrue(p.detect(List.of()).isEmpty());
    }

    @Test
    public void bothCapsAreRequiredForTheShortBarFallback() {
        for (boolean left : new boolean[] {true, false}) {
            Page p = new Page(left, !left);
            p.seven(290, 80, 5);
            assertTrue(p.detect(List.of()).isEmpty());
        }
    }

    @Test
    public void ordinaryBeamWithDownwardStemsDoesNotBecomeAShortRest() {
        Page p = new Page(false, false);
        p.seven(290, 80, 5);
        p.rect(270, 167, 272, 200, 1);
        p.rect(328, 167, 330, 200, 1);
        assertTrue(p.detect(List.of()).isEmpty());
    }

    @Test
    public void lowerBowlLetterWithoutCurvedTerminalIsNotSix() {
        Page p = new Page(true, true);
        p.six(290, 80, 5, false);
        assertTrue(p.detect(List.of()).isEmpty());
    }

    @Test
    public void aCountOutsideTheCappedBarDoesNotClaimIt() {
        Page p = new Page(true, true);
        p.seven(440, 80, 5);
        var displaced =
                new MeasureNumberReconciler.NumberToken(7, 440f / W, 80f / H, 460f / W, 112f / H);
        assertTrue(p.detect(List.of(displaced)).isEmpty());
    }

    @Test
    public void callerPixelsAreUnchanged() {
        Page p = new Page(true, true);
        p.seven(290, 80, 2);
        byte[] labels = p.labels.clone(), gray = p.gray.clone();
        p.detect(List.of());
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
    }
}
