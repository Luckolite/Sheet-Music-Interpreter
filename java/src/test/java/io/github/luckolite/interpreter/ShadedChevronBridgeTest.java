// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-tone chevrons with a shaded-paper bridge and independent rejection controls. */
public class ShadedChevronBridgeTest {
    int detect(
            int paper,
            boolean reverse,
            boolean parallel,
            boolean notation,
            boolean paleOnly,
            boolean far) {
        int w = 1200, h = 1000;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) paper);
        for (int x = 0; x < 25; x++)
            for (int y = 0; y < 13; y++) {
                int axis = reverse ? 24 - x : x;
                double upper = 1 + (parallel ? 0 : 5 * axis / 24.),
                        lower = 11 - (parallel ? 0 : 5 * axis / 24.);
                if (Math.abs(y - upper) > 1.25 && Math.abs(y - lower) > 1.25) continue;
                int at = (100 + y) * w + 100 + x;
                gray[at] = (byte) Math.round(paper * (!paleOnly && axis < 10 ? .45f : .68f));
                if (notation) labels[at] = OmrMeasurePostProcessor.NOTEHEAD;
            }
        var gc = gray.clone();
        var lc = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        w,
                        h,
                        List.of(new NoteArticulationDetector.Anchor(112, far ? 220 : 150, 16, 0)))[
                        0];
        assertArrayEquals(gc, gray);
        assertArrayEquals(lc, labels);
        return result;
    }

    @Test
    public void shadedBridgeJoinsTwoCompleteAccentArms() {
        assertEquals(NoteArticulation.ACCENT, detect(140, false, false, false, false, false));
    }

    @Test
    public void secondShadedPaperLevelRetainsItsActualBridge() {
        assertEquals(NoteArticulation.ACCENT, detect(150, false, false, false, false, false));
    }

    @Test
    public void reversedChevronCannotBecomeAccent() {
        assertEquals(0, detect(140, true, false, false, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void parallelArmsCannotBecomeAccent() {
        assertEquals(0, detect(140, false, true, false, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void semanticNoteheadCannotBecomeAccent() {
        assertEquals(0, detect(140, false, false, true, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void paleShapeStillNeedsDarkSeeds() {
        assertEquals(0, detect(140, false, false, false, true, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void distantShapeHasNoNoteOwner() {
        assertEquals(0, detect(140, false, false, false, false, true) & NoteArticulation.ACCENT);
    }

    @Test
    public void ambiguousDarkPaperDoesNotRecoverChevron() {
        assertEquals(0, detect(80, false, false, false, false, false) & NoteArticulation.ACCENT);
    }
}
