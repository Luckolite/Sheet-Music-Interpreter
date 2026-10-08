// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete bow drawings and independent articulation controls. */
public final class BowFragmentOwnershipTest {
    private static final int W = 1000, H = 1000;
    private static final float G = 12;

    private static byte[] paper(int value) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) value);
        return p;
    }

    private static void ink(byte[] p, int l, int t, int r, int b, int tone) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) p[y * W + x] = (byte) tone;
    }

    private static void line(byte[] p, int x0, int y0, int x1, int y1, int tone) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + (x1 - x0) * i / (float) steps),
                    y = Math.round(y0 + (y1 - y0) * i / (float) steps);
            ink(p, x, y, x + 1, y, tone);
        }
    }

    private static byte[] up() {
        byte[] p = paper(160);
        line(p, 166, 70, 180, 103, 132);
        line(p, 180, 103, 190, 70, 132);
        return p;
    }

    private static byte[] down() {
        byte[] p = paper(160);
        ink(p, 168, 70, 190, 72, 132);
        ink(p, 168, 70, 170, 102, 132);
        ink(p, 188, 70, 190, 102, 132);
        return p;
    }

    private static boolean owns(byte[] p, int l, int t, int r, int b) throws Exception {
        Class<?> glyph =
                Class.forName("io.github.luckolite.interpreter.NoteArticulationDetector$Glyph");
        var ctor =
                glyph.getDeclaredConstructor(
                        int.class, int.class, int.class, int.class, int.class, int[].class);
        ctor.setAccessible(true);
        int[] pixels = new int[(r - l + 1) * (b - t + 1)];
        int n = 0;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) if ((p[y * W + x] & 255) < 150) pixels[n++] = y * W + x;
        Object g = ctor.newInstance(l, t, r, b, n, Arrays.copyOf(pixels, n));
        var m =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "completeBowOwns", glyph, byte[].class, int.class, int.class, float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, p, W, H, G);
    }

    private static int detect(byte[] p) {
        return NoteArticulationDetector.detect(
                new byte[p.length],
                p,
                W,
                H,
                List.of(new NoteArticulationDetector.Anchor(180, 130, G, 0)))[0];
    }

    @Test
    public void contrastedCompleteUpBowOwnsItsDarkTip() throws Exception {
        var p = up();
        ink(p, 178, 96, 182, 102, 25);
        assertTrue(owns(p, 178, 96, 182, 102));
    }

    @Test
    public void completeDownBowOwnsItsOwnDarkLeg() throws Exception {
        var p = down();
        ink(p, 168, 92, 170, 102, 25);
        assertTrue(owns(p, 168, 92, 170, 102));
    }

    @Test
    public void completeDownBowOwnsItsOwnCap() throws Exception {
        var p = down();
        ink(p, 176, 70, 182, 72, 25);
        assertTrue(owns(p, 176, 70, 182, 72));
    }

    @Test
    public void isolatedDotInsideOpenBowSpaceIsNotOwned() throws Exception {
        var p = up();
        ink(p, 178, 77, 181, 80, 25);
        assertFalse(owns(p, 178, 77, 181, 80));
    }

    @Test
    public void separateStaccatoBelowUpBowIsRetained() {
        var p = up();
        ink(p, 178, 114, 181, 117, 25);
        assertEquals(NoteArticulation.STACCATO, detect(p));
    }

    @Test
    public void separateStaccatoBelowDownBowIsRetained() {
        var p = down();
        ink(p, 178, 114, 181, 117, 25);
        assertEquals(NoteArticulation.STACCATO, detect(p));
    }

    @Test
    public void singleSlashCannotProveUpBow() throws Exception {
        var p = paper(160);
        line(p, 166, 70, 180, 103, 132);
        ink(p, 178, 96, 182, 102, 25);
        assertFalse(owns(p, 178, 96, 182, 102));
    }

    @Test
    public void incompleteCapCannotProveDownBow() throws Exception {
        var p = down();
        ink(p, 175, 67, 183, 75, 160);
        assertFalse(owns(p, 168, 92, 170, 102));
    }

    @Test
    public void oneLegCannotProveDownBow() throws Exception {
        var p = down();
        ink(p, 186, 76, 194, 105, 160);
        assertFalse(owns(p, 168, 92, 170, 102));
    }

    @Test
    public void blankPaperCannotProveBow() throws Exception {
        assertFalse(owns(paper(160), 178, 96, 182, 102));
    }

    @Test
    public void filledWedgeIsNotAnOpenBow() throws Exception {
        var p = paper(255);
        for (int y = 90; y <= 103; y++) {
            int r = Math.round(3 * (103 - y) / 13f);
            ink(p, 180 - r, y, 180 + r, y, 25);
        }
        assertFalse(owns(p, 177, 90, 183, 103));
        assertEquals(NoteArticulation.STACCATISSIMO, detect(p));
    }

    @Test
    public void upwardCaretIsGenuineMarcato() throws Exception {
        var p = paper(255);
        line(p, 174, 103, 180, 89, 25);
        line(p, 180, 89, 186, 103, 25);
        assertFalse(owns(p, 174, 89, 187, 103));
        assertEquals(NoteArticulation.MARCATO, detect(p));
    }

    @Test
    public void horizontalChevronIsGenuineAccent() throws Exception {
        var p = paper(255);
        line(p, 172, 90, 188, 96, 25);
        line(p, 188, 96, 172, 102, 25);
        assertFalse(owns(p, 172, 90, 189, 102));
        assertEquals(NoteArticulation.ACCENT, detect(p));
    }

    @Test
    public void flatDashIsGenuineTenuto() throws Exception {
        var p = paper(255);
        ink(p, 174, 99, 186, 99, 25);
        assertFalse(owns(p, 174, 99, 186, 99));
        assertEquals(NoteArticulation.TENUTO, detect(p));
    }

    @Test
    public void closedRectangleIsNotDownBow() throws Exception {
        var p = down();
        ink(p, 168, 100, 190, 102, 132);
        assertFalse(owns(p, 168, 92, 170, 102));
    }

    @Test
    public void connectedWordStrokeBeyondBoundedBoxIsNotBow() throws Exception {
        var p = up();
        line(p, 190, 70, 260, 70, 132);
        assertFalse(owns(p, 178, 99, 182, 103));
    }

    @Test
    public void sourcePixelsRemainUnchanged() throws Exception {
        var p = up();
        var before = p.clone();
        owns(p, 178, 99, 182, 103);
        assertArrayEquals(before, p);
    }
}
