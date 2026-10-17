// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original oval and rule geometry; no score pixels. */
public class IndependentLedgerCalibrationTest {
    int pitch(boolean outer, boolean staffRule, boolean wrongGap, int scale) throws Exception {
        int w = 220 * scale, h = 220 * scale;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        int cx = 100 * scale, cy = 124 * scale;
        for (int y = cy - 7 * scale; y <= cy + 7 * scale; y++)
            for (int x = cx - 10 * scale; x <= cx + 10 * scale; x++)
                if (Math.pow((x - cx) / (10d * scale), 2) + Math.pow((y - cy) / (7d * scale), 2)
                        <= 1) g[y * w + x] = 0;
        if (staffRule) for (int x = 20 * scale; x < 200 * scale; x++) g[100 * scale * w + x] = 0;
        for (int y = (wrongGap ? 118 : 114) * scale; y <= (wrongGap ? 119 : 115) * scale; y++)
            for (int x = 82 * scale; x <= 118 * scale; x++) g[y * w + x] = 0;
        if (outer)
            for (int y = 128 * scale; y <= 129 * scale; y++)
                for (int x = 82 * scale; x <= 118 * scale; x++) g[y * w + x] = 0;
        byte[] before = g.clone();
        Class<?> root = OmrScoreInterpreter.class, c = Class.forName(root.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head =
                ctor.newInstance(
                        242 * scale * scale,
                        90 * scale,
                        109 * scale,
                        118 * scale,
                        134 * scale,
                        100f * scale,
                        125.592f * scale);
        var m =
                root.getDeclaredMethod(
                        "printedPitchStep",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        float.class);
        m.setAccessible(true);
        int result = (int) m.invoke(null, g, w, h, head, 100f * scale, 13.25f * scale);
        assertArrayEquals(before, g);
        return result;
    }

    @Test
    public void independentRulesCalibrateFilledOval() throws Exception {
        assertEquals(-3, pitch(false, true, false, 1));
    }

    @Test
    public void visibleOuterLedgerRetainsOriginal() throws Exception {
        assertEquals(-4, pitch(true, true, false, 1));
    }

    @Test
    public void missingStaffRuleCannotRephase() throws Exception {
        assertEquals(-4, pitch(false, false, false, 1));
    }

    @Test
    public void conflictingRuleSeparationCannotRephase() throws Exception {
        assertEquals(-4, pitch(false, true, true, 1));
    }

    @Test
    public void doubledRasterRetainsCalibration() throws Exception {
        assertEquals(-3, pitch(false, true, false, 2));
    }
}
