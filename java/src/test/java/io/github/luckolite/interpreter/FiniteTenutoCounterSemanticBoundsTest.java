// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw-run/counter controls. No score raster, font, or production-method copy. */
public class FiniteTenutoCounterSemanticBoundsTest {
    private static final int W = 1000, H = 800, G = 16;

    private static byte[] paper(int w, int h) {
        byte[] b = new byte[w * h];
        Arrays.fill(b, (byte) 255);
        return b;
    }

    private static void line(byte[] b, int w, int x0, int x1, int y, int tone) {
        for (int x = x0; x <= x1; x++) b[y * w + x] = (byte) Math.min(b[y * w + x] & 255, tone);
    }

    private static int[] mark(byte[] b, int w, int x, int y) {
        int[] core = new int[21];
        for (int dx = -10; dx <= 10; dx++) {
            int p = y * w + x + dx;
            b[p] = 0;
            core[dx + 10] = p;
        }
        return core;
    }

    private static FiniteTenutoStaffCounter.Decision inspect(
            byte[] b,
            int w,
            int h,
            int x,
            int y,
            float gap,
            List<NoteArticulationDetector.Anchor> anchors) {
        int[] core = mark(b, w, x, y);
        byte[] before = b.clone();
        var actual =
                FiniteTenutoStaffCounter.inspect(b, w, h, core, x - 10, y, x + 10, y, gap, anchors);
        assertArrayEquals("counter must not alter raw pixels", before, b);
        return actual;
    }

    private static List<NoteArticulationDetector.Anchor> tracked(StaffPitchTrack track) {
        return List.of(new NoteArticulationDetector.Anchor(500, 400, G, 0, track));
    }

    private static StaffPitchTrack track(float x0, float x1, float bottom) {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {x0, bottom, G}, new float[] {x1, bottom, G}));
    }

    @Test
    public void clippedRequiredWindowAbstains() {
        for (int[] xy : new int[][] {{50, 400}, {950, 400}, {500, 50}, {500, 750}})
            assertEquals(
                    FiniteTenutoStaffCounter.Decision.AMBIGUOUS,
                    inspect(paper(W, H), W, H, xy[0], xy[1], G, List.of()));
    }

    @Test
    public void declaredColumnBudgetAbstains() {
        assertEquals(
                FiniteTenutoStaffCounter.Decision.AMBIGUOUS,
                inspect(paper(1200, 1000), 1200, 1000, 600, 500, 65, List.of()));
    }

    @Test
    public void interruptedIndependentFamilyRemainsUncertain() {
        byte[] b = paper(W, H);
        for (int y : new int[] {304, 320, 336, 352}) {
            line(b, W, 436, 564, y, 200);
            b[y * W + 480] = (byte) 255;
            b[y * W + 520] = (byte) 255;
        }
        line(b, W, 436, 476, 368, 200);
        line(b, W, 524, 564, 368, 200);
        assertEquals(
                FiniteTenutoStaffCounter.Decision.AMBIGUOUS,
                inspect(b, W, H, 500, 368, G, List.of()));
    }

    @Test
    public void completeForeignFiveCannotBeClearedByMissingTrack() {
        for (boolean outside : new boolean[] {false, true}) {
            byte[] b = paper(W, H);
            for (int y : new int[] {304, 320, 336, 352, 368}) line(b, W, 300, 700, y, 230);
            StaffPitchTrack t = outside ? track(700, 900, 564) : null;
            assertNotEquals(
                    FiniteTenutoStaffCounter.Decision.CLEAR,
                    inspect(b, W, H, 500, 368, G, tracked(t)));
        }
    }

    @Test
    public void confirmedOwnFiveDoesNotInventSixthMember() {
        byte[] b = paper(W, H);
        for (int y : new int[] {320, 336, 352, 368, 384}) line(b, W, 300, 700, y, 200);
        assertEquals(
                FiniteTenutoStaffCounter.Decision.CLEAR,
                inspect(b, W, H, 500, 304, G, tracked(track(300, 700, 384))));
    }

    @Test
    public void clippedIndependentRawRunCannotProveClear() {
        byte[] b = paper(W, H);
        // Body400 gives ROI top319. The first independently drawn rule extends above it.
        for (int y = 318; y <= 324; y++) line(b, W, 436, 564, y, 200);
        for (int y : new int[] {343, 362, 381, 400}) line(b, W, 436, 564, y, 200);
        assertNotEquals(
                FiniteTenutoStaffCounter.Decision.CLEAR, inspect(b, W, H, 500, 400, G, List.of()));
    }
}
