// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original generated hanging rectangles and fading rule shoulders; no score-derived geometry. */
public class FadingHangingRestRuleEdgeTest {
    static final int W = 400, H = 200;

    byte[] page(int shoulder, int rowCount) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 245);
        for (int y = 50; y <= 114; y += 16) box(g, 20, y, 380, y, 0);
        box(g, 100, 67, 119, 74, 0);
        for (int y = 67; y < 67 + rowCount; y++) {
            box(g, 97, y, 99, y, shoulder);
            box(g, 120, y, 122, y, shoulder);
        }
        return g;
    }

    static void box(byte[] g, int l, int t, int r, int b, int tone) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) tone;
    }

    int[] find(byte[] g) {
        int[] ink = new int[66];
        for (int y = 54; y <= 119; y++)
            for (int x = 100; x <= 119; x++)
                if (Math.abs(y - (50 + Math.round((y - 50) / 16f) * 16)) > 3
                        && (g[y * W + x] & 255) < 170) ink[y - 54]++;
        return HalfRestRuleBody.hanging(g, W, H, 50, 16, 100, 119, 54, ink);
    }

    @Test
    public void fadingSupportedShouldersPreserveHangingBody() {
        assertArrayEquals(new int[] {67, 74}, find(page(140, 1)));
    }

    @Test
    public void darkBodyExpansionIsNotARuleFringe() {
        assertNull(find(page(0, 1)));
    }

    @Test
    public void twoRowsCannotUseFirstRowAllowance() {
        assertNull(find(page(140, 2)));
    }

    @Test
    public void detachedRectangleRemainsRejected() {
        byte[] g = page(140, 1);
        box(g, 100, 67, 119, 68, 245);
        assertNull(find(g));
    }

    @Test
    public void lostSupportingRuleRemainsRejected() {
        byte[] g = page(140, 1);
        box(g, 20, 66, 99, 66, 245);
        box(g, 120, 66, 380, 66, 245);
        assertNull(find(g));
    }

    @Test
    public void unrelatedInkStillRejects() {
        byte[] g = page(140, 1);
        box(g, 105, 103, 109, 105, 0);
        assertNull(find(g));
    }

    @Test
    public void recoveredRestKeepsLiteralFourBeats() {
        var rests =
                SixteenthRestDetector.detect(
                        page(140, 1),
                        W,
                        H,
                        List.of(new MeasureRegion(0, 1, .1f, .9f)),
                        List.of(new SixteenthRestDetector.Staff(50, 114, 16, 0, 1)),
                        List.of());
        assertEquals(1, rests.size());
        assertEquals(4, rests.get(0).durationBeats(), 0);
        assertEquals(ScoreRestEvent.Kind.LITERAL, rests.get(0).kind());
    }
}
