// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original mirrored three-beam drawings with an inner edge crossing the scan boundary. */
public class ClippedInnerBeamTest {
    static final int W = 300, H = 260;

    static int read(int thickness, int spacing, boolean up, int count, boolean detached)
            throws Exception {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int j = 0; j < 5; j++)
            for (int x = 20; x < 280; x++) {
                g[(90 + j * 14) * W + x] = (byte) 180;
                l[(90 + j * 14) * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        for (int y = 75; y <= 140; y++)
            for (int x = 139; x <= 141; x++) {
                g[y * W + x] = 20;
                l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        for (int j = 0; j < count; j++)
            for (int y = 140 - thickness - j * spacing; y < 140 - j * spacing; y++)
                for (int x = 115; x <= 141; x++) {
                    if (detached && j == 2 && y >= 112) continue;
                    g[y * W + x] = 20;
                    l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
                }
        for (int y = 68; y <= 82; y++)
            for (int x = 140; x <= 160; x++)
                if (Math.pow((x - 150) / 10.0, 2) + Math.pow((y - 75) / 7.0, 2) <= 1) {
                    g[y * W + x] = 20;
                    l[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        if (up) {
            byte[] gg = g.clone(), ll = l.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    g[(H - 1 - y) * W + (W - 1 - x)] = gg[y * W + x];
                    l[(H - 1 - y) * W + (W - 1 - x)] = ll[y * W + x];
                }
        }
        var hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = hc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object h =
                up
                        ? ctor.newInstance(220, 139, 159, 177, 191, 149f, 184f)
                        : ctor.newInstance(220, 140, 160, 68, 82, 150f, 75f);
        var sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var cs = sc.getDeclaredConstructor(float.class, float.class, float.class);
        cs.setAccessible(true);
        Object st = cs.newInstance(up ? 113f : 90f, up ? 169f : 146f, 14f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class,
                        int.class);
        m.setAccessible(true);
        byte[] oldGray = g.clone(), oldLabels = l.clone();
        int result = (int) m.invoke(null, l, g, W, H, h, st, List.of(h), up ? -1 : 1);
        assertArrayEquals(oldGray, g);
        assertArrayEquals(oldLabels, l);
        return result;
    }

    @Test
    public void downwardThirdBeamCompletesItsClippedBody() throws Exception {
        assertEquals(3, read(7, 11, false, 3, false));
    }

    @Test
    public void upwardThirdBeamCompletesItsClippedBody() throws Exception {
        assertEquals(3, read(7, 11, true, 3, false));
    }

    @Test
    public void narrowerDownwardThirdBodyIsRetained() throws Exception {
        assertEquals(3, read(6, 11, false, 3, false));
    }

    @Test
    public void narrowerUpwardThirdBodyIsRetained() throws Exception {
        assertEquals(3, read(6, 11, true, 3, false));
    }

    @Test
    public void twoBeamsStayTwoInBothDirections() throws Exception {
        for (boolean up : new boolean[] {false, true}) assertEquals(2, read(7, 11, up, 2, false));
    }

    @Test
    public void singleBeamStaysSingleInBothDirections() throws Exception {
        for (boolean up : new boolean[] {false, true}) assertEquals(1, read(7, 11, up, 1, false));
    }

    @Test
    public void detachedInnerStrokeCannotCrossWhiteGapDownward() throws Exception {
        assertEquals(2, read(7, 11, false, 3, true));
    }

    @Test
    public void detachedInnerStrokeCannotCrossWhiteGapUpward() throws Exception {
        assertEquals(2, read(7, 11, true, 3, true));
    }
}
