// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic stems, beams, numeral and notes; no source score pixels. */
public class AdjacentQuintupletTest {
    static final int W = 500, H = 240;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, .1f, .8f));

    static List<ScoreNoteEvent> notes() {
        var result = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 9; i++)
            result.add(new ScoreNoteEvent(0, .1f + i * .08f, i, 0, 1, .4f, false, 0, 3, 2, 0));
        return result;
    }

    static byte[] ink(boolean continuous, boolean numeral, boolean internalBreak) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 9; i++) {
            int x = 54 + i * 40;
            for (int y = 40; y <= 96; y++) g[y * W + x] = 0;
            for (int y = 93; y <= 99; y++)
                for (int xx = x - 9; xx <= x + 1; xx++) g[y * W + xx] = 0;
        }
        for (int x = 54; x <= 374; x++)
            if ((continuous || x <= 174 || x >= 214) && (!internalBreak || x <= 254 || x >= 294))
                for (int y = 37; y <= 43; y++) g[y * W + x] = 0;
        if (numeral)
            for (int y = 0; y < 22; y++)
                for (int x = 0; x < 12; x++) {
                    boolean dark =
                            y < 3
                                    || y < 9 && x < 3
                                    || y >= 9 && y < 12
                                    || y >= 12 && y < 19 && x >= 9
                                    || y >= 15 && y < 19 && x < 3
                                    || y >= 19 && x >= 2 && x <= 8;
                    if (dark) g[(145 + y) * W + 284 + x] = 0;
                }
        return g;
    }

    static List<ScoreNoteEvent> apply(byte[] g) {
        return TripletRhythmDetector.apply(notes(), BARS, g, W, H);
    }

    @Test
    public void onlyMarkedFiveAfterOrdinaryFourChange() {
        var result = apply(ink(false, true, false));
        for (int i = 0; i < 9; i++)
            assertEquals("note " + i, i < 4 ? 1 : 5, result.get(i).tupletDivisor());
        for (int i = 4; i < 9; i++) assertEquals(4, result.get(i).tupletNormalNotes());
    }

    @Test
    public void continuousBeamCannotBePartitionedByNearbyFive() {
        assertEquals(notes(), apply(ink(true, true, false)));
    }

    @Test
    public void separateBeamsRequirePrintedNumeral() {
        assertEquals(notes(), apply(ink(false, false, false)));
    }

    @Test
    public void fiveCannotSpanInternalBeamBreak() {
        assertEquals(notes(), apply(ink(false, true, true)));
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        byte[] g = ink(false, true, false), original = g.clone();
        apply(g);
        assertArrayEquals(original, g);
    }
}
