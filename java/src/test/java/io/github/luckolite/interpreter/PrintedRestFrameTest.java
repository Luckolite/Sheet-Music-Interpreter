// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedRestFrameTest {
    byte[] page(int missing) {
        byte[] g = new byte[600 * 300];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 156; y += 14)
            if (y != missing) for (int x = 30; x < 570; x++) g[y * 600 + x] = 0;
        return g;
    }

    @SuppressWarnings("unchecked")
    SixteenthRestDetector.Staff read(byte[] g, SixteenthRestDetector.Staff s) throws Exception {
        var m =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "printedRestFrames", byte[].class, int.class, int.class, List.class);
        m.setAccessible(true);
        return ((List<SixteenthRestDetector.Staff>) m.invoke(null, g, 600, 300, List.of(s))).get(0);
    }

    SixteenthRestDetector.Staff seed() {
        return new SixteenthRestDetector.Staff(101, 156, 13.75f, 0, 1);
    }

    @Test
    public void fiveRulesCorrectSmallPitchFitDifference() throws Exception {
        var s = read(page(-1), seed());
        assertEquals(100, s.top(), 0);
        assertEquals(156, s.bottom(), 0);
        assertEquals(14, s.gap(), 0);
    }

    @Test
    public void missingRuleCannotCorrectFrame() throws Exception {
        var s = seed();
        assertEquals(s, read(page(128), s));
    }

    @Test
    public void distantStaffCannotMoveSeed() throws Exception {
        var s = new SixteenthRestDetector.Staff(106, 162, 14, 0, 1);
        assertEquals(s, read(page(-1), s));
    }

    @Test
    public void curvedTrackIsPreserved() throws Exception {
        var s =
                new SixteenthRestDetector.Staff(
                        101, 156, 13.75f, 0, 1, StaffPitchTrack.linear(600, 156, 13.75f, .01f));
        assertSame(s, read(page(-1), s));
    }

    @Test
    public void absentRulesPreserveSeed() throws Exception {
        byte[] g = page(-1);
        Arrays.fill(g, (byte) 255);
        var s = seed();
        assertSame(s, read(g, s));
    }

    @Test
    public void originalRasterIsPreserved() throws Exception {
        byte[] g = page(-1), before = g.clone();
        read(g, seed());
        assertArrayEquals(before, g);
    }
}
