// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

public class QualifiedPhrase104 {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    public static void main(String[] a) {
        int n = 0;
        for (String mark : new String[] {"ppp", "pp", "p", "mp", "mf", "f", "ff", "fff"})
            for (String phrase :
                    new String[] {
                        "molto espressivo",
                        "poco dolce",
                        "maestoso con fuoco",
                        "sempre cantabile",
                        "più sostenuto",
                        "grazioso con brio"
                    }) {
                if (ScoreDynamicsDetector.level(mark + " " + phrase)
                        != ScoreDynamicsDetector.level(mark))
                    throw new AssertionError(mark + phrase);
                n++;
            }
        for (String text :
                new String[] {
                    "mf molto ordinary",
                    "mf molto espressivo lyrics",
                    "sample ff maestoso con fuoco",
                    "maestoso con fuoco",
                    "mf mf molto espressivo",
                    "furniture",
                    "mf molto",
                    "mf con unknown"
                }) {
            if (Float.isFinite(ScoreDynamicsDetector.level(text))) throw new AssertionError(text);
            n++;
        }
        System.out.println("PASS " + n + " qualified-phrase controls");
    }
}
