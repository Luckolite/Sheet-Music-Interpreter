// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class ObservedFamilyCallerOriginalTest {
    private static final int WIDTH = 100;

    private static MaximalRawBarExtractor.Body body(
            int tick, int x0, int x1, int centerAtX0, double slope, int half) {
        List<Integer> pixels = new ArrayList<>();
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) {
            int center = (int) Math.rint(centerAtX0 - slope * (x - x0));
            for (int y = center - half; y <= center + half; y++) {
                pixels.add(y * WIDTH + x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return new MaximalRawBarExtractor.Body(tick, x0, x1, centerAtX0, pixels, 7, 7, 7, 0);
    }

    @Test
    public void completePhysicalFamilyUsesExactDomainsInsteadOfSearchTicks() {
        var bodies =
                List.of(
                        body(-13, 10, 40, 30, .50, 3),
                        body(-8, 10, 40, 47, .50, 3),
                        body(-12, 10, 40, 64, .50, 3));
        var discovery =
                new MaximalRawBarExtractor.Discovery(
                        MaximalRawBarExtractor.Status.CANDIDATES, bodies, 123);
        Set<Integer> independentStaff = Set.of(0); // outside the body x span
        assertFalse(
                "the old search-tick equality contract rejects this physical evidence",
                discovery.twoOrThreeSameShape(17));
        assertTrue(
                "exact complete-edge domains and per-column spacing agree",
                discovery.twoOrThreeObservedFamily(17, WIDTH, independentStaff));
        long familyCost = discovery.observedFamilyWorkUpperBound(WIDTH, independentStaff);
        long[] budget = {familyCost};
        assertTrue(
                JoinedLaneFamilyGate.qualifies(
                        discovery, true, 17, WIDTH, independentStaff, budget));
        assertEquals("prepaid work is not refunded on success", 0, budget[0]);
    }

    @Test
    public void completeNonparallelBodyCannotJoinByTickOrSpacingAlone() {
        var bodies =
                List.of(
                        body(-13, 10, 40, 30, .50, 3),
                        body(-8, 10, 40, 47, .50, 3),
                        body(-12, 10, 40, 64, -.20, 3));
        var discovery =
                new MaximalRawBarExtractor.Discovery(
                        MaximalRawBarExtractor.Status.CANDIDATES, bodies, 123);
        assertFalse(discovery.twoOrThreeObservedFamily(17, WIDTH, Set.of(0)));
        long[] oneShort = {discovery.observedFamilyWorkUpperBound(WIDTH, Set.of(0)) - 1};
        assertFalse(
                JoinedLaneFamilyGate.qualifies(discovery, true, 17, WIDTH, Set.of(0), oneShort));
        assertEquals(
                "insufficient budget refuses before work and is not mutated",
                1,
                discovery.observedFamilyWorkUpperBound(WIDTH, Set.of(0)) - oneShort[0]);
    }

    @Test
    public void malformedOrUnboundedBodiesAndStaffEvidenceFailClosed() {
        var bodies =
                List.of(
                        body(-13, 10, 40, 30, .50, 3),
                        body(-8, 10, 40, 47, .50, 3),
                        body(-12, 10, 40, 64, .50, 3));
        var discovery =
                new MaximalRawBarExtractor.Discovery(
                        MaximalRawBarExtractor.Status.CANDIDATES, bodies, 123);
        assertFalse(discovery.twoOrThreeObservedFamily(17, WIDTH, null));
        assertFalse(discovery.twoOrThreeObservedFamily(17, WIDTH, Set.of(-1)));
        assertEquals(Long.MAX_VALUE, discovery.observedFamilyWorkUpperBound(1, Set.of(1)));
        assertFalse(discovery.twoOrThreeObservedFamily(17, 1, Set.of(1)));
        long[] exhausted = {0};
        assertFalse(
                JoinedLaneFamilyGate.qualifies(discovery, true, 17, WIDTH, Set.of(0), exhausted));
        assertEquals(0, exhausted[0]);
    }
}
