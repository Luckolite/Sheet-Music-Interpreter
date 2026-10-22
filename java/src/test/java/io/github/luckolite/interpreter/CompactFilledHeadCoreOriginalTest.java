// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original compact pale engraving at three scales and independent exterior gradients. */
public final class CompactFilledHeadCoreOriginalTest {
    static final int W = 240, H = 220;

    static final class Scene {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];
        final int l, t, r, b;
        final float gap, cx = 110, cy = 110;

        Scene(int scale, boolean gradient) {
            gap = scale;
            int rx = Math.round(scale * .48f), ry = Math.round(scale * .35f);
            l = 110 - rx;
            r = 110 + rx;
            t = 110 - ry;
            b = 110 + ry;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    gray[y * W + x] = (byte) (gradient ? 220 + x / 12 + y / 40 : 250);
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++)
                    if ((x - 110) * (x - 110) / (double) (rx * rx)
                                    + (y - 110) * (y - 110) / (double) (ry * ry)
                            <= 1) {
                        labels[y * W + x] = 2;
                        gray[y * W + x] = (byte) ((gray[y * W + x] & 255) - 55);
                    }
        }

        boolean proves() {
            byte[] a = labels.clone(), bgray = gray.clone();
            boolean result =
                    CompactFilledHeadCore.isFilled(labels, gray, W, H, l, t, r, b, cx, cy, gap);
            assertArrayEquals(a, labels);
            assertArrayEquals(bgray, gray);
            return result;
        }
    }

    @Test
    public void compactPaleBodyAt12() {
        assertTrue(new Scene(12, false).proves());
    }

    @Test
    public void compactPaleBodyAt16() {
        assertTrue(new Scene(16, false).proves());
    }

    @Test
    public void compactPaleBodyAt24() {
        assertTrue(new Scene(24, false).proves());
    }

    @Test
    public void localGradientIsComparedWithItsOwnExterior() {
        assertTrue(new Scene(16, true).proves());
    }

    @Test
    public void semanticBodyOnPaperCannotOverrideHollow() {
        Scene s = new Scene(16, false);
        Arrays.fill(s.gray, (byte) 250);
        assertFalse(s.proves());
    }

    @Test
    public void disconnectedSemanticCornersCannotClaimCompleteBody() {
        Scene s = new Scene(16, false);
        for (int y = s.t; y <= s.b; y++) for (int x = 109; x <= 111; x++) s.labels[y * W + x] = 0;
        assertFalse(s.proves());
    }

    @Test
    public void rawShapeWithoutAcquiredBodyCannotConferAdmission() {
        Scene s = new Scene(16, false);
        Arrays.fill(s.labels, (byte) 0);
        assertFalse(s.proves());
    }

    @Test
    public void darkPaperDoesNotSupplyPaleBodyContrast() {
        Scene s = new Scene(16, false);
        Arrays.fill(s.gray, (byte) 190);
        assertFalse(s.proves());
    }
}
