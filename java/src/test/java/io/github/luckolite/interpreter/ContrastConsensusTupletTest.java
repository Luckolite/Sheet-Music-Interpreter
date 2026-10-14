// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original generated engraving with a lighter attached background stroke. */
public class ContrastConsensusTupletTest {
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

    private static void check(int mode, int glyphInk, int backgroundInk) {
        byte[] gray = ink(true, true, 0);
        for (int y = 143; y < 165; y++)
            for (int x = 119; x < 131; x++)
                if ((gray[y * W + x] & 255) == 0)
                    gray[y * W + x] = (byte) (mode == 3 ? backgroundInk : glyphInk);
        if (mode != 0) {
            for (int x = 130; x < 170; x++) gray[145 * W + x] = (byte) backgroundInk;
            for (int y = 125; y <= 145; y++) gray[y * W + 169] = (byte) backgroundInk;
        }
        if (mode == 2)
            for (int y = 143; y < 165; y++)
                for (int x = 119; x < 131; x++) gray[y * W + x] = (byte) 255;
        byte[] original = gray.clone();
        var result = TripletRhythmDetector.apply(notes(2), BARS, gray, W, H);
        org.junit.Assert.assertArrayEquals(original, gray);
        org.junit.Assert.assertEquals(4, result.size());
        for (int i = 0; i < result.size(); i++)
            org.junit.Assert.assertEquals(
                    "mode=" + mode + " ink=" + glyphInk + " background=" + backgroundInk,
                    mode < 2 && i < 3 ? 3 : 1,
                    result.get(i).tupletDivisor());
    }

    @org.junit.Test
    public void retainTwoThresholdAgreementAcrossLighterBackgroundInk() {
        for (int ink : new int[] {125, 130, 135, 140})
            for (int background : new int[] {156, 160, 164}) check(1, ink, background);
    }

    @org.junit.Test
    public void cleanNumeralsRemainReadable() {
        for (int ink : new int[] {125, 130, 135, 140})
            for (int background : new int[] {156, 160, 164}) check(0, ink, background);
    }

    @org.junit.Test
    public void missingNumeralsCannotChangeTiming() {
        for (int ink : new int[] {125, 130, 135, 140})
            for (int background : new int[] {156, 160, 164}) check(2, ink, background);
    }

    @org.junit.Test
    public void mergedIndistinctInkCannotChangeTiming() {
        for (int ink : new int[] {125, 130, 135, 140})
            for (int background : new int[] {156, 160, 164}) check(3, ink, background);
    }
}
