// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Original pixel plates; no score scans, model outputs, or private coordinates. */
public final class PrintedBeamRuleWitnessOriginals {
    static int assertions;

    static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    static byte[] plate(int width, int height, int gap, int phase, int thickness) {
        byte[] raw = new byte[width * height];
        Arrays.fill(raw, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int y = phase + line * gap; y < phase + line * gap + thickness; y++)
                for (int x = 0; x < width; x++) raw[y * width + x] = 40;
        return raw;
    }

    public static void main(String[] args) {
        int w = 80, h = 180, x = 40;
        for (int gap : new int[] {8, 12, 16})
            for (int thick : new int[] {1, 2}) {
                byte[] raw = plate(w, h, gap, 20, thick);
                var found =
                        PrintedBeamRuleWitness.at(
                                raw, w, h, x, 20 + 4 * gap + (thick - 1) * .5, gap);
                check(found != null, "complete scaled thin rules");
                for (int line = 0; line < 5; line++) {
                    check(found.contains(20 + line * gap), "observed carrier pixel");
                    check(
                            !found.contains(20 + line * gap + thick + 1),
                            "white gutter remains white");
                }
            }
        byte[] raw = plate(w, h, 12, 20, 2);
        for (int y = 44; y <= 45; y++) raw[y * w + x] = (byte) 255;
        check(PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) == null, "missing fifth rule");
        check(
                PrintedBeamRuleWitness.at(raw, w, h, x - 1, 69, 12) != null,
                "no neighboring-column contamination");
        raw = plate(w, h, 12, 20, 2);
        raw[36 * w + x] = 30;
        check(PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) == null, "competing component");
        raw = plate(w, h, 12, 20, 2);
        for (int y = 42; y <= 49; y++) raw[y * w + x] = 30;
        check(
                PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) == null,
                "coowned thick beam remains unknown");
        raw = plate(w, h, 12, 20, 2);
        for (int y = 10; y < 90; y++) raw[y * w + x] = 30;
        check(
                PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) == null,
                "barline is no rule witness");
        raw = plate(w, h, 12, 20, 2);
        for (int y = 44; y <= 45; y++) raw[y * w + x] = (byte) 255;
        for (int y = 46; y <= 47; y++) raw[y * w + x] = 40;
        check(PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) == null, "irregular spacing");
        raw = plate(w, h, 12, 22, 2);
        check(
                PrintedBeamRuleWitness.at(raw, w, h, x, 69, 12) != null,
                "independent phase adjustment");
        check(PrintedBeamRuleWitness.at(raw, w, h, x, 59, 12) == null, "wrong staff phase");
        byte[] same = raw.clone();
        PrintedBeamRuleWitness.at(raw, w, h, x, 71, 12);
        check(Arrays.equals(raw, same), "raw pixels immutable");
        boolean rejected = false;
        try {
            PrintedBeamRuleWitness.at(raw, w, h, -1, 71, 12);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "bounds reject");
        System.out.println("PASS " + assertions + " independent raw rule assertions");
    }
}
