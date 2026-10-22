// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometry only; no copied score pixels or model inference. */
public class FadedUpStemNoteSupportTest {
    static final int W = 320, H = 300;

    static final class Page {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];
        final int l, t, r, b;
        final float gap;

        Page(int g, boolean faded, boolean shortWhite) {
            gap = g;
            Arrays.fill(gray, (byte) 255);
            int cx = 135, cy = 190;
            l = cx - Math.round(g * .48f);
            r = cx + Math.round(g * .48f);
            t = cy - Math.round(g * .35f);
            b = cy + Math.round(g * .35f);
            for (int k = 0; k < 5; k++)
                for (int x = 30; x < 280; x++) put(x, Math.round(cy - 2.5f * g + k * g), 4, 70);
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) {
                    double q =
                            Math.pow((x - cx) / (g * .52), 2) + Math.pow((y - cy) / (g * .40), 2);
                    if (q <= 1) put(x, y, 2, 95);
                }
            int sx = r + 1;
            for (int y = cy - Math.round(3.5f * g); y <= cy; y++)
                for (int x = sx - 1; x <= sx + 1; x++) put(x, y, 1, 100);
            if (faded)
                for (int k = 1; k <= 3; k++) {
                    int y0 = cy - Math.round(k * .8f * g);
                    for (int y = y0; y < y0 + Math.round(g * .25f); y++)
                        for (int x = sx - 1; x <= sx + 1; x++) {
                            gray[y * W + x] = (byte) 230;
                            labels[y * W + x] = 1;
                        }
                }
            if (shortWhite) {
                int y0 = cy - Math.round(1.5f * g);
                for (int y = y0; y < y0 + Math.max(1, Math.round(g * .1f)); y++)
                    for (int x = sx - 1; x <= sx + 1; x++) {
                        gray[y * W + x] = (byte) 255;
                        labels[y * W + x] = 0;
                    }
            }
        }

        void put(int x, int y, int label, int v) {
            if (x < 0 || x >= W || y < 0 || y >= H) return;
            gray[y * W + x] = (byte) Math.min(gray[y * W + x] & 255, v);
            labels[y * W + x] = (byte) label;
        }

        boolean proof(int budget) {
            return FadedUpStemNoteSupport.proves(
                    labels, gray, W, H, l, t, r, b, gap, new FadedUpStemNoteSupport.Budget(budget));
        }
    }

    @Test
    public void fadedMeasuredUpShaftAcrossScales() {
        for (int g : new int[] {12, 16, 24})
            assertTrue("g=" + g, new Page(g, true, false).proof(262144));
    }

    @Test
    public void boundedWhiteInterruptionRetainsObservedEnds() {
        for (int g : new int[] {12, 16, 24})
            assertTrue("g=" + g, new Page(g, true, true).proof(262144));
    }

    @Test
    public void ordinaryContinuousStemIsAlsoSupported() {
        assertTrue(new Page(16, false, false).proof(262144));
    }

    @Test
    public void refusesBeforeBudgetAndLeavesInputExact() {
        Page p = new Page(16, true, false);
        byte[] g = p.gray.clone(), l = p.labels.clone();
        assertFalse(p.proof(0));
        assertTrue(p.proof(262144));
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }

    @Test
    public void originalDarkContinuityDoesNotExplainFadedPositive() throws Exception {
        Page p = new Page(16, true, false);
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head =
                ctor.newInstance(100, p.l, p.r, p.t, p.b, (p.l + p.r) * .5f, (p.t + p.b) * .5f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "attachedRawStem", byte[].class, int.class, int.class, c, float.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, p.gray, W, H, head, p.gap));
        assertTrue(p.proof(262144));
    }
}
