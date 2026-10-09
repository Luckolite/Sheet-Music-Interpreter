// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

/** Original synthetic tab OCR boxes; no source score or private fixture. */
public class TabTripletOnsetTest {
    static final int W = 500, H = 400;

    TablatureDecoder.Word word(String s, float x, float y, float width, float height) {
        return new TablatureDecoder.Word(
                s,
                (x - width / 2) / W,
                (y - height / 2) / H,
                (x + width / 2) / W,
                (y + height / 2) / H);
    }

    List<TablatureDecoder.Staff> tabs(List<TablatureDecoder.Word> words) {
        return TablatureDecoder.withWords(
                List.of(new TablatureDecoder.Staff(80, 20, -1, List.of(), List.of(20f, 480f))),
                words,
                W,
                H);
    }

    ScorePageInterpretation decode(List<TablatureDecoder.Staff> tabs) {
        return TablatureDecoder.apply(
                new ScorePageInterpretation(List.of(), List.of()), tabs, W, H);
    }

    List<TablatureDecoder.Word> threeOnsets(String[] values, boolean chord) {
        var words = new ArrayList<TablatureDecoder.Word>();
        for (int i = 0; i < 3; i++) {
            int x = 100 + i * 60;
            words.add(word(values[i], x, 120, 10, 18));
            if (chord) words.add(word(values[i], x, 140, 10, 18));
            if (!TabNotation.rest(values[i])) words.add(word("E", x, 210, 10, 16));
        }
        words.add(word("3", 160, 250, 10, 12));
        return words;
    }

    void tripletNotes(ScorePageInterpretation score, int count) {
        assertEquals(count, score.notes().size());
        for (var n : score.notes()) {
            assertEquals(3, n.tupletDivisor());
            assertEquals(
                    1.0 / 3,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(n, score.notes(), 4),
                    1e-6);
        }
    }

    @Test
    public void isolatedNotesRemainTriplets() {
        tripletNotes(decode(tabs(threeOnsets(new String[] {"5", "7", "9"}, false))), 3);
    }

    @Test
    public void eachTripletChordOwnsOneOnset() {
        var t = tabs(threeOnsets(new String[] {"5", "7", "9"}, true));
        assertEquals(6, t.get(0).frets().size());
        tripletNotes(decode(t), 6);
    }

    @Test
    public void mutedStrumTripletsRemainRhythmic() {
        var s = decode(tabs(threeOnsets(new String[] {"x", "x", "x"}, true)));
        tripletNotes(s, 6);
        assertTrue(s.notes().stream().allMatch(n -> n.kind() == ScoreNoteEvent.Kind.UNPITCHED));
    }

    @Test
    public void eighthRestOccupiesOneTripletOnset() {
        var t = tabs(threeOnsets(new String[] {"5", "\uE4E6", "9"}, false));
        var s = decode(t);
        tripletNotes(s, 2);
        assertEquals(1, s.rests().size());
        assertEquals(1.0 / 3, s.rests().get(0).durationBeats(), 1e-6);
    }

    @Test
    public void directlyTypedTripletRestKeepsScaledDuration() {
        var t =
                new TablatureDecoder.Staff(
                        80,
                        20,
                        -1,
                        List.of(new TablatureDecoder.Fret(160, 120, 0, -2, .5f, 0, 0, 0, false, 3)),
                        List.of(20f, 480f));
        var s = decode(List.of(t));
        assertEquals(1.0 / 3, s.rests().get(0).durationBeats(), 1e-6);
    }

    @Test
    public void doubledDotsAndTupletScaleComposeOnRest() {
        var t =
                new TablatureDecoder.Staff(
                        80,
                        20,
                        -1,
                        List.of(new TablatureDecoder.Fret(160, 120, 0, -2, .5f, 0, 2, 0, false, 3)),
                        List.of(20f, 480f));
        assertEquals(7.0 / 12, decode(List.of(t)).rests().get(0).durationBeats(), 1e-6);
    }

    List<TablatureDecoder.Word> completeWords(boolean rest) {
        var words = new ArrayList<TablatureDecoder.Word>();
        words.add(word("5", 70, 120, 10, 18));
        words.add(word("H", 70, 210, 10, 16));
        for (int i = 0; i < 3; i++) {
            int x = 180 + i * 60;
            words.add(word(rest && i == 1 ? "\uE4E6" : "7", x, 120, 10, 18));
            if (!rest || i != 1) words.add(word("E", x, 210, 10, 16));
        }
        words.add(word("9", 410, 120, 10, 18));
        words.add(word("Q", 410, 210, 10, 16));
        words.add(word("3", 240, 250, 10, 12));
        return words;
    }

    ScorePageInterpretation completeBar(boolean rest) {
        return decode(tabs(completeWords(rest)));
    }

    void completeRestClock(List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        assertEquals(4, notes.size());
        assertEquals(1, rests.size());
        double[] expected = {0, 2, 8.0 / 3, 3};
        for (int i = 0; i < notes.size(); i++)
            assertEquals(expected[i], ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
        assertEquals(1.0 / 3, notes.get(1).followingRestBeats(), 1e-6);
        assertEquals(7.0 / 3, ScoreRestTiming.beatInMeasure(rests.get(0), rests, notes, 4), 1e-6);
        assertTrue(ScoreRestTiming.active(rests.get(0), rests, notes, 0, 2.5f, 4));
        assertFalse(ScoreRestTiming.active(rests.get(0), rests, notes, 0, 2.75f, 4));
    }

    @Test
    public void tripletRestKeepsCompleteFourBeatClock() {
        var s = completeBar(true);
        completeRestClock(s.notes(), s.rests());
    }

    @Test
    public void syntheticSixStringDetectionKeepsTripletRestClock() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 80; y <= 180; y += 20) for (int x = 20; x <= 480; x++) gray[y * W + x] = 0;
        for (int y = 80; y <= 180; y++) for (int x : new int[] {20, 480}) gray[y * W + x] = 0;
        var words = completeWords(true);
        var detected = TablatureDecoder.detect(gray, W, H);
        assertEquals(1, detected.size());
        var withWords = TablatureDecoder.withWords(detected, words, W, H);
        var rhythmic = TabNotation.rasterRhythm(withWords, gray, W, H, words);
        var s = decode(rhythmic);
        completeRestClock(s.notes(), s.rests());
    }

    @Test
    public void tripletClockAndSilenceSurviveCurrentNativeWriter() throws Exception {
        var s = completeBar(true);
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(
                new DataOutputStream(bytes),
                new OmrScoreInterpreter.Analysis(s.notes(), s.keyChanges(), s.rests()));
        var out =
                NativeDecoderWire.readAnalysis(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 1);
        assertEquals(s.notes(), out.notes());
        assertEquals(s.rests(), out.rests());
        completeRestClock(out.notes(), out.rests());
    }

    @Test
    public void eachChordTripletHasOneThirdBeatClockAdvance() {
        var s = decode(tabs(threeOnsets(new String[] {"5", "7", "9"}, true)));
        for (int i = 0; i < 6; i++)
            assertEquals(
                    (i / 2) / 3.0,
                    ScoreNoteTiming.beatInMeasure(s.notes().get(i), s.notes(), 1),
                    1e-6);
    }

    @Test
    public void graceNoteDoesNotStealTripletOnsetCount() {
        var words = new ArrayList<>(threeOnsets(new String[] {"5", "7", "9"}, false));
        words.add(word("2", 135, 120, 6, 10));
        var t = tabs(words).get(0);
        assertEquals(4, t.frets().size());
        for (var f : t.frets())
            assertEquals((f.marks() & NoteOrnament.GRACE) != 0 ? 1 : 3, f.tuplet());
    }

    @Test
    public void noPrintedTripletCountKeepsPlainChordsAndRests() {
        var words = new ArrayList<>(threeOnsets(new String[] {"5", "\uE4E6", "9"}, true));
        words.removeIf(w -> w.text().equals("3"));
        var s = decode(tabs(words));
        assertTrue(s.notes().stream().allMatch(n -> n.tupletDivisor() == 1));
        assertEquals(.5, s.rests().get(0).durationBeats(), 0);
    }

    @Test
    public void sameColumnIndependentFullMeasureRestStaysFull() {
        var words = new ArrayList<TablatureDecoder.Word>();
        for (int x : new int[] {190, 250, 310}) {
            words.add(word("5", x, 120, 10, 18));
            words.add(word("E", x, 210, 10, 16));
        }
        words.add(word("\uE4E3", 250, 40, 16, 16));
        words.add(word("3", 250, 250, 10, 12));
        var s = decode(tabs(words));
        tripletNotes(s, 3);
        assertEquals(1, s.rests().size());
        assertTrue(s.rests().get(0).isFullMeasure());
        assertEquals(4, s.rests().get(0).durationBeats(), 0);
        assertTrue(
                s.notes().stream()
                        .allMatch(n -> n.leadingRestBeats() == 0 && n.followingRestBeats() == 0));
    }

    @Test
    public void wholeRestGlyphWithProvedTupletRemainsLiteral() {
        var t =
                new TablatureDecoder.Staff(
                        80,
                        20,
                        -1,
                        List.of(
                                new TablatureDecoder.Fret(
                                        250, 120, 0, -2, 4, 0, 0, 0, false, 3, true)),
                        List.of(20f, 480f));
        var r = decode(List.of(t)).rests().get(0);
        assertFalse(r.isFullMeasure());
        assertEquals(8.0 / 3, r.durationBeats(), 1e-6);
    }

    @Test
    public void supportedManualRestTupletRatiosMatchSoundingNotes() {
        for (int actual : new int[] {1, 3, 5, 6, 7}) {
            var t =
                    new TablatureDecoder.Staff(
                            80,
                            20,
                            -1,
                            List.of(
                                    new TablatureDecoder.Fret(
                                            160, 120, 0, -2, .5f, 0, 1, 0, false, actual)),
                            List.of(20f, 480f));
            var n = new ScoreNoteEvent(0, .5f, 0, 0, 1, .3f, false, 1, 0, 0, .5f, actual);
            assertEquals(
                    ScoreNoteTiming.writtenDurationBeats(n),
                    decode(List.of(t)).rests().get(0).durationBeats(),
                    1e-6);
        }
    }
}
