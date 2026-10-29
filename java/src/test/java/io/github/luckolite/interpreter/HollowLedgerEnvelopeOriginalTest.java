// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic hollow ellipse, two antialiased model tips, shaft and short ledger. */
public class HollowLedgerEnvelopeOriginalTest {
    static final int W = 180, H = 360;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];
        float gap = 13.75f;

        Page() {
            Arrays.fill(g, (byte) 255);
        }
    }

    static Page scene() {
        var p = new Page();
        for (int y = 52; y <= 68; y++)
            for (int x = 50; x <= 70; x++) {
                double dx = x - 60, dy = y - 60;
                boolean body = Math.pow(dx / 10., 2) + Math.pow((dy + .5 * dx) / 7., 2) <= 1;
                if (body) {
                    p.l[y * W + x] = 2;
                    p.g[y * W + x] =
                            (byte)
                                    (Math.pow(dx / 7., 2) + Math.pow((dy + .5 * dx) / 4., 2) < 1
                                            ? 255
                                            : 0);
                }
            }
        for (int x = 63; x <= 65; x++) p.g[51 * W + x] = (byte) 190;
        for (int x = 55; x <= 57; x++) p.g[69 * W + x] = (byte) 190;
        for (int y = 66; y <= 68; y++)
            for (int x = 42; x <= 78; x++) {
                p.g[y * W + x] = (byte) 140;
                if (p.l[y * W + x] != 2) p.l[y * W + x] = 5;
            }
        for (int y = 60; y <= 130; y++) {
            p.g[y * W + 50] = 0;
            if (p.l[y * W + 50] != 2) p.l[y * W + 50] = 1;
        }
        return p;
    }

    static boolean body(Page p) throws Exception {
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
        return (boolean) m.invoke(null, p.g, W, H, head, p.gap);
    }

    @Test
    public void completeHollowEnvelopeSurvivesTwoModelTipsAndLedger() throws Exception {
        assertTrue(body(scene()));
    }

    @Test
    public void roundedPixelCenterExtentJustInsideHalfPixelIsEligible() throws Exception {
        var p = scene();
        p.gap = (18 - .49f) / 1.3f;
        assertTrue(body(p));
    }

    @Test
    public void roundedPixelCenterExtentJustOutsideHalfPixelAbstains() throws Exception {
        var p = scene();
        p.gap = (18 - .51f) / 1.3f;
        assertFalse(body(p));
    }

    @Test
    public void independentRawShaftIsRequired() throws Exception {
        var p = scene();
        for (int y = 69; y <= 130; y++) p.g[y * W + 50] = (byte) 255;
        assertFalse(body(p));
    }

    @Test
    public void openCPocketCannotBecomeHead() throws Exception {
        var p = scene();
        for (int y = 57; y <= 62; y++) for (int x = 63; x <= 72; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(body(p));
    }

    @Test
    public void rectangleCannotBorrowHaloAndLedger() throws Exception {
        var p = scene();
        for (int y = 52; y <= 65; y++)
            for (int x = 50; x <= 70; x++)
                p.g[y * W + x] = (byte) (x < 53 || x > 67 || y < 55 ? 0 : 255);
        assertFalse(body(p));
    }

    @Test
    public void uprightDOutlineCannotBorrowHalo() throws Exception {
        var p = scene();
        for (int y = 52; y <= 65; y++) for (int x = 50; x <= 53; x++) p.g[y * W + x] = 0;
        assertFalse(body(p));
    }

    @Test
    public void doubleCounterBOutlineCannotBecomeOneHalf() throws Exception {
        var p = scene();
        for (int y = 59; y <= 60; y++) for (int x = 50; x <= 70; x++) p.g[y * W + x] = 0;
        assertFalse(body(p));
    }

    @Test
    public void unrelatedUpperLineDoesNotOwnModelHalo() throws Exception {
        var p = scene();
        for (int x = 50; x <= 70; x++) p.g[51 * W + x] = 0;
        assertFalse(body(p));
    }

    @Test
    public void moreThanOneRawRowOutsideModelIsNotRecovered() throws Exception {
        var p = scene();
        p.g[50 * W + 64] = (byte) 190;
        assertFalse(body(p));
    }

    @Test
    public void mirroredUpstemEnvelopeRetainsClosedHalf() throws Exception {
        var p = scene();
        var q = new Page();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                q.l[(H - 1 - y) * W + W - 1 - x] = p.l[y * W + x];
                q.g[(H - 1 - y) * W + W - 1 - x] = p.g[y * W + x];
            }
        assertTrue(body(q));
    }

    @Test
    public void doubledGeometryWithOnePixelHaloRemainsEligible() throws Exception {
        var p = new Page();
        p.gap = 27.5f;
        for (int y = 104; y <= 136; y++)
            for (int x = 100; x <= 140; x++) {
                double dx = x - 120, dy = y - 120;
                if (Math.pow(dx / 20., 2) + Math.pow((dy + .5 * dx) / 14., 2) <= 1) {
                    p.l[y * W + x] = 2;
                    p.g[y * W + x] =
                            (byte)
                                    (Math.pow(dx / 14., 2) + Math.pow((dy + .5 * dx) / 8., 2) < 1
                                            ? 255
                                            : 0);
                }
            }
        for (int x = 126; x <= 130; x++) p.g[103 * W + x] = (byte) 190;
        for (int x = 110; x <= 114; x++) p.g[137 * W + x] = (byte) 190;
        for (int y = 132; y <= 136; y++)
            for (int x = 84; x <= 156; x++) {
                p.g[y * W + x] = (byte) 140;
                if (p.l[y * W + x] != 2) p.l[y * W + x] = 5;
            }
        for (int y = 120; y <= 260; y++) {
            p.g[y * W + 100] = 0;
            if (p.l[y * W + 100] != 2) p.l[y * W + 100] = 1;
        }
        assertTrue(body(p));
    }

    @Test
    public void callerPixelsRemainUnchanged() throws Exception {
        var p = scene();
        var g = p.g.clone();
        var l = p.l.clone();
        body(p);
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }
}
