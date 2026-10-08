// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale curves with dark islands, detached marks and beamed note owners. */
public class PaperCurveTenutoOwnershipTest {
    private static final int W = 960, H = 400, GAP = 12;

    private static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 145);
        }

        void box(int l, int t, int r, int b, int tone, byte label) {
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) {
                    gray[y * W + x] = (byte) tone;
                    labels[y * W + x] = label;
                }
        }

        void dash(int cy) {
            box(475, cy, 485, cy + 1, 75, OmrMeasurePostProcessor.SYMBOL);
        }

        void curve(int cy, int side) {
            for (int x = 451; x <= 509; x++) {
                if (side < 0 && x > 485 || side > 0 && x < 475) continue;
                int y = cy + Math.round((x - 480) * .18f);
                box(x, y, x, y + 1, 100, OmrMeasurePostProcessor.SYMBOL);
            }
            dash(cy);
        }

        void beam() {
            box(486, 233, 487, 270, 40, OmrMeasurePostProcessor.STEM_OR_REST);
            box(451, 233, 530, 237, 40, OmrMeasurePostProcessor.SYMBOL);
        }

        int mark() {
            return NoteArticulationDetector.detect(
                    labels,
                    gray,
                    W,
                    H,
                    List.of(new NoteArticulationDetector.Anchor(480, 270, GAP, 0)))[0];
        }
    }

    @Test
    public void directPaperCurveIslandCannotBecomeTenuto() {
        Page p = new Page();
        p.curve(218, 0);
        assertEquals(0, p.mark());
    }

    @Test
    public void paperCurveAboveBeamStillBelongsToCurve() {
        Page p = new Page();
        p.beam();
        p.curve(218, 0);
        assertEquals(0, p.mark());
    }

    @Test
    public void detachedTenutoAboveBeamedNoteRemains() {
        Page p = new Page();
        p.beam();
        p.dash(218);
        assertEquals(NoteArticulation.TENUTO, p.mark());
    }

    @Test
    public void detachedTenutoBelowBeamedNoteRemains() {
        Page p = new Page();
        p.beam();
        p.dash(293);
        assertEquals(NoteArticulation.TENUTO, p.mark());
    }

    @Test
    public void oneSidedPaleInkCannotProveBilateralCurveOwnership() {
        for (int side : new int[] {-1, 1}) {
            Page p = new Page();
            p.curve(218, side);
            assertEquals(NoteArticulation.TENUTO, p.mark());
        }
    }

    @Test
    public void nearbyDisconnectedCurveCannotClaimTenuto() {
        Page p = new Page();
        p.curve(213, 0);
        p.dash(220);
        assertEquals(NoteArticulation.TENUTO, p.mark());
    }

    @Test
    public void curveProofPreservesSourcePlanes() {
        Page p = new Page();
        p.beam();
        p.curve(218, 0);
        byte[] g = p.gray.clone(), l = p.labels.clone();
        p.mark();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}
