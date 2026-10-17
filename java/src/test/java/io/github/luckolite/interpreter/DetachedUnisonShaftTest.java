// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original offset hollow oval and disconnected semantic shaft evidence. */
public class DetachedUnisonShaftTest {
    int parts(boolean joined, boolean pixels) throws Exception {
        int w = 320, h = 220, cx = 160, cy = 120;
        byte[] l = new byte[w * h], g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        int area = 0;
        long sx = 0, sy = 0;
        for (int y = 112; y <= 128; y++)
            for (int x = 146; x <= 174; x++)
                if (Math.pow((x - cx) / 14d, 2) + Math.pow((y - cy) / 8d, 2) <= 1) {
                    l[y * w + x] = 2;
                    g[y * w + x] =
                            (byte)
                                    (Math.pow((x - (cx + 3)) / 8d, 2) + Math.pow((y - cy) / 5d, 2)
                                                    < 1
                                            ? 255
                                            : 0);
                    area++;
                    sx += x;
                    sy += y;
                }
        for (int y = 80; y <= (joined ? 153 : 95); y++)
            for (int x = 160; x <= 161; x++) {
                l[y * w + x] = 1;
                if (pixels) g[y * w + x] = 0;
            }
        byte[] a = l.clone(), b = g.clone();
        Class<?> root = OmrScoreInterpreter.class, c = Class.forName(root.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head =
                ctor.newInstance(area, 146, 174, 112, 128, sx / (float) area, sy / (float) area);
        var m =
                root.getDeclaredMethod(
                        "sideBySideUnison",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class);
        m.setAccessible(true);
        int result = ((List<?>) m.invoke(null, l, g, w, h, head, 14f)).size();
        assertArrayEquals(a, l);
        assertArrayEquals(b, g);
        return result;
    }

    @Test
    public void detachedSemanticShaftCannotSplitWholeOval() throws Exception {
        assertEquals(0, parts(false, true));
    }

    @Test
    public void semanticOnlyConnectedShaftCannotProveTwoVoices() throws Exception {
        assertEquals(0, parts(true, false));
    }

    @Test
    public void attachedPrintedOppositeShaftsRetainTwoVoices() throws Exception {
        assertEquals(
                2L,
                new FractionalUnisonWidthTest.Page(true, true)
                        .notes().stream().filter(n -> n.staffStep() == 2).count());
    }
}
