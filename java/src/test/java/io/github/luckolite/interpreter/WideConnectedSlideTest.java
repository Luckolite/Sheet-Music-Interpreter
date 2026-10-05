// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated long connecting strokes; no source-score geometry or imagery. */
public class WideConnectedSlideTest {
    static final int W = 480, H = 240;
    static final float GAP = 12;

    byte[] ink(int right, double slope, boolean curved) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 92; x <= right; x++) {
            int y =
                    (int)
                            Math.round(
                                    90
                                            + (x - 80) * slope
                                            + (curved
                                                    ? 9
                                                            * Math.sin(
                                                                    (x - 92)
                                                                            * Math.PI
                                                                            / (right - 92))
                                                    : 0));
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        return gray;
    }

    NoteSlideDetector.Head source(int staff, int measure) {
        return new NoteSlideDetector.Head(80, 90, GAP, staff, measure);
    }

    NoteSlideDetector.Head target(int right, double slope) {
        return new NoteSlideDetector.Head(
                right + 12, (float) (90 + (right + 12 - 80) * slope), GAP, 0, 0);
    }

    List<NoteSlideDetector.Stroke> detect(
            int right, double slope, boolean curved, List<NoteSlideDetector.Head> heads) {
        return NoteSlideDetector.detect(ink(right, slope, curved), W, H, List.of(), heads);
    }

    @Test
    public void longStraightStrokeHasItsPrintedSource() {
        var found = detect(188, .27, false, List.of(source(0, 0), target(188, .27)));
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).noteIndex());
        assertTrue(found.get(0).connected());
    }

    @Test
    public void longShallowStrokeHasItsPrintedSource() {
        var found = detect(212, .13, false, List.of(source(0, 0), target(212, .13)));
        assertEquals(1, found.size());
        assertTrue(found.get(0).connected());
    }

    @Test
    public void longAscendingStrokeKeepsDirection() {
        var found = detect(188, -.27, false, List.of(source(0, 0), target(188, -.27)));
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).direction());
        assertTrue(found.get(0).connected());
    }

    @Test
    public void longStrokeWithoutSourceIsRejected() {
        assertTrue(detect(188, .27, false, List.of(target(188, .27))).isEmpty());
    }

    @Test
    public void otherStaffCannotSupplySource() {
        assertTrue(detect(188, .27, false, List.of(source(1, 0), target(188, .27))).isEmpty());
    }

    @Test
    public void otherMeasureCannotSupplySource() {
        assertTrue(detect(188, .27, false, List.of(source(0, 1), target(188, .27))).isEmpty());
    }

    @Test
    public void interveningAttackDoesNotConnectEarlierHead() {
        assertTrue(
                detect(
                                188,
                                .27,
                                false,
                                List.of(
                                        source(0, 0),
                                        new NoteSlideDetector.Head(154, 150, GAP, 0, 0),
                                        target(188, .27)))
                        .isEmpty());
    }

    @Test
    public void ambiguousSourceChordIsRejected() {
        assertTrue(
                detect(
                                188,
                                .27,
                                false,
                                List.of(
                                        source(0, 0),
                                        new NoteSlideDetector.Head(80, 78, GAP, 0, 0),
                                        target(188, .27)))
                        .isEmpty());
    }

    @Test
    public void mismatchedSourcePitchIsRejected() {
        assertTrue(
                detect(
                                188,
                                .27,
                                false,
                                List.of(
                                        new NoteSlideDetector.Head(80, 120, GAP, 0, 0),
                                        target(188, .27)))
                        .isEmpty());
    }

    @Test
    public void nearlyHorizontalInkIsRejected() {
        assertTrue(detect(212, .03, false, List.of(source(0, 0), target(212, .03))).isEmpty());
    }

    @Test
    public void bowedSlurIsRejected() {
        assertTrue(detect(188, .27, true, List.of(source(0, 0), target(188, .27))).isEmpty());
    }

    @Test
    public void UnboundedLengthIsRejected() {
        assertTrue(detect(272, .27, false, List.of(source(0, 0), target(272, .27))).isEmpty());
    }

    @Test
    public void extendedSourceProofDoesNotConnectLaterIsolatedApproach() {
        byte[] gray = ink(188, .27, false);
        for (int x = 300; x <= 318; x++) {
            int y = (int) Math.round(170 + (x - 300) * .65);
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        byte[] original = gray.clone();
        var approach = new NoteSlideDetector.Head(330, 189.5f, GAP, 1, 0);
        var heads = List.of(source(0, 0), target(188, .27), approach);
        var found = NoteSlideDetector.detect(gray, W, H, List.of(), heads);
        assertEquals(2, found.size());
        assertEquals(1, found.get(0).noteIndex());
        assertEquals(-1, found.get(0).direction());
        assertTrue(found.get(0).connected());
        assertEquals(2, found.get(1).noteIndex());
        assertEquals(-1, found.get(1).direction());
        assertFalse(found.get(1).connected());
        assertArrayEquals(original, gray);
        assertEquals(List.of(source(0, 0), target(188, .27), approach), heads);

        // A fresh call cannot retain a component's earlier source witness.
        var isolated = NoteSlideDetector.detect(gray, W, H, List.of(), List.of(approach));
        assertEquals(1, isolated.size());
        var expected = found.get(1);
        assertEquals(
                new NoteSlideDetector.Stroke(
                        expected.left(),
                        expected.leftY(),
                        expected.right(),
                        expected.rightY(),
                        expected.direction(),
                        0,
                        false),
                isolated.get(0));
        assertArrayEquals(original, gray);
    }
}
