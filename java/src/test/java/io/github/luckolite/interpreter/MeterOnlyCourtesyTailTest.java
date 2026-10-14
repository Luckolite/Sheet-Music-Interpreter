// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original binary geometry controls, not score-derived pixels. */
public final class MeterOnlyCourtesyTailTest {
    static final int W = 300, H = 180, LEFT = 180, RIGHT = 270, TOP = 60, BOTTOM = 124;
    static final float GAP = 16;

    record Page(byte[] labels, byte[] gray) {}

    static void rect(Page p, int x, int y, int w, int h, int label, int gray) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                p.labels[yy * W + xx] = (byte) label;
                p.gray[yy * W + xx] = (byte) gray;
            }
    }

    static Page page() {
        var p = new Page(new byte[W * H], new byte[W * H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int y = TOP; y <= BOTTOM; y += 16) rect(p, LEFT, y, W - LEFT, 1, 4, 0);
        rect(p, LEFT, TOP, 2, BOTTOM - TOP + 1, 1, 0);
        // Broad upper numeral ink; two separate closed lower counters form the8.
        rect(p, 207, 65, 4, 21, 5, 0);
        rect(p, 204, 65, 7, 3, 5, 0);
        rect(p, 220, 65, 4, 20, 5, 0);
        rect(p, 220, 65, 14, 4, 5, 0);
        rect(p, 220, 82, 14, 4, 5, 0);
        rect(p, 230, 69, 4, 13, 5, 0);
        rect(p, 218, 91, 16, 29, 5, 0);
        rect(p, 222, 95, 8, 7, 0, 255);
        rect(p, 222, 109, 8, 7, 0, 255);
        return p;
    }

    static boolean detect(Page p) {
        return OmrMeasurePostProcessor.courtesySignatureTail(
                p.labels, p.gray, W, H, LEFT, RIGHT, TOP, BOTTOM, GAP, 35, 150);
    }

    @Test
    public void isolatedStackedEightTailIsNotABar() {
        assertTrue(detect(page()));
    }

    @Test
    public void noteAnywhereAfterBoundaryRetainsBar() {
        var p = page();
        rect(p, 280, 110, 6, 4, 2, 0);
        assertFalse(detect(p));
    }

    @Test
    public void unownedRestInkRetainsBar() {
        var p = page();
        rect(p, 250, 95, 7, 8, 1, 0);
        assertFalse(detect(p));
    }

    @Test
    public void noBoundaryCannotRemoveBar() {
        var p = page();
        rect(p, LEFT, TOP, 2, BOTTOM - TOP + 1, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void denominatorAloneIsNotAMeter() {
        var p = page();
        rect(p, 200, TOP, 40, 29, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void openCounterIsNotEight() {
        var p = page();
        rect(p, 218, 95, 5, 7, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void wideStaffCellsDoNotCountAsDigitHoles() {
        var p = page();
        rect(p, 210, 89, 40, 34, 0, 255);
        for (int y = 92; y < 125; y += 16) rect(p, 210, y, 40, 1, 4, 0);
        rect(p, 210, 92, 1, 33, 1, 0);
        rect(p, 249, 92, 1, 33, 1, 0);
        assertFalse(detect(p));
    }

    @Test
    public void endInkWithoutSemanticOwnershipIsNotDiscarded() {
        var p = page();
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 5) p.labels[i] = 0;
        assertFalse(detect(p));
    }
}
