// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural pixels, independent of any scanned score. */
public class LocalWholeRestRuleTest {
    static final int W = 800, H = 360, T = 100, G = 16;

    static void box(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = 0;
    }

    static byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int n = 0; n < 5; n++) box(g, 20, T + n * G, 779, T + n * G);
        return g;
    }

    static void plate(byte[] g, int x, boolean edge) {
        box(g, x - 12, 117, x + 12, 123);
        if (edge) box(g, x - 12, 117, x + 64, 117);
    }

    static List<WholeRestClassifier.Decision> read(byte[] g) {
        return WholeRestClassifier.detect(
                g,
                W,
                H,
                List.of(new MeasureRegion(.025f, .97375f, .15f, .7f)),
                List.of(new SixteenthRestDetector.Staff(T, T + 4 * G, G, 0, 1)),
                List.of(),
                List.of());
    }

    @Test
    public void partialRuleTailPreservesCenteredWholeRest() {
        var g = page();
        plate(g, 400, true);
        var d = read(g);
        assertEquals(1, d.size());
        assertTrue(d.get(0).event().isFullMeasure());
    }

    @Test
    public void leftTailAlsoPreservesWholeRest() {
        var g = page();
        plate(g, 400, false);
        box(g, 330, 117, 412, 117);
        assertTrue(read(g).get(0).event().isFullMeasure());
    }

    @Test
    public void ordinaryBodyIsNotRepeatedAtEachSearchRow() {
        var g = page();
        plate(g, 400, false);
        assertEquals(1, read(g).size());
    }

    @Test
    public void differentEdgeDepthsKeepBothPlates() {
        var g = page();
        plate(g, 220, false);
        plate(g, 580, true);
        assertEquals(2, read(g).size());
    }

    @Test
    public void missingAttachmentCannotBeSkipped() {
        var g = page();
        plate(g, 400, true);
        for (int x = 388; x <= 412; x++) g[117 * W + x] = (byte) 255;
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void taperIsNotAStaffEdge() {
        var g = page();
        plate(g, 400, false);
        box(g, 382, 117, 418, 117);
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void distantDetachedRectangleIsNotWholeRest() {
        var g = page();
        box(g, 388, 120, 412, 126);
        assertTrue(read(g).isEmpty());
    }

    @Test
    public void pixelsRemainUnchanged() {
        var g = page();
        plate(g, 400, true);
        var old = g.clone();
        read(g);
        assertArrayEquals(old, g);
    }

    @Test
    public void twoLocalWitnessesRepairNearbyPhase() {
        var a = WholeRestClassifier.localRules(page(), W, H, 20, 779, 105, 15);
        assertNotNull(a);
        assertEquals(100, a[0], 1);
        assertEquals(16, a[1], .5);
    }

    @Test
    public void largePhaseDifferenceIsRejected() {
        assertNull(WholeRestClassifier.localRules(page(), W, H, 20, 779, 115, 16));
    }

    @Test
    public void singleWitnessCannotAuthorizePhase() {
        var g = page();
        for (int y = 0; y < H; y++) Arrays.fill(g, y * W + 450, (y + 1) * W, (byte) 255);
        assertNull(WholeRestClassifier.localRules(g, W, H, 20, 779, 105, 15));
    }

    @Test
    public void conflictingWitnessesAreRejected() {
        var g = page();
        for (int y = 0; y < H; y++) Arrays.fill(g, y * W + 450, (y + 1) * W, (byte) 255);
        for (int n = 0; n < 5; n++) box(g, 450, T + 5 + n * G, 779, T + 5 + n * G);
        assertNull(WholeRestClassifier.localRules(g, W, H, 20, 779, 105, 15));
    }
}
