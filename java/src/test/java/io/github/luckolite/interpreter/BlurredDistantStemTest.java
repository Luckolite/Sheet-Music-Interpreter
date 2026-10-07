// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original displaced semantic stem and deep ledger head under shaded paper. */
public class BlurredDistantStemTest {
    static final int W = 220, H = 260;

    static class Drawing {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Drawing(int direction, boolean connected, boolean printed) {
            Arrays.fill(gray, (byte) 130);
            int raw = 100 + 2 * direction,
                    semantic = 100 + 3 * direction,
                    cx = semantic - 6 * direction;
            for (int y = 80; y <= 176; y++) {
                labels[y * W + semantic] = 1;
                if (printed && (connected || y <= 144 || y >= 169)) gray[y * W + raw] = 60;
            }
            for (int y = 172; y <= 180; y++)
                for (int x = cx - 6; x <= cx + 6; x++)
                    if ((x - cx) * (x - cx) / 36d + (y - 176) * (y - 176) / 16d <= 1) {
                        labels[y * W + x] = 2;
                        if (printed) gray[y * W + x] = 30;
                    }
        }

        boolean owns() throws Exception {
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
            return (boolean) m.invoke(null, labels, gray, W, H, 100, 80, 144, 16f);
        }
    }

    @Test
    public void rightBlurredEdgeReachesItsDeepHead() throws Exception {
        assertTrue(new Drawing(1, true, true).owns());
    }

    @Test
    public void leftBlurredEdgeReachesItsDeepHead() throws Exception {
        assertTrue(new Drawing(-1, true, true).owns());
    }

    @Test
    public void semanticBridgeThroughPaperCannotVetoBar() throws Exception {
        assertFalse(new Drawing(1, false, true).owns());
    }

    @Test
    public void semanticStemWithoutPrintedInkCannotVetoBar() throws Exception {
        assertFalse(new Drawing(1, true, false).owns());
    }

    @Test
    public void printedBarWithoutHeadRemainsUnowned() throws Exception {
        var d = new Drawing(1, true, true);
        for (int i = 0; i < d.labels.length; i++) if (d.labels[i] == 2) d.labels[i] = 0;
        assertFalse(d.owns());
    }

    @Test
    public void masksStayUnchanged() throws Exception {
        var d = new Drawing(1, true, true);
        var l = d.labels.clone();
        var g = d.gray.clone();
        d.owns();
        assertArrayEquals(l, d.labels);
        assertArrayEquals(g, d.gray);
    }
}
