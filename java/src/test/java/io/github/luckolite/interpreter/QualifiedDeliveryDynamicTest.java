// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class QualifiedDeliveryDynamicTest {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        String[] marks = {"ppp", "pp", "p", "mp", "mf", "f", "ff", "fff"};
        float[] db = {-18, -12, -8, -4, 0, 3, 6, 9};
        for (int i = 0; i < marks.length; i++)
            for (String suffix :
                    List.of(
                            "animato",
                            "scherzando",
                            "furioso",
                            "legato",
                            "espressivo",
                            "dolce",
                            "cantabile",
                            "marcato",
                            "sostenuto",
                            "tranquillo",
                            "agitato"))
                for (String space : List.of(" ", "")) {
                    String phrase = marks[i] + space + suffix;
                    if (ScoreDynamicsDetector.level(phrase) != db[i])
                        throw new AssertionError(phrase);
                    checks++;
                }
        for (String text :
                List.of(
                        "furioso",
                        "animato",
                        "piano",
                        "forte",
                        "p dolce extra",
                        "sample mf animato",
                        "f scherzando mf",
                        "mf ordinary",
                        "ff furioso lyrics",
                        "m animato",
                        "pianissimo",
                        "furniture",
                        "mflegatos",
                        "f scherzand")) {
            if (!Float.isNaN(ScoreDynamicsDetector.level(text))) throw new AssertionError(text);
            checks++;
        }
        for (String text : List.of("MF ANIMATO.", "fscherzando", "ff furioso"))
            for (boolean ink : new boolean[] {false, true}) {
                byte[] g = new byte[300 * 150];
                Arrays.fill(g, (byte) 255);
                if (ink)
                    for (int y = 60; y < 75; y++) for (int x = 80; x < 140; x++) g[y * 300 + x] = 0;
                var word =
                        new PlayingTechniqueDetector.Word(
                                text, 80f / 300, 60f / 150, 140f / 300, 75f / 150);
                var merged = ScoreDynamicsDetector.ocrWords(text, word, g, 300, 150);
                if (merged.size() != (ink ? 1 : 0)) throw new AssertionError(text + " ink=" + ink);
                if (ink && !merged.get(0).text().equals(text))
                    throw new AssertionError("lost delivery evidence");
                checks++;
            }
        System.out.println("PASS " + checks + " original qualified delivery controls");
    }
}
