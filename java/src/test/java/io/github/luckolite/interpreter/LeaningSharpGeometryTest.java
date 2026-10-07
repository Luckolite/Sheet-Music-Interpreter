// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaft/crossbar drawings; row shifts preserve ink and its vertical pitch. */
public class LeaningSharpGeometryTest {
    static final int W = 120, H = 110;

    private static void dot(byte[] mask, int x, int y) {
        mask[y * W + x] = 3;
    }

    private static byte[] shape(int kind, float lean) {
        byte[] upright = new byte[W * H], result = new byte[W * H];
        if (kind == 0 || kind == 1 || kind == 2) {
            for (int x : new int[] {40, 41, 49, 50})
                for (int y = kind == 1 && x < 45 ? 22 : kind == 1 ? 38 : 25;
                        y <= (kind == 1 && x < 45 ? 57 : kind == 1 ? 75 : 70);
                        y++) dot(upright, x, y);
            if (kind != 2)
                for (int cy : new int[] {38, 54})
                    for (int y = cy - 1; y <= cy + 1; y++)
                        for (int x = 37; x <= 53; x++) dot(upright, x, y);
        } else if (kind == 3) {
            for (int x = 40; x <= 42; x++) for (int y = 20; y <= 75; y++) dot(upright, x, y);
            for (int y = 40; y <= 70; y++)
                for (int x = 40; x <= 54; x++) {
                    double r = (x - 46) * (x - 46) / 49.0 + (y - 55) * (y - 55) / 225.0;
                    if (r <= 1 && r >= .48) dot(upright, x, y);
                }
        } else {
            for (int y = 20; y <= 77; y++) for (int x = 52; x <= 53; x++) dot(upright, x, y);
            for (int cy : new int[] {38, 54})
                for (int y = cy - 5; y <= cy + 5; y++)
                    for (int x = 37; x <= 53; x++)
                        if ((x - 45) * (x - 45) / 64.0 + (y - cy) * (y - cy) / 25.0 <= 1)
                            dot(upright, x, y);
        }
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (upright[y * W + x] != 0) result[y * W + x + Math.round((y - 47.5f) * lean)] = 3;
        return result;
    }

    private static float pitch(byte[] mask) throws Exception {
        int area = 0, minX = W, maxX = -1, minY = H, maxY = -1;
        long sx = 0, sy = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (mask[y * W + x] != 0) {
                    area++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    sx += x;
                    sy += y;
                }
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> ctor = hc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object component =
                ctor.newInstance(
                        area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
        Class<?> ac = Class.forName(OmrScoreInterpreter.class.getName() + "$AccidentalCandidate");
        Constructor<?> ca = ac.getDeclaredConstructors()[0];
        ca.setAccessible(true);
        Method method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "sharpPitchCenter", byte[].class, int.class, int.class, ac, float.class);
        method.setAccessible(true);
        return (float) method.invoke(null, mask, W, H, ca.newInstance(component, (byte) 3), 16f);
    }

    @Test
    public void leaningShaftsKeepSharpAndPitch() throws Exception {
        for (float lean : new float[] {-.3f, -.25f, -.2f, -.15f, .15f, .2f, .25f, .3f})
            assertEquals("lean=" + lean, 46f, pitch(shape(0, lean)), .6f);
    }

    @Test
    public void naturalDoesNotBecomeSharp() throws Exception {
        for (float lean : new float[] {-.3f, -.15f, 0, .15f, .3f})
            assertTrue(Float.isNaN(pitch(shape(1, lean))));
    }

    @Test
    public void disconnectedShaftsDoNotBecomeSharp() throws Exception {
        for (float lean : new float[] {-.3f, -.15f, 0, .15f, .3f})
            assertTrue(Float.isNaN(pitch(shape(2, lean))));
    }

    @Test
    public void flatBowlDoesNotBecomeSharp() throws Exception {
        for (float lean : new float[] {-.3f, -.15f, 0, .15f, .3f})
            assertTrue(Float.isNaN(pitch(shape(3, lean))));
    }

    @Test
    public void pairedRealNoteheadsDoNotBecomeSharp() throws Exception {
        for (float lean : new float[] {-.3f, -.15f, 0, .15f, .3f})
            assertTrue(Float.isNaN(pitch(shape(4, lean))));
    }

    @Test
    public void geometricEvidenceDoesNotChangeInputPixels() throws Exception {
        byte[] mask = shape(0, -.25f), before = mask.clone();
        pitch(mask);
        assertArrayEquals(before, mask);
    }

    @Test
    public void aCropBoundaryCannotProveTheLeaningGlyphIsComplete() throws Exception {
        byte[] original = shape(0, -.25f);
        int left = W;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) if (original[y * W + x] != 0) left = Math.min(left, x);
        byte[] clipped = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = left; x < W; x++) clipped[y * W + x - left] = original[y * W + x];
        assertTrue(Float.isNaN(pitch(clipped)));
    }
}
