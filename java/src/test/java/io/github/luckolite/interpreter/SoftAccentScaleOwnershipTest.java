// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete contours with faint outer edges at a fractional staff scale. */
public class SoftAccentScaleOwnershipTest {
    private static final int W = 1280, H = 1280;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void draw(int paper, int span, int half, boolean upper, boolean lower, byte label) {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
        int l = 478 - span / 2, r = l + span;
        for (int x = l; x <= r; x++) {
            int d = Math.round(half * (r - x) / (float) span);
            int tone = x >= r - 5 ? Math.round(paper * .82f) : 35;
            for (int thickness = 0; thickness < 4; thickness++) {
                if (upper) {
                    int p = (228 - d + thickness) * W + x;
                    gray[p] = (byte) tone;
                    labels[p] = label;
                }
                if (lower) {
                    int p = (228 + d + thickness) * W + x;
                    gray[p] = (byte) tone;
                    labels[p] = label;
                }
            }
        }
    }

    private int mark(float gap) {
        return NoteArticulationDetector.detect(
                labels, gray, W, H, List.of(new NoteArticulationDetector.Anchor(478, 260, gap, 0)))[
                0];
    }

    @Test
    public void completeContourAtFractionalGapRetainsAccent() {
        draw(230, 24, 6, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark(10.5f));
    }

    @Test
    public void widerCompleteContourRetainsAccent() {
        draw(230, 28, 7, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark(12.5f));
    }

    @Test
    public void tallerCompleteContourRetainsAccent() {
        draw(210, 28, 8, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark(12.5f));
    }

    @Test
    public void oversizeCompleteContourStaysUnowned() {
        draw(230, 35, 8, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(10) & NoteArticulation.ACCENT);
    }

    @Test
    public void excessiveHeightStaysUnowned() {
        draw(230, 24, 10, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(10) & NoteArticulation.ACCENT);
    }

    @Test
    public void singleWideArmDoesNotBecomeAccent() {
        draw(230, 28, 7, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(12) & NoteArticulation.ACCENT);
    }

    @Test
    public void notationOwnedWideContourStaysNotation() {
        draw(230, 28, 7, true, true, OmrMeasurePostProcessor.STEM_OR_REST);
        assertEquals(0, mark(12));
    }

    @Test
    public void trueDotBesideCompleteContourSurvives() {
        draw(230, 28, 7, true, true, OmrMeasurePostProcessor.SYMBOL);
        for (int y = 241; y <= 245; y++)
            for (int x = 476; x <= 480; x++) {
                gray[y * W + x] = 35;
                labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        assertEquals(NoteArticulation.ACCENT | NoteArticulation.STACCATO, mark(12));
    }
}
