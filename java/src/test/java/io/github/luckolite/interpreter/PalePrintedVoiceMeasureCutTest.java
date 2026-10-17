// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original asymmetric eight-eighth/half-quarter-quarter engraving with a printed bar. */
public class PalePrintedVoiceMeasureCutTest {
    private static final int W = 500, H = 240;
    private final MeasureRegion region = new MeasureRegion(.04f, .96f, 60f / H, 170f / H);

    private byte[] page(boolean bar, int paper, boolean shortStem) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int y = 80; y <= 144; y += 16) for (int x = 20; x < 480; x++) g[y * W + x] = 30;
        if (bar) for (int y = (shortStem ? 100 : 80); y <= 144; y++) g[y * W + 340] = 25;
        return g;
    }

    private List<ScoreNoteEvent> notes() {
        var a = new ArrayList<ScoreNoteEvent>();
        float[] x = {.05f, .12f, .2f, .28f, .36f, .44f, .52f, .6f, .76f, .85f, .93f};
        for (int i = 0; i < x.length; i++)
            a.add(
                    new ScoreNoteEvent(
                            0,
                            x[i],
                            i % 6,
                            0,
                            1,
                            .5f,
                            false,
                            0,
                            i < 8 ? 1 : 0,
                            2,
                            i == 8 ? 2 : i < 8 ? 0 : 1));
        return a;
    }

    private Float cut(byte[] g) {
        return PrintedVoiceMeasureCut.resolve(region, notes(), 0, g, W, H);
    }

    private byte[] pale(boolean bar, boolean extended, boolean second) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 245);
        for (int x = 20; x < 480; x++)
            for (int line = 0; line < 5; line++) {
                int y = 80 + 16 * line + Math.round((x - 250) * .006f);
                g[y * W + x] = (byte) 190;
            }
        if (bar)
            for (int y = 81 - (extended ? 18 : 0); y <= 145 + (extended ? 18 : 0); y++)
                g[y * W + 340] = (byte) 185;
        if (second) for (int y = 80; y <= 144; y++) g[y * W + 320] = (byte) 185;
        return g;
    }

    @Test
    public void faintTiltedFiveRulesRecoverPrintedOwnership() {
        assertEquals(.68f, cut(pale(true, false, false)), .003f);
    }

    @Test
    public void faintRulesWithoutSeparatorRemainUnresolved() {
        assertNull(cut(pale(false, false, false)));
    }

    @Test
    public void faintLongStemRemainsUnresolved() {
        assertNull(cut(pale(true, true, false)));
    }

    @Test
    public void twoFaintSeparatorsRemainAmbiguous() {
        assertNull(cut(pale(true, false, true)));
    }

    @Test
    public void uniformGrayPaperCannotProveStaff() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 190);
        assertNull(cut(g));
    }
}
