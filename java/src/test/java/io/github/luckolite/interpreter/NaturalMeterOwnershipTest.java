// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NaturalMeterOwnershipTest {
    private final int width = 240, height = 220;

    private byte[] paper() {
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        return gray;
    }

    private void line(byte[] gray, int x0, int y0, int x1, int y1, int thickness) {
        int length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int step = 0; step <= length; step++) {
            int x = Math.round(x0 + (x1 - x0) * step / (float) Math.max(1, length));
            int y = Math.round(y0 + (y1 - y0) * step / (float) Math.max(1, length));
            for (int dy = 0; dy < thickness; dy++)
                for (int dx = 0; dx < thickness; dx++)
                    if (x + dx >= 0 && x + dx < width && y + dy >= 0 && y + dy < height)
                        gray[(y + dy) * width + x + dx] = 0;
        }
    }

    private void natural(byte[] gray, int x, int y, int gap) {
        int right = x + Math.round(gap * .65f), thickness = Math.max(1, Math.round(gap * .12f));
        line(gray, x, y - Math.round(gap * 1.45f), x, y + Math.round(gap * .55f), thickness);
        line(
                gray,
                right,
                y - Math.round(gap * .55f),
                right,
                y + Math.round(gap * 1.45f),
                thickness);
        line(gray, x, y - Math.round(gap * .35f), right, y - Math.round(gap * .55f), thickness);
        line(gray, x, y + Math.round(gap * .55f), right, y + Math.round(gap * .35f), thickness);
    }

    private ScoreNoteEvent note(float x, float y, int accidental) {
        return new ScoreNoteEvent(0, x / width, 4, 0, 1, y / height, false, 0, 0, accidental, 1);
    }

    private boolean owned(byte[] gray, MeterChangeDetector.Crop crop, List<ScoreNoteEvent> notes) {
        var measures = List.of(new MeasureRegion(0, 1, 0, 1));
        assertTrue(
                "Legacy before-note filter accepts this position",
                MeterChangeDetector.precedesNotes(crop, width, height, 0, measures, notes));
        boolean belongs =
                MeterChangeDetector.belongsToNoteAccidental(
                        crop, gray, width, height, 0, measures, notes);
        assertEquals(
                "Shared caller filter respects accidental ownership",
                !belongs,
                MeterChangeDetector.precedesNotes(crop, gray, width, height, 0, measures, notes));
        return belongs;
    }

    @Test
    public void stackedNaturalsAreOwnedAtSeveralStaffScales() {
        for (int gap : new int[] {10, 17, 24}) {
            byte[] gray = paper();
            int x = 60, y = 50, second = y + Math.round(gap * 3.5f);
            natural(gray, x, y, gap);
            natural(gray, x, second, gap);
            int right = x + Math.round(gap * .65f) + Math.max(1, Math.round(gap * .12f));
            var crop = new MeterChangeDetector.Crop(x - 2, y - 2, right + 2, second + 2, 110, gap);
            assertTrue(
                    "gap=" + gap,
                    owned(
                            gray,
                            crop,
                            List.of(note(right + gap, y, 0), note(right + gap, second, 0))));
        }
    }

    @Test
    public void printedSevenFourBesideExplicitNaturalsRemainsMeterInk() {
        byte[] gray = paper();
        int x = 60, y = 50, gap = 17;
        line(gray, x, y, x + 12, y, 2);
        line(gray, x + 12, y, x + 2, y + 24, 2);
        line(gray, x + 9, y + 32, x, y + 51, 2);
        line(gray, x, y + 51, x + 14, y + 51, 2);
        line(gray, x + 10, y + 32, x + 10, y + 60, 2);
        var crop = new MeterChangeDetector.Crop(x - 2, y - 2, x + 17, y + 62, 48, gap);
        assertFalse(owned(gray, crop, List.of(note(x + 33, y + 12, 0), note(x + 33, y + 45, 0))));
    }

    @Test
    public void keyDerivedPitchCannotClaimNaturalInk() {
        byte[] gray = paper();
        natural(gray, 60, 50, 17);
        var crop = new MeterChangeDetector.Crop(58, 45, 76, 85, 110, 17);
        assertFalse(owned(gray, crop, List.of(note(90, 50, ScoreNoteEvent.ACCIDENTAL_FROM_KEY))));
    }

    @Test
    public void distantAccidentalDoesNotOwnSignature() {
        byte[] gray = paper();
        natural(gray, 60, 50, 17);
        var crop = new MeterChangeDetector.Crop(58, 45, 76, 85, 110, 17);
        assertFalse(owned(gray, crop, List.of(note(130, 50, 0))));
    }

    @Test
    public void decodedChordHeadCannotBecomeNumeratorOrDenominator() {
        byte[] gray = paper();
        var crop = new MeterChangeDetector.Crop(65, 40, 80, 100, 40, 17);
        var notes = List.of(note(74, 55, ScoreNoteEvent.ACCIDENTAL_FROM_KEY));
        var measures = List.of(new MeasureRegion(0, 1, 0, 1));
        assertTrue(MeterChangeDetector.precedesNotes(crop, width, height, 0, measures, notes));
        assertFalse(
                MeterChangeDetector.precedesNotes(crop, gray, width, height, 0, measures, notes));
    }

    @Test
    public void normalizedPrintedMeterRemainsAcceptedBySharedCaller() {
        var page = new StackedOpeningMeterFragmentTest();
        page.digit(82);
        page.digit(117);
        for (int y = 101; y <= 109; y++) for (int x = 112; x <= 118; x++) page.ink(x, y, 2);
        page.firstNote();
        byte[] before = page.labels.clone(), ink = page.gray.clone();
        var notes =
                OmrScoreInterpreter.extract(
                        page.normalized(),
                        page.gray,
                        StackedOpeningMeterFragmentTest.W,
                        StackedOpeningMeterFragmentTest.H,
                        StackedOpeningMeterFragmentTest.MEASURES);
        assertEquals("Only the independent played note remains", 1, notes.size());
        var crop = new MeterChangeDetector.Crop(83, 80, 121, 148, 80, 16);
        assertTrue(
                MeterChangeDetector.precedesNotes(
                        crop,
                        page.gray,
                        StackedOpeningMeterFragmentTest.W,
                        StackedOpeningMeterFragmentTest.H,
                        0,
                        StackedOpeningMeterFragmentTest.MEASURES,
                        notes));
        assertArrayEquals(before, page.labels);
        assertArrayEquals(ink, page.gray);
    }
}
