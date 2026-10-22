// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original gapped rule-band scenes, independent of private page pixels and dimensions. */
public class OriginalFrameRestRecoveryTest {
    private static final int W = 440, H = 260;
    private static final List<MeasureRegion> BARS =
            List.of(new MeasureRegion(.02f, .98f, .15f, .85f));

    private byte[] scene(int thickness, int asym, int lowered) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int line = 0; line < 5; line++) {
            int row =
                    Math.round(80 + line * 13.75f) - (line == 1 || line == 2 || line == 4 ? 1 : 0);
            for (int y = row - asym; y < row - asym + thickness; y++)
                for (int x = 12; x < W - 12; x++) g[y * W + x] = 0;
        }
        for (int y = 97 + lowered; y <= 135 + lowered; y++) {
            int x = 210 - (y - 97 - lowered) * 10 / 38;
            g[y * W + x] = 0;
            g[y * W + x + 1] = 0;
        }
        for (int[] bulb : new int[][] {{204, 100 + lowered}, {200, 114 + lowered}})
            oval(g, bulb[0], bulb[1], 5, 4);
        for (int x : new int[] {165, 250}) {
            oval(g, x, 142, 9, 7);
            for (int y = 84; y <= 142; y++) {
                g[y * W + x + 8] = 0;
                g[y * W + x + 9] = 0;
            }
        }
        for (int x = 173; x <= 259; x++) for (int y = 84; y <= 88; y++) g[y * W + x] = 0;
        return g;
    }

    private void oval(byte[] g, int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if ((x - cx) * (x - cx) / (double) (rx * rx)
                                + (y - cy) * (y - cy) / (double) (ry * ry)
                        <= 1) g[y * W + x] = 0;
    }

    private List<ScoreNoteEvent> notes() {
        return List.of(
                new ScoreNoteEvent(0, (165f / W - .02f) / .96f, -1, 0, 1, 142f / H, false, 0, 1)
                        .withStemDirection(1),
                new ScoreNoteEvent(0, (250f / W - .02f) / .96f, -1, 0, 1, 142f / H, false, 0, 2)
                        .withStemDirection(1));
    }

    private List<ScoreRestEvent> detect(byte[] g, float top) {
        return SixteenthRestDetector.detect(
                g,
                W,
                H,
                BARS,
                List.of(new SixteenthRestDetector.Staff(top, top + 55, 13.75f, 0, 1)),
                notes());
    }

    private void recovered(int thickness, int asym, int lowered, float top) {
        byte[] g = scene(thickness, asym, lowered), before = g.clone();
        var result = detect(g, top);
        assertEquals(result.toString(), 1, result.size());
        assertEquals(.25, result.get(0).durationBeats(), 0);
        assertArrayEquals(before, g);
    }

    @Test
    public void twoRowRulesRetainACompleteOriginalGlyph() {
        recovered(2, 0, 19, 80);
    }

    @Test
    public void threeRowRulesRetainACompleteOriginalGlyph() {
        recovered(3, 1, 19, 80);
    }

    @Test
    public void fourRowRulesRetainACompleteOriginalGlyph() {
        recovered(4, 1, 19, 80);
    }

    @Test
    public void lowerSupportedFrameKeepsItsOwnRasterPhase() {
        recovered(3, 0, 20, 81);
    }

    @Test
    public void widerLowerSupportedFrameKeepsItsOwnRasterPhase() {
        recovered(4, 0, 20, 81);
    }

    @Test
    public void aPrintedHeadCannotSupplyASecondRestGlyph() {
        byte[] g = scene(3, 1, 19);
        oval(g, 204, 133, 9, 7);
        assertTrue(detect(g, 80).stream().noneMatch(r -> r.durationBeats() == .25));
    }

    @Test
    public void originalFrameRequiresAllFiveSeparateRules() {
        byte[] g = scene(3, 1, 19);
        assertTrue(OriginalRestFrameRules.supported(g, W, H, 80, 135, 13.75f));
        for (int y = 105; y <= 109; y++) for (int x = 0; x < W; x++) g[y * W + x] = (byte) 255;
        assertFalse(OriginalRestFrameRules.supported(g, W, H, 80, 135, 13.75f));
    }

    @Test
    public void shortLocalStrokesCannotStandInForStaffRules() {
        byte[] g = scene(3, 1, 19);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) if (x < 140 || x > 220) g[y * W + x] = (byte) 255;
        assertFalse(OriginalRestFrameRules.supported(g, W, H, 80, 135, 13.75f));
    }

    @Test
    public void faintRulesCannotBorrowContrastFromGlyphBodies() {
        byte[] g = scene(3, 1, 19);
        for (int line = 0; line < 5; line++) {
            int row =
                    Math.round(80 + line * 13.75f) - (line == 1 || line == 2 || line == 4 ? 1 : 0);
            for (int y = row - 1; y <= row + 1; y++)
                for (int x = 12; x < W - 12; x++) g[y * W + x] = (byte) 220;
        }
        assertFalse(OriginalRestFrameRules.supported(g, W, H, 80, 135, 13.75f));
    }

    @Test
    public void inconsistentFrameGeometryIsRejected() {
        assertFalse(OriginalRestFrameRules.supported(scene(3, 1, 19), W, H, 80, 138, 13.75f));
    }

    @Test
    public void nonFiniteFrameIsRejected() {
        assertFalse(
                OriginalRestFrameRules.supported(scene(3, 1, 19), W, H, Float.NaN, 135, 13.75f));
    }
}
