// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original geometric marks and event sequences, no commercial-score pixels. */
public class LateralFlatRecoveryControl {
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

    static void ink(int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = (byte) shade;
    }

    static void flat() {
        ink(40, 42, 35, 74, 230);
        ink(40, 51, 59, 60, 230);
        ink(40, 51, 70, 71, 230);
        ink(50, 51, 59, 71, 230);
    }

    static boolean detect(boolean clipped) throws Exception {
        Object seed = make("Component", 55, clipped ? 45 : 40, 51, 59, 74, 47f, 66f);
        Object candidate = make("AccidentalCandidate", seed, (byte) 3);
        Object head = make("Component", 120, 63, 79, 59, 71, 71f, 65f);
        return (boolean) call("rawFlatFromBowl", gray, W, H, List.of(candidate), head, 16f);
    }

    static int[] state(boolean accidental) throws Exception {
        var first =
                new ScoreNoteEvent(0, .2f, 3, 0, 1, .4f, false, 0, 1, accidental ? -1 : 2, 0)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        var later =
                new ScoreNoteEvent(0, .7f, 3, 0, 1, .4f, false, 0, 1, 2, 0)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        Object head = make("Component", 120, 63, 79, 59, 71, 71f, 65f);
        var a = make("DetectedNote", first, head, 16f);
        var b = make("DetectedNote", later, head, 16f);
        var result = (List<?>) call("applyAccidentalState", List.of(a, b));
        int[] out = new int[2];
        for (int i = 0; i < 2; i++) {
            var m = result.get(i).getClass().getDeclaredMethod("event");
            m.setAccessible(true);
            out[i] = ((ScoreNoteEvent) m.invoke(result.get(i))).writtenAccidental();
        }
        return out;
    }

    static void check(String label, boolean ok) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }

    public static void main(String[] args) throws Exception {
        blank();
        flat();
        boolean genuine = detect(false);
        check("complete genuine pale flat retained", genuine);
        check("genuine flat with laterally clipped semantic seed retained", detect(true));
        blank();
        flat();
        ink(25, 27, 20, 104, 230);
        ink(27, 41, 35, 37, 230);
        boolean falseFlat = detect(true);
        System.out.println("clippedAngularFlagAccepted=" + falseFlat);
        if (args.length > 0 && args[0].equals("fixed"))
            check("connected angular flag return cannot become flat", !falseFlat);
        int[] badState = state(falseFlat), goodState = state(genuine);
        System.out.println(
                "flagState="
                        + Arrays.toString(badState)
                        + " genuineState="
                        + Arrays.toString(goodState));
        if (args.length > 0 && args[0].equals("fixed"))
            check(
                    "false flat cannot pollute later same pitch",
                    Arrays.equals(badState, new int[] {2, 2}));
        check(
                "genuine flat state reaches later same pitch",
                Arrays.equals(goodState, new int[] {-1, -1}));
        var before = gray.clone();
        detect(true);
        check("source raster preserved", Arrays.equals(before, gray));
    }
}
