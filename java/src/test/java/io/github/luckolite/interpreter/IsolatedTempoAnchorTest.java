// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster equations; OCR word splitting must not delay a tempo direction. */
public class IsolatedTempoAnchorTest {
    final int W = 480, H = 400;
    byte[] gray = new byte[W * H];

    public IsolatedTempoAnchorTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) gray[yy * W + xx] = 0;
    }

    List<ScoreTempoChange> read(int left, boolean note, boolean equals, boolean merged) {
        int top = 110;
        for (int y = 150; y <= 198; y += 12) box(30, y, 420, 1);
        if (equals) {
            box(left - 18, top + 6, 12, 2);
            box(left - 18, top + 12, 12, 2);
        }
        box(left, top, 3, 18);
        box(left + 12, top, 3, 18);
        if (note) {
            box(left - 30, top - 12, 2, 28);
            for (int y = top + 9; y <= top + 16; y++)
                for (int x = left - 39; x <= left - 29; x++)
                    if (Math.pow((x - (left - 34)) / 5d, 2) + Math.pow((y - (top + 13)) / 4d, 2)
                            <= 1) gray[y * W + x] = 0;
        }
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                merged ? "=96" : "96",
                                (left - (merged ? 18 : 0)) / (float) W,
                                top / (float) H,
                                (left + 16f) / W,
                                (top + 18f) / H));
        var tokens = TempoChangeDetector.withIsolatedDigits(List.of(), words, gray, W, H);
        return TempoChangeDetector.detectWithWords(
                tokens,
                words,
                gray,
                W,
                H,
                List.of(
                        new MeasureRegion(30f / W, 180f / W, 138f / H, 210f / H),
                        new MeasureRegion(180f / W, 450f / W, 138f / H, 210f / H)));
    }

    @Test
    public void separateNumeralUsesPrintedBeatNoteAtBarStart() {
        assertEquals(List.of(new ScoreTempoChange(1, 0, 96)), read(260, true, true, false));
    }

    @Test
    public void mergedAndSeparateWordsHaveSameTiming() {
        var separate = read(260, true, true, false);
        Arrays.fill(gray, (byte) 255);
        assertEquals(read(260, true, true, true), separate);
    }

    @Test
    public void genuineMidBarMarkDoesNotSnapToStart() {
        var changes = read(350, true, true, false);
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).positionInMeasure() > .4f);
        assertTrue(changes.get(0).positionInMeasure() < .6f);
    }

    @Test
    public void missingNoteRemainsRejected() {
        assertTrue(read(260, false, true, false).isEmpty());
    }

    @Test
    public void missingEqualsRemainsRejected() {
        assertTrue(read(260, true, false, false).isEmpty());
    }

    @Test
    public void pixelsRemainUnchangedByRepeatedRecognition() {
        read(260, true, true, false);
        var before = gray.clone();
        read(260, true, true, false);
        assertArrayEquals(before, gray);
    }
}
