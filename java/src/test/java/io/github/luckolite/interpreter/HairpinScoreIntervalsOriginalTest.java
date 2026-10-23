// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Portable original score-span laws, independent of app conversion or absolute beat duration. */
public class HairpinScoreIntervalsOriginalTest {
    static ScoreDynamicChange span(int a, float x, int b, float y) {
        return new ScoreDynamicChange(a, x, 0, 1, b, y, 0, 1);
    }

    static void relation(ScoreDynamicChange a, ScoreDynamicChange b, boolean expected) {
        assertEquals(expected, a.overlapsScoreSpan(b));
        assertEquals(expected, b.overlapsScoreSpan(a));
    }

    @Test
    public void twoSeparatedWindowsInOneBarDoNotOverlap() {
        relation(span(3, .05f, 3, .3f), span(3, .6f, 4, .8f), false);
    }

    @Test
    public void touchingHalfOpenWindowsRemainSeparate() {
        relation(span(1, .1f, 1, .5f), span(1, .5f, 2, .8f), false);
    }

    @Test
    public void aRealCrossBarIntersectionStillOverlaps() {
        relation(span(0, .7f, 2, .3f), span(1, .8f, 3, .1f), true);
    }

    @Test
    public void barEndOneAndNextBarZeroAreTheSameBoundary() {
        relation(span(0, .2f, 1, 0), span(0, 1, 2, .5f), false);
    }

    @Test
    public void zeroLengthGeometryCannotCreateAWindow() {
        relation(span(0, 1, 1, 0), span(0, .1f, 2, .9f), false);
        relation(span(1, .5f, 1, .5f), span(1, .1f, 1, .9f), false);
    }

    @Test
    public void containmentIsSymmetricAndPreservesItsActualSpan() {
        relation(span(0, .1f, 4, .9f), span(1, .2f, 1, .8f), true);
    }

    @Test
    public void largeBarNumbersDoNotEraseSmallRetainedFractions() {
        int m = Integer.MAX_VALUE;
        relation(
                span(m, 0, m, Float.MIN_NORMAL),
                span(m, Float.MIN_VALUE, m, Float.MIN_NORMAL),
                true);
    }

    @Test
    public void signedZeroDoesNotInventABoundaryOverlap() {
        relation(span(0, .2f, 1, -0.0f), span(1, 0.0f, 1, .5f), false);
    }
}
