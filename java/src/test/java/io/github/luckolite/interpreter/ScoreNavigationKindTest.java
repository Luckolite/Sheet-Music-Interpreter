// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic mixed-kind source ownership and repeated written-note controls. */
public class ScoreNavigationKindTest {
    private static ScoreNoteEvent note(ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(
                        0,
                        .25f,
                        3,
                        0,
                        2,
                        .42f,
                        false,
                        1,
                        2,
                        -1,
                        .5f,
                        5,
                        .25f,
                        NoteArticulation.TENUTO,
                        ScoreNoteEvent.CLEF_BASS,
                        true,
                        .125f,
                        true,
                        2,
                        5,
                        4,
                        -1)
                .withKind(kind);
    }

    private static ScorePageInterpretation source() {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(0, .5f, 0, 1), new MeasureRegion(.5f, 1, 0, 1)),
                List.of(note(ScoreNoteEvent.Kind.PITCHED), note(ScoreNoteEvent.Kind.UNPITCHED)));
    }

    private static ScoreNavigationProjection.Defaults defaults() {
        return new ScoreNavigationProjection.Defaults(5, 90, 4, 4);
    }

    private static void assertCopy(ScoreNoteEvent source, ScoreNoteEvent projected, int measure)
            throws Exception {
        assertEquals(measure, projected.measureIndex());
        for (RecordComponent component : ScoreNoteEvent.class.getRecordComponents()) {
            if (component.getName().equals("measureIndex")) continue;
            assertEquals(
                    component.getName(),
                    component.getAccessor().invoke(source),
                    component.getAccessor().invoke(projected));
        }
    }

    @Test
    public void untiedRepeatedNotesKeepAllFieldsAndBothKinds() throws Exception {
        var source = source();
        var plan =
                ScoreNavigationPlan.create(
                        2,
                        List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.REPEAT_START),
                                new ScorePlaybackDirection(
                                        2, ScorePlaybackDirection.Kind.REPEAT_END)));
        var played = ScoreNavigationProjection.project(source, plan, defaults());
        assertEquals(4, played.measures().size());
        assertEquals(4, played.notes().size());
        for (int occurrence = 0; occurrence < 2; occurrence++)
            for (int owner = 0; owner < 2; owner++)
                assertCopy(
                        source.notes().get(owner),
                        played.notes().get(occurrence * 2 + owner),
                        occurrence * 2);
        for (var note : played.notes())
            if (note.kind() == ScoreNoteEvent.Kind.UNPITCHED)
                assertThrows(IllegalStateException.class, note::diatonicPitchIdentity);
        assertEquals(2, source.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, source.notes().get(1).kind());
    }

    @Test
    public void linearRouteKeepsOriginalTypedSource() {
        var source = source();
        assertSame(
                source,
                ScoreNavigationProjection.project(
                        source, ScoreNavigationPlan.create(2, List.of()), defaults()));
    }

    @Test
    public void sameGeometryDifferentKindsKeepSourceRhythmOwners() {
        var source = source();
        var selected =
                ScoreRhythmProjection.resolve(
                        source.notes(), List.of(source.notes().get(1)), 4, List.of());
        assertEquals(1, selected.size());
        assertTrue(selected.containsKey(0));
        assertTrue(
                ScoreRhythmProjection.resolve(
                                List.of(source.notes().get(0)),
                                List.of(source.notes().get(1)),
                                4,
                                List.of())
                        .isEmpty());
        var both = ScoreRhythmProjection.resolve(source.notes(), source.notes(), 4, List.of());
        assertEquals(2, both.size());
        assertEquals(both.get(0).onsetBeats(), both.get(1).onsetBeats(), 0);
        assertEquals(both.get(0).durationBeats(), both.get(1).durationBeats(), 0);
    }
}
