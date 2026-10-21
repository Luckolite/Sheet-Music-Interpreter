// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent finite curved stroke and offset round body; no private pixels or source geometry. */
public class TerminalContactPartitionTest {
    static final int W = 240, H = 130;

    static byte[] scene(boolean dot, boolean mirror, boolean branch, boolean beam) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 50; x <= 180; x++) {
            double z = (x - 50) / 130.0;
            int center = beam ? 64 : (int) Math.round(64 - 14 * 4 * z * (1 - z));
            int thick = 2 + (int) Math.round(2 * Math.sin(Math.PI * z));
            for (int y = center - thick / 2; y < center - thick / 2 + thick; y++) g[y * W + x] = 0;
        }
        if (dot)
            for (int y = 64; y <= 74; y++)
                for (int x = 175; x <= 185; x++)
                    if ((x - 180) * (x - 180) + (y - 69) * (y - 69) <= 20.25) g[y * W + x] = 0;
        if (branch) for (int n = 0; n < 22; n++) g[(60 - n) * W + 165 - n] = 0;
        if (mirror) {
            byte[] copy = g.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) g[(H - 1 - y) * W + x] = copy[y * W + x];
        }
        return g;
    }

    static List<NoteArticulationDetector.Anchor> owners(boolean mirror) {
        float y = mirror ? H - 1 - 95 : 95;
        return List.of(
                new NoteArticulationDetector.Anchor(50, y, 20, 0),
                new NoteArticulationDetector.Anchor(180, y, 20, 0));
    }

    static int[] component(byte[] g) {
        return TerminalBodyRasterTest.component(g);
    }

    static SlurEndpointDots.Result check(
            byte[] g, boolean mirror, List<NoteArticulationDetector.Anchor> notes) {
        byte[] copy = g.clone();
        int[] p = component(g), old = p.clone();
        var r = SlurEndpointDots.partition(g, W, H, p, notes);
        assertArrayEquals(copy, g);
        assertArrayEquals(old, p);
        return r;
    }

    @Test
    public void upperCurveAndLowerDotShareTerminalColumns() {
        var r = check(scene(true, false, false, false), false, owners(false));
        assertFalse(r.limited());
        assertEquals(1, r.dots().size());
        assertEquals(1, r.dots().get(0).owner());
    }

    @Test
    public void mirroredLowerCurveAndUpperDotRemainComplete() {
        var r = check(scene(true, true, false, false), true, owners(true));
        assertFalse(r.limited());
        assertEquals(1, r.dots().size());
        assertEquals(1, r.dots().get(0).owner());
    }

    @Test
    public void bareRoundedCurveCannotDonateDot() {
        assertTrue(check(scene(false, false, false, false), false, owners(false)).dots().isEmpty());
    }

    @Test
    public void unrelatedStraightBeamCannotBeCurve() {
        assertTrue(check(scene(true, false, false, true), false, owners(false)).dots().isEmpty());
    }

    @Test
    public void foreignConnectedBranchCannotDisappear() {
        assertTrue(check(scene(true, false, true, false), false, owners(false)).dots().isEmpty());
    }

    @Test
    public void genuineAcceptedHeadIsNotADot() {
        var a = new ArrayList<>(owners(false));
        a.add(new NoteArticulationDetector.Anchor(180, 69, 20, 0));
        assertTrue(check(scene(true, false, false, false), false, a).dots().isEmpty());
    }

    @Test
    public void croppedContactCannotManufactureClosure() {
        byte[] g = scene(true, false, false, false);
        int[] p = component(g);
        assertTrue(
                SlurEndpointDots.partition(g, W, H, Arrays.copyOf(p, p.length - 1), owners(false))
                        .dots()
                        .isEmpty());
    }
}
