// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CrossingConeTest {
    static int apply(byte[] a) {
        var p = ExtentChallenge.page();
        var q =
                ScoreArpeggioDetector.apply(
                        p,
                        a,
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
    public void finiteGlyphEndingOnStaffRailHasCompleteEmptyExitCone() {
        var a = ExtentChallenge.finite();
        for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(a, x, 109, 100);
        assertEquals(1, apply(a));
    }

    @Test
    public void twoReachableExitsAcrossSameRailAbstain() {
        var a = ExtentChallenge.finite();
        for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(a, x, 76, 100);
        for (int y = 77; y <= 85; y++) {
            ExtentChallenge.pixel(a, 60, y, 25);
            ExtentChallenge.pixel(a, 61, y, 25);
        }
        assertEquals(0, apply(a));
    }
}
