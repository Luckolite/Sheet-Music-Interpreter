// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original model islands and engraved heads; no private fixture geometry. */
public final class ScaledPairedBeamAdmissionTest {
    private static final int W = 300, H = 230;
    private static final float G = 14;

    private record Draw(byte[] gray, List<Object> heads, Object island, List<Object> staffs) {}

    private static Constructor<?> component, staff;
    private static Method filter;

    static {
        try {
            Class<?>
                    c =
                            Class.forName(
                                    "io.github.luckolite.interpreter.OmrScoreInterpreter$Component"),
                    s = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
            component =
                    c.getDeclaredConstructor(
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class);
            component.setAccessible(true);
            staff = s.getDeclaredConstructor(float.class, float.class, float.class);
            staff.setAccessible(true);
            filter =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "beamJunctionHeads",
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class);
            filter.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Object head(int area, int l, int t, int r, int b, boolean down)
            throws Exception {
        if (down) {
            int old = t;
            t = H - 1 - b;
            b = H - 1 - old;
            old = l;
            l = W - 1 - r;
            r = W - 1 - old;
        }
        return component.newInstance(area, l, r, t, b, (l + r) * .5f, (t + b) * .5f);
    }

    private static void pixel(byte[] p, int x, int y, int shade, boolean down) {
        if (down) {
            y = H - 1 - y;
            x = W - 1 - x;
        }
        if (x >= 0 && x < W && y >= 0 && y < H) p[y * W + x] = (byte) shade;
    }

    private static void rect(byte[] p, int l, int t, int r, int b, int shade, boolean down) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) pixel(p, x, y, shade, down);
    }

    private static void oval(byte[] p, int x, int y, int rx, int ry, boolean down) {
        for (int yy = y - ry; yy <= y + ry; yy++)
            for (int xx = x - rx; xx <= x + rx; xx++)
                if ((xx - x) * (xx - x) / (double) (rx * rx)
                                + (yy - y) * (yy - y) / (double) (ry * ry)
                        <= 1) pixel(p, xx, yy, 25, down);
    }

    private static Draw draw(boolean down, int paper, int beams, int mainArea, boolean partner)
            throws Exception {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        rect(p, 100, 90, 132, 94, 25, down);
        if (beams == 2) rect(p, 100, 101, 132, 105, 25, down);
        oval(p, 90, 150, 11, 7, down);
        rect(p, 100, 90, 101, 150, 25, down);
        oval(p, 122, 154, 11, 7, down);
        if (partner) rect(p, 132, 90, 133, 154, 25, down);
        Object island = head(82, 96, 92, 106, 101, down),
                main = head(mainArea, 79, 143, 101, 157, down),
                other = head(mainArea, 111, 147, 133, 161, down);
        return new Draw(
                p, List.of(island, main, other), island, List.of(staff.newInstance(120f, 176f, G)));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> rejected(Draw d) throws Exception {
        return (List<Object>) filter.invoke(null, d.gray, W, H, d.heads, d.staffs);
    }

    private static void owned(boolean down, int paper) throws Exception {
        var d = draw(down, paper, 2, 220, true);
        var out = rejected(d);
        assertEquals(1, out.size());
        assertSame(d.island, out.get(0));
    }

    @Test
    public void shadedUpStemPairOwnsBoundedIslandWhenMainAreaIsBelowThreeTimes() throws Exception {
        owned(false, 136);
    }

    @Test
    public void shadedDownStemPairOwnsBoundedIslandWhenMainAreaIsBelowThreeTimes()
            throws Exception {
        owned(true, 136);
    }

    @Test
    public void brightUpStemPairOwnsSameScaleIsland() throws Exception {
        owned(false, 255);
    }

    @Test
    public void brightDownStemPairOwnsSameScaleIsland() throws Exception {
        owned(true, 255);
    }

    @Test
    public void oneBeamCannotUseReducedMainSizeFallback() throws Exception {
        assertTrue(rejected(draw(false, 136, 1, 220, true)).isEmpty());
    }

    @Test
    public void missingPartnerShaftCannotUseReducedMainSizeFallback() throws Exception {
        assertTrue(rejected(draw(false, 136, 2, 220, false)).isEmpty());
    }

    @Test
    public void disconnectedPairedRailsCannotOwnIsland() throws Exception {
        var d = draw(false, 136, 2, 220, true);
        rect(d.gray, 113, 86, 122, 111, 136, false);
        assertTrue(rejected(d).isEmpty());
    }

    @Test
    public void undersizedRealOwnersDoNotPassReducedAreaGate() throws Exception {
        assertTrue(rejected(draw(false, 136, 2, 190, true)).isEmpty());
    }

    @Test
    public void genuineTinyGraceOvalBesidePairedBeamsSurvives() throws Exception {
        var d = draw(false, 136, 2, 220, true);
        oval(d.gray, 101, 97, 6, 5, false);
        rect(d.gray, 106, 48, 107, 97, 25, false);
        assertTrue(rejected(d).isEmpty());
    }

    @Test
    public void genuineTinyGraceOvalBesideDownStemPairedBeamsSurvives() throws Exception {
        var d = draw(true, 136, 2, 220, true);
        oval(d.gray, 101, 97, 6, 5, true);
        rect(d.gray, 106, 48, 107, 97, 25, true);
        assertTrue(rejected(d).isEmpty());
    }

    @Test
    public void genuineSmallHeadOnSharedChordShaftSurvives() throws Exception {
        var d = draw(false, 136, 2, 220, true);
        oval(d.gray, 101, 97, 6, 5, false);
        assertTrue(rejected(d).isEmpty());
    }

    @Test
    public void genuineSmallDyadBesideSameBeamSurvives() throws Exception {
        var d = draw(false, 136, 2, 220, true);
        oval(d.gray, 101, 97, 6, 5, false);
        oval(d.gray, 101, 112, 6, 5, false);
        Object second = head(82, 95, 107, 107, 117, false);
        var heads = new ArrayList<>(d.heads);
        heads.add(second);
        assertTrue(rejected(new Draw(d.gray, heads, d.island, d.staffs)).isEmpty());
    }

    @Test
    public void twoBeamedTinyGraceHeadsCannotSupplyFullSizeOwners() throws Exception {
        var d = draw(false, 136, 2, 82, true);
        assertTrue(rejected(d).isEmpty());
    }

    @Test
    public void sourceRasterAndModelComponentsRemainUnchanged() throws Exception {
        var d = draw(false, 136, 2, 220, true);
        var before = d.gray.clone();
        var heads = new ArrayList<>(d.heads);
        rejected(d);
        assertArrayEquals(before, d.gray);
        assertEquals(heads, d.heads);
    }
}
