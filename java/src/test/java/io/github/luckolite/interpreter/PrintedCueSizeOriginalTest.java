// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored cue-prefix pixels and text controls, independent of any published score. */
public final class PrintedCueSizeOriginalTest {
    static final int W = 640, H = 240;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(100, 156, 14, 0, 1);
    static final MeasureRegion REGION = new MeasureRegion(.1f, .95f, .3f, .85f);

    record Example(
            ScorePageInterpretation score,
            byte[] gray,
            List<PlayingTechniqueDetector.Word> words) {}

    static PlayingTechniqueDetector.Word word(String s, int l, int r) {
        return new PlayingTechniqueDetector.Word(s, l / (float) W, 64f / H, r / (float) W, 82f / H);
    }

    static Example example(boolean small) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 156; y += 14) for (int x = 64; x < 608; x++) gray[y * W + x] = 0;
        int[] xs = {150, 210, 270, 370, 440};
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < xs.length; i++) {
            int rx = small && i < 3 ? 6 : 9, ry = 5;
            for (int y = 142 - ry; y <= 142 + ry; y++)
                for (int x = xs[i] - rx; x <= xs[i] + rx; x++)
                    if (Math.pow((x - xs[i]) / (double) rx, 2)
                                    + Math.pow((y - 142) / (double) ry, 2)
                            <= 1) gray[y * W + x] = 0;
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            (xs[i] / (float) W - REGION.left()) / (REGION.right() - REGION.left()),
                            2,
                            0,
                            1,
                            142f / H,
                            false,
                            0,
                            1,
                            2,
                            0,
                            1,
                            0));
        }
        var score =
                new ScorePageInterpretation(
                        List.of(REGION),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)));
        return new Example(score, gray, List.of(word("Guitar", 120, 190), word("Play", 330, 360)));
    }

    static List<PrintedCueSize.Passage> detect(Example e) {
        return PrintedCueSize.detect(e.score(), e.words(), List.of(STAFF), e.gray(), W, H);
    }

    @Test
    public void pairedInstructionsAndSizeTransitionOwnExactlyThreeHeads() {
        var e = example(true);
        var p = detect(e);
        assertEquals(1, p.size());
        assertEquals(List.of(0, 1, 2), p.get(0).notes());
        assertEquals(3, p.get(0).playOwner());
    }

    @Test
    public void styleApplicationDoesNotChangePitchRhythmOrOtherProperties() {
        var e = example(true);
        var out = PrintedCueSize.applySizes(e.score(), detect(e));
        for (int i = 0; i < out.notes().size(); i++) {
            var n = out.notes().get(i);
            assertEquals(
                    i < 3 ? ScoreNoteEvent.VisualSize.CUE : ScoreNoteEvent.VisualSize.FULL,
                    n.visualSize());
            assertEquals(
                    e.score().notes().get(i), n.withVisualSize(ScoreNoteEvent.VisualSize.FULL));
        }
        assertTrue(
                e.score().notes().stream()
                        .allMatch(n -> n.visualSize() == ScoreNoteEvent.VisualSize.FULL));
    }

    @Test
    public void instrumentVocabularyIsIndependentOfTheGuitarExample() {
        var e = example(true);
        var words = List.of(word("Flute", 120, 190), word("PLAY.", 330, 360));
        assertEquals(1, detect(new Example(e.score(), e.gray(), words)).size());
    }

    @Test
    public void ordinarySizeNotesDoNotBecomeCueNotesFromWordsAlone() {
        assertTrue(detect(example(false)).isEmpty());
    }

    @Test
    public void absentReturnInstructionCannotBoundACuePrefix() {
        var e = example(true);
        assertTrue(detect(new Example(e.score(), e.gray(), List.of(e.words().get(0)))).isEmpty());
    }

    @Test
    public void arbitraryProseDoesNotAuthorizeSmallerNotation() {
        var e = example(true);
        assertTrue(
                detect(
                                new Example(
                                        e.score(),
                                        e.gray(),
                                        List.of(
                                                word("the guitar part", 120, 190),
                                                e.words().get(1))))
                        .isEmpty());
    }

    @Test
    public void twoPossibleReturnInstructionsAbstain() {
        var e = example(true);
        var ws = new ArrayList<>(e.words());
        ws.add(word("Play", 405, 435));
        assertTrue(detect(new Example(e.score(), e.gray(), ws)).isEmpty());
    }

    @Test
    public void repeatedIdenticalOcrReadingDoesNotDuplicatePassage() {
        var e = example(true);
        var ws = new ArrayList<>(e.words());
        ws.addAll(e.words());
        assertEquals(1, detect(new Example(e.score(), e.gray(), ws)).size());
    }

    @Test
    public void gracePrefixIsNotReclassifiedAsAnOrdinaryCuePassage() {
        var e = example(true);
        var ns = new ArrayList<>(e.score().notes());
        ns.set(1, ns.get(1).withArticulations(NoteOrnament.GRACE));
        assertTrue(
                detect(
                                new Example(
                                        e.score().withNotes(ns, List.of(0, 1, 2, 3, 4)),
                                        e.gray(),
                                        e.words()))
                        .isEmpty());
    }

    @Test
    public void unpitchedPrefixDoesNotBorrowAPitchedCueClassification() {
        var e = example(true);
        var ns = new ArrayList<>(e.score().notes());
        ns.set(1, ns.get(1).withKind(ScoreNoteEvent.Kind.UNPITCHED));
        assertTrue(
                detect(
                                new Example(
                                        e.score().withNotes(ns, List.of(0, 1, 2, 3, 4)),
                                        e.gray(),
                                        e.words()))
                        .isEmpty());
    }

    @Test
    public void absentPixelWitnessDoesNotInferSizeFromMetadata() {
        var e = example(true);
        byte[] blank = new byte[W * H];
        Arrays.fill(blank, (byte) 255);
        assertTrue(detect(new Example(e.score(), blank, e.words())).isEmpty());
    }

    @Test
    public void malformedGrayBufferAbstains() {
        var e = example(true);
        assertTrue(detect(new Example(e.score(), new byte[10], e.words())).isEmpty());
    }
}
