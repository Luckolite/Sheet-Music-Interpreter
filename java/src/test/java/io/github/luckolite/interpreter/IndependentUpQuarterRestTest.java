// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original separately drawn rest beside a continuing quarter shaft and above its head. */
public final class IndependentUpQuarterRestTest {
    static final int W = 600, H = 360;
    static final float GAP = 14.5f, TOP = 80, X = 220;
    static final float CAP = TOP + 3.5f * GAP, HEAD = CAP + 2.25f * GAP;

    static void ellipse(byte[] gray, float x, float y, float rx, float ry, int shade) {
        for (int row = (int) Math.floor(y - ry); row <= Math.ceil(y + ry); row++)
            for (int col = (int) Math.floor(x - rx); col <= Math.ceil(x + rx); col++)
                if (Math.pow((col - x) / rx, 2) + Math.pow((row - y) / ry, 2) <= 1)
                    gray[row * W + col] = (byte) shade;
    }

    static byte[] page(boolean bulb, boolean tail, int direction, boolean fullStem, int shade) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        if (bulb) ellipse(gray, X, CAP, .42f * GAP, .3f * GAP, shade);
        if (tail) {
            int first = Math.round(CAP - .2f * GAP), last = Math.round(CAP + 1.5f * GAP);
            for (int y = first; y <= last; y++) {
                int x =
                        Math.round(
                                X + .55f * GAP - (y - first) / (float) (last - first) * .67f * GAP);
                gray[y * W + x] = (byte) shade;
                gray[y * W + x + 1] = (byte) shade;
            }
        }
        ellipse(gray, X, HEAD, .7f * GAP, .45f * GAP, shade);
        if (direction != 0) {
            int shaft = Math.round(X + direction * .7f * GAP);
            int top = Math.round(direction == 1 ? HEAD - (fullStem ? 3 : 2) * GAP : HEAD);
            int bottom = Math.round(direction == 1 ? HEAD : HEAD + 3 * GAP);
            for (int y = top; y <= bottom; y++)
                for (int x = shaft; x <= shaft + 1; x++) gray[y * W + x] = (byte) shade;
        }
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < W - 20; x++) gray[Math.round(TOP + line * GAP) * W + x] = 0;
        return gray;
    }

    static ScoreNoteEvent owner(int measure, int staff, int count, int beams, float position) {
        return new ScoreNoteEvent(
                        measure,
                        position,
                        3,
                        staff,
                        count,
                        HEAD / H,
                        false,
                        0,
                        beams,
                        2,
                        beams == 0 ? 1 : 0)
                .withStemDirection(1);
    }

    static ScoreNoteEvent quarter() {
        return owner(0, 0, 1, 0, (X / W - .03f) / .94f);
    }

    static List<ScoreRestEvent> read(byte[] gray, List<ScoreNoteEvent> notes, boolean tracked) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.03f, .97f, .03f, .95f)),
                List.of(
                        new SixteenthRestDetector.Staff(
                                TOP,
                                TOP + 4 * GAP,
                                GAP,
                                0,
                                1,
                                tracked ? StaffPitchTrack.linear(W, TOP + 4 * GAP, GAP, 0) : null)),
                notes);
    }

    static void eighth(List<ScoreRestEvent> rests) {
        assertEquals(rests.toString(), 1, rests.size());
        var rest = rests.get(0);
        assertEquals(.5, rest.durationBeats(), 0);
        assertEquals(0, rest.measureIndex());
        assertEquals(0, rest.staffIndex());
        assertEquals(1, rest.staffCount());
    }

    @Test
    public void continuingQuarterShaftCannotOwnTheOtherVoicesCompleteRest() {
        eighth(read(page(true, true, 1, true, 0), List.of(quarter()), false));
    }

    @Test
    public void trackedStaffRetainsTheIndependentRest() {
        eighth(read(page(true, true, 1, true, 0), List.of(quarter()), true));
    }

    @Test
    public void faintOwnedShaftAndRestRetainTheSameWrittenSilence() {
        eighth(read(page(true, true, 1, true, 145), List.of(quarter()), false));
    }

    @Test
    public void missingPrintedShaftCannotSupplyOwnershipProof() {
        assertTrue(read(page(true, true, 0, true, 0), List.of(quarter()), false).isEmpty());
    }

    @Test
    public void shaftEndingAtTheGlyphDoesNotProveASeparateQuarter() {
        assertTrue(read(page(true, true, 1, false, 0), List.of(quarter()), false).isEmpty());
    }

    @Test
    public void noRestBodyCannotCreateSilence() {
        assertTrue(read(page(false, false, 1, true, 0), List.of(quarter()), false).isEmpty());
    }

    @Test
    public void bulbWithoutItsDiagonalTailCannotCreateSilence() {
        assertTrue(read(page(true, false, 1, true, 0), List.of(quarter()), false).isEmpty());
    }

    @Test
    public void diagonalWithoutItsRoundedBulbCannotCreateSilence() {
        assertTrue(read(page(false, true, 1, true, 0), List.of(quarter()), false).isEmpty());
    }

    @Test
    public void beamedMetadataDoesNotSupplyTheIndependentQuarterProof() {
        assertTrue(
                read(
                                page(true, true, 1, true, 0),
                                List.of(owner(0, 0, 1, 1, (X / W - .03f) / .94f)),
                                false)
                        .isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyTheProof() {
        assertTrue(
                read(
                                page(true, true, 1, true, 0),
                                List.of(owner(0, 1, 1, 0, (X / W - .03f) / .94f)),
                                false)
                        .isEmpty());
    }

    @Test
    public void nonfinitePositionCannotSupplyTheProof() {
        assertTrue(
                read(page(true, true, 1, true, 0), List.of(owner(0, 0, 1, 0, Float.NaN)), false)
                        .isEmpty());
    }

    @Test
    public void repeatedReadsAndInkMutationKeepCallerOwnership() {
        byte[] gray = page(true, true, 1, true, 0), before = gray.clone();
        var notes = List.of(quarter());
        var first = read(gray, notes, false);
        eighth(first);
        assertEquals(first, read(gray, notes, false));
        assertArrayEquals(before, gray);
        byte[] erased = page(false, false, 1, true, 0);
        System.arraycopy(erased, 0, gray, 0, gray.length);
        assertTrue(read(gray, notes, false).isEmpty());
        System.arraycopy(before, 0, gray, 0, gray.length);
        assertEquals(first, read(gray, notes, false));
        assertArrayEquals(before, gray);
    }
}
