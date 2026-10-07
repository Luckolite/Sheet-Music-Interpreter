// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original compact returning curves on shaded paper, with detached printed shoulders. */
public class CompactTieRasterTest {
    static boolean detect(int mode, int side) throws Exception {
        int w = 180, h = 180, left = 60, right = 88, first = 66, last = 82;
        float cy = 100, gap = 17.5f;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 130);
        for (int x = first; x <= last; x++) {
            float t = (x - first) / (float) (last - first);
            if (mode == 3 && t > .52f) continue;
            float bend = mode == 1 ? 0 : mode == 2 ? 6 * t : 6 * 4 * t * (1 - t);
            int y = Math.round(cy + side * (gap * .65f + bend));
            for (int yy = y - 2; yy <= y + 2; yy++) {
                g[yy * w + x] = (byte) (mode == 4 ? 125 : 55);
                l[yy * w + x] = 5;
            }
        }
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasPrintedTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, l, g, w, h, left, right, cy, gap);
    }

    @Test
    public void detachedSmallBowBelowStillReturns() throws Exception {
        assertTrue(detect(0, 1));
    }

    @Test
    public void detachedSmallBowAboveStillReturns() throws Exception {
        assertTrue(detect(0, -1));
    }

    @Test
    public void smallStraightStrokeHasNoReturn() throws Exception {
        assertFalse(detect(1, 1));
    }

    @Test
    public void smallSlopingStrokeHasNoReturn() throws Exception {
        assertFalse(detect(2, 1));
    }

    @Test
    public void halfBowCannotJoin() throws Exception {
        assertFalse(detect(3, 1));
    }

    @Test
    public void paperShadeCannotJoin() throws Exception {
        assertFalse(detect(4, 1));
    }

    static boolean raster(float gap, int span, float offset, boolean returning) throws Exception {
        int w = 180, h = 180, left = 50, right = left + span;
        float cy = 100.37f;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 130);
        for (int x = left; x <= right; x++) {
            float t = (x - left) / (float) span;
            int y =
                    Math.round(
                            cy
                                    + gap * (offset + (returning ? .3f * 4 * t * (1 - t) : .3f * t))
                                    + 2 * (t - .5f));
            for (int yy = y - 1; yy <= y + 1; yy++) {
                g[yy * w + x] = 65;
                l[yy * w + x] = 5;
            }
        }
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasContinuousTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, l, g, w, h, left, right, cy, gap);
    }

    @Test
    public void fractionalStaffGapRetainsTheReturningRaster() throws Exception {
        assertTrue(raster(15.25f, 40, .65f, true));
    }

    @Test
    public void shortReturningRasterSurvivesThePixelBoundary() throws Exception {
        assertTrue(raster(15.75f, 32, .25f, true));
    }

    @Test
    public void widerRasterToleranceStillRejectsStraightCenter() throws Exception {
        assertFalse(raster(15.25f, 40, .65f, false));
    }

    @Test
    public void shortSlopingRasterHasNoReturn() throws Exception {
        assertFalse(raster(15.75f, 32, .25f, false));
    }
}
