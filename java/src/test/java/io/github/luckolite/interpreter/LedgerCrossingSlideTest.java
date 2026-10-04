// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic slide touching a note-owned ledger rule; no source imagery. */
public class LedgerCrossingSlideTest {
    static final int W = 300, H = 260;

    byte[] page(boolean below, boolean ledger, boolean curved, int thickness) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        int sourceY = below ? 198 : 90;
        for (int y = sourceY - 5; y <= sourceY + 5; y++)
            for (int x = 91; x <= 109; x++)
                if ((x - 100) * (x - 100) / 81d + (y - sourceY) * (y - sourceY) / 25d <= 1)
                    gray[y * W + x] = 0;
        for (int y = below ? 144 : 90; y <= (below ? 198 : 144); y++) gray[y * W + 93] = 0;
        if (ledger)
            for (int center : below ? new int[] {180, 192} : new int[] {96, 108})
                for (int y = center; y < center + thickness; y++)
                    for (int x = 86; x <= 116; x++) gray[y * W + x] = 0;
        for (int x = 112; x <= 132; x++) {
            int y =
                    (int)
                            Math.round(
                                    (below ? 194 : 94)
                                            + (x - 112) * (below ? -1.1 : 1.1)
                                            + (curved
                                                    ? 8 * Math.sin((x - 112) * Math.PI / 20)
                                                    : 0));
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        return gray;
    }

    List<NoteSlideDetector.Stroke> detect(
            boolean below, boolean ledger, boolean curved, int thickness, int staff) {
        return NoteSlideDetector.detect(
                page(below, ledger, curved, thickness),
                W,
                H,
                List.of(new NoteSlideDetector.Staff(120, 168, 12)),
                List.of(
                        new NoteSlideDetector.Head(100, below ? 198 : 90, 12, staff, 0),
                        new NoteSlideDetector.Head(145, below ? 168 : 120, 12, staff, 0)));
    }

    @Test
    public void slideAcrossUpperLedgerIsRecognized() {
        var found = detect(false, true, false, 2, 0);
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).noteIndex());
        assertTrue(found.get(0).connected());
    }

    @Test
    public void slideAcrossLowerLedgerIsRecognized() {
        var found = detect(true, true, false, 2, 0);
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).direction());
        assertTrue(found.get(0).connected());
    }

    @Test
    public void isolatedSlideWithoutLedgerKeepsRecognition() {
        assertEquals(1, detect(false, false, false, 2, 0).size());
    }

    @Test
    public void bowedInkStillFailsStraightStrokeProof() {
        assertTrue(detect(false, true, true, 2, 0).isEmpty());
    }

    @Test
    public void unresolvedStaffCannotAuthorizeLedgerRemoval() {
        assertTrue(detect(false, true, false, 2, 3).isEmpty());
    }

    @Test
    public void thickHorizontalShapeIsNotALedgerRule() {
        assertTrue(detect(false, true, false, 6, 0).isEmpty());
    }

    /** The wave flood may consume its copy without erasing the later slide evidence. */
    @Test
    public void preparedRasterSeparatesWaveAndSlideWorkingPixels() {
        var gray = page(false, true, false, 2);
        byte[] original = gray.clone();
        var staffs = List.of(new NoteSlideDetector.Staff(120, 168, 12));
        var heads =
                List.of(
                        new NoteSlideDetector.Head(100, 90, 12, 0, 0),
                        new NoteSlideDetector.Head(145, 120, 12, 0, 0));
        var expectedWave = WaveGlissDetector.detect(gray, W, H, staffs, heads);
        var expectedSlide = NoteSlideDetector.detect(gray, W, H, staffs, heads);
        assertEquals(1, expectedSlide.size());
        byte[] prepared = NoteSlideDetector.removeStaffLines(gray, W, H, staffs);
        byte[] preserved = prepared.clone();
        assertEquals(
                expectedWave,
                WaveGlissDetector.detectWithRemovedStaffLines(W, H, heads, prepared.clone()));
        assertArrayEquals(preserved, prepared);
        assertEquals(
                expectedSlide,
                NoteSlideDetector.detectWithRemovedStaffLines(gray, W, H, staffs, heads, prepared));
        assertArrayEquals(original, gray);
    }

    /** The OCR stage's aligned geometry can be borrowed without altering its owner. */
    @Test
    public void preparedStaffsPreserveLedgerOrnamentAndCaller() {
        byte[] gray = page(false, true, false, 2), labels = new byte[W * H];
        for (int y : new int[] {120, 132, 144, 156, 168})
            for (int x = 20; x < W - 20; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        byte[] grayBefore = gray.clone(), labelsBefore = labels.clone();
        var measures = List.of(new MeasureRegion(0, 1, 70f / H, 205f / H));
        var notes =
                List.of(
                        new ScoreNoteEvent(
                                0, 100f / W, 13, 0, 1, 90f / H, false, 0, 0, 2, 1, 1, 0, 0),
                        new ScoreNoteEvent(
                                0, 145f / W, 8, 0, 1, 120f / H, false, 0, 0, 2, 1, 1, 0, 0));
        var staffs =
                ScoreDynamicsDetector.alignStaffs(
                        OmrScoreInterpreter.techniqueStaffs(labels, gray, W, H, measures),
                        notes,
                        H);
        assertFalse(staffs.isEmpty());
        var before = List.copyOf(staffs);
        var glyphs = new PortableOrnamentGlyphs();
        var expected =
                PortableNoteOrnaments.apply(glyphs, labels, gray, W, H, measures, notes, List.of());
        var actual =
                PortableNoteOrnaments.applyWithAlignedStaffs(
                        glyphs, gray, W, H, measures, notes, List.of(), staffs);
        assertEquals(expected, actual);
        assertEquals(2, actual.size());
        assertEquals(NoteOrnament.SLIDE, NoteOrnament.type(actual.get(1).articulations()));
        assertEquals(before, staffs);
        for (int i = 0; i < staffs.size(); i++) assertSame(before.get(i), staffs.get(i));
        assertArrayEquals(grayBefore, gray);
        assertArrayEquals(labelsBefore, labels);
    }
}
