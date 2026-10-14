// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

public class InstructionGlossaryCreditTest {
    static int count;
    static Method m;

    static void check(boolean v, String n) {
        count++;
        if (!v) throw new AssertionError(n);
    }

    static ScoreCreditsDetector.Line l(String s, float x, float y) {
        return new ScoreCreditsDetector.Line(s, x, y, x + 220, y + 30);
    }

    static boolean test(
            boolean photo,
            float staff,
            ScoreCreditsDetector.Line target,
            List<ScoreCreditsDetector.Line> terms)
            throws Exception {
        var all = new ArrayList<>(terms);
        all.add(target);
        return (boolean)
                m.invoke(
                        null,
                        new ScoreCreditsDetector.Page(1800, 2400, staff, all, false, photo),
                        target,
                        all);
    }

    @org.junit.Test
    public void originalGlossaryControls() throws Exception {
        m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "instructionGlossaryLine",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        var terms =
                List.of(
                        l("Adagio", 100, 300),
                        l("Andante", 100, 400),
                        l("Allegro", 100, 500),
                        l("Largo", 100, 600));
        var prose = l("Play slowly and softly", 350, 300);
        check(test(false, -1, prose, terms), "aligned definition");
        check(test(false, -1, l("Accent; play gently", 350, 250), terms), "semicolon definition");
        check(!test(false, -1, prose, terms.subList(0, 3)), "insufficient glossary evidence");
        check(!test(true, -1, prose, terms), "photo protected");
        check(!test(false, 700, prose, terms), "notated page protected");
        check(
                !test(false, -1, l("Play slowly and softly", 1400, 300), terms),
                "distant prose protected");
        check(!test(false, -1, l("Morgan Vale", 350, 300), terms), "name protected");
        check(
                !test(false, -1, l("Fast Lane Trio", 350, 300), List.of()),
                "band without glossary protected");
        check(
                !test(false, -1, l("Play slowly and softly", 350, 900), terms),
                "unaligned prose protected");
        System.out.println(count + " controls passed");
    }
}
