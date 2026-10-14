// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PickupCopyPathTest {
    static ScorePageInterpretation source() {
        return PickupSourceProofTest.score().withOpeningPickup(true);
    }

    @Test
    public void tempoCopyPreservesProof() {
        assertTrue(TabTempo.apply(source(), List.of(), List.of(), 100, 100).openingPickup());
    }

    @Test
    public void meterAndTempoCopiesPreserveProof() {
        assertTrue(TabMeter.apply(source(), List.of(), List.of(), 100, 100).openingPickup());
    }

    @Test
    public void repeatedRouteRetainsOriginalOpening() {
        var s = source();
        var plan =
                ScoreNavigationPlan.create(
                        3,
                        List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.REPEAT_START),
                                new ScorePlaybackDirection(
                                        3, ScorePlaybackDirection.Kind.REPEAT_END)));
        assertTrue(
                ScoreNavigationProjection.project(
                                s, plan, new ScoreNavigationProjection.Defaults(0, 120, 3, 4))
                        .openingPickup());
    }

    @Test
    public void ordinaryRouteDoesNotInventProof() {
        var s = PickupSourceProofTest.score();
        var plan =
                ScoreNavigationPlan.create(
                        3,
                        List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.REPEAT_START),
                                new ScorePlaybackDirection(
                                        3, ScorePlaybackDirection.Kind.REPEAT_END)));
        assertFalse(
                ScoreNavigationProjection.project(
                                s, plan, new ScoreNavigationProjection.Defaults(0, 120, 3, 4))
                        .openingPickup());
    }
}
