// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original endpoint-aligned three and complete rest bracket on a verified printed staff. */
public class PrintedRestTupletScaleTest {
    static final int W = 800, H = 420;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, 100f / H, 260f / H));

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

        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++)
                if (rows[y].charAt(x) == '#' || closed && x < 2 && y > 1 && y < 20)
                    g[(183 + y) * W + 274 + x] = 0;
        for (int i = 0; i < 5; i++) line(g, 20, 100 + i * 16, 780, 100 + i * 16, 1);
        line(g, 270, 124, 270, 166, 2);
        line(g, 340, 108, 340, 159, 2);
        line(g, 270, 166, 340, 159, 5);
        line(g, 195, 180, 195, 199, 1);
        line(g, 195, 199, 265, 199, 1);
        line(g, 293, 199, 355, 199, 1);
        line(g, 355, 178, 355, 199, 1);
        return g;
    }

    static void line(byte[] g, int x1, int y1, int x2, int y2, int thick) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / (float) Math.max(1, steps)),
                    y = Math.round(y1 + (y2 - y1) * i / (float) Math.max(1, steps));
            for (int dy = -(thick / 2); dy <= thick / 2; dy++)
                for (int dx = -(thick / 2); dx <= thick / 2; dx++)
                    if (x + dx >= 0 && x + dx < W && y + dy >= 0 && y + dy < H)
                        g[(y + dy) * W + x + dx] = 0;
        }
    }

    static void erase(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) 255;
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(
                new ScoreNoteEvent(0, 280f / W, 5, 0, 1, 124f / H, false, 0, 1)
                        .withStemDirection(-1)
                        .withLeadingRest(.5f),
                new ScoreNoteEvent(0, 350f / W, 7, 0, 1, 108f / H, false, 0, 1)
                        .withStemDirection(-1));
    }

    static List<ScoreRestEvent> rests() {
        return List.of(new ScoreRestEvent(0, 200f / W, 165f / H, 32f / H, 0, 1, .5));
    }

    static TripletRhythmDetector.Rhythm apply(
            byte[] g, List<ScoreNoteEvent> n, List<ScoreRestEvent> r) {
        return TripletRhythmDetector.withRests(n, r, BARS, g, W, H);
    }

    static void rejects(byte[] g) {
        var n = notes();
        var r = rests();
        var result = apply(g, n, r);
        assertEquals(n, result.notes());
        assertEquals(r, result.rests());
    }

    @Test
    public void verifiedStaffGapReadsEndpointAlignedThree() {
        var result = apply(ink(false), notes(), rests());
        for (var n : result.notes()) {
            assertEquals(3, n.tupletDivisor());
            assertEquals(1 / 3d, ScoreNoteTiming.writtenDurationBeats(n), 1e-6);
        }
        assertEquals(1 / 3d, result.rests().get(0).durationBeats(), 1e-6);
        assertEquals(1 / 3d, result.notes().get(0).leadingRestBeats(), 1e-6);
    }

    @Test
    public void missingLeftHookRejects() {
        var g = ink(false);
        erase(g, 194, 178, 196, 198);
        rejects(g);
    }

    @Test
    public void missingRightHookRejects() {
        var g = ink(false);
        erase(g, 354, 176, 356, 198);
        rejects(g);
    }

    @Test
    public void missingBracketArmRejects() {
        var g = ink(false);
        erase(g, 310, 198, 330, 200);
        rejects(g);
    }

    @Test
    public void brokenPrintedBeamRejects() {
        var g = ink(false);
        erase(g, 302, 155, 314, 172);
        rejects(g);
    }

    @Test
    public void detachedFirstShaftRejects() {
        var g = ink(false);
        erase(g, 268, 142, 272, 153);
        rejects(g);
    }

    @Test
    public void closedEightRejects() {
        rejects(ink(true));
    }

    @Test
    public void noNumeralRejects() {
        var g = ink(false);
        erase(g, 273, 182, 286, 205);
        rejects(g);
    }

    @Test
    public void noVerifiedStaffCannotBorrowSmallerScale() {
        var g = ink(false);
        for (int i = 0; i < 5; i++) erase(g, 20, 100 + i * 16, 780, 100 + i * 16);
        rejects(g);
    }

    @Test
    public void inputInkAndOriginalDurationsStayUnchanged() {
        var g = ink(false);
        var before = g.clone();
        var n = notes();
        var r = rests();
        apply(g, n, r);
        assertArrayEquals(before, g);
        assertEquals(1, n.get(0).tupletDivisor());
        assertEquals(.5, n.get(0).leadingRestBeats(), 0);
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }
}
