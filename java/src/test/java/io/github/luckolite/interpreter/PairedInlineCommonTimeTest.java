// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PairedInlineCommonTimeTest {
    static final class Page {
        final int w = 780, h = 430, gap = 16, left = 378, right = 406;
        final boolean broadArc;
        final byte[] labels = new byte[w * h], gray = new byte[w * h];

        Page(boolean pair, boolean boundary) {
            this(pair, boundary, false);
        }

        Page(boolean pair, boolean boundary, boolean broadArc) {
            this.broadArc = broadArc;
            Arrays.fill(gray, (byte) 255);
            staff(90);
            staff(250);
            if (boundary) {
                box(350, 90, 1, 225, (byte) 1);
                box(355, 90, 2, 225, (byte) 1);
            }
            c(90);
            if (pair) c(250);
        }

        void box(int x, int y, int w0, int h0, byte label) {
            for (int yy = y; yy < y + h0; yy++)
                for (int xx = x; xx < x + w0; xx++) {
                    gray[yy * w + xx] = 0;
                    labels[yy * w + xx] = label;
                }
        }

        void staff(int top) {
            for (int i = 0; i < 5; i++) box(25, top + i * gap, 730, 1, (byte) 4);
        }

        void c(int top) {
            drawC(top, true);
        }

        void drawC(int top, boolean restoreStaff) {
            int cx = 392, cy = top + 2 * gap;
            for (int y = cy - 18; y <= cy + 18; y++)
                for (int x = 378; x <= 406; x++) {
                    double outer = Math.pow((x - cx) / 14d, 2) + Math.pow((y - cy) / 18d, 2);
                    double inner = Math.pow((x - (cx + 3)) / 10d, 2) + Math.pow((y - cy) / 15d, 2);
                    if (outer <= 1 && inner >= 1 && !(x > cx && Math.abs(y - cy) < 7)) {
                        gray[y * w + x] = 0;
                        labels[y * w + x] = 5;
                    }
                }
            if (broadArc) box(382, cy - 8, 8, 17, (byte) 5);
            box(398, cy - 15, 2, 8, (byte) 5);
            for (int y = cy - 12; y <= cy - 4; y++)
                for (int x = 398; x <= 406; x++)
                    if ((x - 402) * (x - 402) + (y - (cy - 8)) * (y - (cy - 8)) <= 16) {
                        gray[y * w + x] = 0;
                        labels[y * w + x] = 2;
                    }
            if (restoreStaff) staff(top);
        }

        void eraseC(int top) {
            for (int y = top + 10; y <= top + 55; y++)
                for (int x = 375; x <= 410; x++) {
                    gray[y * w + x] = (byte) 255;
                    labels[y * w + x] = 0;
                }
            staff(top);
        }

        void partialC(int top) {
            eraseC(top);
            drawC(top, true);
            for (int y = top + 34; y <= top + 55; y++)
                for (int x = 375; x <= 410; x++) {
                    gray[y * w + x] = (byte) 255;
                    labels[y * w + x] = 0;
                }
            staff(top);
        }

        void sharp(int top) {
            eraseC(top);
            box(386, top + 12, 2, 40, (byte) 3);
            box(399, top + 12, 2, 40, (byte) 3);
            box(380, top + 23, 27, 3, (byte) 3);
            box(380, top + 39, 27, 3, (byte) 3);
            staff(top);
        }

        void digit(int cx, int cy, boolean six) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 5; x <= cx + 5; x++) {
                    double outer = Math.pow((x - cx) / 5d, 2) + Math.pow((y - cy) / 8d, 2);
                    double inner = Math.pow((x - cx) / 2.5d, 2) + Math.pow((y - cy) / 4.5d, 2);
                    if (outer <= 1 && inner >= 1 && !(six && x > cx && y < cy - 2)) {
                        gray[y * w + x] = 0;
                        labels[y * w + x] = 5;
                    }
                }
        }

        void stackedSixEight(int top) {
            eraseC(top);
            digit(392, top + 19, true);
            digit(392, top + 45, false);
            staff(top);
        }

        List<MeasureRegion> measures() {
            return List.of(
                    new MeasureRegion(25f / w, 346f / w, 82f / h, 322f / h),
                    new MeasureRegion(362f / w, 755f / w, 82f / h, 322f / h));
        }

        List<ScoreMeterChange> read() {
            return MeterChangeDetector.commonTimeReadings(
                    labels, gray, w, h, measures(), List.of());
        }
    }

    @Test
    public void pairedCompactCommonTimeAtDoubleBarIsReadWithoutAnotherClef() {
        var p = new Page(true, true);
        assertEquals(List.of(new ScoreMeterChange(1, 4, 4)), p.read());
    }

    @Test
    public void pairedBulgingLeftArcsKeepTheSameProvedInlineChange() {
        assertEquals(List.of(new ScoreMeterChange(1, 4, 4)), new Page(true, true, true).read());
    }

    @Test
    public void loneInlineCharacterRemainsUnproved() {
        assertTrue(new Page(false, true).read().isEmpty());
    }

    @Test
    public void pairWithoutSharedDoubleBarRemainsUnproved() {
        assertTrue(new Page(true, false).read().isEmpty());
    }

    @Test
    public void pairedPartialArcsDoNotProveCommonTime() {
        var p = new Page(true, true);
        p.partialC(90);
        p.partialC(250);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void pairedKeySharpsDoNotProveCommonTime() {
        var p = new Page(true, true);
        p.sharp(90);
        p.sharp(250);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void pairedFaintSixEightDoesNotBecomeCommonTime() {
        var p = new Page(true, true);
        p.stackedSixEight(90);
        p.stackedSixEight(250);
        for (int i = 0; i < p.gray.length; i++)
            if (p.gray[i] == 0 && p.labels[i] == 5) p.gray[i] = (byte) 130;
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void pairedChordLettersAboveTheStaffStayText() {
        var p = new Page(true, true);
        p.eraseC(90);
        p.eraseC(250);
        p.drawC(10, false);
        p.drawC(170, false);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void mismatchedCommonAndCutCopiesAbstain() {
        var p = new Page(true, true);
        p.box(391, 258, 2, 50, (byte) 5);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void inputsRemainUnchanged() {
        var p = new Page(true, true);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.read();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }

    @Test
    public void thirdNearbySharedBarMakesBoundaryAmbiguous() {
        var p = new Page(true, true);
        p.box(360, 90, 1, 225, (byte) 1);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void brokenSharedBarDoesNotProveTheBoundary() {
        var p = new Page(true, true);
        for (int y = 180; y < 220; y++)
            for (int x = 350; x < 357; x++) {
                p.gray[y * p.w + x] = (byte) 255;
                p.labels[y * p.w + x] = 0;
            }
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void separatelyPrintedStaffBarsDoNotProveSharedBoundary() {
        var p = new Page(true, true);
        for (int y = 155; y < 250; y++)
            for (int x = 350; x < 357; x++) {
                p.gray[y * p.w + x] = (byte) 255;
                p.labels[y * p.w + x] = 0;
            }
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void horizontallyMisalignedCopiesAbstain() {
        var p = new Page(true, true);
        for (int y = 260; y <= 305; y++)
            for (int x = 420; x >= 375; x--) {
                p.gray[y * p.w + x] = p.gray[y * p.w + x - 10];
                p.labels[y * p.w + x] = p.labels[y * p.w + x - 10];
            }
        p.staff(250);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void closedCountersDoNotProveAnOpenCommonTimeSign() {
        var p = new Page(true, true);
        for (int top : new int[] {90, 250}) p.box(403, top + 25, 4, 15, (byte) 5);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void stemmedChordAtBothStaffsDoesNotBecomeCommonTime() {
        var p = new Page(true, true);
        for (int top : new int[] {90, 250})
            p.box(392, top - 15, 2, 66, OmrMeasurePostProcessor.STEM_OR_REST);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void normalizedHeadsBelongOnlyToTheTwoProvedCrops() {
        var p = new Page(true, true);
        p.box(500, 120, 4, 4, OmrMeasurePostProcessor.NOTEHEAD);
        byte[] before = p.labels.clone(), gray = p.gray.clone();
        var proofs = PairedInlineCommonTime.prove(p.labels, p.gray, p.w, p.h, p.measures());
        assertEquals(1, proofs.size());
        var normalized = PairedInlineCommonTime.normalize(p.labels, p.gray, p.w, p.h, p.measures());
        int changed = 0;
        for (int i = 0; i < normalized.length; i++) {
            if (normalized[i] == before[i]) continue;
            changed++;
            assertEquals(OmrMeasurePostProcessor.NOTEHEAD, before[i]);
            assertEquals(0, normalized[i]);
            int x = i % p.w, y = i / p.w;
            assertTrue(
                    proofs.get(0).copies().stream()
                            .anyMatch(
                                    c ->
                                            x >= c.left()
                                                    && x < c.right()
                                                    && y >= c.top()
                                                    && y < c.bottom()));
        }
        assertTrue(changed > 0);
        assertArrayEquals(before, p.labels);
        assertArrayEquals(gray, p.gray);
        assertEquals(OmrMeasurePostProcessor.NOTEHEAD, normalized[120 * p.w + 500]);
    }

    @Test
    public void severedArcsDoNotUseStaffRulesToInventAConnection() {
        var p = new Page(true, true);
        for (int top : new int[] {90, 250}) {
            for (int y = top + 28; y <= top + 36; y++)
                for (int x = 375; x <= 398; x++) {
                    p.gray[y * p.w + x] = (byte) 255;
                    p.labels[y * p.w + x] = 0;
                }
            p.staff(top);
        }
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void additionalInkBesideEitherSignMakesTheColumnAmbiguous() {
        var p = new Page(true, true);
        p.box(414, 106, 4, 32, (byte) 5);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void failedProofDoesNotCloneOrModifyLabels() {
        var p = new Page(false, true);
        assertSame(
                p.labels,
                PairedInlineCommonTime.normalize(p.labels, p.gray, p.w, p.h, p.measures()));
    }
}
