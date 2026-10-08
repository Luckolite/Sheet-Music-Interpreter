// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original synthetic ink controls; no photographed music or model fixtures. */
public final class ShadedStemAdmissionTest {
    static final int W = 180, H = 160;
    static final float GAP = 12;
    static Constructor<?> component;
    static Method defaultStem, explicitStem;
    static int checks = 0;

    static byte[] paper(int shade) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) shade);
        return p;
    }

    static void ink(byte[] p, int l, int t, int r, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) p[y * W + x] = (byte) shade;
    }

    static Object head(int l, int t, int r, int b) throws Exception {
        return component.newInstance(
                (r - l + 1) * (b - t + 1), l, r, t, b, (l + r) * .5f, (t + b) * .5f);
    }

    static int[] stem(byte[] p, Object h) throws Exception {
        return (int[]) defaultStem.invoke(null, p, W, H, h, GAP);
    }

    static void require(boolean yes, String label) {
        checks++;
        if (!yes) throw new AssertionError(label);
    }

    static void round(byte[] p, int cx, int cy, int shade) {
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 7; x <= cx + 7; x++)
                if ((x - cx) * (x - cx) / 49. + (y - cy) * (y - cy) / 16. <= 1)
                    p[y * W + x] = (byte) shade;
    }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        component =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        component.setAccessible(true);
        defaultStem =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "attachedRawStem", byte[].class, int.class, int.class, c, float.class);
        defaultStem.setAccessible(true);
        explicitStem =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "attachedRawStem",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        int.class,
                        int.class);
        explicitStem.setAccessible(true);
        byte[] p = paper(150);
        ink(p, 80, 78, 85, 81, 55);
        require(
                stem(p, head(80, 78, 85, 81)) == null,
                "shaded paper cannot provide a shaft for detached digit bowl");
        p = paper(158);
        ink(p, 54, 78, 108, 80, 45);
        require(
                stem(p, head(79, 77, 85, 81)) == null,
                "horizontal beam island without shaft stays stemless");
        p = paper(150);
        round(p, 84, 90, 45);
        ink(p, 90, 36, 91, 90, 45);
        int[] s = stem(p, head(77, 86, 91, 94));
        require(s != null && s[2] < 0 && s[1] <= 38, "real upward shaft on shaded paper retained");
        p = paper(158);
        round(p, 84, 55, 45);
        ink(p, 77, 55, 78, 108, 45);
        s = stem(p, head(77, 51, 91, 59));
        require(
                s != null && s[2] > 0 && s[1] >= 106,
                "real downward shaft on shaded paper retained");
        p = paper(158);
        round(p, 84, 55, 50);
        ink(p, 72, 54, 97, 55, 50);
        ink(p, 72, 66, 96, 67, 50);
        ink(p, 77, 55, 78, 108, 50);
        ink(p, 78, 99, 87, 101, 50);
        ink(p, 85, 102, 87, 108, 50);
        s = stem(p, head(77, 51, 91, 59));
        require(
                s != null && s[2] > 0 && s[1] >= 106,
                "high ledger head with downward eighth flag retained");
        p = paper(170);
        round(p, 84, 90, 80);
        ink(p, 90, 36, 91, 90, 125);
        s = stem(p, head(77, 86, 91, 94));
        require(s != null && s[2] < 0, "faded contrasting shaded-paper stem retained");
        p = paper(158);
        round(p, 84, 55, 45);
        ink(p, 77, 55, 78, 108, 133);
        s = stem(p, head(77, 51, 91, 59));
        require(
                s != null && s[2] > 0 && s[1] >= 106,
                "blurred shaft with 25-level paper contrast retained");
        p = paper(255);
        round(p, 84, 90, 45);
        ink(p, 90, 36, 91, 90, 45);
        s = stem(p, head(77, 86, 91, 94));
        require(s != null && s[2] < 0 && s[1] <= 38, "crisp white-paper stem unchanged");
        p = paper(235);
        round(p, 84, 90, 90);
        ink(p, 90, 36, 91, 90, 190);
        s = (int[]) explicitStem.invoke(null, p, W, H, head(77, 86, 91, 94), GAP, 2, 205);
        require(s != null && s[2] < 0, "explicit pale-stem fallback on white paper retained");
        System.out.println(checks + " original shaded-stem controls passed");
    }

    @org.junit.Test
    public void originalProceduralControls() throws Exception {
        main(new String[0]);
    }
}
