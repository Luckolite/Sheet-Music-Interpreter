// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original generated engraving: three double-beamed attacks followed by one eighth. */
public class OffsetTupletWindowTest {
    private static final int W = 400, H = 240;
    private static final List<MeasureRegion> BARS =
            List.of(new MeasureRegion(0, 1, .25f, .7166667f));
    private static final String[] THREE = {
        "..#######...", ".##########.", "###......###", "####.....###",
        "####.....###", "####.....###", ".##.....####", ".......####.",
        "......####..", "....#####...", "....#####...", "....#####...",
        "......####..", ".......####.", "##.....####.", "###....####.",
        "###....####.", "###....####.", ".###....###.", "..########..",
        "..########..", "....####...."
    };

    private static byte[] ink(boolean numeral, boolean secondary, int offset) {
        return ink(numeral, secondary, offset, 0);
    }

    private static byte[] ink(boolean numeral, boolean secondary, int offset, int staffShift) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int row : new int[] {110, 124, 138, 152, 166})
            for (int x = 20; x < 380; x++) gray[(row + staffShift) * W + x] = 0;
        for (int x = 94; x <= 169; x++) for (int y = 112; y <= 115; y++) gray[y * W + x] = 0;
        if (secondary)
            for (int x = 94; x <= 144; x++) for (int y = 104; y <= 107; y++) gray[y * W + x] = 0;
        for (int x : new int[] {94, 119, 144, 169})
            for (int y = 72; y <= 115; y++) gray[y * W + x] = 0;
        if (numeral)
            for (int y = 0; y < THREE.length; y++)
                for (int x = 0; x < 12; x++)
                    if (THREE[y].charAt(x) == '#') gray[(143 + y) * W + 119 + offset + x] = 0;
        return gray;
    }

    private static List<ScoreNoteEvent> notes(int beams) {
        List<ScoreNoteEvent> result = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            result.add(
                    new ScoreNoteEvent(
                            0,
                            .25f + i * .0625f,
                            i == 1 ? 12 : 11,
                            0,
                            1,
                            (i == 1 ? 72f : 79f) / H,
                            false,
                            0,
                            i < 3 ? beams : 1,
                            2,
                            0));
        return result;
    }

    static int passed, failed;

    static void check(String name, byte[] gray, boolean expected) {
        check(name, gray, expected, 2);
    }

    static void check(String name, byte[] gray, boolean expected, int beams) {
        var before = gray.clone();
        var original = notes(beams);
        var out = TripletRhythmDetector.apply(original, BARS, gray, W, H);
        boolean good = Arrays.equals(before, gray);
        for (int i = 0; i < 4; i++)
            good &= out.get(i).tupletDivisor() == (expected && i < 3 ? 3 : 1);
        if (good) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println(
                    "FAIL "
                            + name
                            + " ratios="
                            + out.stream().map(ScoreNoteEvent::tupletDivisor).toList());
        }
    }

    @org.junit.Test
    public void originalSyntheticControls() throws Exception {
        passed = 0;
        failed = 0;
        main(new String[0]);
    }

    public static void main(String[] args) {
        for (int offset : new int[] {0, -19, 19}) {
            check("owned numeral offset" + offset, ink(true, true, offset), true);
            check("missing numeral offset" + offset, ink(false, true, offset), false);
            check("missing secondary beam offset" + offset, ink(true, false, offset), false);
            var g = ink(true, true, offset);
            for (int x = 121; x < 134; x++)
                for (int y = 104; y <= 107; y++) g[y * W + x] = (byte) 255;
            check("broken secondary beam offset" + offset, g, false);
        }
        check("distant left numeral", ink(true, true, -40), false);
        check("distant right numeral", ink(true, true, 40), false);
        for (int offset : new int[] {0, -19, 19}) {
            var g = ink(true, true, offset, -50);
            for (int x = 94; x <= 144; x++) for (int y = 96; y <= 99; y++) g[y * W + x] = 0;
            check("32nd triplet offset" + offset, g, true, 3);
        }
        System.out.println("Passed " + passed + " failed " + failed);
        if (failed > 0) throw new AssertionError(failed);
    }
}
