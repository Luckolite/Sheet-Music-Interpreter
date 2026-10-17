// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Defensive contracts of the new bounded view; baseline behavior is covered separately. */
public class QuarterRestRuleContrastBoundsTest {
    static QuarterRestRuleContrast.View view(
            RuleCrossingQuarterRestTest.Page p,
            byte[] g,
            int w,
            int h,
            int l,
            int r,
            int first,
            int last,
            int top,
            boolean[] mask) {
        return QuarterRestRuleContrast.view(g, w, h, p.staff, l, r, first, last, top, mask);
    }

    @Test
    public void absentRasterFailsClosed() {
        var p = RuleCrossingQuarterRestTest.normal();
        assertNull(view(p, null, 260, 220, 130, p.right, p.first, p.last, 0, p.lines));
    }

    @Test
    public void overflowingDimensionsFailClosed() {
        var p = RuleCrossingQuarterRestTest.normal();
        assertNull(
                view(
                        p,
                        p.gray,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        130,
                        p.right,
                        p.first,
                        p.last,
                        0,
                        p.lines));
    }

    @Test
    public void endpointOutsideImageFailsClosed() {
        var p = RuleCrossingQuarterRestTest.normal();
        assertNull(view(p, p.gray, 260, 220, -1, p.right, p.first, p.last, 0, p.lines));
        assertNull(view(p, p.gray, 260, 220, 130, 260, p.first, p.last, 0, p.lines));
    }

    @Test
    public void maskOutsideImageFailsClosed() {
        var p = RuleCrossingQuarterRestTest.normal();
        assertNull(view(p, p.gray, 260, 220, 130, p.right, p.first, p.last, 1, p.lines));
    }

    @Test
    public void nonfiniteFrameFailsClosed() {
        var p = RuleCrossingQuarterRestTest.normal();
        p.staff = new SixteenthRestDetector.Staff(Float.NaN, 108, 12, 0, 1);
        assertNull(view(p, p.gray, 260, 220, 130, p.right, p.first, p.last, 0, p.lines));
    }
}
