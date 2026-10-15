// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumberedPitchedPickupTest {
    static ScorePageInterpretation score(boolean numbered, boolean shortBar, boolean tied) {
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .32f, 0, 0, 1, .25f, tied, 0, 1, 2, 0),
                        new ScoreNoteEvent(0, .73f, 1, 0, 1, .24f, false, 0, 1, 2, 0));
        return new ScorePageInterpretation(
                        List.of(
                                new MeasureRegion(.1f, shortBar ? .18f : .3f, .2f, .3f),
                                new MeasureRegion(.31f, .6f, .2f, .3f),
                                new MeasureRegion(.61f, .9f, .2f, .3f)),
                        notes,
                        0,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of())
                .withOpeningPickup(numbered);
    }

    @Test
    public void numberedTwoEighthsProveOneBeat() {
        assertEquals(1, ScoreOpeningDuration.provedQuarterBeats(score(true, true, false), 4), 0);
    }

    @Test
    public void missingNumberingProofKeepsPositionGuard() {
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(score(false, true, false), 4)));
    }

    @Test
    public void fullWidthBarCannotShorten() {
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(score(true, false, false), 4)));
    }

    @Test
    public void incomingTieCannotShorten() {
        assertTrue(
                Double.isNaN(ScoreOpeningDuration.provedQuarterBeats(score(true, true, true), 4)));
    }

    @Test
    public void legacyListApiDoesNotInferProof() {
        var s = score(true, true, false);
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(
                                s.notes(), s.rests(), s.measures(), 4, 0)));
    }
}
