// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original partial-rest/quarter ownership pixels; no score/model data. */
public final class DownQuarterContactRestTest {
    static final int W = 600, H = 360;
    static final float X = 220, TOP = 80, G = 16.5f, CAP = TOP - .5f * G, HEAD = CAP + 1.5f * G;

    static void ellipse(byte[] g, float x, float y, float rx, float ry, int shade) {
        for (int yy = (int) Math.floor(y - ry); yy <= Math.ceil(y + ry); yy++)
            for (int xx = (int) Math.floor(x - rx); xx <= Math.ceil(x + rx); xx++)
                if (Math.pow((xx - x) / rx, 2) + Math.pow((yy - y) / ry, 2) <= 1)
                    g[yy * W + xx] = (byte) shade;
    }

    static byte[] page(
            boolean bulb,
            boolean tail,
            boolean head,
            int direction,
            int slope,
            int shade,
            boolean contact) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (bulb) ellipse(g, X, CAP, G * .42f, G * .30f, shade);
        if (tail) {
            int first = Math.round(CAP - G * .2f),
                    last = Math.round(CAP + G * (contact ? 1.5f : .55f));
            for (int y = first; y <= last; y++) {
                float f = (y - first) / (1.7f * G);
                int x = Math.round(X + G * .55f - slope * f * G * .67f);
                g[y * W + x] = (byte) shade;
                g[y * W + x + 1] = (byte) shade;
            }
        }
        if (head) ellipse(g, X, HEAD, G * .7f, G * .45f, shade);
        if (direction != 0) {
            int x = Math.round(X + direction * G * .7f),
                    a = Math.round(direction == 1 ? HEAD - 3 * G : HEAD),
                    b = Math.round(direction == 1 ? HEAD : HEAD + 3 * G);
            for (int y = a; y <= b; y++)
                for (int xx = x; xx <= x + 1; xx++) g[y * W + xx] = (byte) shade;
        }
        for (int j = 0; j < 5; j++)
            for (int x = 20; x < W - 20; x++) g[Math.round(TOP + j * G) * W + x] = 0;
        return g;
    }

    static ScoreNoteEvent quarter(int direction, int staff, int beams) {
        return new ScoreNoteEvent(
                        0,
                        (X / W - .03f) / .94f,
                        6,
                        staff,
                        1,
                        HEAD / H,
                        false,
                        0,
                        beams,
                        2,
                        beams == 0 ? 1 : 0)
                .withStemDirection(direction);
    }

    static List<ScoreRestEvent> read(byte[] g, List<ScoreNoteEvent> n, boolean tracked) {
        byte[] before = g.clone();
        var out =
                SixteenthRestDetector.detect(
                        g,
                        W,
                        H,
                        List.of(new MeasureRegion(.03f, .97f, .03f, .95f)),
                        List.of(
                                new SixteenthRestDetector.Staff(
                                        TOP,
                                        TOP + 4 * G,
                                        G,
                                        0,
                                        1,
                                        tracked
                                                ? StaffPitchTrack.linear(W, TOP + 4 * G, G, 0)
                                                : null)),
                        n);
        assertArrayEquals(before, g);
        return out;
    }

    static List<ScoreRestEvent> ordinary(byte[] g) {
        return read(g, List.of(quarter(-1, 0, 0)), false);
    }

    static void eighth(List<ScoreRestEvent> r) {
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void visibleBulbAndTailEnteringSeparateQuarterRemainAnEighthRest() {
        eighth(ordinary(page(true, true, true, -1, 1, 0, true)));
    }

    @Test
    public void faintQuarterAndContactRestPreserveTheSameValue() {
        eighth(ordinary(page(true, true, true, -1, 1, 145, true)));
    }

    @Test
    public void trackedPrintedStaffRetainsTheContactOwnership() {
        eighth(read(page(true, true, true, -1, 1, 0, true), List.of(quarter(-1, 0, 0)), true));
    }

    @Test
    public void noRestBodyCannotAddSilence() {
        assertTrue(ordinary(page(false, false, true, -1, 1, 0, true)).isEmpty());
    }

    @Test
    public void bulbAloneCannotSupplyMissingTail() {
        assertTrue(ordinary(page(true, false, true, -1, 1, 0, true)).isEmpty());
    }

    @Test
    public void diagonalAloneCannotSupplyMissingBulb() {
        assertTrue(ordinary(page(false, true, true, -1, 1, 0, true)).isEmpty());
    }

    @Test
    public void missingPrintedQuarterShaftCannotSupplyIndependentOwner() {
        assertTrue(ordinary(page(true, true, true, 0, 1, 0, true)).isEmpty());
    }

    @Test
    public void missingMeasuredQuarterHeadCannotSupplyOcclusion() {
        assertTrue(ordinary(page(true, true, false, -1, 1, 0, true)).isEmpty());
    }

    @Test
    public void aStraightSpineCannotBecomeADiagonalRest() {
        assertTrue(ordinary(page(true, true, true, -1, 0, 0, true)).isEmpty());
    }

    @Test
    public void aReverseDiagonalCannotBecomeARestTail() {
        assertTrue(ordinary(page(true, true, true, -1, -1, 0, true)).isEmpty());
    }

    @Test
    public void anUnconnectedShortTailCannotInventItsHiddenFoot() {
        assertTrue(ordinary(page(true, true, true, -1, 1, 0, false)).isEmpty());
    }

    @Test
    public void beamedMetadataCannotSupplyTheQuarterOwner() {
        assertTrue(
                read(page(true, true, true, -1, 1, 0, true), List.of(quarter(-1, 0, 1)), false)
                        .isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyTheQuarterOwner() {
        assertTrue(
                read(page(true, true, true, -1, 1, 0, true), List.of(quarter(-1, 1, 0)), false)
                        .isEmpty());
    }

    @Test
    public void actualSoundingHeadOwnsTheUpperBody() {
        var n = quarter(-1, 0, 0);
        var upper =
                new ScoreNoteEvent(0, n.positionInMeasure(), 9, 0, 1, CAP / H, false, 0, 1, 2, 0)
                        .withStemDirection(1);
        assertTrue(
                read(page(true, true, true, -1, 1, 0, true), List.of(n, upper), false).isEmpty());
    }

    @Test
    public void repeatedEraseRestoreKeepsCallerPixelsAndOriginalNotes() {
        byte[] g = page(true, true, true, -1, 1, 0, true), old = g.clone();
        var n = List.of(quarter(-1, 0, 0));
        var r = read(g, n, false);
        eighth(r);
        assertEquals(r, read(g, n, false));
        System.arraycopy(page(false, false, true, -1, 1, 0, true), 0, g, 0, g.length);
        assertTrue(read(g, n, false).isEmpty());
        System.arraycopy(old, 0, g, 0, g.length);
        assertEquals(r, read(g, n, false));
        assertArrayEquals(old, g);
        assertEquals(1, n.get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void aStraightTailCannotBorrowLeftwardMotionFromTheRoundedHeadRim() {
        float gap = 27, cap = TOP - .35f * gap, head = cap + 1.5f * gap;
        for (int shade : new int[] {0, 145}) {
            byte[] g = new byte[W * H];
            Arrays.fill(g, (byte) 255);
            ellipse(g, X, cap, gap * .42f, gap * .30f, shade);
            for (int y = Math.round(cap - gap * .2f); y <= Math.round(head); y++) {
                int x = Math.round(X + gap * .55f);
                g[y * W + x] = (byte) shade;
                g[y * W + x + 1] = (byte) shade;
            }
            ellipse(g, X, head, gap * .7f, gap * .45f, shade);
            for (int y = Math.round(head); y <= Math.round(head + 3 * gap); y++) {
                int x = Math.round(X - gap * .7f);
                g[y * W + x] = (byte) shade;
                g[y * W + x + 1] = (byte) shade;
            }
            for (int j = 0; j < 5; j++)
                for (int x = 20; x < W - 20; x++) g[Math.round(TOP + j * gap) * W + x] = 0;
            byte[] before = g.clone();
            var owner =
                    new ScoreNoteEvent(
                                    0, (X / W - .03f) / .94f, 6, 0, 1, head / H, false, 0, 0, 2, 1)
                            .withStemDirection(-1);
            var rests =
                    SixteenthRestDetector.detect(
                            g,
                            W,
                            H,
                            List.of(new MeasureRegion(.03f, .97f, .03f, .95f)),
                            List.of(new SixteenthRestDetector.Staff(TOP, TOP + 4 * gap, gap, 0, 1)),
                            List.of(owner));
            assertTrue(rests.toString(), rests.isEmpty());
            assertArrayEquals(before, g);
        }
    }
}
