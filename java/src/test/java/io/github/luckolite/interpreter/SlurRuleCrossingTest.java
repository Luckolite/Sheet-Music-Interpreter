// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original curve merging into a distant staff rule. */
public class SlurRuleCrossingTest {
    static byte[] image(boolean bowed, boolean connected, boolean flip) {
        return image(bowed, connected, flip, false);
    }

    static byte[] image(boolean bowed, boolean connected, boolean flip, boolean intermediate) {
        int w = 240;
        byte[] g = new byte[w * w];
        Arrays.fill(g, (byte) 255);
        for (int dx = -45; dx <= 45; dx++) {
            int y = 80 + (bowed ? Math.round(dx * dx / (intermediate ? 250f : 144f)) : 0);
            for (int dy = -2; dy <= 2; dy++) g[(y + dy) * w + 100 + dx] = 20;
        }
        for (int x = 20; x < 220; x++)
            for (int y = intermediate ? 88 : 90; y <= (intermediate ? 90 : 92); y++)
                g[y * w + x] = 20;
        for (int y = connected ? 80 : 88; y <= 130; y++) g[y * w + 100] = 20;
        if (flip) {
            byte[] old = g.clone();
            for (int y = 0; y < 240; y++) System.arraycopy(old, y * 240, g, (239 - y) * 240, 240);
        }
        return g;
    }

    static boolean read(byte[] g, boolean flip) {
        return DetachedSlurStemCap.matches(
                g, 240, 240, 100, flip ? 161 : 78, flip ? 151 : 88, flip ? 1 : -1, 12, 170);
    }

    @Test
    public void upperCurveBeforeRuleIsSufficient() {
        assertTrue(read(image(true, false, false), false));
    }

    @Test
    public void lowerCurveBeforeRuleIsSufficient() {
        assertTrue(read(image(true, false, true), true));
    }

    @Test
    public void straightStrokeRemainsInsufficient() {
        assertFalse(read(image(false, false, false), false));
    }

    @Test
    public void connectedCurveRemainsInsufficient() {
        assertFalse(read(image(true, true, false), false));
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        byte[] g = image(true, false, false), old = g.clone();
        read(g, false);
        assertArrayEquals(old, g);
    }

    @Test
    public void intermediateUpperSpanBeforeRuleIsSufficient() {
        assertTrue(read(image(true, false, false, true), false));
    }

    @Test
    public void intermediateLowerSpanBeforeRuleIsSufficient() {
        assertTrue(read(image(true, false, true, true), true));
    }
}
