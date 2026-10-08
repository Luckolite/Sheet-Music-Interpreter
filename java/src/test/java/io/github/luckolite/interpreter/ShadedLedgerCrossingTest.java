// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original synthetic ledger/flag controls; no source-score or model fixture. */
public final class ShadedLedgerCrossingTest {
    static final int W = 180, H = 180;
    static final float GAP = 12;
    static Constructor<?> component, staff;
    static Method inner;
    static int checks;

    static byte[] paper(int shade) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) shade);
        return p;
    }

    static void ink(byte[] p, int l, int t, int r, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) p[y * W + x] = (byte) shade;
    }

    static Object head() throws Exception {
        return component.newInstance(113, 77, 91, 75, 83, 84f, 79f);
    }

    static byte[] fixture(int shade, int crossingWidth, boolean stem) {
        byte[] p = paper(shade);
        for (int y = 75; y <= 83; y++)
            for (int x = 77; x <= 91; x++)
                if ((x - 84) * (x - 84) / 49. + (y - 79) * (y - 79) / 16. <= 1) p[y * W + x] = 45;
        ink(p, 70, 79, 102, 80, 45);
        ink(p, 70, 91, 102, 92, 65);
        if (stem) ink(p, 77, 79, 78, 136, 70);
        if (crossingWidth > 0) ink(p, 84, 84, 83 + crossingWidth, 109, 45);
        return p;
    }

    static boolean supported(byte[] p) throws Exception {
        return (boolean)
                inner.invoke(null, p, W, H, head(), staff.newInstance(103f, 151f, GAP), false);
    }

    static void require(boolean yes, String label) {
        checks++;
        if (!yes) throw new AssertionError(label);
    }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component"),
                s = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
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
        staff = s.getDeclaredConstructor(float.class, float.class, float.class);
        staff.setAccessible(true);
        inner =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasInnerLedgerInk",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        s,
                        boolean.class);
        inner.setAccessible(true);
        require(supported(fixture(158, 0, true)), "complete shaded inner ledger retained");
        require(
                supported(fixture(158, 1, true)),
                "one-pixel flag shaft crossing retains stem-owned inner ledger");
        require(
                supported(fixture(158, 2, true)),
                "two-pixel flag shaft crossing retains stem-owned inner ledger");
        require(
                !supported(fixture(158, 5, true)),
                "broad vertical letter fragment cannot bridge a ledger rule");
        byte[] p = fixture(158, 0, false);
        ink(p, 85, 87, 96, 96, 158);
        require(!supported(p), "stemless interrupted underline retains continuity gate");
        p = fixture(158, 0, true);
        ink(p, 83, 88, 102, 95, 158);
        require(!supported(p), "one-sided short rule cannot support remote head");
        p = fixture(158, 0, true);
        ink(p, 84, 88, 85, 95, 158);
        require(!supported(p), "blank paper between two short rails is not a shaft crossing");
        require(supported(fixture(255, 0, true)), "white-paper complete ledger unchanged");
        System.out.println(checks + " original ledger-crossing controls passed");
    }

    @org.junit.Test
    public void originalProceduralControls() throws Exception {
        main(new String[0]);
    }
}
