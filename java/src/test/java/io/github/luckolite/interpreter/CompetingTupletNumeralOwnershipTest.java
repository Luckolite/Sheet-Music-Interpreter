// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompetingTupletNumeralOwnershipTest {
    private static final int W = 400, H = 500;
    private static final String[] THREE = {
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

    private static byte[] image(boolean foreign, boolean own) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int top : new int[] {foreign ? 150 : -1, own ? 200 : -1})
            if (top >= 0)
                for (int y = 0; y < THREE.length; y++)
                    for (int x = 0; x < 12; x++)
                        if (THREE[y].charAt(x) == '#') gray[(top + y) * W + 180 + x] = 0;
        return gray;
    }

    private static List<ScoreNoteEvent> notes() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {156, 186, 216})
            n.add(
                    new ScoreNoteEvent(0, x / W, 0, 1, 2, 260f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
        n.add(new ScoreNoteEvent(0, 186f / W, 0, 0, 2, 160f / H, false, 0, 0, 2, 2, 1));
        return n;
    }

    private static List<ScoreNoteEvent> apply(boolean foreign, boolean own) {
        return TripletRhythmDetector.apply(
                notes(), List.of(new MeasureRegion(0, 1, .06f, .604f)), image(foreign, own), W, H);
    }

    @Test
    public void earlierForeignNumeralCannotHideAnOwnedNumeral() {
        var n = apply(true, true);
        assertTrue(
                n.subList(0, 3).stream()
                        .allMatch(
                                x ->
                                        x.tupletDivisor() == 3
                                                && x.tupletNormalNotes() == 2
                                                && x.stemDirection() == 1));
        assertEquals(1, n.get(3).tupletDivisor());
    }

    @Test
    public void aForeignNumeralAloneCannotMarkThisVoice() {
        assertTrue(apply(true, false).subList(0, 3).stream().allMatch(x -> x.tupletDivisor() == 1));
    }

    @Test
    public void ordinarySingleOwnedNumeralKeepsItsExistingResult() {
        assertTrue(apply(false, true).subList(0, 3).stream().allMatch(x -> x.tupletDivisor() == 3));
    }
}
