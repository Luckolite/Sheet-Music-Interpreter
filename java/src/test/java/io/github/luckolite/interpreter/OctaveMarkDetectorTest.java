// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OctaveMarkDetectorTest {
    static final int W = 700, H = 600;
    static final List<MeasureRegion> M = List.of(new MeasureRegion(0, 1, 0, 1));
    static final PlayingTechniqueDetector.Staff TOP =
            new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 2);
    static final PlayingTechniqueDetector.Staff BOTTOM =
            new PlayingTechniqueDetector.Staff(280, 344, 16, 1, 2);

    static byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    static void dash(byte[] g, int a, int b, int y) {
        for (int x = a; x <= b; x++)
            if ((x - a) % 14 < 7) {
                g[y * W + x] = 0;
                g[(y + 1) * W + x] = 0;
            }
    }

    static PlayingTechniqueDetector.Word word(String text, int x, int y) {
        return new PlayingTechniqueDetector.Word(
                text, x / (float) W, y / (float) H, (x + 40) / (float) W, (y + 20) / (float) H);
    }

    static ScoreNoteEvent note(int x, int staff) {
        return new ScoreNoteEvent(
                        0,
                        x / (float) W,
                        2,
                        staff,
                        2,
                        (staff == 0 ? 150 : 330) / (float) H,
                        false,
                        0,
                        0,
                        1,
                        1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    static List<ScoreNoteEvent> apply(
            byte[] g, List<PlayingTechniqueDetector.Word> words, List<ScoreNoteEvent> notes) {
        return OctaveMarkDetector.apply(words, List.of(TOP, BOTTOM), M, notes, g, W, H);
    }

    @Test
    public void dashedSpanStopsAtItsPrintedEnd() {
        var g = page();
        dash(g, 120, 370, 55);
        var n =
                apply(
                        g,
                        List.of(word("8va", 80, 40)),
                        List.of(note(150, 0), note(300, 0), note(500, 0)));
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(1, n.get(1).octaveShift());
        assertEquals(0, n.get(2).octaveShift());
    }

    @Test
    public void lowerPartIsUnaffected() {
        var g = page();
        dash(g, 120, 600, 55);
        var n = apply(g, List.of(word("8va", 80, 40)), List.of(note(150, 0), note(150, 1)));
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void twoOctaveMarkUsesTwoOctaves() {
        var g = page();
        dash(g, 120, 600, 55);
        assertEquals(
                2,
                apply(g, List.of(word("15ma", 80, 40)), List.of(note(150, 0)))
                        .get(0)
                        .octaveShift());
    }

    @Test
    public void lowerOctaveDirectionBelongsBelowItsStaff() {
        var g = page();
        dash(g, 120, 600, 390);
        assertEquals(
                -1,
                apply(g, List.of(word("8vb", 80, 370)), List.of(note(150, 1)))
                        .get(0)
                        .octaveShift());
    }

    @Test
    public void ordinaryNumbersCannotTranspose() {
        var g = page();
        dash(g, 120, 600, 55);
        assertEquals(
                0,
                apply(g, List.of(word("15", 80, 40)), List.of(note(150, 0))).get(0).octaveShift());
    }

    @Test
    public void singleNoteDirectionDoesNotLeak() {
        var g = page();
        var n = apply(g, List.of(word("(8va)", 130, 40)), List.of(note(150, 0), note(300, 0)));
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void ocrSuppliedParenthesizedSpanRaisesEveryCoveredNote() {
        var g = page();
        dash(g, 120, 370, 55);
        var n =
                apply(
                        g,
                        List.of(word("(8va)--------", 80, 40)),
                        List.of(note(150, 0), note(300, 0), note(500, 0)));
        assertEquals(List.of(1, 1, 0), n.stream().map(ScoreNoteEvent::octaveShift).toList());
    }

    @Test
    public void laterDirectionReplacesEarlierRatherThanAdding() {
        var g = page();
        dash(g, 120, 600, 55);
        var n =
                apply(
                        g,
                        List.of(word("8va", 80, 40), word("15ma", 260, 40)),
                        List.of(note(150, 0), note(300, 0)));
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(2, n.get(1).octaveShift());
    }

    @Test
    public void conflictingReadingsAreNotGuessed() {
        var g = page();
        dash(g, 120, 600, 55);
        assertEquals(
                0,
                apply(g, List.of(word("8va", 80, 40), word("15ma", 80, 40)), List.of(note(150, 0)))
                        .get(0)
                        .octaveShift());
    }

    @Test
    public void wordsInsideTheStaffAreNotDirections() {
        var g = page();
        dash(g, 120, 600, 125);
        assertEquals(
                0,
                apply(g, List.of(word("8va", 80, 115)), List.of(note(150, 0)))
                        .get(0)
                        .octaveShift());
    }

    @Test
    public void writtenPitchAndAccidentalArePreserved() {
        var g = page();
        dash(g, 120, 600, 55);
        var original = note(150, 0);
        var n = apply(g, List.of(word("8va", 80, 40)), List.of(original)).get(0);
        assertEquals(original.staffStep(), n.staffStep());
        assertEquals(original.clefBottomDiatonic(), n.clefBottomDiatonic());
        assertEquals(original.writtenAccidental(), n.writtenAccidental());
        assertEquals(original.pageY(), n.pageY(), 0);
    }

    @Test
    public void metadataCopiesPreserveTheShift() {
        var n = note(150, 0).withOctaveShift(2);
        assertEquals(
                2,
                n.withLeadingRest(1)
                        .withArticulations(2)
                        .withCompactOpening()
                        .withClef(30)
                        .withCrossStaffBeam()
                        .octaveShift());
    }

    @Test
    public void repeatedApplicationDoesNotDoubleTheShift() {
        var g = page();
        dash(g, 120, 600, 55);
        var words = List.of(word("8va", 80, 40));
        var n = apply(g, words, List.of(note(150, 0)));
        assertEquals(1, apply(g, words, n).get(0).octaveShift());
    }

    @Test
    public void shiftPreservesPrintedSeparatorsAndUnicodeBoundaries() {
        assertEquals(1, OctaveMarkDetector.shift("\t[8 V.A]_–—┘┐」"));
        assertEquals(-2, OctaveMarkDetector.shift("(15 M.B)┘"));
        assertEquals(0, OctaveMarkDetector.shift(null));
        for (String text : List.of("8\u00a0va", "8\u2003va", "８va", "8va┘tail", "8va suffix")) {
            assertEquals(text, 0, OctaveMarkDetector.shift(text));
        }
        assertEquals(2, OctaveMarkDetector.shift("15MA"));
    }
}
