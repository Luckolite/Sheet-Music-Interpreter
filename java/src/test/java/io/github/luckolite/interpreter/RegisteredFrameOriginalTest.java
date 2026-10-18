// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored physical registration law; no score pixels or forced counter classification. */
public class RegisteredFrameOriginalTest {
    static final int W = 240, H = 200;

    static byte[] scene(float[] rows) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (float r : rows) for (int x = 20; x <= 220; x++) g[Math.round(r) * W + x] = 0;
        for (int x : new int[] {100, 108})
            for (int y = Math.round(rows[0]); y <= Math.round(rows[4]); y++) g[y * W + x] = 0;
        return g;
    }

    static boolean proof(byte[] g, float[] r) {
        return ParallelStaffRules.proved(
                g, W, H, 100, Math.round(r[0] - 16 * .12f), Math.round(r[4] + 16 * .12f), 16, r);
    }

    @Test
    public void physicalUniformRowsOwnSpacesWithoutChangingPixels() {
        float[] r = {100, 116, 132, 148, 164};
        byte[] g = scene(r), copy = g.clone();
        float[] before = r.clone();
        assertTrue(proof(g, r));
        assertArrayEquals(copy, g);
        assertArrayEquals(before, r, 0);
    }

    @Test
    public void physicalVaryingRowsAreDistinctFromNominalGap() {
        float[] r = {100, 116, 133, 149, 166};
        assertTrue(proof(scene(r), r));
    }

    @Test
    public void completePairKeepsOriginalExpandedEndpoints() throws Exception {
        float[] r = {100, 116, 133, 149, 166};
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "completeCounterRulePair",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float[].class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, scene(r), W, H, 100, 98, 168, 16f, r));
    }

    @Test
    public void aBrokenPhysicalColumnCannotBorrowForeignInk() {
        float[] r = {100, 116, 132, 148, 164};
        byte[] g = scene(r);
        for (int y = 120; y <= 128; y++) {
            g[y * W + 100] = (byte) 255;
            g[y * W + 96] = 0;
        }
        assertFalse(proof(g, r));
    }

    @Test
    public void visibleInterspaceInkStillFailsClearance() {
        float[] r = {100, 116, 132, 148, 164};
        byte[] g = scene(r);
        for (int y = 121; y <= 127; y++) for (int x = 101; x < 108; x++) g[y * W + x] = 0;
        assertFalse(proof(g, r));
    }

    @Test
    public void solidCounterBodyDoesNotBecomePaperSeparatedPair() {
        float[] r = {100, 116, 132, 148, 164};
        byte[] g = scene(r);
        for (int y = 100; y <= 164; y++) for (int x = 100; x <= 108; x++) g[y * W + x] = 0;
        assertFalse(proof(g, r));
    }

    @Test
    public void foreignFrameCannotBindSameExpandedAperture() {
        float[] r = {100, 116, 132, 148, 164};
        assertFalse(
                ParallelStaffRules.proved(
                        scene(r), W, H, 100, 98, 166, 16, new float[] {116, 132, 148, 164, 180}));
    }

    @Test
    public void invalidOrMissingFrameCannotActivateProof() {
        float[] r = {100, 116, 132, 148, 164};
        byte[] g = scene(r);
        assertFalse(ParallelStaffRules.proved(g, W, H, 100, 98, 166, 16, null));
        assertFalse(
                ParallelStaffRules.proved(
                        g, W, H, 100, 98, 166, 16, new float[] {100, 116, 116, 148, 164}));
        assertFalse(
                ParallelStaffRules.proved(
                        g, W, H, 100, 98, 166, 16, new float[] {100, 116, Float.NaN, 148, 164}));
    }
}
