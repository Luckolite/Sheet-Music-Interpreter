// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original sharp geometry with varying staff phase, crossbar spacing and raster scale. */
public class SharpStaffBandTest {
    static byte[] draw(int g, int span, int thickness, float position, float separation, int rise) {
        int w = 320, h = 240, first = 80, left = 100, right = left + span - 1;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        int cy = Math.round(first + g * position);
        for (int shaft : new int[] {left + Math.round(span * .22f), left + Math.round(span * .63f)})
            for (int x = shaft; x < shaft + thickness; x++)
                for (int y = cy - Math.round(g * 1.35f); y <= cy + Math.round(g * 1.35f); y++)
                    gray[y * w + x] = 0;
        for (int sign : new int[] {-1, 1})
            for (int x = left; x <= right; x++) {
                int yy =
                        cy
                                + Math.round(sign * g * separation * .5f)
                                - Math.round(rise * (x - left) / (float) (span - 1));
                for (int y = yy; y <= yy + thickness; y++) gray[y * w + x] = 0;
            }
        for (int i = 0; i < 5; i++)
            for (int x = 20; x < 300; x++) gray[(first + i * g) * w + x] = 0;
        return gray;
    }

    @Test
    public void staffInterruptedSharpsDoNotBecomeCutTime() {
        int total = 0;
        for (int g : new int[] {12, 14, 16, 20})
            for (int span : new int[] {13, 15, 17, 19})
                for (int t : new int[] {1, 2})
                    for (float pos : new float[] {1.25f, 1.5f, 1.75f, 2f})
                        for (float sep : new float[] {.8f, 1f, 1.2f, 1.4f})
                            for (int rise : new int[] {3, 5}) {
                                if (span < g * .9f || span > g * 1.6f) continue;
                                total++;
                                int result =
                                        CommonTimeMeter.read(
                                                draw(g, span, t, pos, sep, rise),
                                                320,
                                                240,
                                                100,
                                                100 + span - 1,
                                                80,
                                                g,
                                                0);
                                assertEquals(
                                        "gap="
                                                + g
                                                + " span="
                                                + span
                                                + " thickness="
                                                + t
                                                + " position="
                                                + pos
                                                + " spacing="
                                                + sep
                                                + " rise="
                                                + rise,
                                        0,
                                        result);
                            }
        assertEquals(768, total);
    }

    @Test
    public void realNarrowTimeSignsAndPixelsRemainValid() {
        for (boolean cut : new boolean[] {false, true})
            for (int radius : new int[] {11, 12, 13}) {
                var page = new CommonTimeSharpOwnershipTest.NarrowPage(16, 0, radius, 0, cut);
                var original = page.gray.clone();
                assertEquals(cut ? 2 : 4, page.read());
                assertArrayEquals(original, page.gray);
            }
    }
}
