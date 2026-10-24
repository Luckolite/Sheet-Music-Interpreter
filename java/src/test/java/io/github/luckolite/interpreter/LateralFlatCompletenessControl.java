// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Independent geometric adversarial marks; no score pixels. */
public class LateralFlatCompletenessControl {
    static final int W = 120, H = 120;
    static byte[] gray;

    static Object make(String n, Object... v) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(v);
    }

    static Object call(String n, Object... v) throws Exception {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(n) && m.getParameterCount() == v.length) {
                m.setAccessible(true);
                return m.invoke(null, v);
            }
        throw new AssertionError(n);
    }

    static void blank() {
        gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
    }

    static void ink(int l, int r, int t, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = (byte) 230;
    }

    static void flat(int dx) {
        ink(30 + dx, 32 + dx, 35, 74);
        ink(30 + dx, 48 + dx, 59, 60);
        ink(30 + dx, 48 + dx, 70, 71);
        ink(47 + dx, 48 + dx, 59, 71);
    }

    static boolean detect(int dx, int seedLeft, int headLeft) throws Exception {
        Object seed = make("Component", 32, seedLeft, 48 + dx, 59, 74, 46f + dx, 66f);
        Object candidate = make("AccidentalCandidate", seed, (byte) 3);
        Object head = make("Component", 120, headLeft, headLeft + 16, 59, 71, headLeft + 8f, 65f);
        return (boolean) call("rawFlatFromBowl", gray, W, H, List.of(candidate), head, 16f);
    }

    static void check(String label, boolean ok) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }

    public static void main(String[] args) throws Exception {
        boolean fixed = args.length > 0 && args[0].equals("fixed");
        blank();
        flat(0);
        ink(10, 12, 20, 104);
        ink(12, 31, 35, 37);
        boolean longFlag = detect(0, 45, 63);
        System.out.println("longFlagAccepted=" + longFlag);
        if (fixed) check("long connected angular flag beyond first expansion rejected", !longFlag);
        blank();
        flat(0);
        check("complete flat with narrow semantic seed retained", detect(0, 45, 63));
        blank();
        flat(-30);
        check("complete flat spine on page left frame retained", detect(-30, 15, 33));
        blank();
        flat(0);
        check("complete flat right edge at target-head bound retained", detect(0, 45, 50));
        ink(50, 66, 59, 71);
        check("paper gap keeps nearby target head disconnected", detect(0, 45, 50));
        blank();
        flat(0);
        ink(48, 90, 70, 71);
        ink(88, 90, 20, 104);
        boolean clippedAtHead = detect(0, 45, 50);
        System.out.println("rightContinuationAtHeadBoundAccepted=" + clippedAtHead);
        if (fixed)
            check("connected continuation beyond target-head bound rejected", !clippedAtHead);
    }
}
