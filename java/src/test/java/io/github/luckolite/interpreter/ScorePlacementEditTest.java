// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic editing controls on explicit written owners and clocks. */
public class ScorePlacementEditTest {
    private static ScoreNoteEvent main(int measure, float x, int step, float duration) {
        return new ScoreNoteEvent(measure, x, step, 0, 1, .3f, false, 0, 0, 2, duration)
                .withClef(30);
    }

    private static List<ScoreNoteEvent> phrase() {
        return List.of(
                main(0, .1f, 0, 1),
                new ScoreNoteEvent(0, .25f, -2, 0, 1, .3f, false, 0, 3, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GRACE),
                new ScoreNoteEvent(0, .27f, -1, 0, 1, .3f, false, 0, 3, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GRACE),
                main(0, .3f, 1, 1),
                main(0, .7f, 2, 1),
                main(1, .2f, 3, 4));
    }

    @Test
    public void graceDeletionRenumbersSurvivorAndRecomputesStealFromSamePrincipal() {
        var source = phrase();
        var written = ScorePlacementMap.resolveWritten(source, 4, List.of());
        var playback = ScorePlacementMap.resolvePlayback(source, 4, List.of());
        var owners = List.of(0, 2, 3, 4, 5);
        var changed = owners.stream().map(source::get).toList();
        var clocks = ScorePlacementMap.editedClocks(source, changed, playback, written, owners);
        assertEquals(0, clocks.written().get(1).ordinal());
        assertEquals(2, clocks.written().get(1).principalIndex());
        assertEquals(25, clocks.written().get(1).stealPercent(), 0);
        assertEquals(.25, clocks.playback().get(1).durationBeats(), 0);
        assertEquals(playback.get(5), clocks.playback().get(4));
        assertEquals(written.get(5), clocks.written().get(4));
        assertThrows(UnsupportedOperationException.class, () -> clocks.written().clear());
    }

    @Test
    public void removingWrittenPrincipalCannotReassignItsSurvivingGraceBySpacing() {
        var source = phrase();
        var owners = List.of(0, 1, 2, 4, 5);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.editedClocks(
                                source,
                                owners.stream().map(source::get).toList(),
                                ScorePlacementMap.resolvePlayback(source, 4, List.of()),
                                ScorePlacementMap.resolveWritten(source, 4, List.of()),
                                owners));
    }

    @Test
    public void octaveEditKeepsClockButDurationEditInvalidatesOnlyItsMeasure() {
        var source = List.of(main(0, .1f, 0, 4), main(1, .2f, 3, 4));
        var playback = ScorePlacementMap.resolvePlayback(source, 4, List.of());
        var octave = List.of(main(0, .1f, 7, 4), source.get(1));
        assertEquals(playback, ScorePlacementMap.edited(source, octave, playback, List.of(0, 1)));
        var duration = List.of(main(0, .1f, 7, 2), source.get(1));
        var updated = ScorePlacementMap.edited(source, duration, playback, List.of(0, 1));
        assertEquals(Set.of(1), updated.keySet());
        assertEquals(playback.get(1), updated.get(1));
    }
}
