// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated four-rule raster and optional thick occluding beam. */
public class ShadedBeamOcclusionTest {
    private static final int W = 240, H = 200;
    private static final float G = 14, B = 150;

    private byte[][] page(int paper, boolean beam, boolean oneSide) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int i = 1; i < 5; i++)
            for (int x = 40; x < 200; x++) {
                int y = Math.round(B - i * G);
                g[y * W + x] = 35;
                l[y * W + x] = 4;
            }
        if (beam)
            for (int y = 146; y <= 154; y++)
                for (int x = 40; x < (oneSide ? 130 : 200); x++) g[y * W + x] = 20;
        return new byte[][] {l, g};
    }

    private float[] resolve(byte[][] p) {
        return BeamOccludedStaffPhase.resolve(p[0], p[1], W, H, 120, 110, 130, B + 6, G);
    }

    @Test
    public void darkPaperCannotProveAnOccludingBeam() {
        assertNull(resolve(page(130, false, false)));
    }

    @Test
    public void realBeamOnDarkPaperStillProvesPhase() {
        float[] p = resolve(page(130, true, false));
        assertNotNull(p);
        assertEquals(B, p[0], 1);
    }

    @Test
    public void realBeamOnWhitePaperStillProvesPhase() {
        float[] p = resolve(page(240, true, false));
        assertNotNull(p);
        assertEquals(B, p[0], 1);
    }

    @Test
    public void darkOneSidedBeamCannotProvePhase() {
        assertNull(resolve(page(130, true, true)));
    }

    @Test
    public void whitePaperWithoutBeamIsRejected() {
        assertNull(resolve(page(240, false, false)));
    }

    @Test
    public void preservesSourcePixels() {
        var p = page(130, true, false);
        byte[] l = p[0].clone(), g = p[1].clone();
        resolve(p);
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }
}
