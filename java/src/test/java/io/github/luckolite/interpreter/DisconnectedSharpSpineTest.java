// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original rectangular glyphs; no source-score pixels. */
public class DisconnectedSharpSpineTest {
    static final int W = 100, H = 110;
    byte[] ink = new byte[W * H];

    void box(int x, int y, int w, int h, byte value) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) ink[yy * W + xx] = value;
    }

    void sharp() {
        box(28, 30, 2, 43, (byte) 3);
        box(36, 30, 2, 43, (byte) 3);
        box(25, 43, 16, 5, (byte) 3);
        box(25, 59, 16, 5, (byte) 3);
    }

    boolean recognized() throws Exception {
        int n = 0, l = W, r = -1, t = H, b = -1;
        long sx = 0, sy = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (ink[y * W + x] == 3) {
                    n++;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                    sx += x;
                    sy += y;
                }
        var root = OmrScoreInterpreter.class;
        var type = Class.forName(root.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var component = ctor.newInstance(n, l, r, t, b, sx / (float) n, sy / (float) n);
        var ac = Class.forName(root.getName() + "$AccidentalCandidate");
        var ca = ac.getDeclaredConstructor(type, byte.class);
        ca.setAccessible(true);
        var candidate = ca.newInstance(component, (byte) 3);
        var method =
                root.getDeclaredMethod(
                        "isSharpGlyph", byte[].class, int.class, int.class, ac, float.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ink, W, H, candidate, 17.25f);
    }

    @Test
    public void intactTwoSpinesStillProveSharp() throws Exception {
        sharp();
        assertTrue(recognized());
    }

    @Test
    public void shortRasterCutIsTolerated() throws Exception {
        sharp();
        box(36, 36, 2, 2, (byte) 0);
        assertTrue(recognized());
    }

    @Test
    public void detachedUpperRightTipDoesNotProveSpine() throws Exception {
        sharp();
        box(36, 35, 2, 8, (byte) 0);
        assertFalse(recognized());
    }

    @Test
    public void detachedUpperLeftTipDoesNotProveSpine() throws Exception {
        sharp();
        box(28, 35, 2, 8, (byte) 0);
        assertFalse(recognized());
    }

    @Test
    public void detachedLowerTipDoesNotProveSpine() throws Exception {
        sharp();
        box(36, 64, 2, 6, (byte) 0);
        assertFalse(recognized());
    }

    @Test
    public void classifierPreservesSource() throws Exception {
        sharp();
        var old = ink.clone();
        recognized();
        assertArrayEquals(old, ink);
    }
}
