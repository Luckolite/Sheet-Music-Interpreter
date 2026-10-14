// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AngularFermataRoofTest {
    static final int W = 200, H = 300;

    static byte[] drawing(boolean curved, boolean inverted, int radius, int depth) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 98; y <= 102; y++) for (int x = 98; x <= 102; x++) g[y * W + x] = 0;
        for (int x = 100 - radius; x <= 100 + radius; x++) {
            double a = Math.abs(x - 100) / (double) radius;
            int y = 78 + (int) Math.round(depth * (curved ? a * a : a));
            g[y * W + x] = 0;
            g[(y + 1) * W + x] = 0;
        }
        if (inverted) {
            byte[] old = g.clone();
            for (int y = 0; y < H; y++) System.arraycopy(old, y * W, g, (H - 1 - y) * W, W);
        }
        return g;
    }

    static int count(boolean curved, boolean inverted, int radius, int depth) {
        return NoteArticulationDetector.fermataMarks(
                        new byte[W * H],
                        drawing(curved, inverted, radius, depth),
                        W,
                        H,
                        List.of(
                                new NoteArticulationDetector.Anchor(
                                        100, inverted ? H - 1 - 130 : 130, 16, 0)))
                .size();
    }

    @Test
    public void upwardMarcatoOverDotIsNotFermata() {
        assertEquals(0, count(false, false, 8, 13));
    }

    @Test
    public void downwardMarcatoUnderDotIsNotFermata() {
        assertEquals(0, count(false, true, 8, 13));
    }

    @Test
    public void tallRoundedRoofRemainsFermata() {
        assertEquals(1, count(true, false, 8, 13));
    }

    @Test
    public void invertedTallRoundedRoofRemainsFermata() {
        assertEquals(1, count(true, true, 8, 13));
    }

    @Test
    public void wideRoundedRoofRemainsFermata() {
        assertEquals(1, count(true, false, 12, 8));
    }
}
