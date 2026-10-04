// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rounded disks and rectangular beam bands, never source score pixels. */
public class ThickBeamDotCoreTest {
    private static final int W = 300, H = 180;
    private final byte[] gray = new byte[W * H];

    public ThickBeamDotCoreTest() {
        Arrays.fill(gray, (byte) 250);
    }

    private void band(int thickness, float slope) {
        for (int x = 112; x <= 220; x++) {
            int center = 74 + Math.round((x - 130) * slope);
            for (int y = center - thickness / 2; y < center - thickness / 2 + thickness; y++)
                gray[y * W + x] = 20;
        }
    }

    private void roundDotWithTie(int thickness, float slope) {
        for (int y = 76; y <= 84; y++)
            for (int x = 122; x <= 130; x++)
                if ((x - 126) * (x - 126) + (y - 80) * (y - 80) <= 17) gray[y * W + x] = 20;
        for (int x = 126; x <= 210; x++) {
            int center = 80 + Math.round((x - 126) * slope);
            for (int y = center - thickness / 2; y < center - thickness / 2 + thickness; y++)
                gray[y * W + x] = 20;
        }
    }

    private int count() throws Exception {
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var head = ctor.newInstance(260, 90, 112, 71, 89, 101f, 80f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        type,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        return (int)
                method.invoke(
                        null, List.of(), head, 17.25f, gray, W, H, false, List.of(), List.of(head));
    }

    @Test
    public void horizontalThickBeamCannotSupplyDotDisks() throws Exception {
        band(8, 0);
        assertEquals(0, count());
    }

    @Test
    public void sevenPixelBeamCannotSupplyDotDisks() throws Exception {
        band(7, 0);
        assertEquals(0, count());
    }

    @Test
    public void gentlySlopedThickBeamCannotSupplyDotDisks() throws Exception {
        band(8, .08f);
        assertEquals(0, count());
    }

    @Test
    public void roundedDotWithTwoPixelTieStillCounts() throws Exception {
        roundDotWithTie(2, 0);
        assertEquals(1, count());
    }

    @Test
    public void roundedDotWithSlopingTieStillCounts() throws Exception {
        roundDotWithTie(2, -.2f);
        assertEquals(1, count());
    }

    @Test
    public void beamCheckDoesNotRewriteRaster() throws Exception {
        band(8, 0);
        var before = gray.clone();
        count();
        assertArrayEquals(before, gray);
    }
}
