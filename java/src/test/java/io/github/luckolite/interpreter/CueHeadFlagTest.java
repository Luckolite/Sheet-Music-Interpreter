// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original small-note raster geometry; no score images or copied source coordinates. */
public class CueHeadFlagTest {
    private int count(
            boolean up,
            boolean flag,
            boolean detached,
            boolean broken,
            boolean continuation,
            int scale,
            int ink)
            throws Exception {
        int w = 260 * scale, h = 180 * scale, cx = 110 * scale, cy = (up ? 110 : 70) * scale;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int rail : new int[] {40, 56, 72, 88, 104})
            for (int y = rail * scale; y < rail * scale + scale; y++)
                for (int x = 20 * scale; x < 240 * scale; x++) {
                    g[y * w + x] = (byte) ink;
                    l[y * w + x] = 4;
                }
        int rx = 6 * scale, ry = 5 * scale;
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if ((x - cx) * (x - cx) / (double) (rx * rx)
                                + (y - cy) * (y - cy) / (double) (ry * ry)
                        <= 1) {
                    g[y * w + x] = (byte) ink;
                    l[y * w + x] = 2;
                }
        int shaft = up ? cx + rx : cx - rx, end = cy + (up ? -35 : 35) * scale;
        for (int y = Math.min(cy, end); y <= Math.max(cy, end); y++) {
            if (broken && Math.abs(y - cy) >= 12 * scale && Math.abs(y - cy) <= 18 * scale)
                continue;
            for (int dx = 0; dx < scale; dx++) {
                g[y * w + shaft + dx] = (byte) ink;
                l[y * w + shaft + dx] = 1;
            }
        }
        if (flag)
            for (int d = 0; d <= 22 * scale; d++) {
                int x =
                        shaft
                                + (detached ? 12 * scale : 0)
                                + Math.round(
                                        12 * scale * (float) Math.sin(Math.PI * d / (22 * scale)));
                int y = end + (up ? d : -d);
                for (int dx = 0; dx < 2 * scale; dx++) {
                    g[y * w + x + dx] = (byte) ink;
                    l[y * w + x + dx] = 5;
                }
            }
        if (continuation)
            for (int dx = 0; dx <= 19 * scale; dx++)
                for (int dy = 0; dy < 2 * scale; dy++) {
                    int x = shaft - dx, y = end + (up ? -1 : 1) * (3 * scale + dx / 2 + dy);
                    g[y * w + x] = (byte) ink;
                    l[y * w + x] = 5;
                }
        byte[] beforeG = g.clone(), beforeL = l.clone();
        Class<?> core = OmrScoreInterpreter.class;
        Class<?> headType = Class.forName(core.getName() + "$Component"),
                staffType = Class.forName(core.getName() + "$Staff");
        var headConstructor = headType.getDeclaredConstructors()[0];
        headConstructor.setAccessible(true);
        Object head =
                headConstructor.newInstance(
                        100 * scale * scale,
                        cx - rx,
                        cx + rx,
                        cy - ry,
                        cy + ry,
                        (float) cx,
                        (float) cy);
        var staffConstructor =
                staffType.getDeclaredConstructor(float.class, float.class, float.class);
        staffConstructor.setAccessible(true);
        Object staff = staffConstructor.newInstance(40f * scale, 104f * scale, 16f * scale);
        var method =
                core.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        headType,
                        staffType);
        method.setAccessible(true);
        int result = (int) method.invoke(null, l, g, w, h, head, staff);
        assertArrayEquals(beforeG, g);
        assertArrayEquals(beforeL, l);
        return result;
    }

    @Test
    public void downStemCueRetainsItsEighthFlag() throws Exception {
        assertEquals(1, count(false, true, false, false, false, 1, 35));
    }

    @Test
    public void upStemCueRetainsItsEighthFlag() throws Exception {
        assertEquals(1, count(true, true, false, false, false, 1, 35));
    }

    @Test
    public void scaledCueUsesItsOwnGlyphSize() throws Exception {
        assertEquals(1, count(false, true, false, false, false, 2, 35));
        assertEquals(1, count(true, true, false, false, false, 2, 35));
    }

    @Test
    public void cueQuartersRemainQuarters() throws Exception {
        assertEquals(0, count(false, false, false, false, false, 1, 35));
        assertEquals(0, count(true, false, false, false, false, 1, 35));
    }

    @Test
    public void nearbyDetachedHookCannotSupplyAFlag() throws Exception {
        assertEquals(0, count(false, true, true, false, false, 1, 35));
        assertEquals(0, count(true, true, true, false, false, 1, 35));
    }

    @Test
    public void aBrokenShaftCannotBorrowTheHook() throws Exception {
        assertEquals(0, count(false, true, false, true, false, 1, 35));
        assertEquals(0, count(true, true, false, true, false, 1, 35));
    }

    @Test
    public void phrasingPastTheFreeEndRemainsASlur() throws Exception {
        assertEquals(0, count(false, true, false, false, true, 1, 35));
        assertEquals(0, count(true, true, false, false, true, 1, 35));
    }

    @Test
    public void fadedCueKeepsItsOwnRootedHook() throws Exception {
        assertEquals(1, count(false, true, false, false, false, 1, 120));
    }
}
