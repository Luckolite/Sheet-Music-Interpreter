// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Isolated optical stage contract only; does not execute NativePageGuide.analyze or inference. */
public final class NativeCallerArpeggioContractTest {
    static ScorePageInterpretation isolated(
            ScorePageInterpretation s,
            byte[] g,
            List<PlayingTechniqueDetector.Staff> staffs,
            int physicalPage) {
        return ScoreArpeggioDetector.apply(s, g, 260, 250, staffs, Math.max(0, physicalPage));
    }

    static List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(
                new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2));
    }

    @Test
    public void qualifiedThreeHeadWaveRetainsAllLeavesAndPhysicalPage() {
        var s = ExtentChallenge.page();
        var g = ExtentChallenge.finite();
        var copy = g.clone();
        var q = isolated(s, g, staffs(), 7);
        assertEquals(1, q.expressiveEvents().size());
        assertEquals(s, q.withExpressiveEvents(s.expressiveEvents()));
        assertArrayEquals(copy, g);
        var e = q.expressiveEvents().get(0);
        assertEquals(7, e.evidence().get(0).pageIndex());
        assertEquals(List.of(0, 1, 2), ScoreArpeggioNotation.targetIndices(q, e));
    }

    @Test
    public void paperAddsNoMarkAndUnknownPageUsesExistingZeroConvention() {
        var s = ExtentChallenge.page();
        assertEquals(s, isolated(s, ExtentChallenge.paper(), staffs(), -1));
        var q = isolated(s, ExtentChallenge.finite(), staffs(), -1);
        assertEquals(0, q.expressiveEvents().get(0).evidence().get(0).pageIndex());
    }

    @Test
    public void wrongPhysicalStaffCannotBorrowChordOwnership() {
        var s = ExtentChallenge.page();
        assertEquals(
                s,
                isolated(
                        s,
                        ExtentChallenge.finite(),
                        List.of(new PlayingTechniqueDetector.Staff(190, 238, 12, 0, 2)),
                        5));
    }
}
