// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HairpinBoundaryOriginalTest {
    static final int W = 500, H = 220;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(80, 128, 12, 0, 1);
    static final MeasureRegion BAR = new MeasureRegion(.1f, .8f, 60f / H, 148f / H);

    static byte[] pixels(String kind) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 245);
        for (int y = 80; y <= 128; y += 12)
            for (int x = 50; x <= 406; x++) {
                g[y * W + x] = 0;
                g[(y + 1) * W + x] = 0;
            }
        int x = kind.equals("interior") ? 350 : 405;
        if (!kind.equals("none"))
            for (int y = kind.equals("short") ? 94 : 80; y <= 128; y++) g[y * W + x] = 0;
        if (kind.equals("head"))
            for (int y = 129; y <= 139; y++)
                for (int xx = x - 3; xx <= x + 3; xx++) g[y * W + xx] = 0;
        if (kind.equals("brokenRule"))
            for (int xx = 389; xx <= 401; xx++) {
                g[92 * W + xx] = (byte) 245;
                g[93 * W + xx] = (byte) 245;
            }
        if (kind.equals("sixth")) for (int xx = 50; xx <= 406; xx++) g[140 * W + xx] = 0;
        return g;
    }

    static boolean result(String kind, float end) {
        return PrintedHairpinBoundary.proved(pixels(kind), W, H, STAFF, BAR, end);
    }

    @Test
    public void completeSeparatorWithFiveRulesOwnsNearEnd() {
        assertTrue(result("bar", 388));
    }

    @Test
    public void staffRulesAloneCannotProveBoundary() {
        assertFalse(result("none", 388));
    }

    @Test
    public void shortStemCannotProveBoundary() {
        assertFalse(result("short", 388));
    }

    @Test
    public void stemEndingInHeadCannotProveBoundary() {
        assertFalse(result("head", 388));
    }

    @Test
    public void interruptedHorizontalRuleCannotProveBoundary() {
        assertFalse(result("brokenRule", 388));
    }

    @Test
    public void distantEndCannotBeSnappedToBar() {
        assertFalse(result("bar", 360));
    }

    @Test
    public void interiorSeparatorCannotBorrowRegionBoundary() {
        assertFalse(result("interior", 388));
    }

    @Test
    public void sourceGrayIsPreserved() {
        var g = pixels("bar");
        var old = g.clone();
        assertTrue(PrintedHairpinBoundary.proved(g, W, H, STAFF, BAR, 388));
        assertArrayEquals(old, g);
    }
}
