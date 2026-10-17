// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.*;
import java.util.*;

/** Licensed rendered words with original procedural dashes, detached hooks and staff geometry. */
public class DetachedFullOctaveOwnershipTest {
    static final int W = 520, H = 600, G = 16, LAST = 303, PHASE = 188;

    static class Page {
        byte[] g = new byte[W * H];
        int top;
        List<PlayingTechniqueDetector.Staff> staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 144, G, 0, 2),
                        new PlayingTechniqueDetector.Staff(260, 324, G, 1, 2),
                        new PlayingTechniqueDetector.Staff(410, 474, G, 0, 2));

        Page(int glyph, boolean below, int hook) throws Exception {
            Arrays.fill(g, (byte) 255);
            top = below ? 176 : 40;
            Object object = PrintedOctaveWordTest.class.getDeclaredField("G" + glyph).get(null);
            Class<?> c = object.getClass();
            var wm = c.getDeclaredMethod("width");
            var hm = c.getDeclaredMethod("height");
            var pm = c.getDeclaredMethod("pixels");
            wm.setAccessible(true);
            hm.setAccessible(true);
            pm.setAccessible(true);
            int width = (Integer) wm.invoke(object), height = (Integer) hm.invoke(object);
            byte[] pixels = (byte[]) pm.invoke(object);
            for (int y = 0; y < height; y++)
                System.arraycopy(pixels, y * width, g, (top + y) * W + 60, width);
            // Start after either the 8va or 15ma word; the terminal dash location is constant.
            int start = glyph < 2 ? 104 : 136;
            for (int x = start; x <= LAST; x++) if ((x - start) % 16 < 8) ink(x, top + 12, 0);
            for (int x = start; x <= LAST; x++) if ((x - start) % 16 < 8) ink(x, top + 13, 0);
            if (hook != 0) hook(316, top + 13, hook < 0 ? -1 : 1, false);
        }

        void ink(int x, int y, int gray) {
            g[y * W + x] = (byte) gray;
        }

        void hook(int x, int y, int direction, boolean fringe) {
            for (int yy = Math.min(y, y + direction * 9);
                    yy <= Math.max(y, y + direction * 9);
                    yy++) for (int xx = x; xx <= x + 1; xx++) ink(xx, yy, 90);
            if (fringe) {
                for (int yy = Math.min(y, y + direction * 9) - 1;
                        yy <= Math.max(y, y + direction * 9) + 1;
                        yy++)
                    for (int xx = x - 1; xx <= x + 2; xx++)
                        if ((g[yy * W + xx] & 255) == 255) ink(xx, yy, 190);
            }
        }

        List<ScoreNoteEvent> run() {
            var notes =
                    List.of(
                            note(.35f, 126, 0),
                            note(.35f, 300, 1),
                            note(.80f, 126, 0),
                            note(.80f, 300, 1),
                            note(.35f, 450, 0));
            return OctaveMarkDetector.apply(
                    List.of(), staffs, List.of(new MeasureRegion(0, 1, 0, 1)), notes, g, W, H);
        }
    }

    static ScoreNoteEvent note(float x, int y, int staff) {
        return new ScoreNoteEvent(0, x, 2, staff, 2, (float) y / H, false, 0, 0, 2, 1);
    }

    static void shifts(Page p, int upper, int lower) throws Exception {
        byte[] before = p.g.clone();
        var n = p.run();
        assertEquals(upper, n.get(0).octaveShift());
        assertEquals(lower, n.get(1).octaveShift());
        assertEquals(0, n.get(2).octaveShift());
        assertEquals(0, n.get(3).octaveShift());
        assertEquals(0, n.get(4).octaveShift());
        assertArrayEquals(before, p.g);
    }

    @Test
    public void completeAltaWordBelowRightHandWithDetachedUpHookMeansDown() throws Exception {
        shifts(new Page(0, true, -1), -1, 0);
    }

    @Test
    public void antiAliasFringeRetainsCompleteWordOwnership() throws Exception {
        var p = new Page(0, true, 0);
        p.hook(316, 189, -1, true);
        shifts(p, -1, 0);
    }

    @Test
    public void fifteenAltaBelowStaffWithUpHookMeansTwoDown() throws Exception {
        shifts(new Page(2, true, -1), -2, 0);
    }

    @Test
    public void ordinaryUpperAltaRemainsUpper() throws Exception {
        shifts(new Page(0, false, 1), 1, 0);
    }

    @Test
    public void ordinaryLowerBassaRemainsLower() throws Exception {
        shifts(new Page(1, true, -1), -1, 0);
    }

    @Test
    public void ordinaryUpperFifteenRemainsTwoUpper() throws Exception {
        shifts(new Page(2, false, 1), 2, 0);
    }

    @Test
    public void ordinaryLowerFifteenRemainsTwoLower() throws Exception {
        shifts(new Page(3, true, -1), -2, 0);
    }

    @Test
    public void missingHookDoesNotReverseAlta() throws Exception {
        shifts(new Page(0, true, 0), 0, 1);
    }

    @Test
    public void twoOpposedHooksCannotReverseAlta() throws Exception {
        var p = new Page(0, true, -1);
        p.hook(324, 189, 1, false);
        shifts(p, 0, 1);
    }

    @Test
    public void unrelatedOutOfPhaseStrokeCannotReverseAlta() throws Exception {
        var p = new Page(0, true, 0);
        p.hook(316, 186, -1, false);
        shifts(p, 0, 1);
    }

    @Test
    public void tallBarlineCannotReverseAlta() throws Exception {
        var p = new Page(0, true, 0);
        for (int y = 158; y <= 218; y++) for (int x = 316; x <= 317; x++) p.ink(x, y, 0);
        shifts(p, 0, 1);
    }

    @Test
    public void wideBlockCannotReverseAlta() throws Exception {
        var p = new Page(0, true, 0);
        for (int y = 180; y <= 189; y++) for (int x = 316; x <= 327; x++) p.ink(x, y, 0);
        shifts(p, 0, 1);
    }

    @Test
    public void nearbySeparateDarkStrokeRejectsAmbiguousEndpoint() throws Exception {
        var p = new Page(0, true, -1);
        for (int y = 180; y <= 189; y++) p.ink(313, y, 0);
        shifts(p, 0, 1);
    }

    @Test
    public void missingChainCannotCreateReversedSpan() throws Exception {
        var p = new Page(0, true, -1);
        for (int y = 188; y <= 189; y++) for (int x = 104; x <= 303; x++) p.ink(x, y, 255);
        shifts(p, 0, 0);
    }

    @Test
    public void connectedUpperAltaDownHookRemainsUpper() throws Exception {
        var p = new Page(0, false, 0);
        p.hook(302, 53, 1, false);
        shifts(p, 1, 0);
    }

    @Test
    public void connectedLowerBassaUpHookRemainsLower() throws Exception {
        var p = new Page(1, true, 0);
        p.hook(302, 189, -1, false);
        shifts(p, -1, 0);
    }

    @Test
    public void fragmentedUnconnectedVerticalDotsCannotReverseAlta() throws Exception {
        var p = new Page(0, true, 0);
        for (int y : new int[] {179, 182, 185, 188})
            for (int x = 316; x <= 317; x++) p.ink(x, y, 0);
        shifts(p, 0, 1);
    }

    @Test
    public void isolatedDarkPixelInOuterPaperRingRejectsEndpoint() throws Exception {
        var p = new Page(0, true, -1);
        p.ink(314, 185, 0);
        shifts(p, 0, 1);
    }
}
