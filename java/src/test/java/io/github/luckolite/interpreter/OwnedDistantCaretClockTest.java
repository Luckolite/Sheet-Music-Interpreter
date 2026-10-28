// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import io.github.luckolite.interpreter.ScoreClockContextSource.*;

/** Synthetic legal issuer records test transport only, never musical clock authority. */
public final class OwnedDistantCaretClockTest {
    private static final String HASH = "a".repeat(64);

    private static ScoreClockContextSource issued(
            OwnedDistantCaretOriginalTest.Scene s, List<ScoreNoteEvent> notes) {
        var value =
                new PrintedValue(
                        0,
                        0,
                        0,
                        1,
                        ValueKind.QUARTER,
                        1,
                        0,
                        0,
                        ValueRoute.OPAQUE_DEFAULT,
                        "SYNTHETIC_TRANSPORT_FIXTURE",
                        0,
                        1,
                        1,
                        1,
                        0,
                        HASH,
                        HASH,
                        Model.NONE,
                        Status.SOURCE_PREMISE);
        return new ScoreClockContextSource(
                new Origin(
                        HASH,
                        0,
                        1,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        ScoreClockContextCapture.digest(s.raw),
                        ScoreClockContextCapture.digest(s.labels)),
                notes.size(),
                1,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(value),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Missing(Requirement.VOICE_OWNERSHIP, -1)));
    }

    private static ScorePageInterpretation apply(
            OwnedDistantCaretOriginalTest.Scene s, ScorePageInterpretation score) {
        return PrintedBeamSource.apply(
                score,
                s.labels,
                s.raw,
                OwnedDistantCaretOriginalTest.W,
                OwnedDistantCaretOriginalTest.H,
                s.staffs,
                0);
    }

    private static OmrScoreInterpreter.Analysis analysis(
            List<ScoreNoteEvent> notes, ScoreClockContextSource context) {
        return new OmrScoreInterpreter.Analysis(
                notes,
                List.of(),
                List.of(),
                ScoreTremoloSource.empty(notes.size()),
                ScoreClockProducerSource.unavailable(notes.size()),
                context);
    }

    private static ScoreClockContextSource capture(
            OwnedDistantCaretOriginalTest.Scene s,
            ScorePageInterpretation score,
            OmrScoreInterpreter.Analysis producer) {
        return ScoreClockContextCapture.capture(
                s.labels,
                s.raw,
                OwnedDistantCaretOriginalTest.W,
                OwnedDistantCaretOriginalTest.H,
                score,
                producer,
                null,
                null);
    }

    @Test
    public void actualOwnedCaretPreservesEveryPreissuedContextRecord() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var context = issued(s, s.notes);
        var before = s.score().withClockContextSource(context);
        byte[] raw = s.raw.clone(), labels = s.labels.clone();
        var after = apply(s, before);
        assertEquals(NoteArticulation.MARCATO, after.notes().get(0).articulations());
        assertEquals(0, after.notes().get(1).articulations());
        assertArrayEquals(raw, s.raw);
        assertArrayEquals(labels, s.labels);
        assertEquals(
                "Trusted own MARCATO-only edit must retain exact previously issued context",
                context,
                after.clockContextSource());
    }

    @Test
    public void genericWithNotesStillInvalidatesEvenAMarcatoOnlyEdit() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var context = issued(s, s.notes);
        var notes = new ArrayList<>(s.notes);
        notes.set(0, notes.get(0).withArticulations(NoteArticulation.MARCATO));
        var after = s.score().withClockContextSource(context).withNotes(notes, List.of(0, 1));
        assertEquals(ScoreClockContextSource.unavailable(2, 1, 0), after.clockContextSource());
    }

    @Test
    public void genericCaptureRequiresExactNoteAndMatchingFrame() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var context = issued(s, s.notes);
        var producer = analysis(s.notes, context);
        assertEquals(context.printedValues(), capture(s, s.score(), producer).printedValues());
        var notes = new ArrayList<>(s.notes);
        notes.set(0, notes.get(0).withArticulations(NoteArticulation.MARCATO));
        assertTrue(
                capture(s, new ScorePageInterpretation(s.measures, notes), producer)
                        .printedValues()
                        .isEmpty());
        s.raw[0] = 0;
        assertTrue(capture(s, s.score(), producer).printedValues().isEmpty());
    }

    @Test
    public void genericCaptureRejectsDuplicateOriginalOrFinalOwners() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var duplicates = List.of(s.notes.get(0), s.notes.get(0));
        var context = issued(s, duplicates);
        assertTrue(capture(s, s.score(), analysis(duplicates, context)).printedValues().isEmpty());
        assertTrue(
                capture(
                                s,
                                new ScorePageInterpretation(s.measures, duplicates),
                                analysis(s.notes, issued(s, s.notes)))
                        .printedValues()
                        .isEmpty());
    }

    @Test
    public void captureAfterCaretCannotBorrowAnUnchangedPrintedValue() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var producer = analysis(s.notes, issued(s, s.notes));
        var after = apply(s, s.score());
        assertEquals(NoteArticulation.MARCATO, after.notes().get(0).articulations());
        assertTrue(capture(s, after, producer).printedValues().isEmpty());
    }

    @Test
    public void noOpaqueProofRetainsExistingContextWithoutCreatingFacts() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        for (int y = 335; y <= 343; y++) for (int x = 207; x <= 210; x++) s.pixel(x, y, 255);
        var context = issued(s, s.notes);
        var after = apply(s, s.score().withClockContextSource(context));
        assertEquals(0, after.notes().get(0).articulations());
        assertEquals(context, after.clockContextSource());
    }

    @Test
    public void existingAttackRetainsExistingContextWithoutCreatingFacts() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        s.notes.set(0, s.notes.get(0).withArticulations(NoteArticulation.STACCATO));
        var context = issued(s, s.notes);
        var after = apply(s, s.score().withClockContextSource(context));
        assertEquals(NoteArticulation.STACCATO, after.notes().get(0).articulations());
        assertEquals(context, after.clockContextSource());
    }

    @Test
    public void unknownContextRemainsUnknownAfterRealCaretRecovery() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var before = s.score();
        var after = apply(s, before);
        assertEquals(NoteArticulation.MARCATO, after.notes().get(0).articulations());
        assertEquals(before.clockContextSource(), after.clockContextSource());
        assertTrue(after.clockContextSource().printedValues().isEmpty());
    }
}
