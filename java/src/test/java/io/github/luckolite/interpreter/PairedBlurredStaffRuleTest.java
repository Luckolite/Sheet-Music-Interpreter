// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full-size beams near a locally blurred last rule on shaded paper. */
public class PairedBlurredStaffRuleTest {
    static final int W = 480, H = 240, G = 16, A = 220, B = 266, TOP = 96;

    byte[] image(int rails, int paper, boolean complete) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int y = TOP; y <= TOP + 4 * G; y += G) {
            if (!complete && y < TOP + 2 * G) continue;
            for (int x = 20; x < W - 20; x++)
                for (int dy = -1; dy <= 1; dy++) p[(y + dy) * W + x] = 60;
        }
        int[] blur = {100, 80, 65, 80, 100};
        for (int x = A - 3; x <= B + 3; x++)
            for (int dy = -2; dy <= 2; dy++) p[(160 + dy) * W + x] = (byte) blur[dy + 2];
        for (int n = 0; n < rails; n++)
            for (int x = A; x <= B; x++)
                for (int dy = -2; dy <= 2; dy++) p[(174 + n * 10 + dy) * W + x] = 20;
        for (int x : new int[] {A, B})
            for (int y = 120; y <= 176 + (rails - 1) * 10; y++) p[y * W + x] = 20;
        return p;
    }

    int count(byte[] p, int rails, boolean reverse) throws Exception {
        int[] a = {A, 176 + (rails - 1) * 10, 1}, b = {B, 176 + (rails - 1) * 10, 1};
        if (reverse) {
            var t = a;
            a = b;
            b = t;
        }
        try {
            var m =
                    PairedGraceBeamInk.class.getDeclaredMethod(
                            "countFullSize",
                            byte[].class,
                            int.class,
                            int.class,
                            int[].class,
                            int[].class,
                            float.class,
                            float.class);
            m.setAccessible(true);
            return (int) m.invoke(null, p, W, H, a, b, (float) G, (float) TOP);
        } catch (NoSuchMethodException e) {
            return PairedGraceBeamInk.countFullSize(p, W, H, a, b, G);
        }
    }

    @Test
    public void singleBeamCannotGainBlurredStaffRail() throws Exception {
        assertEquals(0, count(image(1, 140, true), 1, false));
    }

    @Test
    public void reversedPairStillRejectsStaffRail() throws Exception {
        assertEquals(0, count(image(1, 140, true), 1, true));
    }

    @Test
    public void twoActualBeamsSurviveStaffConfirmation() throws Exception {
        assertEquals(2, count(image(2, 140, true), 2, false));
    }

    @Test
    public void insufficientOtherRulesCannotEraseACore() throws Exception {
        assertEquals(2, count(image(1, 140, false), 1, false));
    }

    @Test
    public void brightPaperPreservesOriginalFullSizePath() throws Exception {
        assertEquals(
                PairedGraceBeamInk.countFullSize(
                        image(1, 250, true), W, H, new int[] {A, 176, 1}, new int[] {B, 176, 1}, G),
                count(image(1, 250, true), 1, false));
    }

    @Test
    public void rawInkIsReadOnly() throws Exception {
        var p = image(1, 140, true);
        var old = p.clone();
        count(p, 1, false);
        assertArrayEquals(old, p);
    }
}
