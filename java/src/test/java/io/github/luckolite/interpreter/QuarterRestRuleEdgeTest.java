// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original row profiles and procedural compact-rest drawings; no score-derived pixels. */
public class QuarterRestRuleEdgeTest {
    private int[] ink() {
        int[] a = new int[80];
        for (int y = 8; y <= 49; y++) a[y] = 6;
        a[60] = 3;
        return a;
    }

    private boolean[] rules() {
        boolean[] a = new boolean[80];
        a[58] = a[59] = true;
        return a;
    }

    private int end(int[] a, boolean[] r) {
        return SixteenthRestDetector.quarterTailWithoutRuleEdge(a, r, 0, 8, 60, 16, 58);
    }

    @Test
    public void detachedNarrowRuleRemnant() {
        assertEquals(49, end(ink(), rules()));
    }

    @Test
    public void needsAdjacentProvedRule() {
        assertEquals(60, end(ink(), new boolean[80]));
    }

    @Test
    public void broadInkCannotBeDiscarded() {
        var a = ink();
        a[60] = 5;
        assertEquals(60, end(a, rules()));
    }

    @Test
    public void connectedInkCannotBeDiscarded() {
        var a = ink();
        for (int y = 50; y < 60; y++) a[y] = 1;
        assertEquals(60, end(a, rules()));
    }

    @Test
    public void closeDetachedInkRemains() {
        var a = ink();
        a[55] = 1;
        assertEquals(60, end(a, rules()));
    }

    @Test
    public void needsBottomRulePosition() {
        assertEquals(
                60,
                SixteenthRestDetector.quarterTailWithoutRuleEdge(ink(), rules(), 0, 8, 60, 16, 48));
    }

    @Test
    public void shortBodyCannotSupplyQuarter() {
        var a = ink();
        for (int y = 8; y < 35; y++) a[y] = 0;
        assertEquals(
                60,
                SixteenthRestDetector.quarterTailWithoutRuleEdge(a, rules(), 0, 35, 60, 16, 58));
    }

    @Test
    public void inputsRemainUnchanged() {
        var a = ink();
        var b = a.clone();
        var r = rules();
        var q = r.clone();
        end(a, r);
        assertArrayEquals(b, a);
        assertArrayEquals(q, r);
    }

    @Test
    public void originalQuarterWithRuleRemnant() {
        var p = new CompactQuarterRestTest.Page(16, true, true, false, false);
        p.gray[165 * CompactQuarterRestTest.W + 154] = 0;
        p.gray[165 * CompactQuarterRestTest.W + 155] = 0;
        p.gray[165 * CompactQuarterRestTest.W + 156] = 0;
        var r = p.detect();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(1, r.get(0).durationBeats(), 0);
    }

    @Test
    public void ruleRemnantCannotSupplyMissingHook() {
        var p = new CompactQuarterRestTest.Page(16, false, true, false, false);
        for (int x = 154; x <= 156; x++) p.gray[165 * CompactQuarterRestTest.W + x] = 0;
        assertTrue(p.detect().isEmpty());
    }
}
