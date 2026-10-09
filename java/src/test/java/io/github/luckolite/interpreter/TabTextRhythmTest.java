// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Original generated rasters and text boxes. No commercial score material. */
public class TabTextRhythmTest {
    final int W = 500, H = 400;

    TablatureDecoder.Word word(String s, float x, float y, float width, float height) {
        return new TablatureDecoder.Word(
                s,
                (x - width / 2) / W,
                (y - height / 2) / H,
                (x + width / 2) / W,
                (y + height / 2) / H);
    }

    TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> fs) {
        return new TablatureDecoder.Staff(80, 20, -1, fs, List.of(20f, 480f));
    }

    byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 180; y += 20) for (int x = 20; x <= 480; x++) g[y * W + x] = 0;
        return g;
    }

    void stem(byte[] g, int x) {
        for (int y = 194; y <= 234; y++) g[y * W + x] = 0;
    }

    void arc(byte[] g, int left, int right, int cy) {
        for (int x = left; x <= right; x++) {
            int y =
                    cy
                            - Math.round(
                                    6
                                            + 15
                                                    * (float)
                                                            Math.sin(
                                                                    Math.PI
                                                                            * (x - left)
                                                                            / (right - left)));
            g[y * W + x] = 0;
        }
    }

    @Test
    public void overlappingPdfSearchMatchesRetainWholeFret() {
        var a = word("12", 100, 80, 22, 18);
        var b = word("1", 95, 80, 7, 18);
        var c = word("2", 106, 80, 9, 18);
        assertEquals(List.of(a), TabTextSource.disjoint(List.of(b, a, c)));
    }

    @Test
    public void separatedPdfDigitsJoinWithoutJoiningSeparateAttacks() {
        var words =
                List.of(
                        word("1", 94, 80, 7, 18),
                        word("4", 108, 80, 9, 18),
                        word("5", 150, 80, 9, 18));
        var fs = TablatureDecoder.withWords(List.of(staff(List.of())), words, W, H).get(0).frets();
        assertEquals(List.of(14, 5), fs.stream().map(TablatureDecoder.Fret::fret).toList());
    }

    @Test
    public void graceFretsUseRelativeSizeAndKeepTheirPitch() {
        var fs =
                TablatureDecoder.withWords(
                                List.of(staff(List.of())),
                                List.of(
                                        word("5", 100, 80, 9, 18),
                                        word("7", 160, 80, 6, 12),
                                        word("9", 190, 80, 9, 18)),
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(0, fs.get(0).marks() & NoteOrnament.GRACE);
        assertTrue((fs.get(1).marks() & NoteOrnament.GRACE) != 0);
        assertEquals(7, fs.get(1).fret());
    }

    @Test
    public void smallUniformFontIsNotAllGrace() {
        var fs =
                TablatureDecoder.withWords(
                                List.of(staff(List.of())),
                                List.of(word("5", 100, 80, 7, 12), word("7", 150, 80, 7, 12)),
                                W,
                                H)
                        .get(0)
                        .frets();
        assertTrue(fs.stream().noneMatch(f -> (f.marks() & NoteOrnament.GRACE) != 0));
    }

    @Test
    public void smuflRestAndFlagHaveExplicitValues() {
        assertEquals(.25, TabNotation.duration("\uE4E7"), 0);
        var fs =
                TablatureDecoder.withWords(
                                List.of(staff(List.of())),
                                List.of(
                                        word("5", 100, 80, 10, 18),
                                        word("\uE243", 105, 220, 10, 30),
                                        word("\uE4E6", 200, 120, 12, 20)),
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(2, fs.get(0).beams());
        assertEquals(-2, fs.get(1).fret());
        assertEquals(.5, fs.get(1).duration(), 0);
    }

    @Test
    public void tappingAndHammerLettersAreNotHalfOrThirtySecondDurations() {
        var t =
                TablatureDecoder.withWords(
                                List.of(staff(List.of())),
                                List.of(
                                        word("5", 100, 80, 10, 18),
                                        word("H", 100, 55, 10, 14),
                                        word("T", 150, 55, 10, 14),
                                        word("7", 150, 80, 10, 18)),
                                W,
                                H)
                        .get(0);
        assertTrue(t.frets().stream().allMatch(f -> f.duration() == 0 && f.beams() == 0));
    }

    @Test
    public void visibleTiePlusBlankStemAddsOneContinuation() {
        byte[] g = page();
        stem(g, 100);
        stem(g, 180);
        arc(g, 110, 175, 120);
        var t =
                new TablatureDecoder.Staff(
                        80,
                        20,
                        -1,
                        List.of(new TablatureDecoder.Fret(100, 120, 2, 7)),
                        List.of(20f, 480f));
        var fs = TabRhythmContinuations.apply(List.of(t), g, W, H).get(0).frets();
        assertEquals(2, fs.size());
        assertTrue(fs.get(1).tied());
        assertEquals(7, fs.get(1).fret());
    }

    @Test
    public void muteStopsATieEvenInsideAChord() {
        byte[] g = page();
        stem(g, 100);
        stem(g, 180);
        arc(g, 110, 175, 120);
        var fs =
                TabRhythmContinuations.apply(
                                List.of(
                                        staff(
                                                List.of(
                                                        new TablatureDecoder.Fret(100, 120, 2, 7),
                                                        new TablatureDecoder.Fret(100, 140, 3, 5),
                                                        new TablatureDecoder.Fret(
                                                                180, 120, 2, -1)))),
                                g,
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(3, fs.size());
        assertTrue(fs.stream().noneMatch(TablatureDecoder.Fret::tied));
    }

    @Test
    public void noStemDoesNotInventATiedAttack() {
        byte[] g = page();
        arc(g, 110, 175, 120);
        var fs =
                TabRhythmContinuations.apply(
                                List.of(staff(List.of(new TablatureDecoder.Fret(100, 120, 2, 7)))),
                                g,
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(1, fs.size());
    }

    @Test
    public void stemWithoutArcDoesNotInventATiedAttack() {
        byte[] g = page();
        stem(g, 180);
        assertEquals(
                1,
                TabRhythmContinuations.apply(
                                List.of(staff(List.of(new TablatureDecoder.Fret(100, 120, 2, 7)))),
                                g,
                                W,
                                H)
                        .get(0)
                        .frets()
                        .size());
    }

    @Test
    public void monophonicLegatoDoesNotInventAChordOnAnotherString() {
        byte[] g = page();
        stem(g, 100);
        stem(g, 180);
        arc(g, 110, 175, 120);
        var fs =
                TabRhythmContinuations.apply(
                                List.of(
                                        staff(
                                                List.of(
                                                        new TablatureDecoder.Fret(100, 120, 2, 7),
                                                        new TablatureDecoder.Fret(
                                                                180, 140, 3, 9)))),
                                g,
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(2, fs.size());
        assertTrue(fs.stream().noneMatch(TablatureDecoder.Fret::tied));
    }

    @Test
    public void straightRuleIsNotATie() {
        byte[] g = page();
        stem(g, 180);
        for (int x = 110; x < 175; x++) g[110 * W + x] = 0;
        assertEquals(
                1,
                TabRhythmContinuations.apply(
                                List.of(staff(List.of(new TablatureDecoder.Fret(100, 120, 2, 7)))),
                                g,
                                W,
                                H)
                        .get(0)
                        .frets()
                        .size());
    }

    @Test
    public void explicitTechniqueChangesPreserveCount() {
        var t =
                staff(
                        List.of(
                                new TablatureDecoder.Fret(100, 80, 0, 5),
                                new TablatureDecoder.Fret(160, 80, 0, 7),
                                new TablatureDecoder.Fret(220, 80, 0, 5)));
        var fs =
                TabPerformanceMarks.apply(
                                List.of(t),
                                List.of(
                                        word("H", 160, 55, 10, 14),
                                        word("P", 220, 55, 10, 14),
                                        word("T", 100, 55, 10, 14)),
                                W,
                                H)
                        .get(0)
                        .frets();
        assertEquals(3, fs.size());
        assertEquals(TabEffect.TAP, TabEffect.kind(fs.get(0).marks()));
        assertEquals(TabEffect.HAMMER, TabEffect.kind(fs.get(1).marks()));
        assertEquals(TabEffect.PULL, TabEffect.kind(fs.get(2).marks()));
    }

    @Test
    public void harmonicTuningIsSoundingPitch() {
        var f =
                new TablatureDecoder.Fret(
                        100, 80, 0, 5, 1, 0, 0, TabEffect.encode(TabEffect.HARMONIC, 0));
        var t = staff(List.of(f));
        t = TabTuning.withHeader(List.of(t), "Tuning: D A D G A D").get(0);
        var n =
                TablatureDecoder.apply(
                                new ScorePageInterpretation(List.of(), List.of()), List.of(t), W, H)
                        .notes()
                        .get(0);
        int d = n.clefBottomDiatonic() + n.staffStep();
        assertEquals(
                86,
                (d / 7 + 1) * 12 + new int[] {0, 2, 4, 5, 7, 9, 11}[d % 7] + n.writtenAccidental());
    }

    @Test
    public void shortOpeningMeterChangesWholeBarRest() {
        var m = new MeasureRegion(.04f, .96f, .15f, .5f);
        var score =
                new ScorePageInterpretation(
                        List.of(m),
                        List.of(),
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(ScoreRestEvent.fullMeasure(0, .5f, .3f, .05f, 0, 1)),
                        List.of(),
                        List.of());
        var out =
                TabMeter.apply(
                        score,
                        List.of(staff(List.of())),
                        List.of(word("\uE082", 55, 110, 18, 25), word("\uE084", 55, 145, 18, 25)),
                        W,
                        H);
        assertEquals(2, out.meterChanges().get(0).numerator());
        assertEquals(4, out.rests().get(0).durationBeats(), 0);
        assertTrue(out.rests().get(0).isFullMeasure());
        assertEquals(
                2,
                out.rests().get(0).resolvedDurationBeats(out.meterChanges().get(0).quarterBeats()),
                0);
    }

    @Test
    public void parenthesizedFretIsNotLost() {
        assertEquals(12, TabNotation.parse("(12)", 90, 110, 80, 0).get(0).fret());
    }

    @Test
    public void quarterTempoRequiresNoteAndEqualsAndPreservesExplicitTempo() {
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()),
                        List.of(staff(List.of(new TablatureDecoder.Fret(150, 80, 0, 5)))),
                        W,
                        H);
        var words =
                List.of(
                        word("\uECA5", 50, 30, 12, 28),
                        word("=", 70, 37, 10, 6),
                        word("132", 95, 35, 24, 16));
        assertEquals(
                132,
                TabTempo.apply(score, List.of(staff(List.of())), words, W, H)
                        .tempoChanges()
                        .get(0)
                        .bpm(),
                0);
        assertTrue(
                TabTempo.apply(score, List.of(staff(List.of())), words.subList(1, 3), W, H)
                        .tempoChanges()
                        .isEmpty());
        var explicit =
                new ScorePageInterpretation(
                        score.measures(),
                        score.notes(),
                        1,
                        List.of(),
                        List.of(new ScoreTempoChange(0, 0, 90)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        assertEquals(
                90,
                TabTempo.apply(explicit, List.of(staff(List.of())), words, W, H)
                        .tempoChanges()
                        .get(0)
                        .bpm(),
                0);
    }

    @Test
    public void dottedQuarterIsNotSilentlyReadAsPlainQuarter() {
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()),
                        List.of(staff(List.of(new TablatureDecoder.Fret(150, 80, 0, 5)))),
                        W,
                        H);
        var words =
                List.of(
                        word("\uECA5", 45, 30, 12, 28),
                        word("\uECB7", 58, 36, 4, 4),
                        word("=", 70, 37, 10, 6),
                        word("132", 95, 35, 24, 16));
        assertTrue(
                TabTempo.apply(score, List.of(staff(List.of())), words, W, H)
                        .tempoChanges()
                        .isEmpty());
    }
}
