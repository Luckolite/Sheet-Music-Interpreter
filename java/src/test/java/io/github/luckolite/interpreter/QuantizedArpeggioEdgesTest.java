// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original thick strokes with independently quantized edges, no source score pixels. */
public class QuantizedArpeggioEdgesTest {
    boolean bar(int kind, int scale, double phase, int paper, int ink) throws Exception {
        int w = 280 * scale, h = 220 * scale;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) paper);
        int[] rows = {70 * scale, 88 * scale, 106 * scale, 124 * scale, 142 * scale};
        float slope = kind == 5 ? .08f : 0;
        for (int y : rows) for (int x = 20 * scale; x < 260 * scale; x++) g[y * w + x] = (byte) ink;
        for (int y = rows[0] - 3 * scale; y <= rows[4] + 3 * scale; y++) {
            double angle = (y - rows[0]) * 2 * Math.PI / (18 * scale) + phase;
            double axis = 140 * scale - slope * (y - 106 * scale),
                    left = -3 * scale,
                    right = 3 * scale;
            if (kind == 1) {
                left += 2 * scale * Math.sin(angle);
                right += 1.5 * scale * Math.cos(angle);
            }
            if (kind == 2) {
                double bulge = Math.max(0, 1 - Math.abs(y - 106 * scale) / (14d * scale));
                left -= 2 * scale * bulge;
                right += 2 * scale * bulge;
            }
            if (kind == 3) left += 2 * scale * Math.sin(angle);
            if (kind == 4) {
                double jitter = .45 * scale * Math.sin(angle);
                left += jitter;
                right += jitter;
            }
            for (int x = (int) Math.round(axis + left); x <= Math.round(axis + right); x++)
                g[y * w + x] = (byte) ink;
        }
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineSpansStaff",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, w, h, 140 * scale, rows, 18f * scale, 0f, slope);
    }

    @Test
    public void quantizedEdgesRejectWavesAcrossPhases() throws Exception {
        for (double p : new double[] {0, .4, .8, 1.2})
            assertFalse("phase=" + p, bar(1, 1, p, 255, 0));
    }

    @Test
    public void enlargedWaveRetainsDecision() throws Exception {
        assertFalse(bar(1, 2, .4, 255, 0));
    }

    @Test
    public void grayPaperWaveRetainsDecision() throws Exception {
        assertFalse(bar(1, 1, .4, 210, 100));
    }

    @Test
    public void straightAndTiltedBarsRemain() throws Exception {
        assertTrue(bar(0, 1, 0, 255, 0));
        assertTrue(bar(5, 1, 0, 255, 0));
    }

    @Test
    public void isolatedBulgeDoesNotImplyRepeatedWave() throws Exception {
        assertTrue(bar(2, 1, 0, 255, 0));
    }

    @Test
    public void oneSidedRoughnessNeedsSecondEdgeProof() throws Exception {
        assertTrue(bar(3, 1, 0, 255, 0));
    }

    @Test
    public void subpixelJitterDoesNotImplyArpeggio() throws Exception {
        assertTrue(bar(4, 1, 0, 255, 0));
    }
}
