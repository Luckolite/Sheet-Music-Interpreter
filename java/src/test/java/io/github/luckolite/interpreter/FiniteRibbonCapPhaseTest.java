// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Full pixel squares have a unique cap midpoint at both edge and vertex phases. */
public class FiniteRibbonCapPhaseTest {
    static Set<Long> rectangle(int rows, int dx, int dy) {
        var c = new HashSet<Long>();
        for (int x = 20; x <= 70; x++)
            for (int y = 30; y < 30 + rows; y++) c.add(FiniteSlurRibbon.pixel(x + dx, y + dy));
        return c;
    }

    static Set<Long> bowl(boolean below) {
        var c = new HashSet<Long>();
        for (int x = 20; x <= 100; x++) {
            double t = (x - 20) / 80.;
            int y = (int) Math.floor(80 - 48 * t * (1 - t));
            for (int yy = y; yy < y + 4; yy++)
                c.add(FiniteSlurRibbon.pixel(x, below ? 200 - yy : yy));
        }
        return c;
    }

    static void complete(Set<Long> cells) {
        var copy = Set.copyOf(cells);
        var p = FiniteSlurRibbon.certify(cells, 6);
        assertTrue(p.toString(), p.complete());
        assertFalse(p.limitReached());
        assertEquals(cells.size() * 32L, p.coveredAreaTwice());
        assertTrue(p.maxCutSquared() <= 576);
        assertTrue(p.insideStrip() && p.noncrossing() && p.positiveOrientation());
        assertEquals(copy, cells);
    }

    @Test
    public void twoPixelCapHasExactVertexMidpoint() {
        complete(rectangle(2, 0, 0));
    }

    @Test
    public void fourPixelCapHasExactVertexMidpoint() {
        complete(rectangle(4, 0, 0));
    }

    @Test
    public void sixPixelCapKeepsExactWidthBudget() {
        complete(rectangle(6, 0, 0));
    }

    @Test
    public void translatedCapRetainsWholeArea() {
        complete(rectangle(4, 93, 117));
    }

    @Test
    public void negativeCoordinateCapRetainsWholeArea() {
        complete(rectangle(4, -150, -100));
    }

    @Test
    public void upperReturningEvenCapRetainsAllCells() {
        complete(bowl(false));
    }

    @Test
    public void lowerReturningEvenCapRetainsAllCells() {
        complete(bowl(true));
    }

    @Test
    public void oddPhaseOldCapStillCloses() {
        complete(rectangle(3, 0, 0));
    }

    @Test
    public void trueEightPixelBodyCannotBorrowSix() {
        assertFalse(FiniteSlurRibbon.certify(rectangle(8, 0, 0), 6).complete());
    }

    @Test
    public void separateForeignPixelCannotBeIgnored() {
        var c = rectangle(4, 0, 0);
        c.add(FiniteSlurRibbon.pixel(90, 45));
        assertFalse(FiniteSlurRibbon.certify(c, 6).complete());
    }

    @Test
    public void closedInteriorHoleCannotBeIgnored() {
        var c = rectangle(6, 0, 0);
        c.remove(FiniteSlurRibbon.pixel(45, 32));
        assertFalse(FiniteSlurRibbon.certify(c, 6).complete());
    }

    @Test
    public void forkCannotUseAConvenientEndPhase() {
        var c = rectangle(4, 0, 0);
        for (int y = 34; y <= 50; y++) c.add(FiniteSlurRibbon.pixel(45, y));
        assertFalse(FiniteSlurRibbon.certify(c, 6).complete());
    }
}
