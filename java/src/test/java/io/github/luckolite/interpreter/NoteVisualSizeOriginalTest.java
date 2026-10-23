// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original visual-only note contracts; small glyphs never acquire grace or silent semantics. */
public final class NoteVisualSizeOriginalTest {
    static ScoreNoteEvent full() {
        return new ScoreNoteEvent(
                0, .2f, 0, 0, 1, .4f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 2, 1, 0);
    }

    static ScoreNoteEvent cue() {
        return full().withVisualSize(ScoreNoteEvent.VisualSize.CUE);
    }

    @Test
    public void legacyConstructionDefaultsToFullSize() {
        assertEquals(ScoreNoteEvent.VisualSize.FULL, full().visualSize());
    }

    @Test
    public void smallGlyphRetainsPitchAndOrdinaryWrittenDuration() {
        var n = cue();
        assertEquals(ScoreNoteEvent.Kind.PITCHED, n.kind());
        assertEquals(2, ScoreNoteTiming.writtenDurationBeats(n), 0);
        assertEquals(0, n.articulations() & NoteOrnament.GRACE);
        assertEquals(full().articulations(), n.articulations());
    }

    @Test
    public void everyExistingWitherCarriesVisualSize() {
        var n = cue();
        var copies =
                List.of(
                        n.withKind(ScoreNoteEvent.Kind.UNPITCHED),
                        n.withStemDirection(1),
                        n.withTupletRatio(3, 2),
                        n.withBoundaryTies(1),
                        n.withOctaveShift(1),
                        n.withCompactOpening(),
                        n.withLeadingRest(1),
                        n.withCrossStaffBeam(),
                        n.withClef(ScoreNoteEvent.CLEF_BASS),
                        n.withArticulations(NoteArticulation.ACCENT));
        for (var copy : copies) assertEquals(ScoreNoteEvent.VisualSize.CUE, copy.visualSize());
    }

    @Test
    public void changingVisualSizeDoesNotMutateOriginal() {
        var n = full();
        var small = n.withVisualSize(ScoreNoteEvent.VisualSize.CUE);
        assertEquals(ScoreNoteEvent.VisualSize.FULL, n.visualSize());
        assertEquals(n, small.withVisualSize(ScoreNoteEvent.VisualSize.FULL));
    }

    @Test
    public void missingVisualClassificationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> full().withVisualSize(null));
    }
}
