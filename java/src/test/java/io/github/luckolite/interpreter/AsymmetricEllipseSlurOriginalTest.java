// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic asymmetric arches and independently drawn impostors. */
public class AsymmetricEllipseSlurOriginalTest {
    static final int W = 960, H = 460;
    static final float G = 12;

    byte[] page(int mode, boolean lower, boolean mirror) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int px = -1, py = -1;
        for (int x = 80; x <= 880; x++) {
            double t = 2.0 * (x - 80) / 800 - 1;
            if (mirror) t = -t;
            double root = Math.sqrt(Math.max(0, 1 - t * t));
            double y;
            switch (mode) {
                case 0:
                    y = 200 + 12 * t - 72 * root - 24 * t * root;
                    break;
                case 1:
                    y = 180 + 20 * t;
                    break;
                case 2:
                    y = 140 + 64 * Math.abs(t);
                    break;
                case 3:
                    y = 200 + 48 * t * t * t;
                    break;
                case 4:
                    y = 200 - 24 * root - 48 * t * root;
                    break;
                default:
                    throw new AssertionError();
            }
            if (lower) y = 440 - y;
            int yy = (int) Math.round(y);
            if (px >= 0) line(g, px, py, x, yy);
            else g[yy * W + x] = 0;
            px = x;
            py = yy;
        }
        return g;
    }

    void line(byte[] g, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0),
                sx = x0 < x1 ? 1 : -1,
                dy = -Math.abs(y1 - y0),
                sy = y0 < y1 ? 1 : -1,
                err = dx + dy;
        while (true) {
            g[y0 * W + x0] = 0;
            if (x0 == x1 && y0 == y1) break;
            int e = 2 * err;
            if (e >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    List<PrintedSlurContours.Bow> find(byte[] g) {
        return PrintedSlurContours.find(
                        g,
                        null,
                        W,
                        H,
                        0,
                        10,
                        W - 1,
                        H - 1,
                        StaffPitchTrack.fromVerifiedSamples(
                                List.of(new float[] {0, 380, G}, new float[] {W - 1, 380, G})))
                .bows();
    }

    void positive(boolean lower, boolean mirror) {
        var bows = find(page(0, lower, mirror));
        assertEquals(1, bows.size());
        var b = bows.get(0);
        assertEquals(80, b.start().x());
        assertEquals(880, b.end().x());
        assertEquals(!lower, b.above());
        assertTrue(b.rmsInGaps() <= .15);
        assertTrue(b.bendInGaps() <= 12);
    }

    @Test
    public void smoothAsymmetricUpperArch() {
        positive(false, false);
    }

    @Test
    public void asymmetricArchWithOppositeLongSide() {
        positive(false, true);
    }

    @Test
    public void lowerAsymmetricArch() {
        positive(true, false);
    }

    @Test
    public void mirroredLowerAsymmetricArch() {
        positive(true, true);
    }

    @Test
    public void straightRailRemainsRejected() {
        assertTrue(find(page(1, false, false)).isEmpty());
    }

    @Test
    public void hingedHairpinRemainsRejected() {
        assertTrue(find(page(2, false, false)).isEmpty());
    }

    @Test
    public void inflectedCubicRemainsRejected() {
        assertTrue(find(page(3, false, false)).isEmpty());
    }

    @Test
    public void inflectedAsymmetricImpostorRemainsRejected() {
        assertTrue(find(page(4, false, false)).isEmpty());
    }
}
