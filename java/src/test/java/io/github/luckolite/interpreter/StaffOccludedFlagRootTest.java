// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original mirrored hooks crossed by a staff band; no score-derived pixels. */
public class StaffOccludedFlagRootTest {
    static final int W = 300, H = 260;
    byte[] g = new byte[W * H], labels = new byte[W * H];

    void setup(boolean up, boolean hook, boolean detached) {
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 145; y++) g[y * W + 100] = 0;
        if (hook)
            for (int d = detached ? 18 : 9; d <= 34; d++) {
                int x = 100 + Math.round(12 * (float) Math.sin(Math.PI * (d - 9) / 40));
                for (int dx = 0; dx < 1; dx++) g[(80 + d) * W + x + dx] = 0;
            }
        for (int y = 93; y <= 96; y++) for (int x = 20; x < 280; x++) g[y * W + x] = 0;
        if (!up) {
            byte[] old = g.clone();
            for (int y = 0; y < H; y++) System.arraycopy(old, y * W, g, (H - 1 - y) * W, W);
        }
    }

    boolean flag(boolean up) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        int cy = up ? 145 : H - 1 - 145;
        Object head = ct.newInstance(200, 80, 100, cy - 7, cy + 7, 90f, (float) cy);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasCurvedFlag",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        int.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, labels, g, W, H, head, 14f, 100, up ? 80 : H - 1 - 80, up);
    }

    @Test
    public void upperRootSurvivesRuleOcclusion() throws Exception {
        setup(true, true, false);
        assertTrue(flag(true));
    }

    @Test
    public void lowerRootSurvivesRuleOcclusion() throws Exception {
        setup(false, true, false);
        assertTrue(flag(false));
    }

    @Test
    public void ruleAloneCannotSupplyFlag() throws Exception {
        setup(true, false, false);
        assertFalse(flag(true));
    }

    @Test
    public void detachedHookCannotBorrowRule() throws Exception {
        setup(true, true, true);
        assertFalse(flag(true));
    }

    @Test
    public void pixelsRemainExact() throws Exception {
        setup(true, true, false);
        byte[] old = g.clone();
        flag(true);
        assertArrayEquals(old, g);
    }
}
