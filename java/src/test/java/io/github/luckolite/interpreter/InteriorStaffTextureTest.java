// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteriorStaffTextureTest {
    static final int W = 400, H = 400;
    static final int[] TOPS = {40, 140, 210, 300};

    static int[] strength() {
        int[] s = new int[H];
        for (int top : TOPS) for (int i = 0; i < 5; i++) s[top + i * 10] = 300;
        return s;
    }

    static byte[] gray(boolean faint) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int top : new int[] {40, 300})
            for (int i = 0; i < 5; i++)
                for (int x = 40; x < 360; x++) g[(top + i * 10) * W + x] = 0;
        for (int y = 110; y < 270; y++)
            for (int x = 0; x < 150; x++)
                g[y * W + x] =
                        (byte) Math.round(205 + 20 * Math.sin(x / 3.0) + 10 * Math.cos(y / 30.0));
        if (faint)
            for (int i = 0; i < 5; i++)
                for (int x = 120; x < 200; x++) g[(140 + i * 10) * W + x] = (byte) 245;
        return g;
    }

    @Test
    public void twoRegularTextureGroupsBetweenRealStavesAreRejected() {
        var result = RawStaffLineDetector.detectFromStrength(strength(), 10, H, gray(false), W);
        assertEquals(2, result.size());
        assertEquals(40, result.get(0).top());
        assertEquals(300, result.get(1).top());
    }

    @Test
    public void faintShortFiveRuleStaffIsPreserved() {
        var result = RawStaffLineDetector.detectFromStrength(strength(), 10, H, gray(true), W);
        assertEquals(3, result.size());
        assertEquals(140, result.get(1).top());
    }

    @Test
    public void missingRasterDoesNotInventNegativeEvidence() {
        assertEquals(4, RawStaffLineDetector.detectFromStrength(strength(), 10, H, null, W).size());
    }

    @Test
    public void oneProvedStaffDoesNotJustifyInteriorRemoval() {
        byte[] g = gray(false);
        for (int y = 300; y < H; y++) Arrays.fill(g, y * W, (y + 1) * W, (byte) 255);
        var result = RawStaffLineDetector.detectFromStrength(strength(), 10, H, g, W);
        assertEquals(4, result.size());
    }
}
