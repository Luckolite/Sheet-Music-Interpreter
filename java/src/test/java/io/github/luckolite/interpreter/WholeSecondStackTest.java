// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original synthetic geometry; no score scans or model predictions. */
public class WholeSecondStackTest {
    private static List<ScoreNoteEvent> chord(boolean open, boolean dots) {
        return chord(open, dots, false, false, false);
    }

    private static List<ScoreNoteEvent> chord(
            boolean open, boolean dots, boolean mirror, boolean rest, boolean blank) {
        return chord(open, dots, mirror, rest, blank, 0, 1);
    }

    private static List<ScoreNoteEvent> chord(
            boolean open,
            boolean dots,
            boolean mirror,
            boolean rest,
            boolean blank,
            int filledHead,
            int scale) {
        int w = 600, h = 300;
        byte[] labels = new byte[w * h], gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {60, 80, 100, 120, 140})
            for (int x = 20; x < 580; x++) {
                labels[y * w + x] = OmrMeasurePostProcessor.STAFF;
                gray[y * w + x] = 0;
            }
        int ordinal = 0;
        for (int[] center : new int[][] {{255, 60}, {255, 80}, {255, 100}, {220, 90}}) {
            ordinal++;
            int cx = center[0], cy = center[1];
            for (int y = cy - 10; y <= cy + 10; y++)
                for (int x = cx - 18; x <= cx + 18; x++) {
                    double oval = Math.pow((x - cx) / 18.0, 2) + Math.pow((y - cy) / 10.0, 2);
                    if (oval <= 1) {
                        labels[y * w + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        if (!open || ordinal == filledHead || oval >= .40) gray[y * w + x] = 0;
                    }
                }
        }
        // A thin model bridge joins the adjacent seconds, not their raw white centres.
        for (int y = 87; y <= 93; y++)
            for (int x = 236; x <= 240; x++) labels[y * w + x] = OmrMeasurePostProcessor.NOTEHEAD;
        if (dots)
            for (int cy : new int[] {55, 75, 85, 105})
                for (int y = cy - 3; y <= cy + 3; y++)
                    for (int x = 285 - 3; x <= 285 + 3; x++)
                        if ((x - 285) * (x - 285) + (y - cy) * (y - cy) <= 9) gray[y * w + x] = 0;
        if (rest)
            for (int y = 100; y <= 106; y++)
                for (int x = 400; x <= 420; x++) {
                    gray[y * w + x] = 0;
                    labels[y * w + x] = OmrMeasurePostProcessor.SYMBOL;
                }
        if (blank) Arrays.fill(gray, (byte) 255);
        if (mirror)
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w / 2; x++) {
                    int a = y * w + x, b = y * w + w - 1 - x;
                    byte t = gray[a];
                    gray[a] = gray[b];
                    gray[b] = t;
                    t = labels[a];
                    labels[a] = labels[b];
                    labels[b] = t;
                }
        if (scale > 1) {
            byte[] gg = new byte[w * h * scale * scale], ll = new byte[gg.length];
            for (int y = 0; y < h * scale; y++)
                for (int x = 0; x < w * scale; x++) {
                    gg[y * w * scale + x] = gray[(y / scale) * w + x / scale];
                    ll[y * w * scale + x] = labels[(y / scale) * w + x / scale];
                }
            gray = gg;
            labels = ll;
            w *= scale;
            h *= scale;
        }
        byte[] beforeGray = gray.clone(), beforeLabels = labels.clone();
        var notes =
                OmrScoreInterpreter.extract(
                        labels, gray, w, h, List.of(new MeasureRegion(.05f, .95f, .1f, .65f)));
        assertArrayEquals(beforeGray, gray);
        assertArrayEquals(beforeLabels, labels);
        return notes;
    }

    @Test
    public void displacedWholeSecondsRetainBothTonesAndDotColumn() {
        var notes = chord(true, true);
        assertEquals(4, notes.size());
        assertEquals(
                Set.of(4, 5, 6, 8),
                new HashSet<>(notes.stream().map(ScoreNoteEvent::staffStep).toList()));
        for (var n : notes) {
            assertEquals(4f, n.unbeamedDurationBeats(), .01f);
            assertEquals(1, n.augmentationDots());
            assertEquals(notes.get(0).positionInMeasure(), n.positionInMeasure(), .001f);
        }
    }

    @Test
    public void openSecondsDoNotInventUnprintedDots() {
        var notes = chord(true, false);
        assertEquals(4, notes.size());
        for (var n : notes) assertEquals(0, n.augmentationDots());
    }

    @Test
    public void wideSolidBlobIsNotSplitAsWholeSeconds() {
        assertTrue(chord(false, false).size() < 4);
    }

    @Test
    public void mirroredChordRetainsFourWholePitches() {
        var notes = chord(true, false, true, false, false);
        assertEquals(4, notes.size());
        assertEquals(
                Set.of(4, 5, 6, 8),
                new HashSet<>(notes.stream().map(ScoreNoteEvent::staffStep).toList()));
        for (var n : notes) assertEquals(4f, n.unbeamedDurationBeats(), .01f);
    }

    @Test
    public void unrelatedRestDoesNotEraseDisplacedWholeDot() {
        var notes = chord(true, true, false, true, false);
        assertEquals(4, notes.size());
        for (var n : notes) assertEquals(1, n.augmentationDots());
    }

    @Test
    public void whiteSemanticOvalsCannotProveWholeChord() {
        assertTrue(chord(true, false, false, false, true).size() < 4);
    }

    @Test
    public void everyHeadNeedsIndependentOpenCenter() {
        for (int n = 1; n <= 4; n++)
            assertTrue("solid head " + n, chord(true, false, false, false, false, n, 1).size() < 4);
    }

    @Test
    public void enlargedChordRetainsDotsAndPitches() {
        var notes = chord(true, true, false, true, false, 0, 2);
        assertEquals(4, notes.size());
        assertEquals(
                Set.of(4, 5, 6, 8),
                new HashSet<>(notes.stream().map(ScoreNoteEvent::staffStep).toList()));
        for (var n : notes) {
            assertEquals(4f, n.unbeamedDurationBeats(), .01f);
            assertEquals(1, n.augmentationDots());
        }
    }
}
