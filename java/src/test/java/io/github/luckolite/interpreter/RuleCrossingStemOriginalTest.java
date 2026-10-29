// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric shaft, head and antialiased rules; no source score pixels. */
public class RuleCrossingStemOriginalTest {
    static final int W = 260, H = 300, X = 120, TOP = 200, BOTTOM = 254;
    static final float GAP = 13.5f;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page() {
            Arrays.fill(g, (byte) 255);
        }

        void rect(int x, int y, int w, int h, int label, int tone) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    l[yy * W + xx] = (byte) label;
                    g[yy * W + xx] = (byte) tone;
                }
        }
    }

    static Page scene(boolean aa) {
        var p = new Page();
        p.rect(X, 34, 2, 221, 1, 0);
        p.rect(X, 25, 18, 10, 2, 0);
        for (int y : new int[] {89, 103, 117}) {
            p.rect(70, y, 101, 1, 4, aa ? 5 : 0);
            p.rect(X, y, 2, 1, 1, aa ? 3 : 0);
        }
        return p;
    }

    static boolean attached(Page p) throws Exception {
        return attached(p, X, TOP, BOTTOM);
    }

    static boolean attached(Page p, int x, int top, int bottom) throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "distantHeadOnSameStem",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p.l, p.g, W, H, x, top, bottom, GAP);
    }

    @Test
    public void fullShaftSurvivesAntialiasedRuleCrossings() throws Exception {
        assertTrue(attached(scene(true)));
    }

    @Test
    public void blackRuleCrossingRemainsOwned() throws Exception {
        assertTrue(attached(scene(false)));
    }

    @Test
    public void semanticBridgeAcrossPaperCannotOwnBar() throws Exception {
        var p = scene(true);
        for (int y = 130; y < 170; y++)
            for (int x = X; x <= X + 1; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(attached(p));
    }

    @Test
    public void bareBarHasNoHeadOwner() throws Exception {
        var p = scene(true);
        p.rect(X, 25, 18, 10, 0, 255);
        assertFalse(attached(p));
    }

    @Test
    public void nearbyHeadWithoutShaftContactCannotOwnBar() throws Exception {
        var p = scene(true);
        p.rect(X, 25, 18, 10, 0, 255);
        p.rect(X + 8, 25, 18, 10, 2, 0);
        assertFalse(attached(p));
    }

    @Test
    public void secondAntialiasToneStillOwnsShaft() throws Exception {
        var p = scene(true);
        for (int y : new int[] {89, 103, 117}) {
            p.rect(70, y, 101, 1, 4, 14);
            p.rect(X, y, 2, 1, 1, 8);
        }
        assertTrue(attached(p));
    }

    @Test
    public void horizontalTranslationPreservesOwnership() throws Exception {
        var p = scene(true);
        var q = new Page();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W - 7; x++) {
                q.l[y * W + x + 7] = p.l[y * W + x];
                q.g[y * W + x + 7] = p.g[y * W + x];
            }
        assertTrue(attached(q, X + 7, TOP, BOTTOM));
    }

    @Test
    public void verticallyReflectedShaftRetainsOwnership() throws Exception {
        var p = scene(true);
        var q = new Page();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                q.l[(H - 1 - y) * W + x] = p.l[y * W + x];
                q.g[(H - 1 - y) * W + x] = p.g[y * W + x];
            }
        assertTrue(attached(q, X, H - 1 - BOTTOM, H - 1 - TOP));
    }

    @Test
    public void callerPixelsRemainUnchanged() throws Exception {
        var p = scene(true);
        var l = p.l.clone();
        var g = p.g.clone();
        attached(p);
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}
