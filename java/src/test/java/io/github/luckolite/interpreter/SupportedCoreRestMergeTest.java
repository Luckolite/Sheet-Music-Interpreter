// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent stable-merge oracles: completed physical records precede additive alternatives. */
public final class SupportedCoreRestMergeTest {
    private static ScoreRestEvent rest(int m, float x, int staff, float height) {
        return new ScoreRestEvent(m, x, .4f, height, staff, 2, 2);
    }

    private static SixteenthRestDetector.Detection merge(
            SixteenthRestDetector.Detection a, SixteenthRestDetector.Detection b) throws Exception {
        Method m =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "mergeSupportedCore",
                        SixteenthRestDetector.Detection.class,
                        SixteenthRestDetector.Detection.class);
        m.setAccessible(true);
        return (SixteenthRestDetector.Detection) m.invoke(null, a, b);
    }

    @Test
    public void subpixelAlternativeCannotReplaceCompletedRest() throws Exception {
        var old = rest(4, .3f, 0, .01f);
        var alternative = rest(4, .302f, 0, .02f);
        var dot = new SixteenthRestDetector.RestDot(100, 80, old);
        var strong = new SixteenthRestDetector.Detection(List.of(old), List.of(dot));
        var extra =
                new SixteenthRestDetector.Detection(
                        List.of(alternative),
                        List.of(new SixteenthRestDetector.RestDot(101, 80, alternative)));
        assertSame(strong, merge(strong, extra));
        assertSame(old, strong.rests().get(0));
        assertSame(dot, strong.dots().get(0));
    }

    @Test
    public void newSiteInsertsWithoutRebuildingPriorRecordsOrDots() throws Exception {
        var a = rest(0, .1f, 0, .01f);
        var b = rest(4, .3f, 0, .01f);
        var novel = rest(0, .5f, 1, .01f);
        var stale = rest(4, .302f, 0, .02f);
        var oldDot = new SixteenthRestDetector.RestDot(100, 80, b);
        var newDot = new SixteenthRestDetector.RestDot(120, 80, novel);
        var out =
                merge(
                        new SixteenthRestDetector.Detection(List.of(a, b), List.of(oldDot)),
                        new SixteenthRestDetector.Detection(
                                List.of(novel, stale),
                                List.of(
                                        newDot,
                                        new SixteenthRestDetector.RestDot(101, 80, stale))));
        assertEquals(3, out.rests().size());
        assertSame(a, out.rests().get(0));
        assertSame(novel, out.rests().get(1));
        assertSame(b, out.rests().get(2));
        assertEquals(2, out.dots().size());
        assertSame(oldDot, out.dots().get(0));
        assertSame(newDot, out.dots().get(1));
    }

    @Test
    public void differentStaffIsNotACompletedPhysicalDuplicate() throws Exception {
        var old = rest(2, .4f, 0, .01f);
        var novel = rest(2, .4f, 1, .01f);
        var out =
                merge(
                        new SixteenthRestDetector.Detection(List.of(old), List.of()),
                        new SixteenthRestDetector.Detection(List.of(novel), List.of()));
        assertEquals(2, out.rests().size());
        assertSame(old, out.rests().get(0));
        assertSame(novel, out.rests().get(1));
    }

    @Test
    public void emptyRecoveryReturnsExactCompletedDetection() throws Exception {
        var old = rest(1, .2f, 0, .01f);
        var strong = new SixteenthRestDetector.Detection(List.of(old), List.of());
        assertSame(
                strong, merge(strong, new SixteenthRestDetector.Detection(List.of(), List.of())));
    }
}
