// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original separately shafted quarter and complete lower eighth-rest voice. */
public final class SeparatedQuarterRestRecognitionTest {
    static final int W = 600, H = 360;
    static final float GAP = 14.5f,
            TOP = 80,
            HEAD_X = 220,
            HEAD_Y = TOP + 2.5f * GAP,
            CAP_Y = TOP + 3.5f * GAP;

    private void ellipse(byte[] gray, float cx, float cy, float rx, float ry) {
        for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
            for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++)
                if (Math.pow((x - cx) / rx, 2) + Math.pow((y - cy) / ry, 2) <= 1)
                    gray[y * W + x] = 0;
    }

    private byte[] page(boolean upShaft, boolean tail, boolean connected) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < W - 20; x++) gray[Math.round(TOP + line * GAP) * W + x] = 0;
        ellipse(gray, HEAD_X, HEAD_Y, .7f * GAP, .5f * GAP);
        int shaft = Math.round(HEAD_X + .7f * GAP);
        if (upShaft)
            for (int y = Math.round(HEAD_Y - 3 * GAP); y <= Math.round(HEAD_Y); y++)
                for (int x = shaft; x <= shaft + 1; x++) gray[y * W + x] = 0;
        ellipse(gray, HEAD_X, CAP_Y, .42f * GAP, .30f * GAP);
        int first = Math.round(CAP_Y - .2f * GAP), last = Math.round(CAP_Y + 1.5f * GAP);
        if (tail)
            for (int y = first; y <= last; y++) {
                int x =
                        Math.round(
                                HEAD_X
                                        + .55f * GAP
                                        - (y - first) / (float) (last - first) * .67f * GAP);
                gray[y * W + x] = 0;
                gray[y * W + x + 1] = 0;
            }
        if (connected)
            for (int y = Math.round(HEAD_Y); y <= last; y++)
                for (int x = shaft; x <= shaft + 1; x++) gray[y * W + x] = 0;
        return gray;
    }

    private ScoreNoteEvent owner(int measure, int staff, boolean quarter, float position, float y) {
        return new ScoreNoteEvent(
                        measure,
                        position,
                        3,
                        staff,
                        1,
                        y / H,
                        false,
                        0,
                        quarter ? 0 : 1,
                        2,
                        quarter ? 1 : 0)
                .withStemDirection(1);
    }

    private ScoreNoteEvent quarter() {
        return owner(0, 0, true, (HEAD_X / W - .03f) / .94f, HEAD_Y);
    }

    private List<ScoreRestEvent> read(byte[] gray, boolean tracked, List<ScoreNoteEvent> notes) {
        var track = tracked ? StaffPitchTrack.linear(W, TOP + 4 * GAP, GAP, 0) : null;
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.03f, .97f, .05f, .95f)),
                List.of(new SixteenthRestDetector.Staff(TOP, TOP + 4 * GAP, GAP, 0, 1, track)),
                notes);
    }

    private void assertCompleteEighth(List<ScoreRestEvent> result) {
        assertEquals(result.toString(), 1, result.size());
        var rest = result.get(0);
        assertEquals(.5, rest.durationBeats(), 0);
        assertEquals(0, rest.staffIndex());
        assertEquals(0, rest.measureIndex());
        assertEquals(140 / 360.0, rest.pageY(), 1e-6);
        assertEquals(27 / 360.0, rest.pageHeight(), 1e-6);
    }

    @Test
    public void broadRuleMaskCannotJoinQuarterHeadToSeparateCompleteRest() {
        assertCompleteEighth(read(page(true, true, false), false, List.of(quarter())));
    }

    @Test
    public void trackedStaffKeepsTheSameCompleteRest() {
        assertCompleteEighth(read(page(true, true, false), true, List.of(quarter())));
    }

    @Test
    public void missingPrintedUpShaftCannotAuthorizeTheSeparateVoice() {
        assertTrue(read(page(false, true, false), false, List.of(quarter())).isEmpty());
    }

    @Test
    public void connectedContinuingShaftCannotBeCroppedIntoTheRest() {
        assertTrue(read(page(true, true, true), false, List.of(quarter())).isEmpty());
    }

    @Test
    public void incompleteBulbCannotAddSilence() {
        assertTrue(read(page(true, false, false), false, List.of(quarter())).isEmpty());
    }

    @Test
    public void beamedHeadDoesNotSupplyTheIndependentQuarterProof() {
        assertTrue(
                read(
                                page(true, true, false),
                                false,
                                List.of(owner(0, 0, false, (HEAD_X / W - .03f) / .94f, HEAD_Y)))
                        .isEmpty());
    }

    @Test
    public void headInsideTheGlyphStillBlocksRestOwnership() {
        assertTrue(
                read(
                                page(true, true, false),
                                false,
                                List.of(owner(0, 0, false, (HEAD_X / W - .03f) / .94f, CAP_Y)))
                        .isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyTheQuarterProof() {
        assertTrue(
                read(
                                page(true, true, false),
                                false,
                                List.of(owner(0, 1, true, (HEAD_X / W - .03f) / .94f, HEAD_Y)))
                        .isEmpty());
    }

    @Test
    public void anotherMeasureCannotSupplyTheQuarterProof() {
        assertTrue(
                read(
                                page(true, true, false),
                                false,
                                List.of(owner(1, 0, true, (HEAD_X / W - .03f) / .94f, HEAD_Y)))
                        .isEmpty());
    }

    @Test
    public void nonfiniteOwnerPositionCannotSupplyPrintedProof() {
        assertTrue(
                read(page(true, true, false), false, List.of(owner(0, 0, true, Float.NaN, HEAD_Y)))
                        .isEmpty());
    }

    @Test
    public void callerInkAndRepeatedReadsRemainIndependent() {
        byte[] gray = page(true, true, false), original = gray.clone();
        var notes = List.of(quarter());
        var first = read(gray, false, notes);
        assertCompleteEighth(first);
        assertEquals(first, read(gray, false, notes));
        assertArrayEquals(original, gray);
        int shaft = Math.round(HEAD_X + .7f * GAP);
        for (int y = Math.round(HEAD_Y - 3 * GAP); y < HEAD_Y - GAP; y++)
            for (int x = shaft; x <= shaft + 1; x++) gray[y * W + x] = (byte) 255;
        assertTrue(read(gray, false, notes).isEmpty());
        System.arraycopy(original, 0, gray, 0, original.length);
        assertEquals(first, read(gray, false, notes));
        assertArrayEquals(original, gray);
    }
}
