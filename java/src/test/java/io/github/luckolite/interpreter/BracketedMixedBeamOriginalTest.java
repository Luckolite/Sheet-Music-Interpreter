// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original mixed durations and independently complete beam groups, no score pixels. */
public class BracketedMixedBeamOriginalTest {
    static final int W = 500, H = 240;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, .1f, .8f));

    static void box(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = 0;
    }

    static byte[] page(boolean joined) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++) {
                boolean ink =
                        y < 3
                                || y < 9 && x < 3
                                || y >= 9 && y < 12
                                || y >= 12 && y < 19 && x >= 9
                                || y >= 15 && y < 19 && x < 3
                                || y >= 19 && x >= 2 && x <= 8;
                if (ink) g[(30 + y) * W + 174 + x] = 0;
            }
        for (int x : new int[] {75, 125, 175, 225, 250, 275, 300}) {
            box(g, x - 4, 122, x + 4, 128);
            box(g, x + 7, 67, x + 9, 125);
        }
        box(g, 82, 67, 184, 73);
        box(g, 232, 67, 309, 73);
        box(g, 232, 77, 309, 83);
        if (joined) box(g, 185, 67, 231, 73);
        return g;
    }

    static List<ScoreNoteEvent> notes() {
        var a = new ArrayList<ScoreNoteEvent>();
        int k = 0;
        for (int x : new int[] {75, 125, 175, 225, 250, 275, 300})
            a.add(
                    new ScoreNoteEvent(
                            0, x / (float) W, 0, 0, 1, 125f / H, false, 0, k++ < 3 ? 1 : 2, 2, 0));
        return a;
    }

    @Test
    public void explicitBracketKeepsMixedTupletAcrossBeamGroups() {
        byte[] g = page(false);
        box(g, 69, 39, 170, 40);
        box(g, 189, 39, 306, 40);
        box(g, 69, 39, 70, 49);
        box(g, 305, 39, 306, 49);
        var r = TripletRhythmDetector.apply(notes(), BARS, g, W, H);
        for (var x : r) assertEquals(5, x.tupletDivisor());
    }

    @Test
    public void fingeringDoesNotJoinSeparatePrimaryBeams() {
        var n = notes();
        var r = TripletRhythmDetector.apply(n, BARS, page(false), W, H);
        for (var x : r) assertEquals(1, x.tupletDivisor());
    }

    @Test
    public void oneContinuousMixedPrimaryBeamKeepsPrintedFive() {
        var r = TripletRhythmDetector.apply(notes(), BARS, page(true), W, H);
        for (var x : r) assertEquals(5, x.tupletDivisor());
    }
}
