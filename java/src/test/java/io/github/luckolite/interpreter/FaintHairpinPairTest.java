// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rasterized straight strokes only; no score pixels, coordinates or glyph masks. */
public class FaintHairpinPairTest {
    static final int W = 480, H = 260;
    static final float GAP = 14;

    static class Page {
        byte[] gray = new byte[W * H];

        Page(String mode, boolean reverse, float phase) {
            Arrays.fill(gray, (byte) 255);
            int left = 100, right = 212;
            for (int x = left; x <= right; x++) {
                int xx = reverse ? left + right - x : x;
                double d = x - left;
                int a = (int) Math.round(170 - .085 * d + phase),
                        b = (int) Math.round(170 + .085 * d + phase);
                if (mode.equals("parallel")) {
                    a = 160;
                    b = 178;
                }
                if (mode.equals("curved")) {
                    a += (int) Math.round(7 * Math.sin(Math.PI * d / (right - left)));
                    b += (int) Math.round(7 * Math.sin(Math.PI * d / (right - left)));
                }
                int ink = x >= right - 35 ? 80 : 238;
                if (mode.equals("allFaint")) ink = 238;
                if (mode.equals("oneCore") && x >= right - 35) stroke(xx, a, 238);
                else stroke(xx, a, ink);
                stroke(xx, b, ink);
                if (mode.equals("solid")) for (int y = a; y <= b; y++) gray[y * W + xx] = 80;
            }
            if (mode.equals("gap")) for (int y = 145; y <= 193; y++) gray[y * W + 143] = (byte) 255;
            if (mode.equals("blankTip"))
                for (int x = 100; x <= 120; x++)
                    for (int y = 145; y <= 193; y++) gray[y * W + x] = (byte) 255;
        }

        void stroke(int x, int y, int ink) {
            for (int d = -1; d <= 1; d++) gray[(y + d) * W + x] = (byte) ink;
        }
    }

    static List<ScoreDynamicChange> detect(Page p) {
        return ScoreDynamicsDetector.detect(
                List.of(),
                List.of(new PlayingTechniqueDetector.Staff(70, 126, GAP, 0, 1)),
                List.of(new MeasureRegion(.08f, .92f, .2f, .55f)),
                List.of(),
                p.gray,
                W,
                H);
    }

    static void recovered(boolean reverse, float phase) {
        var p = new Page("normal", reverse, phase);
        var a = detect(p);
        assertEquals(a.toString(), 1, a.size());
        assertEquals(reverse ? -1 : 1, a.get(0).direction());
    }

    @Test
    public void twoDarkCoresProveCompleteFaintCrescendo() {
        recovered(false, 0);
    }

    @Test
    public void twoDarkCoresProveCompleteFaintDiminuendo() {
        recovered(true, 0);
    }

    @Test
    public void independentSubpixelRasterPhasesRetainDirection() {
        for (float p : new float[] {.25f, .5f, .75f}) {
            recovered(false, p);
            recovered(true, p);
        }
    }

    @Test
    public void blankTipIsNeverExtrapolated() {
        assertTrue(detect(new Page("blankTip", false, 0)).isEmpty());
    }

    @Test
    public void singleWhiteInteriorColumnIsNeverFilled() {
        assertTrue(detect(new Page("gap", false, 0)).isEmpty());
    }

    @Test
    public void parallelLinesRemainExcluded() {
        assertTrue(detect(new Page("parallel", false, 0)).isEmpty());
    }

    @Test
    public void curvedArmsRemainExcluded() {
        assertTrue(detect(new Page("curved", false, 0)).isEmpty());
    }

    @Test
    public void oneDarkCoreCannotSupplyMissingOtherCore() {
        assertTrue(detect(new Page("oneCore", false, 0)).isEmpty());
    }

    @Test
    public void twoMerelyFaintLinesDoNotProposeTheirOwnDirection() {
        assertTrue(detect(new Page("allFaint", false, 0)).isEmpty());
    }

    @Test
    public void inputPixelsRemainUnchanged() {
        var p = new Page("normal", false, 0);
        byte[] before = p.gray.clone();
        detect(p);
        assertArrayEquals(before, p.gray);
    }

    @Test
    public void nearPaperArmsWithoutIndependentContrastStayAbsent() {
        for (int ink = 248; ink <= 254; ink++) {
            var p = new Page("normal", false, 0);
            for (int i = 0; i < p.gray.length; i++)
                if ((p.gray[i] & 255) == 238) p.gray[i] = (byte) ink;
            assertTrue("ink " + ink, detect(p).isEmpty());
        }
    }

    @Test
    public void faintDetachedNearbyArmsCannotExtendGenuineHairpin() {
        var p = new Page("normal", false, 0);
        for (int i = 0; i < p.gray.length; i++) if ((p.gray[i] & 255) == 238) p.gray[i] = 80;
        for (int x = 220; x <= 300; x++) {
            p.stroke(x, (int) Math.round(170 - .085 * (x - 100)), 250);
            p.stroke(x, (int) Math.round(170 + .085 * (x - 100)), 250);
        }
        assertEquals(1, detect(p).size());
    }

    @Test
    public void connectedNearbyPaleStrokesDoNotDuplicateGenuineHairpin() {
        var p = new Page("normal", false, 0);
        for (int i = 0; i < p.gray.length; i++) if ((p.gray[i] & 255) == 238) p.gray[i] = 80;
        for (int x = 212; x <= 300; x++) {
            p.stroke(x, (int) Math.round(170 - .085 * (x - 100)), 250);
            p.stroke(x, (int) Math.round(170 + .085 * (x - 100)), 250);
        }
        assertEquals(1, detect(p).size());
    }
}
