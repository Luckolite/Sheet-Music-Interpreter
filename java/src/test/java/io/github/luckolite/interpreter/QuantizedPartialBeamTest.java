// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently rasterized straight lanes, with deliberate fractional-pixel phases. */
public class QuantizedPartialBeamTest {
    private static final class Figure {
        final int w = 300, h = 220;
        byte[] gray = new byte[w * h];
        int[] a, b;
        int left = 92, right = 110, top, bottom;
        float cx = 102, cy, gap = 14;

        Figure(float slope, float phase) {
            Arrays.fill(gray, (byte) 255);
            a = new int[] {90, Math.round(150 + phase), 1};
            b = new int[] {126, Math.round(150 + phase + 36 * slope), 1};
            top = Math.round(125 + phase);
            bottom = top + 24;
            cy = 137 + phase;
            for (int x = 90; x <= 126; x++) {
                float c = 147 + phase + slope * (x - 90);
                for (int y = (int) Math.ceil(c - 3); y <= Math.floor(c + 3); y++)
                    gray[y * w + x] = 35;
                if (x <= 110)
                    for (int y = (int) Math.ceil(c - 17); y <= Math.floor(c - 11); y++)
                        gray[y * w + x] = 35;
            }
            for (int y = 90; y <= a[1]; y++) gray[y * w + 90] = 35;
            for (int y = 73; y <= b[1]; y++) gray[y * w + 126] = 35;
            oval(99, 90, 11, 7);
            oval(135, 73, 11, 7);
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if ((x - cx) * (x - cx) / (double) (rx * rx)
                                    + (y - cy) * (y - cy) / (double) (ry * ry)
                            <= 1) gray[y * w + x] = 35;
        }

        void staff(float phase) {
            for (int i = 0; i < 5; i++) {
                int y = Math.round(phase + i * 13.75f);
                for (int d = 0; d < 2; d++)
                    for (int x = 12; x < 285; x++) gray[(y + d) * w + x] = 35;
            }
        }

        void mirror(boolean horizontal, boolean vertical) {
            byte[] next = new byte[gray.length];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    next[(vertical ? h - 1 - y : y) * w + (horizontal ? w - 1 - x : x)] =
                            gray[y * w + x];
            gray = next;
            if (horizontal) {
                a[0] = w - 1 - a[0];
                b[0] = w - 1 - b[0];
                int old = left;
                left = w - 1 - right;
                right = w - 1 - old;
                cx = w - 1 - cx;
            }
            if (vertical) {
                a[1] = h - 1 - a[1];
                b[1] = h - 1 - b[1];
                a[2] = -a[2];
                b[2] = -b[2];
                int old = top;
                top = h - 1 - bottom;
                bottom = h - 1 - old;
                cy = h - 1 - cy;
            }
        }

        boolean proof() {
            byte[] prior = gray.clone();
            boolean result =
                    PartialBeamHeadContour.provedMerged(
                            gray, w, h, a, b, cx, cy, left, top, right, bottom, 270, gap);
            assertArrayEquals(prior, gray);
            return result;
        }
    }

    private Figure normal() {
        return new Figure(-.47f, 0);
    }

    @Test
    public void fractionalSlopeHasOneCompleteStraightEdge() {
        assertTrue(normal().proof());
    }

    @Test
    public void fractionalPhaseDoesNotInventAnOval() {
        assertTrue(new Figure(-.47f, .35f).proof());
    }

    @Test
    public void translatedRasterPhasePreservesOwnership() {
        assertTrue(new Figure(-.47f, 2).proof());
    }

    @Test
    public void upwardPartialUsesTheSameRasterEnvelope() {
        Figure f = normal();
        f.mirror(false, true);
        assertTrue(f.proof());
    }

    @Test
    public void rightOwnedPartialUsesTheSameRasterEnvelope() {
        Figure f = normal();
        f.mirror(true, false);
        assertTrue(f.proof());
    }

    @Test
    public void reflectedUpwardPartialUsesTheSameRasterEnvelope() {
        Figure f = normal();
        f.mirror(true, true);
        assertTrue(f.proof());
    }

    @Test
    public void printedRulesMayOccludeOutsideEdgeSamples() {
        Figure f = normal();
        f.staff(90.75f);
        assertTrue(f.proof());
    }

    @Test
    public void aCueOnThePrintedRuleKeepsItsContour() {
        Figure f = normal();
        f.staff(90.75f);
        f.oval(102, 137, 6, 4);
        assertFalse(f.proof());
    }

    @Test
    public void ordinaryOvalAddsUnexplainedContour() {
        Figure f = normal();
        f.oval(102, 137, 9, 9);
        assertFalse(f.proof());
    }

    @Test
    public void smallOvalAddsUnexplainedContour() {
        Figure f = normal();
        f.oval(102, 137, 6, 4);
        assertFalse(f.proof());
    }

    @Test
    public void sideAttachedCueRetainsItsIndependentContour() {
        Figure f = normal();
        f.oval(84, 133, 6, 4);
        f.left = 88;
        assertFalse(f.proof());
    }

    @Test
    public void paperGapPreventsACompletePrimary() {
        Figure f = normal();
        for (int x = 115; x <= 119; x++)
            for (int y = 120; y < 170; y++) f.gray[y * f.w + x] = (byte) 255;
        assertFalse(f.proof());
    }

    @Test
    public void anAbsentSecondaryDoesNotBecomeAQuantizedBeam() {
        Figure f = normal();
        for (int x = 93; x <= 110; x++)
            for (int y = 120; y < 140; y++) f.gray[y * f.w + x] = (byte) 255;
        assertFalse(f.proof());
    }

    @Test
    public void opposingShaftsCannotSupplyOneVoice() {
        Figure f = normal();
        f.b[2] = -1;
        assertFalse(f.proof());
    }
}
