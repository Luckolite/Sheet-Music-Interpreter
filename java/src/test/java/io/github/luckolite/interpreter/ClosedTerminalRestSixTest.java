// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural closed-terminal curves, distinct from two-waisted eight/B/rotations. */
public class ClosedTerminalRestSixTest {
    static final int SW = 60, SH = 61;

    static byte[] shape(int kind) {
        byte[] g = new byte[SW * SH];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < SH; y++)
            for (int x = 0; x < SW; x++) {
                double center = 28 - 8 * y / 60.0;
                double radius = 20 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 30) / 30.0, 2)));
                boolean ink = x >= Math.ceil(center - radius) && x <= Math.floor(center + radius);
                if (kind == 0) {
                    int pinch = (int) Math.round(9 * Math.max(0, 1 - Math.abs(y - 30) / 13.0));
                    ink &= x <= Math.floor(center + radius) - pinch;
                }
                if (kind == 1 || kind == 3) {
                    ink =
                            Math.pow((x - 25) / 19.0, 2) + Math.pow((y - 16) / 16.0, 2) <= 1
                                    || Math.pow((x - 25) / 19.0, 2) + Math.pow((y - 44) / 16.0, 2)
                                            <= 1;
                    if (kind == 3) ink |= x >= 4 && x <= 12 && y >= 0 && y <= 60;
                }
                double holeX = (kind == 1 || kind == 3) ? 25 : 28 - 8 * y / 60.0;
                if (Math.pow((x - holeX) / 5.5, 2) + Math.pow((y - 16) / 7.5, 2) < 1
                        || Math.pow((x - holeX) / 6.5, 2) + Math.pow((y - 45) / 8.5, 2) < 1)
                    ink = false;
                if (ink) g[y * SW + x] = 0;
            }
        if (kind == 4)
            for (int y = 28; y <= 31; y++) for (int x = 0; x <= 12; x++) g[y * SW + x] = 0;
        return g;
    }

    static byte[] tight(byte[] g) {
        int l = SW, r = -1, t = SH, b = -1;
        for (int y = 0; y < SH; y++)
            for (int x = 0; x < SW; x++)
                if (g[y * SW + x] == 0) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
        int w = r - l + 1, h = b - t + 1;
        byte[] a = new byte[w * h + 2];
        a[0] = (byte) w;
        a[1] = (byte) h;
        for (int y = 0; y < h; y++) System.arraycopy(g, (t + y) * SW + l, a, 2 + y * w, w);
        return a;
    }

    static boolean six(byte[] original, boolean mirror, boolean rotate) throws Exception {
        byte[] q = tight(original);
        int w = q[0] & 255, h = q[1] & 255;
        byte[] a = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                a[y * w + x] =
                        q[2 + (rotate ? h - 1 - y : y) * w + ((mirror || rotate) ? w - 1 - x : x)];
        var m =
                MultiMeasureRestDetector.class.getDeclaredMethod(
                        "looksLikeSix",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, a, w, 180, 0, w - 1, 0, h - 1);
    }

    static int count(int kind) {
        int w = 400, h = 360;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        byte[] q = tight(shape(kind));
        int gw = q[0] & 255, gh = q[1] & 255;
        for (int y = 0; y < gh; y++)
            for (int x = 0; x < gw; x++) {
                g[(65 + y) * w + 200 - gw / 2 + x] = q[2 + y * gw + x];
                if (q[2 + y * gw + x] == 0) l[(65 + y) * w + 200 - gw / 2 + x] = 5;
            }
        for (int y = 144; y <= 272; y += 32)
            for (int x = 40; x <= 360; x++) {
                g[y * w + x] = 0;
                l[y * w + x] = 4;
            }
        for (int y = 214; y <= 226; y++)
            for (int x = 90; x <= 270; x++) {
                g[y * w + x] = 0;
                l[y * w + x] = 1;
            }
        byte[] before = g.clone();
        var result =
                MultiMeasureRestDetector.detect(
                        l, g, w, h, List.of(new MeasureRegion(.1f, .9f, .4f, .9f)), List.of());
        assertArrayEquals(before, g);
        assertEquals(1, result.size());
        return result.get(0).value();
    }

    @Test
    public void closedTerminalSixHasSixLogicalMeasures() {
        assertEquals(6, count(0));
    }

    @Test
    public void curvedUnilateralBodyProvesSixDespiteTwoPockets() throws Exception {
        assertTrue(six(shape(0), false, false));
    }

    @Test
    public void genuineTwoWaistedEightIsNotSix() throws Exception {
        assertFalse(six(shape(1), false, false));
    }

    @Test
    public void genuineEightCountRemainsEight() {
        assertEquals(8, count(1));
    }

    @Test
    public void rotatedClosedTerminalIsNotSix() throws Exception {
        assertFalse(six(shape(0), false, true));
    }

    @Test
    public void upperAndLowerBWithStraightSpineIsNotSix() throws Exception {
        assertFalse(six(shape(3), false, false));
    }

    @Test
    public void twoPocketOvalWithoutUnilateralWaistIsAmbiguous() throws Exception {
        assertFalse(six(shape(2), false, false));
    }

    @Test
    public void mirroredClosedTerminalIsNotSix() throws Exception {
        assertFalse(six(shape(0), true, false));
    }

    @Test
    public void foreignLeftBridgeCannotTurnEightIntoSix() throws Exception {
        assertFalse(six(shape(4), false, false));
    }
}
