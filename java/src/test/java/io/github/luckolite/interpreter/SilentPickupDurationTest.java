// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SilentPickupDurationTest {
    static ScoreRestEvent r(int staff, int count, double duration) {
        return new ScoreRestEvent(0, .65f, .2f, .01f, staff, count, duration);
    }

    static double span(boolean proof, ScoreRestEvent... rests) {
        return ScoreOpeningDuration.provedNumberedSilentPickup(List.of(), List.of(rests), 3, proof);
    }

    @Test
    public void numberedQuarterRest() {
        assertEquals(1, span(true, r(0, 1, 1)), 0);
    }

    @Test
    public void unknownZeroDoesNotProvePickup() {
        assertTrue(Double.isNaN(span(false, r(0, 1, 1))));
    }

    @Test
    public void agreeingStaves() {
        assertEquals(.5, span(true, r(0, 2, .5), r(1, 2, .5)), 0);
    }

    @Test
    public void missingStaffRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 2, 1))));
    }

    @Test
    public void disagreementRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 2, 1), r(1, 2, .5))));
    }

    @Test
    public void fullMeasureSilenceRejects() {
        assertTrue(Double.isNaN(span(true, ScoreRestEvent.fullMeasure(0, .5f, .2f, .01f, 0, 1))));
    }

    @Test
    public void duplicateVoiceRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 1, .5), r(0, 1, .5))));
    }

    @Test
    public void nominalDurationRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 1, 3))));
    }

    @Test
    public void wholeRestRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 1, 4))));
    }

    @Test
    public void invalidDurationRejects() {
        assertTrue(Double.isNaN(span(true, r(0, 1, Double.NaN))));
    }

    @Test
    public void noRestsRejects() {
        assertTrue(Double.isNaN(span(true)));
    }
}
