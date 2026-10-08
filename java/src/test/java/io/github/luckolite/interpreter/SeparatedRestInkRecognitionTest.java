// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Original procedural rest and detached stem sharing columns, without score pixels. */
public class SeparatedRestInkRecognitionTest {
    static final int W = 600, H = 300;

    private byte[] page(boolean clutter, boolean tail, boolean connected) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 580; x++) gray[y * W + x] = 0;
        for (int y = 86; y <= 96; y++)
            for (int x = 170; x <= 184; x++)
                if (Math.pow((x - 177) / 7.0, 2) + Math.pow((y - 91) / 5.0, 2) <= 1)
                    gray[y * W + x] = 0;
        if (tail)
            for (int y = 88; y <= 116; y++) {
                int x = 185 - (y - 88) * 11 / 28;
                gray[y * W + x] = 0;
                gray[y * W + x + 1] = 0;
            }
        if (clutter)
            for (int y = 60; y <= (connected ? 91 : 82); y++) {
                gray[y * W + 171] = 0;
                gray[y * W + 172] = 0;
            }
        return gray;
    }

    private List<ScoreRestEvent> rests(byte[] gray, List<ScoreNoteEvent> notes) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.03f, .97f, .1f, .9f)),
                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                notes);
    }

    @Test
    public void isolatedEighthRestRemainsRecognized() {
        var r = rests(page(false, true, false), List.of());
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void detachedStemInSameColumnsCannotHideCompleteEighthRest() {
        var r = rests(page(true, true, false), List.of());
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void incompleteBulbDoesNotBecomeACompleteRest() {
        assertTrue(rests(page(true, false, false), List.of()).isEmpty());
    }

    @Test
    public void connectedContinuingShaftCannotBeCroppedIntoRest() {
        assertTrue(rests(page(true, true, true), List.of()).isEmpty());
    }

    @Test
    public void realHeadOwnerStillBlocksRest() {
        var head =
                new ScoreNoteEvent(
                        0, (177 / 600f - .03f) / .94f, 0, 0, 1, 91 / 300f, false, 0, 1, 2, 0);
        assertTrue(rests(page(true, true, false), List.of(head)).isEmpty());
    }

    @Test
    public void sourceRasterRemainsUnchanged() {
        byte[] gray = page(true, true, false), before = gray.clone();
        rests(gray, List.of());
        assertArrayEquals(before, gray);
    }
}
