// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored two-dimensional end bulbs with a varying raster projection of a finite bow. */
public class TerminalBodyRasterTest {
    static final int W = 240, H = 130;

    static byte[] raster(boolean bulbs, boolean variable, boolean branch) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 50; x <= 180; x++) {
            double t = (x - 50) / 130.0;
            int center = (int) Math.round(64 - 14 * 4 * t * (1 - t));
            int thick = variable ? 2 + (int) Math.round(2 * Math.sin(Math.PI * t)) : 2;
            for (int y = center - thick / 2; y < center - thick / 2 + thick; y++)
                gray[y * W + x] = 0;
        }
        if (bulbs)
            for (int cx : new int[] {50, 180})
                for (int y = 62; y <= 72; y++)
                    for (int x = cx - 5; x <= cx + 5; x++)
                        if ((x - cx) * (x - cx) + (y - 67) * (y - 67) <= 20.25) gray[y * W + x] = 0;
        if (branch) for (int n = 0; n < 22; n++) gray[(66 - n) * W + 50 - n] = 0;
        return gray;
    }

    static int[] component(byte[] gray) {
        int first = -1;
        for (int p = 0; p < gray.length; p++)
            if ((gray[p] & 255) < 155) {
                first = p;
                break;
            }
        boolean[] seen = new boolean[gray.length];
        int[] q = new int[gray.length];
        int read = 0, end = 1;
        q[0] = first;
        seen[first] = true;
        while (read < end) {
            int p = q[read++], x = p % W, y = p / W;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
                    int k = yy * W + xx;
                    if (!seen[k] && (gray[k] & 255) < 155) {
                        seen[k] = true;
                        q[end++] = k;
                    }
                }
        }
        int total = 0;
        for (byte b : gray) if ((b & 255) < 155) total++;
        assertEquals("Full authored bow and bulbs form one component", total, end);
        return Arrays.copyOf(q, end);
    }

    static List<NoteArticulationDetector.Anchor> owners() {
        return List.of(
                new NoteArticulationDetector.Anchor(50, 95, 20, 0),
                new NoteArticulationDetector.Anchor(180, 95, 20, 0));
    }

    static SlurEndpointDots.Result check(
            byte[] gray, int[] pixels, List<NoteArticulationDetector.Anchor> notes) {
        byte[] old = gray.clone();
        int[] before = pixels.clone();
        var result = SlurEndpointDots.partition(gray, W, H, pixels, notes);
        assertArrayEquals(old, gray);
        assertArrayEquals(before, pixels);
        return result;
    }

    @Test
    public void exactTwoDimensionalBulbsSurviveSplitColumnProjection() {
        byte[] gray = raster(true, false, false);
        int[] pixels = component(gray);
        boolean split = false;
        for (int x = 46; x <= 184; x++) {
            int runs = 0;
            boolean ink = false;
            for (int y = 0; y < H; y++) {
                boolean on = (gray[y * W + x] & 255) < 155;
                if (on && !ink) runs++;
                ink = on;
            }
            if (runs > 1) split = true;
        }
        assertTrue("Original has split vertical runs; no pixels were manufactured", split);
        var r = check(gray, pixels, owners());
        assertFalse(r.limited());
        assertEquals(2, r.dots().size());
    }

    @Test
    public void varyingThinConnectorStillOwnsEverySample() {
        byte[] gray = raster(true, true, false);
        var r = check(gray, component(gray), owners());
        assertFalse(r.limited());
        assertEquals(2, r.dots().size());
    }

    @Test
    public void varyingBareBowCannotDonateDots() {
        byte[] g = raster(false, true, false);
        assertTrue(check(g, component(g), owners()).dots().isEmpty());
    }

    @Test
    public void extraTerminalBranchRemainsUnknown() {
        byte[] g = raster(true, true, true);
        assertTrue(check(g, component(g), owners()).dots().isEmpty());
    }

    @Test
    public void croppedComponentCannotManufactureBody() {
        byte[] g = raster(true, true, false);
        int[] p = component(g);
        assertTrue(check(g, Arrays.copyOf(p, p.length - 1), owners()).dots().isEmpty());
    }

    @Test
    public void acceptedHeadInsideComponentCannotBecomeDot() {
        byte[] g = raster(true, true, false);
        var a = new ArrayList<>(owners());
        a.add(new NoteArticulationDetector.Anchor(180, 67, 20, 0));
        assertTrue(check(g, component(g), a).dots().isEmpty());
    }

    @Test
    public void foreignStaffCannotShareConnector() {
        byte[] g = raster(true, true, false);
        assertTrue(
                check(
                                g,
                                component(g),
                                List.of(
                                        owners().get(0),
                                        new NoteArticulationDetector.Anchor(180, 95, 20, 1)))
                        .dots()
                        .isEmpty());
    }
}
