// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric ink; cropped evidence cannot establish a complete sharp. */
public class ClippedSharpHeaderTest {
    static final int W = 100, H = 110;
    byte[] gray = new byte[W * H];

    public ClippedSharpHeaderTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) gray[yy * W + xx] = 0;
    }

    void sharp() {
        box(28, 30, 2, 43);
        box(36, 30, 2, 43);
        box(25, 43, 16, 5);
        box(25, 59, 16, 5);
    }

    float read(boolean retry, int l, int r, float margin) throws Exception {
        var root = OmrScoreInterpreter.class;
        var type = Class.forName(root.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var component = ctor.newInstance(100, l, r, 30, 72, (l + r) / 2f, 51f);
        var ac = Class.forName(root.getName() + "$AccidentalCandidate");
        var ca = ac.getDeclaredConstructor(type, byte.class);
        ca.setAccessible(true);
        var candidate = ca.newInstance(component, (byte) 3);
        var m =
                retry
                        ? root.getDeclaredMethod(
                                "printedSignatureSharp",
                                byte[].class,
                                int.class,
                                int.class,
                                ac,
                                float.class,
                                boolean.class)
                        : root.getDeclaredMethod(
                                "printedSignatureSharpInCrop",
                                byte[].class,
                                int.class,
                                int.class,
                                ac,
                                float.class,
                                boolean.class,
                                float.class);
        m.setAccessible(true);
        return (float)
                (retry
                        ? m.invoke(null, gray, W, H, candidate, 17.25f, false)
                        : m.invoke(null, gray, W, H, candidate, 17.25f, false, margin));
    }

    @Test
    public void clippedRightEdgeCannotProveCompleteSharp() throws Exception {
        sharp();
        assertTrue(Float.isNaN(read(false, 28, 32, .3f)));
    }

    @Test
    public void clippedLeftEdgeCannotProveCompleteSharp() throws Exception {
        sharp();
        assertTrue(Float.isNaN(read(false, 33, 37, .3f)));
    }

    @Test
    public void widerRetryRetainsTrueFragmentedSharp() throws Exception {
        sharp();
        assertTrue(Float.isFinite(read(true, 28, 32, .3f)));
    }

    @Test
    public void completeCropRetainsSharp() throws Exception {
        sharp();
        assertTrue(Float.isFinite(read(false, 25, 40, .3f)));
    }

    @Test
    public void plainShaftNeverBecomesSharp() throws Exception {
        box(28, 30, 2, 43);
        assertTrue(Float.isNaN(read(true, 28, 32, .3f)));
    }
}
