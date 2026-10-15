// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic staff, paired repeat dots, and unequal rails. */
public class RepeatColumnProofTest {
    static final int W = 600, H = 240;

    private void rect(byte[] g, int x, int y, int w, int h, int tone) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = (byte) tone;
    }

    byte[] page(boolean dots, boolean thick, boolean bothBlocked) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 120; y <= 168; y += 12) rect(g, 40, y, 520, 1, 0);
        rect(g, 290, 120, thick ? 6 : 2, 49, 0);
        rect(g, 300, 120, 2, 49, 0);
        if (dots)
            for (int cy : new int[] {138, 150})
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        if (dx * dx + dy * dy <= 5) g[(cy + dy) * W + 312 + dx] = 0;
        rect(g, 335, 131, 48, 3, 255);
        if (bothBlocked) rect(g, 240, 131, 48, 3, 255);
        return g;
    }

    @Test
    public void clearOppositeStaffFlankProvesBothRails() {
        var b = PrintedRepeatGlyphs.provedColumns(page(true, true, false), W, H, 120, 168, 12);
        assertTrue(b.get(292));
        assertTrue(b.get(300));
        assertFalse(b.get(312));
    }

    @Test
    public void missingDotsCannotOverrideHeadGuard() {
        assertTrue(
                PrintedRepeatGlyphs.provedColumns(page(false, true, false), W, H, 120, 168, 12)
                        .isEmpty());
    }

    @Test
    public void equalThinRailsDoNotMakeRepeat() {
        assertTrue(
                PrintedRepeatGlyphs.provedColumns(page(true, false, false), W, H, 120, 168, 12)
                        .isEmpty());
    }

    @Test
    public void missingBothStaffWitnessesAbstains() {
        assertTrue(
                PrintedRepeatGlyphs.provedColumns(page(true, true, true), W, H, 120, 168, 12)
                        .isEmpty());
    }
}
