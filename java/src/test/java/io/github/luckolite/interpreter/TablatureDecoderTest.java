// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TablatureDecoderTest {
    static final int W = 500, H = 400;

    byte[] page(int lines, boolean paired, boolean second) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (paired)
            for (int y = 40; y <= 80; y += 10) for (int x = 20; x < 480; x++) g[y * W + x] = 0;
        for (int y = 140; y <= 140 + (lines - 1) * 20; y += 20)
            for (int x = 20; x < 480; x++) g[y * W + x] = 0;
        if (second)
            for (int y = 280; y <= 380; y += 20) for (int x = 20; x < 480; x++) g[y * W + x] = 0;
        for (int x : new int[] {20, 250, 479}) for (int y = 140; y <= 240; y++) g[y * W + x] = 0;
        return g;
    }

    TablatureDecoder.Word word(String text, int x, int string) {
        return new TablatureDecoder.Word(
                text,
                (x - 8f) / W,
                (140 + string * 20 - 7f) / H,
                (x + 8f) / W,
                (140 + string * 20 + 7f) / H);
    }

    @Test
    public void sixRulesDetectedWithoutChangingFiveRuleStaves() {
        assertEquals(1, TablatureDecoder.detect(page(6, true, false), W, H).size());
        assertTrue(TablatureDecoder.detect(page(5, false, false), W, H).isEmpty());
    }

    @Test
    public void sevenEquallySpacedRulesAreNotTruncatedToSix() {
        var rows = TablatureDecoder.detect(page(7, false, false), W, H);
        assertEquals(1, rows.size());
        assertEquals(7, rows.get(0).stringCount());
    }

    @Test
    public void secondTabRowIsNotMistakenForPairedStandardStaff() {
        var t = TablatureDecoder.detect(page(6, false, true), W, H);
        assertEquals(2, t.size());
        assertEquals(-1, t.get(1).standardTop(), 0);
    }

    @Test
    public void readsOpenStringsChordsAndTwoDigitFrets() {
        var t =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, true, false), W, H),
                        List.of(word("0", 100, 5), word("12", 100, 0), word("4", 100, 3)),
                        W,
                        H);
        assertEquals(3, t.get(0).frets().size());
        assertEquals(12, t.get(0).frets().get(1).fret());
    }

    @Test
    public void unknownGlyphIsNotGuessedAsZero() {
        var t =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, false, false), W, H),
                        List.of(word("O", 100, 0), word("1412", 150, 0), word("37", 200, 0)),
                        W,
                        H);
        assertTrue(t.get(0).frets().isEmpty());
    }

    @Test
    public void slideEndpointsAndDeadStringsAreDistinct() {
        var t =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, false, false), W, H),
                        List.of(word("14/16", 100, 0), word("X", 300, 2)),
                        W,
                        H);
        assertEquals(
                List.of(14, 16, -1),
                t.get(0).frets().stream().map(TablatureDecoder.Fret::fret).toList());
    }

    @Test
    public void repeatedOcrDoesNotDoubleChordNotes() {
        var ws = List.of(word("2", 100, 0), word("2", 100, 0));
        var t =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, false, false), W, H), ws, W, H);
        assertEquals(1, t.get(0).frets().size());
    }

    @Test
    public void numericFretsIgnoreKeySignatureAndSupportCapoAndAlternateTuning() {
        int[] tune = {64, 59, 55, 50, 45, 38};
        assertEquals(38, TablatureDecoder.midi(5, 0, tune, 0));
        assertEquals(78, TablatureDecoder.midi(0, 12, tune, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTuningRejected() {
        TablatureDecoder.midi(0, 0, new int[] {64}, 0);
    }

    @Test
    public void tabBarlinesRepairFalseStandardSplits() {
        var t = TablatureDecoder.detect(page(6, true, false), W, H);
        var m =
                List.of(
                        new MeasureRegion(.1f, .4f, .1f, .3f),
                        new MeasureRegion(.4f, .7f, .1f, .3f),
                        new MeasureRegion(.7f, .95f, .1f, .3f));
        var out = TablatureDecoder.reconcileMeasures(m, t, W, H);
        assertEquals(2, out.size());
        assertEquals(.5f, out.get(0).right(), .002);
    }

    @Test
    public void standaloneUsesBarsAndKeepsUnknownRhythm() {
        var t =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, false, false), W, H),
                        List.of(word("0", 100, 5), word("12", 300, 0)),
                        W,
                        H);
        var score =
                TablatureDecoder.apply(new ScorePageInterpretation(List.of(), List.of()), t, W, H);
        assertEquals(2, score.measures().size());
        assertEquals(2, score.notes().size());
        assertEquals(0, score.notes().get(0).unbeamedDurationBeats(), 0);
        assertEquals(-14, score.notes().get(0).staffStep());
    }

    @Test
    public void inputPixelsRemainUnmodified() {
        byte[] g = page(6, true, false), saved = g.clone();
        var t = TablatureDecoder.detect(g, W, H);
        byte[] clean = TablatureDecoder.withoutTabs(g, W, H, t, true);
        assertArrayEquals(saved, g);
        assertEquals(255, clean[140 * W + 100] & 255);
        assertEquals(0, clean[40 * W + 100] & 255);
    }

    private ScorePageInterpretation paired(boolean sounding) {
        var words = List.of(word("0", 100, 0), word("0", 200, 1), word("0", 350, 2));
        var tabs =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, true, false), W, H), words, W, H);
        var notes = new ArrayList<ScoreNoteEvent>();
        int[] steps = {7, 4, 2}, xs = {100, 200, 350};
        for (int i = 0; i < 3; i++)
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    (xs[i] / 500f - .04f) / .91f,
                                    steps[i] - (sounding ? 7 : 0),
                                    0,
                                    1,
                                    .15f,
                                    false,
                                    0,
                                    1,
                                    0,
                                    0)
                            .withClef(ScoreNoteEvent.CLEF_TREBLE));
        return TablatureDecoder.apply(
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.04f, .95f, .1f, .3f)), notes),
                tabs,
                W,
                H);
    }

    @Test
    public void pairedNotationKeepsOnePerformanceAndItsRhythm() {
        var result = paired(false);
        assertEquals(3, result.notes().size());
        for (var n : result.notes()) {
            assertEquals(-1, n.octaveShift());
            assertEquals(1, n.beamCount());
        }
    }

    @Test
    public void soundingNotationIsNotTransposedTwice() {
        for (var n : paired(true).notes()) assertEquals(0, n.octaveShift());
    }

    @Test
    public void fullyMutedStrokeKeepsItsTimeWithoutInventingPitch() {
        var tabs =
                TablatureDecoder.withWords(
                        TablatureDecoder.detect(page(6, true, false), W, H),
                        List.of(word("X", 300, 0), word("X", 300, 1), word("X", 300, 2)),
                        W,
                        H);
        var first =
                new ScoreNoteEvent(0, .2f, 0, 0, 1, .15f, false, 0, 1, 0, 0)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        var muted =
                new ScoreNoteEvent(0, .6f, 4, 0, 1, .15f, false, 0, 1, 0, 0)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(
                                List.of(new MeasureRegion(0, 1, .1f, .3f)), List.of(first, muted)),
                        tabs,
                        W,
                        H);
        assertEquals(2, score.notes().size());
        assertEquals(first, score.notes().get(0));
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, score.notes().get(1).kind());
        assertEquals(.5, ScoreNoteTiming.writtenDurationBeats(score.notes().get(1)), 0);
        assertTrue(score.rests().isEmpty());
        assertEquals(
                1,
                score.notes().stream().mapToDouble(ScoreNoteTiming::writtenDurationBeats).sum(),
                0);
    }

    private static void assertTabProjectionGeometry(
            TablatureDecoder.Staff row, float top, float standardTop, List<Float> bars) {
        assertEquals(top, row.top(), 0);
        assertEquals(20f, row.gap(), 0);
        assertEquals(top + 100f, row.bottom(), 0);
        assertEquals(standardTop, row.standardTop(), 0);
        assertEquals(6, row.stringCount());
        assertEquals(bars, row.bars());
        assertTrue(row.frets().isEmpty());
        assertTrue(row.tuning().isEmpty());
    }

    @Test
    public void lowCoverageTabRowsRetainGeometryAndEnclosingBars() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        // Fifty pixels exceed the six-percent pass but cannot reach the 55-percent pass.
        for (int y = 140; y <= 240; y += 20) for (int x = 20; x < 70; x++) gray[y * W + x] = 0;
        for (int x : new int[] {20, 45, 69}) for (int y = 140; y <= 240; y++) gray[y * W + x] = 0;
        byte[] original = gray.clone();
        var rows = TablatureDecoder.detect(gray, W, H);
        assertEquals(1, rows.size());
        assertTabProjectionGeometry(rows.get(0), 140f, -1f, List.of(20f, 45f, 69f));
        assertArrayEquals(original, gray);
    }

    @Test
    public void paleTabRulesRespectStrictHighestThreshold() {
        for (int shade : new int[] {246, 247, 248}) {
            byte[] gray = page(6, false, false);
            for (int y = 140; y <= 240; y += 20)
                Arrays.fill(gray, y * W + 20, y * W + 480, (byte) shade);
            byte[] original = gray.clone();
            var rows = TablatureDecoder.detect(gray, W, H);
            if (shade < 248) {
                assertEquals(1, rows.size());
                assertTabProjectionGeometry(rows.get(0), 140f, -1f, List.of(20f, 250f, 479f));
            } else assertTrue(rows.isEmpty());
            assertArrayEquals(original, gray);
        }
    }

    @Test
    public void pairedAndMultipleTabSystemsKeepOrderedGeometryAndBars() {
        byte[] gray = page(6, true, true);
        for (int x : new int[] {30, 260, 470}) for (int y = 280; y <= 380; y++) gray[y * W + x] = 0;
        byte[] original = gray.clone();
        var rows = TablatureDecoder.detect(gray, W, H);
        assertEquals(2, rows.size());
        assertTabProjectionGeometry(rows.get(0), 140f, 40f, List.of(20f, 250f, 479f));
        assertTabProjectionGeometry(rows.get(1), 280f, -1f, List.of(30f, 260f, 470f));
        assertArrayEquals(original, gray);
    }

    private static byte[] bottomEdgeTabFixture(int strings, int height, boolean clippedSeventh) {
        int width = 128;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int string = 0; string < strings; string++) {
            int y = clippedSeventh && string == 6 ? 69 : 10 + string * 10;
            Arrays.fill(gray, y * width, (y + 1) * width, (byte) 0);
        }
        int actualBottom = clippedSeventh ? 69 : 10 + (strings - 1) * 10;
        for (int x : new int[] {20, 90})
            for (int y = 10; y <= actualBottom; y++) gray[y * width + x] = 0;
        return gray;
    }

    private static void assertBottomEdgeTabGeometry(TablatureDecoder.Staff row, int strings) {
        assertEquals(Float.floatToRawIntBits(10f), Float.floatToRawIntBits(row.top()));
        assertEquals(Float.floatToRawIntBits(10f), Float.floatToRawIntBits(row.gap()));
        assertEquals(
                Float.floatToRawIntBits(strings == 6 ? 60f : 70f),
                Float.floatToRawIntBits(row.bottom()));
        assertEquals(Float.floatToRawIntBits(-1f), Float.floatToRawIntBits(row.standardTop()));
        assertEquals(strings, row.stringCount());
        assertEquals(List.of(20f, 90f), row.bars());
        assertTrue(row.frets().isEmpty());
        assertTrue(row.tuning().isEmpty());
    }

    @Test
    public void clippedSeventhStringKeepsGeometryAndBoundsBarReads() {
        byte[] gray = bottomEdgeTabFixture(7, 70, true);
        byte[] original = gray.clone();
        var rows = TablatureDecoder.detect(gray, 128, 70);
        assertEquals(1, rows.size());
        // The tolerant line fit still ends at 70, beyond the last available raster row 69.
        assertBottomEdgeTabGeometry(rows.get(0), 7);
        assertArrayEquals(original, gray);
        byte[] masked = TablatureDecoder.withoutTabs(gray, 128, 70, rows, true);
        assertNotSame(gray, masked);
        byte[] expectedMask = original.clone();
        Arrays.fill(expectedMask, 4 * 128, expectedMask.length, (byte) 255);
        assertArrayEquals(expectedMask, masked);
        assertArrayEquals(original, gray);
    }

    @Test
    public void ordinarySixAndSevenStringBarsKeepTheirCompleteGeometry() {
        for (int strings : new int[] {6, 7}) {
            byte[] gray = bottomEdgeTabFixture(strings, 82, false);
            byte[] original = gray.clone();
            var rows = TablatureDecoder.detect(gray, 128, 82);
            assertEquals(1, rows.size());
            assertBottomEdgeTabGeometry(rows.get(0), strings);
            assertArrayEquals(original, gray);
        }
    }
}
