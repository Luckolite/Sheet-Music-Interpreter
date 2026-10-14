// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PickupSourceProofTest {
    static ScorePageInterpretation score() {
        return new ScorePageInterpretation(
                List.of(
                        new MeasureRegion(.1f, .3f, .2f, .3f),
                        new MeasureRegion(.31f, .6f, .2f, .3f),
                        new MeasureRegion(.61f, .9f, .2f, .3f)),
                List.of(),
                0,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 3, 4)),
                List.of(new ScoreRestEvent(0, .65f, .25f, .01f, 0, 1, 1)));
    }

    @Test
    public void legacyUnknownZeroIsNotProof() {
        assertFalse(score().openingPickup());
        assertTrue(Double.isNaN(ScoreOpeningDuration.provedQuarterBeats(score(), 3)));
    }

    @Test
    public void sourceProofResolvesSilentOpening() {
        assertEquals(
                1, ScoreOpeningDuration.provedQuarterBeats(score().withOpeningPickup(true), 3), 0);
    }

    @Test
    public void boundaryClockUsesProof() {
        assertEquals(
                1,
                ScoreBoundaryClock.resolve(score().withOpeningPickup(true), 3)
                        .openingQuarterBeats(),
                0);
    }

    @Test
    public void printedMeterIsUnchanged() {
        var s = score().withOpeningPickup(true);
        assertEquals(3, s.meterChanges().get(0).numerator());
    }

    @Test
    public void expressionsPreserveProof() {
        assertTrue(score().withOpeningPickup(true).withExpressiveEvents(List.of()).openingPickup());
    }

    @Test
    public void directionsPreserveProof() {
        assertTrue(
                score().withOpeningPickup(true).withPlaybackDirections(List.of()).openingPickup());
    }

    @Test
    public void slursPreserveProof() {
        assertTrue(
                score().withOpeningPickup(true)
                        .withSlurSource(ScoreSlurSource.empty(0))
                        .openingPickup());
    }

    @Test
    public void explicitNoteEditsPreserveProof() {
        assertTrue(score().withOpeningPickup(true).withNotes(List.of(), List.of()).openingPickup());
    }

    @Test
    public void proofCanBeCleared() {
        assertFalse(score().withOpeningPickup(true).withOpeningPickup(false).openingPickup());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySourceRejectsProof() {
        new ScorePageInterpretation(List.of(), List.of()).withOpeningPickup(true);
    }
}
