// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedTupletNumeralTest {
    private static final int W = 400, H = 500, G = 20;
    private static final String[] THREE = {
        "..#######...", ".##########.", "###......###", "####.....###",
        "####.....###", "####.....###", ".##.....####", ".......####.",
        "......####..", "....#####...", "....#####...", "....#####...",
        "......####..", ".......####.", "##.....####.", "###....####.",
        "###....####.", "###....####.", ".###....###.", "..########..",
        "..########..", "....####...."
    };

    private byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    private void three(byte[] g, int left, int top) {
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(top + y) * W + left + x] = 0;
    }

    private void rect(byte[] g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) g[y * W + x] = 0;
    }

    private int[] find(byte[] g, boolean owner) {
        return RepeatedTupletNumeral.find(
                g,
                W,
                H,
                new int[] {30, 25, 41, 46},
                200,
                280,
                220,
                220,
                G,
                b -> owner && b[0] >= 233 && b[0] <= 237 && b[1] >= 98 && b[1] <= 102);
    }

    private byte[] pair() {
        byte[] g = paper();
        three(g, 30, 25);
        three(g, 235, 100);
        return g;
    }

    @Test
    public void joinedBaselineBeamKeepsPrintedIdentity() {
        byte[] g = pair();
        rect(g, 200, 119, 280, 124);
        assertNotNull(find(g, true));
    }

    @Test
    public void thinTieAcrossUpperBowlKeepsPrintedIdentity() {
        byte[] g = pair();
        for (int x = 195; x < 290; x++) {
            int y = 104 + (x - 241) * (x - 241) / 500;
            rect(g, x, y, x, y + 1);
        }
        assertNotNull(find(g, true));
    }

    @Test
    public void aClearRepeatStillNeedsItsBeamOwner() {
        assertNull(find(pair(), false));
    }

    @Test
    public void anEmptyTargetCannotBorrowTheReference() {
        byte[] g = paper();
        three(g, 30, 25);
        assertNull(find(g, true));
    }

    @Test
    public void aSingleBowlCannotBorrowAThree() {
        byte[] g = pair();
        rect(g, 200, 119, 280, 124);
        for (int y = 110; y <= 118; y++) for (int x = 235; x < 247; x++) g[y * W + x] = (byte) 255;
        assertNull(find(g, true));
    }

    @Test
    public void aClosedEightCannotBorrowAThree() {
        byte[] g = pair();
        rect(g, 235, 101, 237, 119);
        assertNull(find(g, true));
    }

    @Test
    public void aFilledBlobCannotBorrowAThree() {
        byte[] g = pair();
        rect(g, 235, 100, 246, 121);
        assertNull(find(g, true));
    }

    @Test
    public void aFiveCannotBorrowAThree() {
        byte[] g = pair();
        rect(g, 235, 100, 237, 109);
        assertNull(find(g, true));
    }

    @Test
    public void tooMuchOcclusionRemainsUncertain() {
        byte[] g = pair();
        rect(g, 200, 108, 280, 121);
        assertNull(find(g, true));
    }

    @Test
    public void lowContrastStainRemainsUncertain() {
        byte[] g = pair();
        for (int y = 97; y < 125; y++) for (int x = 229; x < 251; x++) g[y * W + x] = (byte) 180;
        assertNull(find(g, true));
    }

    @Test
    public void noReferenceInkCannotCreateAThree() {
        byte[] g = pair();
        for (int y = 25; y <= 46; y++) for (int x = 30; x <= 41; x++) g[y * W + x] = (byte) 255;
        assertNull(find(g, true));
    }

    @Test
    public void unrelatedInkBetweenTheBowlsRejectsTheMatch() {
        byte[] g = pair();
        rect(g, 235, 107, 238, 114);
        assertNull(find(g, true));
    }

    private List<ScoreNoteEvent> pipeline(boolean owns) {
        byte[] g = paper();
        three(g, 94, 166);
        three(g, 254, 166);
        rect(g, 200, 184, 320, 189);
        List<ScoreNoteEvent> notes = new ArrayList<>();
        for (int x : new int[] {60, 100, 140, 220, 260, 300}) {
            notes.add(
                    new ScoreNoteEvent(0, x / (float) W, 0, 1, 2, 270f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
            rect(g, x + 5, 205, x + 7, 270);
        }
        rect(g, 65, 205, 147, 210);
        if (owns) rect(g, 225, 205, 307, 210);
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(0, 1, .1f, .74f)), g, W, H);
    }

    @Test
    public void completePipelineMarksBothOwnedGroups() {
        var notes = pipeline(true);
        assertTrue(
                notes.stream().allMatch(n -> n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2));
    }

    @Test
    public void completePipelineRejectsTheUnattachedRepeat() {
        var notes = pipeline(false);
        assertTrue(notes.subList(0, 3).stream().allMatch(n -> n.tupletDivisor() == 3));
        assertTrue(notes.subList(3, 6).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void malformedImageIsRejected() {
        assertNull(
                RepeatedTupletNumeral.find(
                        null, W, H, new int[] {30, 25, 41, 46}, 200, 280, 220, 220, G, b -> true));
    }

    @Test
    public void malformedReferenceIsRejected() {
        assertNull(
                RepeatedTupletNumeral.find(
                        pair(),
                        W,
                        H,
                        new int[] {-1, 25, 41, 46},
                        200,
                        280,
                        220,
                        220,
                        G,
                        b -> true));
    }

    @Test
    public void aThreeBesideAnotherDigitIsNotAStandaloneTuplet() {
        byte[] g = pair();
        three(g, 250, 100);
        assertNull(find(g, true));
    }

    @Test
    public void theThreeInThirteenIsNotAStandaloneTuplet() {
        byte[] g = pair();
        rect(g, 228, 100, 230, 121);
        assertNull(find(g, true));
    }
}
