// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale connective ink, independently complete shape and component ownership. */
public class SoftAccentOwnershipTest {
    private static final int W = 1280, H = 1280;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void paper(int tone) {
        Arrays.fill(gray, (byte) tone);
        Arrays.fill(labels, (byte) 0);
    }

    private void px(int x, int y, int tone, byte label) {
        gray[y * W + x] = (byte) tone;
        labels[y * W + x] = label;
    }

    private void chevron(int tone, float slope, boolean upper, boolean lower, byte label) {
        for (int x = 466; x <= 490; x++) {
            float center = 228 + (x - 478) * slope;
            int half = Math.round(6f * (490 - x) / 24f);
            int ink = x >= 485 ? Math.round(tone * .82f) : 35;
            for (int d = 0; d < 2; d++) {
                if (upper) px(x, Math.round(center) - half + d, ink, label);
                if (lower) px(x, Math.round(center) + half + d, ink, label);
            }
        }
    }

    private int mark() {
        return NoteArticulationDetector.detect(
                labels, gray, W, H, List.of(new NoteArticulationDetector.Anchor(478, 260, 14, 0)))[
                0];
    }

    @Test
    public void darkPaperPaleTipRetainsBothArms() {
        paper(180);
        chevron(180, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void mediumPaperPaleTipRetainsBothArms() {
        paper(210);
        chevron(210, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void lightGrayPaperPaleTipRetainsBothArms() {
        paper(230);
        chevron(230, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void risingPaleTipRetainsCompleteAccent() {
        paper(210);
        chevron(210, .04f, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void fallingPaleTipRetainsCompleteAccent() {
        paper(210);
        chevron(210, -.04f, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void darkBrightPaperAccentStillSurvives() {
        paper(255);
        chevron(35, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.ACCENT, mark());
    }

    @Test
    public void incompleteUpperArmIsNotAccent() {
        paper(210);
        chevron(210, 0, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark() & NoteArticulation.ACCENT);
    }

    @Test
    public void incompleteLowerArmIsNotAccent() {
        paper(210);
        chevron(210, 0, false, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark() & NoteArticulation.ACCENT);
    }

    @Test
    public void parallelDashesAreNotAccent() {
        paper(210);
        for (int x = 466; x <= 490; x++)
            for (int d = 0; d < 2; d++) {
                px(x, 222 + d, 35, OmrMeasurePostProcessor.SYMBOL);
                px(x, 234 + d, 35, OmrMeasurePostProcessor.SYMBOL);
            }
        assertEquals(0, mark() & NoteArticulation.ACCENT);
    }

    @Test
    public void notationOwnedChevronDoesNotBecomeArticulation() {
        paper(210);
        chevron(210, 0, true, true, OmrMeasurePostProcessor.NOTEHEAD);
        assertEquals(0, mark());
    }

    @Test
    public void completeAccentOutsideNoteAxisStaysUnowned() {
        paper(210);
        chevron(210, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(
                0,
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(510, 260, 14, 0)))[0]);
    }

    @Test
    public void grayPaperHasNoMark() {
        paper(210);
        assertEquals(0, mark());
    }

    @Test
    public void lowContrastPaperChevronDoesNotAcquireDarkSeed() {
        paper(210);
        chevron(210, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        for (int i = 0; i < gray.length; i++) if ((gray[i] & 255) < 200) gray[i] = (byte) 190;
        assertEquals(0, mark());
    }

    @Test
    public void detectorPreservesRawPlanes() {
        paper(210);
        chevron(210, 0, true, true, OmrMeasurePostProcessor.SYMBOL);
        byte[] a = gray.clone(), b = labels.clone();
        mark();
        assertArrayEquals(a, gray);
        assertArrayEquals(b, labels);
    }
}
