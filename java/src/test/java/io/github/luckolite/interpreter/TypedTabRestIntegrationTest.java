// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Original six-rule rasters, synthetic OCR boxes and typed tokens; no score material. */
public class TypedTabRestIntegrationTest {
    static final int W = 500, H = 400;

    TablatureDecoder.Word word(String s, float x, float y, float width, float height) {
        return new TablatureDecoder.Word(
                s,
                (x - width / 2) / W,
                (y - height / 2) / H,
                (x + width / 2) / W,
                (y + height / 2) / H);
    }

    List<TablatureDecoder.Word> meter(int n, int d) {
        return List.of(
                word(Character.toString((char) (0xe080 + n)), 55, 110, 18, 25),
                word(Character.toString((char) (0xe080 + d)), 55, 145, 18, 25));
    }

    TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> fs, List<Float> bars) {
        return new TablatureDecoder.Staff(80, 20, -1, fs, bars);
    }

    TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> fs) {
        return staff(fs, List.of(20f, 480f));
    }

    ScorePageInterpretation empty() {
        return new ScorePageInterpretation(List.of(), List.of());
    }

    byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 180; y += 20) for (int x = 20; x <= 480; x++) g[y * W + x] = 0;
        for (int y = 80; y <= 180; y++) {
            g[y * W + 20] = 0;
            g[y * W + 480] = 0;
        }
        return g;
    }

    ScorePageInterpretation fresh(String glyph, float x, float y, int n, int d) {
        var words = new ArrayList<>(meter(n, d));
        words.add(word(glyph, x, y, 16, 16));
        byte[] gray = page();
        var tabs = TablatureDecoder.detect(gray, W, H);
        assertEquals(1, tabs.size());
        assertEquals(6, tabs.get(0).stringCount());
        tabs = TablatureDecoder.withWords(tabs, words, W, H);
        tabs = TabNotation.rasterRhythm(tabs, gray, W, H, words);
        return TabMeter.apply(TablatureDecoder.apply(empty(), tabs, W, H), tabs, words, W, H);
    }

    ScoreRestEvent rest(ScorePageInterpretation s) {
        assertEquals(1, s.rests().size());
        return s.rests().get(0);
    }

    float span(ScorePageInterpretation s) {
        return s.meterChanges().get(0).quarterBeats();
    }

    ScorePageInterpretation existing(ScoreRestEvent r, List<ScoreNoteEvent> notes) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .96f, .15f, .5f)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of(r),
                List.of(),
                List.of());
    }

    @Test
    public void freshCenteredSmuflWholeRestThreeFourKeepsWholeGlyph() {
        var s = fresh("\uE4E3", 250, 130, 3, 4);
        var r = rest(s);
        assertTrue(r.isFullMeasure());
        assertEquals(4, r.durationBeats(), 0);
        assertEquals(3, r.resolvedDurationBeats(span(s)), 0);
        assertEquals(0, ScoreRestTiming.beatInMeasure(r, s.rests(), s.notes(), span(s)), 0);
        assertTrue(ScoreRestTiming.active(r, s.rests(), s.notes(), 0, 2.9f, span(s)));
        assertFalse(ScoreRestTiming.active(r, s.rests(), s.notes(), 0, 3, span(s)));
    }

    @Test
    public void freshCenteredSmuflWholeRestSixEightResolvesThree() {
        var s = fresh("\uE4E3", 250, 130, 6, 8);
        var r = rest(s);
        assertTrue(r.isFullMeasure());
        assertEquals(4, r.durationBeats(), 0);
        assertEquals(3, r.resolvedDurationBeats(span(s)), 0);
    }

    @Test
    public void freshUnicodeWholeRestTwoFourResolvesTwo() {
        var s = fresh("𝄻", 250, 130, 2, 4);
        assertTrue(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
        assertEquals(2, rest(s).resolvedDurationBeats(span(s)), 0);
    }

    @Test
    public void freshWholeRestEightFourResolvesEight() {
        var s = fresh("\uE4E3", 250, 130, 8, 4);
        assertTrue(rest(s).isFullMeasure());
        assertEquals(8, rest(s).resolvedDurationBeats(span(s)), 0);
    }

    @Test
    public void nonCenteredWholeGlyphRemainsLiteral() {
        var s = fresh("\uE4E3", 150, 130, 3, 4);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
        assertEquals(4, rest(s).resolvedDurationBeats(span(s)), 0);
    }

    @Test
    public void dottedWholeGlyphRemainsLiteralSix() {
        var s = fresh("\uE4E3.", 250, 130, 3, 4);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(6, rest(s).durationBeats(), 0);
    }

    @Test
    public void quarterRestIsLiteralAndNotExpanded() {
        var s = fresh("\uE4E5", 250, 130, 6, 8);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(1, rest(s).durationBeats(), 0);
    }

    @Test
    public void legacyLiteralFourIsRetainedAcrossMeters() {
        for (int[] m : new int[][] {{2, 4}, {3, 4}, {6, 8}, {8, 4}}) {
            var r = new ScoreRestEvent(0, .5f, .3f, .05f, 0, 1, 4);
            var s =
                    TabMeter.apply(
                            existing(r, List.of()),
                            List.of(staff(List.of())),
                            meter(m[0], m[1]),
                            W,
                            H);
            assertSame(r, rest(s));
            assertEquals(ScoreRestEvent.Kind.LITERAL, rest(s).kind());
            assertEquals(4, rest(s).durationBeats(), 0);
        }
    }

    @Test
    public void existingTypedFullIsRetainedAndResolvedAgainstNewMeter() {
        var r = ScoreRestEvent.fullMeasure(0, .5f, .3f, .05f, 0, 1);
        var s =
                TabMeter.apply(
                        existing(r, List.of()), List.of(staff(List.of())), meter(2, 4), W, H);
        assertSame(r, rest(s));
        assertEquals(4, r.durationBeats(), 0);
        assertEquals(2, r.resolvedDurationBeats(span(s)), 0);
    }

    @Test
    public void manualDurationFourDoesNotInventGlyphEvidence() {
        var s =
                TablatureDecoder.apply(
                        empty(),
                        List.of(
                                staff(
                                        List.of(
                                                new TablatureDecoder.Fret(
                                                        250, 130, 0, -2, 4, 0, 0, 0)))),
                        W,
                        H);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
    }

    @Test
    public void inferredBarBoundariesDoNotProveFullMeasure() {
        var words = List.of(word("\uE4E3", 250, 130, 16, 16));
        var tabs = TablatureDecoder.withWords(List.of(staff(List.of(), List.of())), words, W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
    }

    @Test
    public void severalWholeRestsRemainLiteral() {
        var words = List.of(word("\uE4E3", 250, 130, 16, 16), word("\uE4E3", 350, 130, 16, 16));
        var tabs = TablatureDecoder.withWords(List.of(staff(List.of())), words, W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertEquals(2, s.rests().size());
        assertTrue(s.rests().stream().noneMatch(ScoreRestEvent::isFullMeasure));
        assertTrue(s.rests().stream().allMatch(r -> r.durationBeats() == 4));
    }

    @Test
    public void externalAugmentationDotPreventsFullMeaning() {
        var words = List.of(word("\uE4E3", 250, 130, 16, 16), word("\uE1E7", 270, 130, 4, 4));
        var tabs = TablatureDecoder.withWords(List.of(staff(List.of())), words, W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(6, rest(s).durationBeats(), 0);
    }

    @Test
    public void noteDurationLabelDoesNotOverwriteRestGlyph() {
        var words = List.of(word("\uE4E3", 250, 130, 16, 16), word("Q", 250, 220, 14, 16));
        var tabs = TablatureDecoder.withWords(List.of(staff(List.of())), words, W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertTrue(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
    }

    @Test
    public void ambiguousMovingVoiceStaysLiteral() {
        var tokens =
                List.of(
                        new TablatureDecoder.Fret(80, 120, 2, 5, 1, 0, 0, 0),
                        new TablatureDecoder.Fret(420, 120, 2, 7, 1, 0, 0, 0));
        var tabs =
                TabNotation.rhythmWords(
                        List.of(staff(tokens)), List.of(word("\uE4E3", 250, 40, 16, 16)), W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertFalse(rest(s).isFullMeasure());
        assertEquals(4, rest(s).durationBeats(), 0);
    }

    @Test
    public void glyphInsideStringLaneDoesNotClaimIndependentVoice() {
        var tokens =
                List.of(
                        new TablatureDecoder.Fret(80, 120, 2, 5, 1, 0, 0, 0),
                        new TablatureDecoder.Fret(420, 120, 2, 7, 1, 0, 0, 0));
        var tabs =
                TabNotation.rhythmWords(
                        List.of(staff(tokens)), List.of(word("\uE4E3", 250, 130, 16, 16)), W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertFalse(rest(s).isFullMeasure());
    }

    @Test
    public void freshSeparateFullVoiceKeepsLowerThreeQuarterClock() {
        var words = new ArrayList<>(meter(3, 4));
        words.add(word("\uE4E3", 250, 40, 16, 16));
        for (int x : new int[] {80, 250, 420}) {
            words.add(word("5", x, 120, 10, 18));
            words.add(word("Q", x, 220, 14, 16));
        }
        var tabs = TablatureDecoder.withWords(List.of(staff(List.of())), words, W, H);
        tabs = TabNotation.rasterRhythm(tabs, page(), W, H, words);
        var s = TabMeter.apply(TablatureDecoder.apply(empty(), tabs, W, H), tabs, words, W, H);
        var r = rest(s);
        assertTrue(r.isFullMeasure());
        assertEquals(3, r.resolvedDurationBeats(span(s)), 0);
        assertEquals(3, s.notes().size());
        for (int i = 0; i < 3; i++) {
            var note = s.notes().get(i);
            assertEquals(0, note.leadingRestBeats(), 0);
            assertEquals(0, note.followingRestBeats(), 0);
            assertEquals(i, ScoreNoteTiming.beatInMeasure(note, s.notes(), 3), .0001);
            assertEquals(
                    1, ScoreNoteTiming.resolvedWrittenDurationBeats(note, s.notes(), 3), .0001);
        }
    }

    @Test
    public void separateFullVoicePreservesHalfAndSixTripletChordsExactly() {
        var fs = new ArrayList<TablatureDecoder.Fret>();
        for (int string : new int[] {2, 3})
            fs.add(new TablatureDecoder.Fret(80, 80 + string * 20, string, 5, 2, 0, 0, 0));
        for (int x : new int[] {200, 250, 300, 350, 400, 450})
            for (int string : new int[] {2, 3})
                fs.add(
                        new TablatureDecoder.Fret(
                                x, 80 + string * 20, string, 7, 0, 1, 0, 0, false, 3));
        var base = TablatureDecoder.apply(empty(), List.of(staff(fs)), W, H);
        var tabs =
                TabNotation.rhythmWords(
                        List.of(staff(fs)), List.of(word("\uE4E3", 250, 40, 16, 16)), W, H);
        tabs = TabNotation.rasterRhythm(tabs, page(), W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertTrue(rest(s).isFullMeasure());
        assertEquals(14, s.notes().size());
        assertEquals(base.notes(), s.notes());
        for (int i = 0; i < s.notes().size(); i++) {
            assertEquals(
                    ScoreNoteTiming.beatInMeasure(base.notes().get(i), base.notes(), 4),
                    ScoreNoteTiming.beatInMeasure(s.notes().get(i), s.notes(), 4),
                    0);
            assertEquals(
                    ScoreNoteTiming.resolvedWrittenDurationBeats(
                            base.notes().get(i), base.notes(), 4),
                    ScoreNoteTiming.resolvedWrittenDurationBeats(s.notes().get(i), s.notes(), 4),
                    0);
        }
        assertEquals(4, rest(s).durationBeats(), 0);
    }

    @Test
    public void deferredRestDotDoesNotCreateFullSilenceOrDotLowerNote() {
        var fs = List.of(new TablatureDecoder.Fret(250, 120, 2, 5, 1, 0, 0, 0));
        var base = TablatureDecoder.apply(empty(), List.of(staff(fs)), W, H);
        var words = List.of(word("\uE4E3", 250, 40, 16, 16), word("\uE1E7", 270, 40, 4, 4));
        var tabs = TabNotation.rhythmWords(List.of(staff(fs)), words, W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertTrue(s.rests().isEmpty());
        assertEquals(base.notes(), s.notes());
    }

    @Test
    public void unknownSimultaneousRhythmDoesNotProveIndependentRest() {
        var fs = List.of(new TablatureDecoder.Fret(250, 120, 2, 5));
        var base = TablatureDecoder.apply(empty(), List.of(staff(fs)), W, H);
        var tabs =
                TabNotation.rhythmWords(
                        List.of(staff(fs)), List.of(word("\uE4E3", 250, 40, 16, 16)), W, H);
        var s = TablatureDecoder.apply(empty(), tabs, W, H);
        assertTrue(s.rests().isEmpty());
        assertEquals(base.notes(), s.notes());
    }

    @Test
    public void twoFreshCompleteBarsResolveChangingMeters() {
        byte[] gray = page();
        for (int y = 80; y <= 180; y++) gray[y * W + 250] = 0;
        var words = new ArrayList<>(meter(3, 4));
        words.add(word("\uE086", 280, 110, 18, 25));
        words.add(word("\uE088", 280, 145, 18, 25));
        words.add(word("\uE4E3", 135, 130, 16, 16));
        words.add(word("\uE4E3", 365, 130, 16, 16));
        var tabs = TablatureDecoder.withWords(TablatureDecoder.detect(gray, W, H), words, W, H);
        var s = TabMeter.apply(TablatureDecoder.apply(empty(), tabs, W, H), tabs, words, W, H);
        assertEquals(2, s.rests().size());
        assertEquals(2, s.meterChanges().size());
        for (int i = 0; i < 2; i++) {
            var r = s.rests().get(i);
            assertTrue(r.isFullMeasure());
            assertEquals(i, r.measureIndex());
            assertEquals(4, r.durationBeats(), 0);
            assertEquals(3, r.resolvedDurationBeats(s.meterChanges().get(i).quarterBeats()), 0);
        }
        assertEquals(4, s.meterChanges().get(0).denominator());
        assertEquals(8, s.meterChanges().get(1).denominator());
    }

    @Test
    public void centeredGlyphToleranceDoesNotSwallowOffsetLiteral() {
        assertTrue(rest(fresh("\uE4E3", 260, 130, 3, 4)).isFullMeasure());
        var offset = rest(fresh("\uE4E3", 265, 130, 3, 4));
        assertFalse(offset.isFullMeasure());
        assertEquals(4, offset.durationBeats(), 0);
    }

    @Test
    public void smallerLiteralValuesAndDottedValuesRemainExact() {
        for (double d : new double[] {.5, 2, 6, 7}) {
            var r = new ScoreRestEvent(0, .5f, .3f, .05f, 0, 1, d);
            var s =
                    TabMeter.apply(
                            existing(r, List.of()), List.of(staff(List.of())), meter(3, 4), W, H);
            assertSame(r, rest(s));
            assertEquals(d, r.durationBeats(), 0);
        }
    }
}
