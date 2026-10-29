// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ellipse, semantic clipping and shaft, isolated at the additional-body predicate. */
public class ModelBoundaryLedgerBodyOriginalTest {
    static final int W = 240, H = 240;
    static final float GAP = 16;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page() {
            Arrays.fill(g, (byte) 255);
        }
    }

    static Page oval(boolean clipped, boolean shaft) {
        var p = new Page();
        for (int y = 114; y <= 126; y++)
            for (int x = 94; x <= 116; x++)
                if (Math.pow((x - 105) / 11., 2) + Math.pow((y - 120) / 6., 2) <= 1) {
                    p.l[y * W + x] = 2;
                    p.g[y * W + x] = 0;
                }
        if (clipped) for (int x = 94; x <= 116; x++) p.l[114 * W + x] = 0;
        if (shaft)
            for (int y = 120; y <= 172; y++) {
                if (p.l[y * W + 94] != 2) p.l[y * W + 94] = 1;
                p.g[y * W + 94] = 0;
            }
        return p;
    }

    static boolean complete(Page p) throws Exception {
        int area = 0, x0 = W, x1 = 0, y0 = H, y1 = 0;
        long sx = 0, sy = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (p.l[y * W + x] == 2) {
                    area++;
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                    sx += x;
                    sy += y;
                }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var head = ctor.newInstance(area, x0, x1, y0, y1, sx / (float) area, sy / (float) area);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "completeUpperLedgerBody",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p.g, W, H, head, GAP);
    }

    @Test
    public void singleClippedModelTipRetainsCompleteRawBody() throws Exception {
        assertTrue(complete(oval(true, true)));
    }

    @Test
    public void exactModelBodyRemainsSupported() throws Exception {
        assertTrue(complete(oval(false, true)));
    }

    @Test
    public void existingStemlessBodyRouteRemainsSupported() throws Exception {
        assertTrue(complete(oval(false, false)));
    }

    @Test
    public void newBoundaryRouteRequiresOwnedShaft() throws Exception {
        assertFalse(complete(oval(true, false)));
    }

    @Test
    public void broadInstructionStripCannotOwnBoundary() throws Exception {
        var p = oval(true, true);
        for (int x = 94; x <= 116; x++) p.g[114 * W + x] = 0;
        assertFalse(complete(p));
    }

    @Test
    public void detachedTipCannotOwnBoundary() throws Exception {
        var p = oval(true, true);
        for (int x = 94; x <= 116; x++) p.g[114 * W + x] = (byte) 255;
        p.g[114 * W + 105] = 0;
        for (int x = 104; x <= 106; x++) p.g[115 * W + x] = (byte) 255;
        assertFalse(complete(p));
    }

    @Test
    public void twoRowsOfMissingModelSupportRemainUnresolved() throws Exception {
        var p = oval(true, true);
        for (int x = 94; x <= 116; x++) p.l[115 * W + x] = 0;
        assertFalse(complete(p));
    }

    @Test
    public void bothClippedEdgesRemainUnresolved() throws Exception {
        var p = oval(true, true);
        for (int x = 94; x <= 116; x++) p.l[126 * W + x] = 0;
        assertFalse(complete(p));
    }

    @Test
    public void diagonalMirrorSupportsOneBottomTip() throws Exception {
        var p = oval(true, true);
        var q = new Page();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int xx = 210 - x, yy = 240 - y;
                if (xx >= 0 && xx < W && yy >= 0 && yy < H) {
                    q.l[yy * W + xx] = p.l[y * W + x];
                    q.g[yy * W + xx] = p.g[y * W + x];
                }
            }
        assertTrue(complete(q));
    }

    @Test
    public void antialiasedClippedTipStillRetainsBody() throws Exception {
        var p = oval(true, true);
        p.g[114 * W + 105] = (byte) 180;
        assertTrue(complete(p));
    }

    @Test
    public void translatedOnePixelBoundaryRetainsBody() throws Exception {
        var p = oval(true, true);
        var q = new Page();
        for (int y = 0; y < H - 9; y++)
            for (int x = 0; x < W - 15; x++) {
                q.l[(y + 9) * W + x + 15] = p.l[y * W + x];
                q.g[(y + 9) * W + x + 15] = p.g[y * W + x];
            }
        assertTrue(complete(q));
    }

    @Test
    public void separatedTipIslandsRemainUnresolved() throws Exception {
        var p = oval(true, true);
        for (int x = 94; x <= 116; x++) p.g[114 * W + x] = (byte) 255;
        p.g[114 * W + 102] = 0;
        p.g[114 * W + 108] = 0;
        assertFalse(complete(p));
    }

    @Test
    public void semanticBodyWithoutRawBodyCannotAcquireHead() throws Exception {
        var p = oval(true, true);
        for (int y = 115; y <= 126; y++)
            for (int x = 95; x <= 116; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(complete(p));
    }

    @Test
    public void callerRasterIsUnchanged() throws Exception {
        var p = oval(true, true);
        var g = p.g.clone();
        complete(p);
        assertArrayEquals(g, p.g);
    }
}
