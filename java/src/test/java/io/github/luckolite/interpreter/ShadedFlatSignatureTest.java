// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated flat spines and bowls on shaded paper. */
public class ShadedFlatSignatureTest {
    private void flat(JoinedSignatureSharpTest p, int x, int cy, boolean partial) {
        for (int y = cy - 28; y <= cy + 7; y++) for (int xx = x; xx <= x + 2; xx++) p.ink(xx, y, 3);
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int xx = x + 2; xx <= x + 12; xx++) {
                double z = Math.pow((xx - x - 3) / 9d, 2) + Math.pow((y - cy) / 7d, 2);
                if (z >= .35 && z <= 1) {
                    p.gray[y * JoinedSignatureSharpTest.W + xx] = 0;
                    if (!partial) p.labels[y * JoinedSignatureSharpTest.W + xx] = 3;
                }
            }
    }

    private JoinedSignatureSharpTest page(boolean reduction, int paper) {
        var p = new JoinedSignatureSharpTest();
        p.row(100, 0, 0, false);
        flat(p, 80, 132, false);
        flat(p, 100, 108, false);
        flat(p, 120, 140, true);
        if (reduction) {
            p.row(310, 0, 0, false);
            flat(p, 80, 342, false);
        }
        for (int i = 0; i < p.gray.length; i++)
            p.gray[i] = (byte) ((p.gray[i] & 255) == 255 ? paper : 25);
        return p;
    }

    @Test
    public void darkHeaderRecoversItsThirdFragmentedFlat() {
        assertEquals(List.of(-3), page(false, 130).keys());
    }

    @Test
    public void realDarkHeaderReductionSurvives() {
        assertEquals(List.of(-3, -1), page(true, 130).keys());
    }

    @Test
    public void brightHeaderStillUsesOriginalThresholds() {
        assertEquals(List.of(-3), page(false, 250).keys());
    }

    @Test
    public void preservesCallerPixels() {
        var p = page(false, 130);
        byte[] a = p.gray.clone(), b = p.labels.clone();
        p.keys();
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }

    @Test
    public void nullRasterKeepsOriginalLimit() {
        assertEquals(205, ShadedInkWindow.limit(null, 100, 100, 0, 0, 99, 99, 205));
    }

    @Test
    public void uniformPaperDoesNotInventContrast() {
        byte[] a = new byte[10000];
        Arrays.fill(a, (byte) 130);
        assertEquals(205, ShadedInkWindow.limit(a, 100, 100, 0, 0, 99, 99, 205));
    }
}
