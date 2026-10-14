// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.TerminalCreditStroke;

public class TerminalCreditStrokeControlsTest {
    static String run(String old, List<String> texts, List<Float> conf, List<Boolean> proof) {
        return TerminalCreditStroke.restore(old, texts, conf, proof);
    }

    static void check(boolean yes, String name) {
        if (!yes) throw new AssertionError(name);
    }

    @org.junit.Test
    public void originalControls() {
        String old = "-JORDAN RA", after = "JORDAN RAI";
        var texts = List.of(after, after, after);
        var scores = List.of(.95f, .94f, .96f);
        var yes = List.of(true, true, true);
        check(run(old, texts, scores, yes).equals(after), "agreeing stroke");
        check(
                run(old, List.of(after, after, "JORDAN RAL"), scores, yes).equals(old),
                "disagreement");
        check(run(old, texts, List.of(.95f, .89f, .96f), yes).equals(old), "low confidence");
        check(run(old, texts, List.of(.95f, Float.NaN, .96f), yes).equals(old), "nan");
        check(
                run(old, texts, scores, List.of(true, false, true)).equals(old),
                "missing pixel proof");
        check(
                run(old, List.of(after, after), List.of(.95f, .94f), List.of(true, true))
                        .equals(old),
                "only two scales");
        check(run("Jordan Ra", texts, scores, yes).equals("Jordan Ra"), "ordinary mixed case");
        check(
                run("JORDAN RAI", List.of("JORDAN RAII", "JORDAN RAII", "JORDAN RAII"), scores, yes)
                        .equals("JORDAN RAI"),
                "existing terminal I");
        check(
                run(old, List.of("JORDON RAI", "JORDON RAI", "JORDON RAI"), scores, yes)
                        .equals(old),
                "internal replacement");
        check(run(old, null, scores, yes).equals(old), "null readings");
        check(run(old, texts, Arrays.asList(.95f, null, .96f), yes).equals(old), "null score");
        check(
                run("Music by JORDAN RA", texts, scores, yes).equals("Music by JORDAN RA"),
                "explicit role outside candidate");
        int[] p = new int[40 * 20];
        Arrays.fill(p, 0xffeeeeee);
        for (int y = 5; y < 15; y++) for (int x = 10; x < 15; x++) p[y * 40 + x] = 0xff555555;
        var image = TerminalCreditStroke.prepare(40, 20, p);
        check(
                image.width() == 46
                        && image.height() == 26
                        && image.pixels()[0] == 0xffffffff
                        && image.pixels()[10 * 46 + 15] == 0xff000000,
                "contrast and margin preserve ink");
        boolean invalid = false;
        try {
            TerminalCreditStroke.prepare(40, 20, new int[1]);
        } catch (IllegalArgumentException e) {
            invalid = true;
        }
        check(invalid, "invalid input");
        check(
                run(old, texts, List.of(.95f, 1.1f, .96f), yes).equals(old),
                "invalid confidence above one");
        System.out.println("15 original repair and crop controls passed");
    }
}
