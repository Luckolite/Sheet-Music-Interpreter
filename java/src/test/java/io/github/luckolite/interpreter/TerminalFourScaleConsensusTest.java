// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.TerminalCreditStroke;

public class TerminalFourScaleConsensusTest {
    static int checks = 0;

    static void check(String expected, List<String> r, List<Float> c, List<Boolean> b) {
        checks++;
        String actual = TerminalCreditStroke.restore("-MARCO ALBA", r, c, b);
        if (!expected.equals(actual)) throw new AssertionError(checks + " " + actual);
    }

    @org.junit.Test
    public void originalFourScaleControls() {
        String o = "-MARCO ALBA", yes = "MARCO ALBAI";
        var ink = List.of(true, true, true, true);
        check(yes, List.of(yes, "MARCO ALBAE", yes, yes), List.of(.95f, .88f, .93f, .94f), ink);
        check(o, List.of(yes, "MARCO ALBAL", yes, yes), List.of(.95f, .91f, .93f, .94f), ink);
        check(o, List.of(yes, yes, yes, yes), List.of(.95f, .88f, .89f, .94f), ink);
        check(
                o,
                List.of(yes, yes, yes, yes),
                List.of(.95f, .88f, .93f, .94f),
                List.of(true, false, true, true));
        check(o, List.of(yes, yes, yes, yes), List.of(.95f, Float.NaN, .93f, .94f), ink);
        check(o, List.of(yes, yes, yes, yes), List.of(.95f, 1.01f, .93f, .94f), ink);
        check(yes, List.of(yes, yes, yes), List.of(.95f, .93f, .94f), List.of(true, true, true));
        check(o, List.of(yes, yes, yes), List.of(.95f, .89f, .94f), List.of(true, true, true));
        System.out.println(checks + " synthetic controls passed");
    }
}
