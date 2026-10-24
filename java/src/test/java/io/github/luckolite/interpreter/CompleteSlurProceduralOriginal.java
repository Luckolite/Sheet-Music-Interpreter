// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Formula-authored scene; no score pixels or cached witness coordinates. */
public final class CompleteSlurProceduralOriginal {
    static final int W = 240, H = 240;

    record Scene(byte[] g, int[] target, int[] receiver) {}

    static Scene scene(int shift, boolean whiteBreak, boolean headOverlap) {
        return scene(shift, whiteBreak, headOverlap, false);
    }

    static Scene scene(int shift, boolean whiteBreak, boolean headOverlap, boolean bent) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 160; y += 20)
            for (int d = 0; d < 2; d++) for (int x = 20; x <= 220; x++) g[(y + d) * W + x] = 20;
        for (int y = 80; y <= 161; y++) for (int x : new int[] {20, 220}) g[y * W + x] = 20;
        var head = new TreeSet<Integer>();
        for (int y = 80; y <= 106; y++)
            for (int x = 104 + shift; x <= 138 + shift; x++) {
                double xx = x - 121 - shift,
                        yy = y - 93,
                        q = Math.pow(xx / 16., 2) + Math.pow(yy / 10., 2);
                if (q <= 1) {
                    head.add(y * W + x);
                    if (Math.pow((xx - .4 * yy) / 6., 2) + Math.pow(yy / 7., 2) >= 1)
                        g[y * W + x] = 25;
                }
            }
        for (int x = 80 + shift; x <= 110 + shift; x++) {
            int cy = bent ? 99 - (x - 80 - shift) / 10 : 99;
            for (int y = cy - 1; y <= cy; y++) g[y * W + x] = 25;
        }
        for (int x = 74 + shift; x <= 90 + shift; x++) {
            int cy = bent ? 105 - (x - 74 - shift) / 4 : 103;
            for (int y = cy - 1; y <= cy + 1; y++) g[y * W + x] = 25;
        }
        var target = new TreeSet<Integer>();
        for (int y = 96; y <= 108; y++)
            for (int x = 80 + shift; x <= 91 + shift; x++)
                if ((g[y * W + x] & 255) < 165) target.add(y * W + x);
        for (int x = 78 + shift; x <= 83 + shift; x++) {
            int cy = bent ? 107 - (x - 74 - shift) / 4 : 105;
            g[cy * W + x] = (byte) 218;
            target.add(cy * W + x);
        }
        if (whiteBreak) {
            g[105 * W + 80 + shift] = (byte) 255;
        }
        if (headOverlap) head.add(target.first());
        return new Scene(
                g,
                target.stream().mapToInt(Integer::intValue).toArray(),
                head.stream().mapToInt(Integer::intValue).toArray());
    }

    public static void main(String[] args) {
        int passes = 0;
        for (int shift : new int[] {0, 11, 23}) {
            var s = scene(shift, false, false);
            var b = new SlurEvidenceBudget(180000000, 30000000);
            var p =
                    CompleteSlurExplanation.propose(
                            s.g, W, H, s.target, List.of(s.receiver), 80, 20, b);
            if (!p.firstStrip.complete()
                    || !p.secondStrip.complete()
                    || p.exclusiveOwnership()
                    || p.target().length != s.target.length)
                throw new AssertionError("complete source-free explanation");
            System.out.println(
                    "original shift="
                            + shift
                            + " target="
                            + p.target().length
                            + " used="
                            + p.used().length
                            + " strips="
                            + p.first().length
                            + "/"
                            + p.second().length);
            passes++;
        }
        for (boolean overlap : new boolean[] {false, true}) {
            var s = scene(0, !overlap, overlap);
            try {
                CompleteSlurExplanation.propose(
                        s.g,
                        W,
                        H,
                        s.target,
                        List.of(s.receiver),
                        80,
                        20,
                        new SlurEvidenceBudget(180000000, 30000000));
                throw new AssertionError("paper/independent head overlap must keep");
            } catch (SlurEvidenceBudget.Unknown expected) {
                passes++;
            }
        }
        System.out.println("PASS proceduralCompleteExplanationAndAdverses=" + passes);
    }
}
