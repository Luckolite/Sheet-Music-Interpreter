// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated rectangle-edge controls, independent of retained score pixels. */
public class RoundedWholeRestEdgeTest {
    static final int W = 400, H = 240;

    static void box(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = 0;
    }

    static byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 70; y <= 126; y += 14) box(g, 10, y, 390, y);
        box(g, 191, 85, 208, 89);
        box(g, 192, 90, 206, 90);
        return g;
    }

    static List<WholeRestClassifier.Decision> read(byte[] g) {
        return WholeRestClassifier.detect(
                g,
                W,
                H,
                List.of(new MeasureRegion(.05f, .95f, .2f, .6f)),
                List.of(new SixteenthRestDetector.Staff(70, 126, 14, 0, 1)),
                List.of(),
                List.of());
    }

    @Test
    public void oneInsetCornerRowPreservesFullMeasureIdentity() {
        var d = read(page());
        assertEquals(1, d.size());
        assertTrue(d.get(0).event().isFullMeasure());
        assertEquals(3, d.get(0).event().resolvedDurationBeats(3), 0);
    }

    @Test
    public void missingInteriorPixelDoesNotBecomeRoundedEdge() {
        byte[] g = page();
        g[90 * W + 199] = (byte) 255;
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void longTaperDoesNotBecomeRectangle() {
        byte[] g = page();
        box(g, 193, 91, 205, 91);
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void narrowStemTerminalDoesNotBecomeRoundedEdge() {
        byte[] g = page();
        for (int x = 192; x <= 206; x++) g[90 * W + x] = (byte) 255;
        box(g, 197, 90, 201, 90);
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void detachedInkBelowBodyStillRejects() {
        byte[] g = page();
        box(g, 193, 92, 205, 92);
        assertTrue(read(g).isEmpty());
    }
}
