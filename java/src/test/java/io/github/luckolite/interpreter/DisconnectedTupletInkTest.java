// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original connected cap/stem and disconnected neighboring curves. */
public class DisconnectedTupletInkTest {
    static final int W = 400, H = 240;
    byte[] gray = new byte[W * H];

    public DisconnectedTupletInkTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void rect(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
    }

    List<ScoreNoteEvent> read() {
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .25f, 2, 0, 1, .4f, false, 0, 1),
                        new ScoreNoteEvent(0, .3125f, 2, 0, 1, .4f, false, 0, 1),
                        new ScoreNoteEvent(0, .375f, 2, 0, 1, .4f, false, 0, 1));
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(0, 1, .2f, .6f)), gray, W, H);
    }

    void capStem() {
        rect(116, 145, 136, 147);
        rect(126, 148, 129, 168);
    }

    @Test
    public void neighboringCurvesCannotSupplyLetterLobes() {
        capStem();
        rect(135, 151, 144, 154);
        rect(135, 160, 144, 163);
        rect(143, 151, 144, 163);
        assertTrue(read().stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void solitaryCapAndStemIsNotThree() {
        capStem();
        assertTrue(read().stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void connectedPrintedThreeRetainsBothBowls() {
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
            "......####..",
            ".......####.",
            "##.....####.",
            "###....####.",
            "###....####.",
            "###....####.",
            ".###....###.",
            "..########..",
            "....####...."
        };
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#') rect(116 + x, 145 + y, 116 + x, 145 + y);
        assertTrue(read().stream().allMatch(n -> n.tupletDivisor() == 3));
    }
}
