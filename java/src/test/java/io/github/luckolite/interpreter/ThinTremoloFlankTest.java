// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original thin rules and floating strokes; no score-derived pixels. */
public class ThinTremoloFlankTest {
    static boolean pair(boolean floating, boolean flip, int thickness) {
        int w = 180, h = 220;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 70; y <= 160; y++) for (int x = 59; x <= 61; x++) g[y * w + x] = 0;
        for (int y = 70; y <= 145; y++) for (int x = 89; x <= 91; x++) g[y * w + x] = 0;
        for (int cy : new int[] {90, 104, 118})
            for (int x = floating ? 68 : 20; x <= (floating ? 83 : 150); x++)
                for (int dy = -thickness / 2; dy <= thickness / 2; dy++) g[(cy + dy) * w + x] = 0;
        if (flip) {
            byte[] old = g.clone();
            for (int y = 0; y < h; y++) System.arraycopy(old, y * w, g, (h - 1 - y) * w, w);
        }
        var a = new PairedTremoloInk.Stem(60, flip ? 59 : 160, flip ? 149 : 70);
        var b = new PairedTremoloInk.Stem(90, flip ? 74 : 145, flip ? 149 : 70);
        return !PairedTremoloInk.find(g, w, h, a, b, 14).isEmpty();
    }

    @Test
    public void thinStaffRulesAreNotTremolo() {
        assertFalse(pair(false, false, 3));
    }

    @Test
    public void mirroredThinStaffRulesAreNotTremolo() {
        assertFalse(pair(false, true, 3));
    }

    @Test
    public void thinFloatingStrokesRetainTremolo() {
        assertTrue(pair(true, false, 3));
        assertTrue(pair(true, true, 3));
    }

    @Test
    public void thickConnectedBeamsRemainRejected() {
        assertFalse(pair(false, false, 7));
        assertFalse(pair(false, true, 7));
    }
}
