// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AnchoredObjectTest {
    static ScorePageInterpretation page(int stem) {
        var ns = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 3, 7})
            ns.add(
                    new ScoreNoteEvent(
                                    0,
                                    35f / 204,
                                    step,
                                    0,
                                    2,
                                    (100 - step * 6f) / 250,
                                    false,
                                    0,
                                    0,
                                    0,
                                    2)
                            .withStemDirection(stem));
        return new ScorePageInterpretation(List.of(new MeasureRegion(56f / 260, 1, 0, 1)), ns, 1);
    }

    static byte[] ink(int bar, boolean contact, boolean link) {
        var g = ExtentChallenge.finite();
        for (int y = 52; y <= 100; y += 12)
            for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(g, x, y, 100);
        for (int y = 52; y <= 125; y++) {
            if (bar >= 0) {
                ExtentChallenge.pixel(g, bar, y, 25);
                ExtentChallenge.pixel(g, bar + 1, y, 25);
            }
            ExtentChallenge.pixel(g, 84, y, 25);
        }
        for (int hy : new int[] {58, 82, 100})
            for (int x = contact ? 84 : 89; x <= 94; x++) ExtentChallenge.pixel(g, x, hy - 3, 25);
        if (link) for (int x = 60; x <= 84; x++) ExtentChallenge.pixel(g, x, 75, 25);
        return g;
    }

    static int apply(byte[] g, int stem) {
        var p = page(stem);
        var q =
                ScoreArpeggioDetector.apply(
                        p,
                        g,
                        260,
                        250,
                        List.of(
                                new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2)),
                        0);
        assertEquals(p.notes(), q.notes());
        return q.expressiveEvents().size();
    }

    @Test
    public void independentlyAnchoredMeasureAndChordObjectsPreserveFiniteWave() {
        assertEquals(1, apply(ink(56, true, false), -1));
    }

    @Test
    public void nearbyShaftWithoutMeasureBoundaryCannotBeExcluded() {
        assertEquals(0, apply(ink(72, true, false), -1));
    }

    @Test
    public void nearbyShaftWithoutHeadConnectionCannotBeExcluded() {
        assertEquals(0, apply(ink(56, false, false), -1));
    }

    @Test
    public void unknownStemCannotExcludeNearbyObject() {
        assertEquals(0, apply(ink(56, true, false), 0));
    }

    @Test
    public void oppositeStemEvidenceCannotExcludeDownwardShaft() {
        assertEquals(0, apply(ink(56, true, false), 1));
    }

    @Test
    public void thinVisibleLinkFromShaftToWaveAbstains() {
        assertEquals(0, apply(ink(56, true, true), -1));
    }

    @Test
    public void completeComponentTouchingOwnershipBoundaryAbstains() {
        assertEquals(0, apply(ink(54, true, false), -1));
    }
}
