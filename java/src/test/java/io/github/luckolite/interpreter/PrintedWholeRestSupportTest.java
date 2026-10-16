// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original uneven staff spacing with a partial antialiased support fringe. */
public class PrintedWholeRestSupportTest {
    static final int W = 800, H = 320;
    byte[] gray = new byte[W * H];

    public PrintedWholeRestSupportTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {100, 118, 134, 150, 166}) box(20, y, 779, y);
        box(20, 117, 365, 117);
        box(435, 117, 779, 117);
        box(389, 119, 411, 125);
    }

    void box(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
    }

    List<WholeRestClassifier.Decision> read(List<ScoreNoteEvent> notes) {
        return WholeRestClassifier.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.025f, .97375f, .2f, .7f)),
                List.of(new SixteenthRestDetector.Staff(100, 166, 16.5f, 0, 1)),
                notes,
                List.of());
    }

    @Test
    public void actualSecondRuleOwnsHangingPlate() {
        var out = read(List.of());
        assertEquals(1, out.size());
        assertTrue(out.get(0).event().isFullMeasure());
    }

    @Test
    public void fullBarDurationUsesThreeBeatContext() {
        var out = read(List.of());
        assertEquals(1, out.size());
        assertEquals(3, out.get(0).event().resolvedDurationBeats(3), 0);
    }

    @Test
    public void separateNotePreventsWholeBarClaim() {
        var n = new ScoreNoteEvent(0, .15f, 0, 0, 1, .5f, false, 0, 0, 2, 1);
        assertTrue(read(List.of(n)).stream().noneMatch(d -> d.event().isFullMeasure()));
    }

    @Test
    public void detachedPlateRemainsUnproved() {
        for (int x = 389; x <= 411; x++) gray[119 * W + x] = (byte) 255;
        assertTrue(read(List.of()).isEmpty());
    }

    @Test
    public void standingPlateIsNotWholeRest() {
        for (int y = 119; y <= 125; y++)
            for (int x = 389; x <= 411; x++) gray[y * W + x] = (byte) 255;
        box(389, 111, 411, 116);
        assertTrue(read(List.of()).isEmpty());
    }

    @Test
    public void sourcePixelsPreserved() {
        byte[] before = gray.clone();
        read(List.of());
        assertArrayEquals(before, gray);
    }
}
