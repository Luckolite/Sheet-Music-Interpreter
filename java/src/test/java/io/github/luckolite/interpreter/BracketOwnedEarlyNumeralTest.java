// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original numeral pixels with interleaved note/rest events. */
public class BracketOwnedEarlyNumeralTest {
    static final int W = 400, H = 240;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, .1f, .8f));

    static byte[] ink(boolean closed) {
        String[] rows = {
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
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < 12; x++)
                if (rows[y].charAt(x) == '#' || closed && x < 2 && y > 1 && y < rows.length - 2)
                    gray[(145 + y) * W + 119 + x] = 0;
        return gray;
    }

    private static void check(int mode, int offset, int scale) {
        byte[] g = ink(mode == 4);
        for (int x : new int[] {94, 144})
            if (mode != 2 || x == 94)
                for (int y = 80; y <= 140; y++)
                    for (int dx = 0; dx < 2; dx++) g[y * W + x + dx] = 0;
        if (mode != 1) {
            for (int x = 94; x <= 117; x++) g[156 * W + x] = 0;
            for (int x = 132; x <= 156 + offset; x++) g[156 * W + x] = 0;
            for (int y = 143; y <= 156; y++) {
                g[y * W + 94] = 0;
                g[y * W + 156 + offset] = 0;
            }
        }
        byte[] pixels = new byte[W * H * scale * scale];
        for (int y = 0; y < H * scale; y++)
            for (int x = 0; x < W * scale; x++)
                pixels[y * W * scale + x] = g[(y / scale) * W + x / scale];
        var a =
                new ScoreNoteEvent(0, .25f, 2, 0, 1, 80f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(mode == 8 ? 0 : -1);
        var b =
                new ScoreNoteEvent(0, .375f, 2, 0, 1, 80f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(mode == 8 ? 0 : (mode == 3 ? 1 : -1));
        var c =
                new ScoreNoteEvent(
                                0,
                                .3125f,
                                -3,
                                0,
                                1,
                                (mode == 7 ? 80f : 161f) / H,
                                mode == 5,
                                0,
                                0,
                                2,
                                2,
                                1)
                        .withStemDirection(mode == 6 ? 1 : 0);
        var original = List.of(a, c, b);
        byte[] unchanged = pixels.clone();
        var out =
                TripletRhythmDetector.withoutNumeralHeads(
                        original, BARS, pixels, W * scale, H * scale);
        assertEquals(
                "mode=" + mode + " offset=" + offset + " scale=" + scale,
                (mode == 0 || mode == 8) ? List.of(a, b) : original,
                out);
        assertArrayEquals(unchanged, pixels);
    }

    @Test
    public void completeBracketOwnsPrintedNumeralBeforeRestDetection() {
        for (int offset : new int[] {0, 8, 12})
            for (int scale : new int[] {1, 2}) {
                check(0, offset, scale);
                check(8, offset, scale);
            }
    }

    @Test
    public void missingOrConflictingOwnershipRetainsEvents() {
        for (int mode = 1; mode < 8; mode++)
            for (int offset : new int[] {0, 8, 12})
                for (int scale : new int[] {1, 2}) check(mode, offset, scale);
    }
}
