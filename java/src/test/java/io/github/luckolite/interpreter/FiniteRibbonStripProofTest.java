// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent whole-pixel-cell strip proof oracles, frozen before helper implementation. */
public class FiniteRibbonStripProofTest {
    static Set<Long> cells(byte[] g) {
        var s = new HashSet<Long>();
        for (int y = 0; y < 360; y++)
            for (int x = 0; x < 420; x++)
                if ((g[y * 420 + x] & 255) == 0) s.add(((long) x << 32) | (y & 0xffffffffL));
        return s;
    }

    @Test
    public void finiteRibbonsCoverEveryCellWithoutChangingTheSixBudget() {
        for (double radius : new double[] {1.25, 2.25, 2.8})
            for (boolean mirror : new boolean[] {false, true}) {
                var c = cells(FiniteNormalRibbonTest.ribbon(radius, mirror, false));
                var before = Set.copyOf(c);
                var p = FiniteSlurRibbon.certify(c, 6);
                assertTrue("Complete independently authored finite ribbon", p.complete());
                assertEquals(c.size(), p.pixelCount());
                assertEquals(c.size() * 32L, p.coveredAreaTwice());
                assertTrue(p.maxCutSquared() <= 24L * 24L);
                assertTrue(p.insideStrip());
                assertTrue(p.noncrossing());
                assertTrue(p.positiveOrientation());
                assertEquals(before, c);
            }
    }

    @Test
    public void visibleInteriorForkNeverGetsAWholeRibbonCertificate() {
        for (boolean mirror : new boolean[] {false, true})
            assertFalse(
                    FiniteSlurRibbon.certify(
                                    cells(FiniteNormalRibbonTest.ribbon(1.25, mirror, true)), 6)
                            .complete());
    }

    @Test
    public void closedHoleOrSeparateComponentCannotBeSilentlyDiscarded() {
        var s = cells(FiniteNormalRibbonTest.ribbon(1.25, false, false));
        s.add(((long) 340 << 32) | 200L);
        assertFalse(FiniteSlurRibbon.certify(s, 6).complete());
        var ring = new HashSet<Long>();
        for (int x = 100; x < 140; x++)
            for (int y = 100; y < 140; y++)
                if (x < 103 || x >= 137 || y < 103 || y >= 137) ring.add(((long) x << 32) | y);
        assertFalse(FiniteSlurRibbon.certify(ring, 6).complete());
    }

    @Test
    public void genuinelySevenPixelBodyCannotBorrowASixPixelWidth() {
        var s = new HashSet<Long>();
        for (int x = 100; x <= 150; x++)
            for (int y = 100; y <= 106; y++) s.add(((long) x << 32) | y);
        assertFalse(FiniteSlurRibbon.certify(s, 6).complete());
    }

    @Test
    public void emptyAndUndefinedBudgetAbstain() {
        assertFalse(FiniteSlurRibbon.certify(Set.of(), 6).complete());
        assertFalse(
                FiniteSlurRibbon.certify(
                                cells(FiniteNormalRibbonTest.ribbon(1.25, false, false)), 0)
                        .complete());
    }
}
