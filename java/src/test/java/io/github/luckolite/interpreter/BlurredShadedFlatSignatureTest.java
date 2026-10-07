// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original flat bowls on gray paper, with one absent semantic shaft. */
public class BlurredShadedFlatSignatureTest {
    void flat(JoinedSignatureSharpTest p, int x, int cy, boolean missing) {
        for (int y = cy - 28; y <= cy + 7; y++)
            for (int xx = x; xx <= x + 2; xx++) {
                p.gray[y * JoinedSignatureSharpTest.W + xx] = 80;
                if (!missing) p.labels[y * JoinedSignatureSharpTest.W + xx] = 3;
            }
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int xx = x + 2; xx <= x + 12; xx++) {
                double z = Math.pow((xx - x - 3) / 9., 2) + Math.pow((y - cy) / 7., 2);
                if (z >= .35 && z <= 1) {
                    p.gray[y * JoinedSignatureSharpTest.W + xx] = 80;
                    if (!missing) p.labels[y * JoinedSignatureSharpTest.W + xx] = 3;
                }
            }
    }

    JoinedSignatureSharpTest page(boolean reduction) {
        var p = new JoinedSignatureSharpTest();
        p.row(100, 0, 0, false);
        for (int i = 0; i < p.gray.length; i++)
            p.gray[i] = (byte) ((p.gray[i] & 255) == 255 ? 156 : 80);
        flat(p, 80, 132, true);
        flat(p, 100, 108, false);
        flat(p, 120, 140, false);
        if (reduction) {
            p.row(310, 0, 0, false);
            for (int y = 250; y < 440; y++)
                for (int x = 0; x < JoinedSignatureSharpTest.W; x++)
                    p.gray[y * JoinedSignatureSharpTest.W + x] =
                            (byte)
                                    ((p.gray[y * JoinedSignatureSharpTest.W + x] & 255) == 0
                                            ? 80
                                            : 156);
            flat(p, 80, 342, false);
        }
        return p;
    }

    int flatLimit(byte[] g) {
        try {
            var m =
                    ShadedInkWindow.class.getDeclaredMethod(
                            "flatSpineLimit",
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class);
            m.setAccessible(true);
            return (int) m.invoke(null, g, 100, 100, 0, 0, 99, 99, 205);
        } catch (NoSuchMethodException old) {
            return ShadedInkWindow.limit(g, 100, 100, 0, 0, 99, 99, 205);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void blurredThreeFlatHeaderRetainsTheMissingShaft() {
        assertEquals(List.of(-3), page(false).keys());
    }

    @Test
    public void realReductionStillChangesTheKey() {
        assertEquals(List.of(-3, -1), page(true).keys());
    }

    @Test
    public void faintContrastCannotInventFlatSpines() {
        byte[] g = new byte[10000];
        Arrays.fill(g, (byte) 156);
        Arrays.fill(g, 0, 400, (byte) 125);
        assertEquals(205, flatLimit(g));
    }

    @Test
    public void normalInkWindowKeepsItsPreviousGuard() {
        byte[] g = new byte[10000];
        Arrays.fill(g, (byte) 156);
        Arrays.fill(g, 0, 400, (byte) 80);
        assertEquals(205, ShadedInkWindow.limit(g, 100, 100, 0, 0, 99, 99, 205));
    }

    @Test
    public void brightPaperRetainsItsNormalThreshold() {
        byte[] g = new byte[10000];
        Arrays.fill(g, (byte) 250);
        Arrays.fill(g, 0, 400, (byte) 80);
        assertEquals(205, flatLimit(g));
    }

    @Test
    public void callerRasterIsPreserved() {
        var p = page(false);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.keys();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}
