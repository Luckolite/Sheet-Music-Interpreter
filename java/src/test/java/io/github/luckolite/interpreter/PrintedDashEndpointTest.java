// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedDashEndpointTest {
    final List<PrintedDashSpan.SystemBand> systems =
            List.of(new PrintedDashSpan.SystemBand(.1f, .9f, .1f, .2f));
    final List<MeasureRegion> measures =
            List.of(new MeasureRegion(.1f, .6f, .1f, .2f), new MeasureRegion(.62f, .9f, .1f, .2f));

    Optional<ScoreAnchor> read(PrintedDashSpan.End end, List<ScoreTempoChange> tempos) {
        var span =
                new PrintedDashSpan.Span(
                        0, 0, List.of(new PrintedDashChains.Run(.3f, .6f, .22f, 20)), end);
        return PrintedDashEndpoint.numeric(
                span, systems, measures, tempos, new ScoreAnchor(0, 0), .01f, .01f);
    }

    @Test
    public void provedNumericBarStartSuppliesExactBeat() {
        assertEquals(
                Optional.of(new ScoreAnchor(1, 0)),
                read(PrintedDashSpan.End.CLOSED, List.of(new ScoreTempoChange(1, 0, 120))));
    }

    @Test
    public void openPageEdgeDoesNotInventEnd() {
        assertTrue(
                read(PrintedDashSpan.End.PAGE_EDGE, List.of(new ScoreTempoChange(1, 0, 120)))
                        .isEmpty());
    }

    @Test
    public void missingContinuationDoesNotInventEnd() {
        assertTrue(
                read(
                                PrintedDashSpan.End.MISSING_CONTINUATION,
                                List.of(new ScoreTempoChange(1, 0, 120)))
                        .isEmpty());
    }

    @Test
    public void opticalMidBarFractionDoesNotBecomeBeat() {
        assertTrue(
                read(PrintedDashSpan.End.CLOSED, List.of(new ScoreTempoChange(1, .4f, 120)))
                        .isEmpty());
    }

    @Test
    public void conflictingNumericMarksRemainUnresolved() {
        assertTrue(
                read(
                                PrintedDashSpan.End.CLOSED,
                                List.of(
                                        new ScoreTempoChange(1, 0, 120),
                                        new ScoreTempoChange(1, 0, 140)))
                        .isEmpty());
    }

    @Test
    public void missingNumericEndpointRemainsUnresolved() {
        assertTrue(read(PrintedDashSpan.End.CLOSED, List.of()).isEmpty());
    }
}
