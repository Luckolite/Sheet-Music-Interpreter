// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rail-overlap caption bodies; no word-box rewrite or invented foreground. */
public final class CaptionRuleSeparatorOriginalTest {
    static final int W = 500, H = 400, L = 100, R = 299;

    static byte[] scene(int gap, int phase) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int bottom = 100 + phase + 4 * gap;
        for (int row = 100 + phase; row <= bottom; row += gap)
            for (int x = 20; x < 470; x++) {
                g[row * W + x] = 40;
                g[(row + 1) * W + x] = (byte) 128;
                g[(row + 2) * W + x] = (byte) 236;
            }
        for (int y = bottom + gap; y <= bottom + 2 * gap - 2; y++)
            for (int x = 120; x < 280; x++) if ((x - 120) % 20 < 8) g[y * W + x] = 60;
        return g;
    }

    static PlayingTechniqueDetector.Word word(int gap, int phase) {
        int bottom = 100 + phase + 4 * gap;
        return new PlayingTechniqueDetector.Word(
                "let pedal ring",
                L / (float) W,
                (bottom + .25f) / H,
                (R + 1) / (float) W,
                (bottom + 2 * gap + 1.25f) / H);
    }

    static float inspect(byte[] g, int gap, int phase) {
        return CaptionRuleSeparator.bodyTop(g, W, H, word(gap, phase), 100 + phase + 4 * gap, gap);
    }

    @Test
    public void fullRegisteredBandAndAllBodyPaperFrameAtSeveralPhases() {
        for (int gap : new int[] {12, 16, 20})
            for (int phase : new int[] {0, 1}) {
                byte[] g = scene(gap, phase), before = g.clone();
                var word = word(gap, phase);
                assertEquals(100 + phase + 5 * gap, inspect(g, gap, phase), 0);
                assertArrayEquals(before, g);
                assertEquals("let pedal ring", word.text());
                assertEquals(PlayingTechniqueDetector.WordAdmission.UNCLASSIFIED, word.admission());
            }
    }

    @Test
    public void genuineLetterTouchingRailDoesNotBorrowItsCap() {
        byte[] g = scene(16, 0);
        for (int y = 165; y <= 180; y++) g[y * W + 160] = 30;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void foreignStrongInkInFringeRefusesBandOwnership() {
        byte[] g = scene(16, 0);
        g[166 * W + 160] = 20;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void noCompletePaperSeparatorIncludingFaintBridgeRefuses() {
        byte[] g = scene(16, 0);
        for (int y = 167; y <= 180; y++) g[y * W + 160] = (byte) 254;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void missingIndependentRuleRefuses() {
        byte[] g = scene(16, 0);
        for (int y = 115; y <= 119; y++) for (int x = 20; x < 470; x++) g[y * W + x] = (byte) 255;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void clippedOrForeignBodyAtOriginalBoxEdgeRefuses() {
        byte[] g = scene(16, 0);
        g[184 * W + L] = 100;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
        g = scene(16, 0);
        g[197 * W + 160] = 80;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void paperShadingIsNotAnObservedWhiteFrame() {
        byte[] g = scene(16, 0);
        for (int y = 167; y <= 170; y++) for (int x = L; x <= R; x++) g[y * W + x] = (byte) 254;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
    }

    @Test
    public void weakBandWithoutDarkCoreAndClippedPhaseRefuse() {
        byte[] g = scene(16, 0);
        for (int y = 164; y <= 166; y++) for (int x = 20; x < 470; x++) g[y * W + x] = (byte) 200;
        assertTrue(Float.isNaN(inspect(g, 16, 0)));
        g = scene(16, 0);
        var old = word(16, 0);
        var clipped =
                new PlayingTechniqueDetector.Word(
                        old.text(), old.left(), 166.25f / H, old.right(), old.bottom());
        assertTrue(Float.isNaN(CaptionRuleSeparator.bodyTop(g, W, H, clipped, 164, 16)));
    }
}
