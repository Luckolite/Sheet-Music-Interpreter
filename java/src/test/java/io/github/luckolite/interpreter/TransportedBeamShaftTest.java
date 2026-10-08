// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original procedural pixels; no retained page or learned image is used. */
public class TransportedBeamShaftTest {
    static final int W = 280, H = 250;
    static final float G = 12;

    record Drawing(byte[] gray, float slope, StaffPitchTrack track) {}

    static Drawing page(int paper, float slope, int railWidth) {
        byte[] pixels = new byte[W * H];
        Arrays.fill(pixels, (byte) paper);
        float bottom = 213;
        StaffPitchTrack track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, bottom - slope * W * .5f, G},
                                new float[] {W - 1, bottom + slope * (W - 1 - W * .5f), G}));
        Drawing d = new Drawing(pixels, slope, track);
        for (int x = 12; x < W - 12; x++)
            for (int line = 0; line < 5; line++) {
                int y = Math.round(track.at(x)[0] - line * G);
                for (int t = -(railWidth / 2); t <= railWidth / 2; t++)
                    ink(d, x, y + t, Math.max(20, paper - 48));
            }
        return d;
    }

    static void ink(Drawing d, int x, int y, int value) {
        if (x >= 0 && x < W && y >= 0 && y < H) d.gray[y * W + x] = (byte) value;
    }

    static void head(Drawing d, int cx, int cy) {
        for (int y = cy - 6; y <= cy + 6; y++)
            for (int x = cx - 8; x <= cx + 8; x++)
                if ((x - cx) * (x - cx) / 64f + (y - cy) * (y - cy) / 36f <= 1) ink(d, x, y, 35);
    }

    static int[] shaft(Drawing d, int cx, int cy, int end, int direction) {
        head(d, cx, cy);
        int origin = cx + (direction < 0 ? 8 : -8);
        for (int y = Math.min(cy, end); y <= Math.max(cy, end); y++) {
            int x = Math.round(origin - d.slope * (y - cy));
            for (int t = -1; t <= 1; t++) ink(d, x + t, y, 45);
        }
        return new int[] {Math.round(origin - d.slope * (end - cy)), end, direction};
    }

    static void beam(Drawing d, int[] a, int[] b, int offset, int thickness) {
        for (int x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) {
            float t = (x - a[0]) / (float) (b[0] - a[0]);
            int y = Math.round(a[1] + (b[1] - a[1]) * t + offset);
            for (int dy = -(thickness / 2); dy <= thickness / 2; dy++) ink(d, x, y + dy, 42);
        }
    }

    static int[] detected(Drawing d, int cx, int cy) {
        return TransportedBeamShaft.detect(
                d.gray, W, H, cx - 8, cx + 8, cy - 6, cy + 6, cx, cy, G, d.track);
    }

    static int count(Drawing d, int x1, int y1, int x2, int y2) {
        return PairedGraceBeamInk.countTransported(
                d.gray, W, H, detected(d, x1, y1), detected(d, x2, y2), G, 165, d.track);
    }

    static Drawing pair(int paper, float slope, int beams, int width) {
        Drawing d = page(paper, slope, width);
        int[] a = shaft(d, 90, 190, 145, -1), b = shaft(d, 118, 194, 149, -1);
        for (int i = 0; i < beams; i++) beam(d, a, b, i * 8, 5);
        return d;
    }

    @Test
    public void shadedRisingPairHasTwoOwnedCores() {
        assertEquals(2, count(pair(145, .1f, 2, 1), 90, 190, 118, 194));
    }

    @Test
    public void shadedFallingShaftPairHasTwoOwnedCores() {
        assertEquals(2, count(pair(145, -.12f, 2, 1), 90, 190, 118, 194));
    }

    @Test
    public void lightPaperPairHasTwoOwnedCores() {
        assertEquals(2, count(pair(225, .08f, 2, 1), 90, 190, 118, 194));
    }

    @Test
    public void thickStaffRulesDoNotSupplySecondBeam() {
        assertEquals(0, count(pair(145, .1f, 1, 3), 90, 190, 118, 194));
    }

    @Test
    public void singleBeamRemainsSingle() {
        assertEquals(0, count(pair(145, -.1f, 1, 1), 90, 190, 118, 194));
    }

    @Test
    public void bareShaftsHaveNoPair() {
        assertEquals(0, count(pair(145, .1f, 0, 1), 90, 190, 118, 194));
    }

    @Test
    public void returningSlurDoesNotSupplySecondRail() {
        Drawing d = pair(145, .08f, 1, 1);
        int[] a = detected(d, 90, 190), b = detected(d, 118, 194);
        assertNotNull(a);
        assertNotNull(b);
        for (int x = a[0]; x <= b[0]; x++) {
            float t = (x - a[0]) / (float) (b[0] - a[0]);
            int y = Math.round(a[1] + (b[1] - a[1]) * t - 8 - 12 * 4 * t * (1 - t));
            for (int dy = -1; dy <= 1; dy++) ink(d, x, y + dy, 42);
        }
        assertEquals(0, count(d, 90, 190, 118, 194));
    }

    @Test
    public void oppositeShaftDirectionsDoNotJoin() {
        Drawing d = page(145, .05f, 1);
        shaft(d, 90, 190, 145, -1);
        shaft(d, 118, 150, 195, 1);
        assertEquals(0, count(d, 90, 190, 118, 150));
    }

    @Test
    public void twoCompleteDirectionsRemainAmbiguous() {
        Drawing d = page(145, .05f, 1);
        shaft(d, 90, 150, 105, -1);
        shaft(d, 90, 150, 195, 1);
        assertNull(detected(d, 90, 150));
    }

    @Test
    public void stemlessWholeBodySuppliesNoShaft() {
        Drawing d = page(145, .05f, 1);
        head(d, 90, 150);
        assertNull(detected(d, 90, 150));
    }

    @Test
    public void interruptedShaftSuppliesNoEndpoint() {
        Drawing d = pair(145, .1f, 2, 1);
        for (int y = 168; y <= 177; y++) for (int x = 80; x <= 105; x++) ink(d, x, y, 145);
        assertNull(detected(d, 90, 190));
    }

    @Test
    public void shortGraceShaftSuppliesNoFullSizeEndpoint() {
        Drawing d = page(145, .05f, 1);
        shaft(d, 90, 170, 150, -1);
        assertNull(detected(d, 90, 170));
    }

    @Test
    public void unverifiedFrameSuppliesNoEndpoint() {
        Drawing d = pair(145, .1f, 2, 1);
        assertNull(
                TransportedBeamShaft.detect(
                        d.gray,
                        W,
                        H,
                        82,
                        98,
                        184,
                        196,
                        90,
                        190,
                        G,
                        StaffPitchTrack.linear(W, 213, G, .1f)));
    }

    @Test
    public void inputsAreBoundedAndPixelsNotMutated() {
        Drawing d = pair(145, .1f, 2, 1);
        byte[] before = d.gray.clone();
        assertNotNull(detected(d, 90, 190));
        assertArrayEquals(before, d.gray);
        assertNull(
                TransportedBeamShaft.detect(
                        new byte[W * H - 1], W, H, 82, 98, 184, 196, 90, 190, G, d.track));
        assertNull(
                TransportedBeamShaft.detect(d.gray, W, H, -1, 98, 184, 196, 90, 190, G, d.track));
        assertNull(
                TransportedBeamShaft.detect(
                        d.gray, W, H, 82, 98, 184, 196, Float.NaN, 190, G, d.track));
        assertNull(
                TransportedBeamShaft.detect(
                        d.gray, W, H, 82, 98, 184, 196, 90, 190, Float.NaN, d.track));
    }
}
