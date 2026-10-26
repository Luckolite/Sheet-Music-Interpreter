// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** The unchanged actual ordinary producer and quarter finish, isolated from the later eighth pass. */
public final class EighthQuarterStageControls {
    static int checks;

    static void check(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    static Class<?> C, V;
    static Constructor<?> CT;
    static Method PRODUCER, FINISH, GLYPH;
    static List<MeasureRegion> regions = List.of(new MeasureRegion(.02f, .98f, .15f, .8f));
    static List<SixteenthRestDetector.Staff> staffs =
            List.of(new SixteenthRestDetector.Staff(80, 137, 14.25f, 0, 1));

    static SixteenthRestDetector.Detection run(byte[] raw, FaintQuarterRestInk.Budget q)
            throws Exception {
        Object ctx = q == null ? null : CT.newInstance(raw, 280, 210, regions, List.of(), q);
        var old =
                (SixteenthRestDetector.Detection)
                        PRODUCER.invoke(
                                null, raw, 280, 210, regions, staffs, List.of(), true, false, null,
                                ctx, null);
        return q == null ? old : (SixteenthRestDetector.Detection) FINISH.invoke(ctx, old);
    }

    public static void main(String[] args) throws Exception {
        C = Class.forName("io.github.luckolite.interpreter.SixteenthRestDetector$FaintRecovery");
        V = Class.forName("io.github.luckolite.interpreter.SixteenthRestDetector$FaintView");
        CT =
                C.getDeclaredConstructor(
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        FaintQuarterRestInk.Budget.class);
        CT.setAccessible(true);
        PRODUCER =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "detectWithDots",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class,
                        boolean.class,
                        boolean.class,
                        Map.class,
                        C,
                        V);
        PRODUCER.setAccessible(true);
        FINISH = C.getDeclaredMethod("finish", SixteenthRestDetector.Detection.class);
        FINISH.setAccessible(true);
        GLYPH =
                FadedQuarterGlyphTest.class.getDeclaredMethod(
                        "glyph", char.class, int.class, int.class, int.class, double.class);
        GLYPH.setAccessible(true);
        for (char symbol : new char[] {'\ue4e5', '\ue261', '\ue262'})
            for (int h : new int[] {40, 44})
                for (int rows : new int[] {12, 16}) {
                    byte[] raw = (byte[]) GLYPH.invoke(null, symbol, h, 223, rows, .5),
                            saved = raw.clone();
                    var old = run(raw, null);
                    var q = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
                    var result = run(raw, q);
                    System.out.println(
                            (int) symbol
                                    + ":"
                                    + h
                                    + ":"
                                    + rows
                                    + "="
                                    + result
                                    + "|work="
                                    + q.workUsed
                                    + "|bytes="
                                    + q.bytesUsed);
                    check(result.rests().containsAll(old.rests()), "all old geometry");
                    check(result.dots().equals(old.dots()), "old dots");
                    check(Arrays.equals(saved, raw), "raw");
                    if (q.workUsed > 0) {
                        var exact = new FaintQuarterRestInk.Budget(q.workUsed, q.bytesUsed);
                        check(run(raw, exact).equals(result), "exact quarter stage");
                        check(
                                exact.workUsed == q.workUsed && exact.bytesUsed == q.bytesUsed,
                                "same quarter counters");
                    }
                }
        System.err.println("PASS " + checks + " isolated quarter-stage assertions");
    }
}
