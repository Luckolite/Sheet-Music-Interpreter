// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original broad flat shafts and lower bowls, with optional staff-crossing ink. */
public class DistinctSharpShaftTest {
    static byte[] draw(int tall, int wide, int thick, int bowl, int bar) {
        byte[] p = new byte[120 * 110];
        int x = 40, top = 25, bottom = top + tall - 1;
        for (int y = top; y <= bottom; y++)
            for (int xx = x; xx < x + thick; xx++) p[y * 120 + xx] = 3;
        for (int xx = thick; xx < wide; xx++) {
            float t = (xx - thick) / (float) (wide - thick - 1);
            int upper = bottom - bowl + Math.round(t * t * 3),
                    lower = bottom - Math.round(t * (bowl - 3));
            for (int y = upper; y <= lower; y++)
                if (y <= upper + thick - 1 || y >= lower - thick + 1) p[y * 120 + x + xx] = 3;
        }
        if (bar > 0)
            for (int xx = x; xx < x + wide; xx++)
                for (int y = bottom - bowl + 2; y < bottom - bowl + 2 + bar; y++)
                    p[y * 120 + xx] = 3;
        return p;
    }

    @Test
    public void singleBroadFlatShaftDoesNotBecomeTwoSharpShafts() throws Exception {
        var m = LeaningSharpGeometryTest.class.getDeclaredMethod("pitch", byte[].class);
        m.setAccessible(true);
        int total = 0;
        for (int h : new int[] {30, 34, 38, 42})
            for (int w : new int[] {13, 15, 17, 19})
                for (int t : new int[] {3, 4, 5})
                    for (int bowl : new int[] {10, 13, 16})
                        for (int bar : new int[] {0, 1, 2}) {
                            float value = (float) m.invoke(null, (Object) draw(h, w, t, bowl, bar));
                            total++;
                            assertTrue(
                                    "height="
                                            + h
                                            + " width="
                                            + w
                                            + " thickness="
                                            + t
                                            + " bowl="
                                            + bowl
                                            + " rule="
                                            + bar,
                                    Float.isNaN(value));
                        }
        assertEquals(432, total);
    }
}
