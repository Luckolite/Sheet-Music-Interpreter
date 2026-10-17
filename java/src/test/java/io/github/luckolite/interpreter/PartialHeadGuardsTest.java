// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PartialHeadGuardsTest {
    @Test
    public void oneRealHeadMaskPixelPreventsConnectedWordOwnership() {
        var s = ConnectedArticulationTest.scene(true, false);
        s[1][132 * 1000 + 211] = OmrMeasurePostProcessor.NOTEHEAD;
        var anchors = List.of(new NoteArticulationDetector.Anchor(211, 179, 12, 0));
        assertTrue(
                ExpressiveWordBodyInk.detect(
                                ConnectedArticulationTest.words(), s[1], s[0], 1000, 1000, anchors)
                        .isEmpty());
    }

    @Test
    public void unmaskedGraceAnchorInsideWordPreventsOwnership() {
        var s = ConnectedArticulationTest.scene(true, false);
        var anchors = List.of(new NoteArticulationDetector.Anchor(211, 132, 12, 0));
        assertTrue(
                ExpressiveWordBodyInk.detect(
                                ConnectedArticulationTest.words(), s[1], s[0], 1000, 1000, anchors)
                        .isEmpty());
    }

    @Test
    public void ornamentBitsSurviveActualPostpass() {
        var s = ConnectedArticulationTest.scene(true, false);
        var n =
                new ScoreNoteEvent(
                        0,
                        .211f,
                        0,
                        0,
                        1,
                        .179f,
                        false,
                        1,
                        1,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        0,
                        3,
                        0,
                        NoteOrnament.MORDENT,
                        ScoreNoteEvent.CLEF_TREBLE);
        assertEquals(
                List.of(n),
                PrintedWordArticulations.apply(
                        ConnectedArticulationTest.words(),
                        List.of(new PlayingTechniqueDetector.Staff(150, 198, 12, 0, 1)),
                        List.of(new MeasureRegion(0, 1, .15f, .21f)),
                        List.of(n),
                        s[1],
                        s[0],
                        1000,
                        1000));
    }
}
