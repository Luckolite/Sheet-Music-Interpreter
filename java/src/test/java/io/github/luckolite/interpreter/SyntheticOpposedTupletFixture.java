// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original generated fixture: one numbered upper beam and an interleaved lower beam. */
public final class SyntheticOpposedTupletFixture {
    static final int W = 420, H = 400;
    static final String[] THREE = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    static void rect(byte[] g, int x1, int y1, int x2, int y2) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) g[y * W + x] = 0;
    }

    static List<ScoreNoteEvent> exercise(boolean opposing) {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {180, 240, 300})
            n.add(
                    new ScoreNoteEvent(0, x / (float) W, 12, 0, 1, 230f / H, false, 0, 1)
                            .withStemDirection(1));
        if (opposing)
            for (int x : new int[] {180, 270})
                n.add(
                        new ScoreNoteEvent(0, x / (float) W, 3, 0, 1, 260f / H, false, 0, 1)
                                .withStemDirection(-1));
        return n;
    }

    static byte[] ink(boolean opposing) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x : new int[] {180, 240, 300}) {
            rect(g, x - 6, 227, x + 6, 233);
            rect(g, x + 5, 170, x + 7, 230);
        }
        rect(g, 185, 170, 307, 175);
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(140 + y) * W + 234 + x] = 0;
        if (opposing) {
            for (int x : new int[] {180, 270}) {
                rect(g, x - 6, 257, x + 6, 263);
                rect(g, x - 7, 260, x - 5, 311);
            }
            rect(g, 173, 306, 265, 312);
        }
        return g;
    }
}
