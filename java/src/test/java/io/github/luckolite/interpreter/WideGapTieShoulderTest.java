// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Original asymmetric returning curve with a semantic island, not score pixels. */
public class WideGapTieShoulderTest {
    private ShortTieBowlTest.Page page(int mode) {
        var p = new ShortTieBowlTest.Page(150);
        if (mode != 0)
            for (int x = 106; x <= 134; x++) {
                float t = (x - 106) / 28f;
                if (mode == 2 && t > .4f && t < .6f) continue;
                int y = Math.round(146 + 10 * 4 * t * (1 - t));
                for (int dy = -1; dy <= 1; dy++) p.rect(x, y + dy, 1, 1, 5);
            }
        if (mode != 0)
            for (int y = 150; y <= 157; y++) for (int x = 120; x <= 128; x++) p.a[y * p.w + x] = 2;
        else p.oval(125, 156, 5, 4, false, false);
        return p;
    }

    private int bowls(int mode) throws Exception {
        return bowls(page(mode));
    }

    private int bowls(ShortTieBowlTest.Page p) throws Exception {
        var c = OmrScoreInterpreter.class;
        var f =
                c.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        f.setAccessible(true);
        var heads = (java.util.List<?>) f.invoke(null, p.a, p.w, p.h, (byte) 2);
        var st =
                c.getDeclaredMethod(
                        "findStaffs",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        java.util.List.class);
        st.setAccessible(true);
        var staffs =
                (java.util.List<?>)
                        st.invoke(
                                null,
                                p.a,
                                p.g,
                                p.w,
                                p.h,
                                java.util.List.of(new MeasureRegion(.05f, .95f, .3f, .85f)));
        var m =
                c.getDeclaredMethod(
                        "shortTieBowlHeads",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        java.util.List.class,
                        java.util.List.class);
        m.setAccessible(true);
        var a = p.a.clone();
        var g = p.g.clone();
        int count = ((java.util.List<?>) m.invoke(null, p.a, p.g, p.w, p.h, heads, staffs)).size();
        assertArrayEquals(a, p.a);
        assertArrayEquals(g, p.g);
        return count;
    }

    @Test
    public void ownershipProvesSemanticIsland() throws Exception {
        assertEquals(1, bowls(1));
    }

    @Test
    public void completeReturningCurveKeepsOnlyEndpoints() {
        assertEquals(2, page(1).notes().size());
    }

    @Test
    public void completeReturningCurveTiesEndpoints() {
        var n = page(1).notes();
        assertTrue(n.get(n.size() - 1).tiedFromPrevious());
    }

    @Test
    public void unrelatedSmallPitchSurvives() {
        assertEquals(3, page(0).notes().size());
    }

    @Test
    public void brokenCurveDoesNotTie() {
        var n = page(2).notes();
        assertFalse(n.get(n.size() - 1).tiedFromPrevious());
    }

    @Test
    public void inputMasksUnchanged() {
        var p = page(1);
        var a = p.a.clone();
        var g = p.g.clone();
        p.notes();
        assertArrayEquals(a, p.a);
        assertArrayEquals(g, p.g);
    }

    private ShortTieBowlTest.Page transformed(boolean mirror, int scale) {
        var p = page(1);
        int w = p.w, h = p.h;
        byte[] a = p.a, g = p.g;
        p.w *= scale;
        p.h *= scale;
        p.a = new byte[p.w * p.h];
        p.g = new byte[p.w * p.h];
        for (int y = 0; y < p.h; y++)
            for (int x = 0; x < p.w; x++) {
                int xx = x / scale, yy = y / scale;
                if (mirror) {
                    xx = w - 1 - xx;
                    yy = h - 1 - yy;
                }
                p.a[y * p.w + x] = a[yy * w + xx];
                p.g[y * p.w + x] = g[yy * w + xx];
            }
        return p;
    }

    @Test
    public void mirroredUpperCurveOwnsItsIsland() throws Exception {
        assertEquals(1, bowls(transformed(true, 1)));
    }

    @Test
    public void enlargedCurveOwnsItsIsland() throws Exception {
        assertEquals(1, bowls(transformed(false, 2)));
    }

    @Test
    public void enlargedMirroredCurveOwnsItsIsland() throws Exception {
        assertEquals(1, bowls(transformed(true, 2)));
    }

    @Test
    public void noCurveCannotDemoteSmallPitch() throws Exception {
        assertEquals(0, bowls(0));
    }

    @Test
    public void brokenCurveCannotDemoteIsland() throws Exception {
        assertEquals(0, bowls(2));
    }

    @Test
    public void ownStemPreservesSmallHead() throws Exception {
        var p = page(1);
        p.rect(127, 110, 2, 45, 1);
        assertEquals(0, bowls(p));
    }

    @Test
    public void absentEndpointStemPreventsOwnership() throws Exception {
        var p = page(1);
        for (int y = 90; y < 132; y++)
            for (int x = 110; x < 112; x++) {
                p.a[y * p.w + x] = 0;
                p.g[y * p.w + x] = (byte) 255;
            }
        assertEquals(0, bowls(p));
    }
}
