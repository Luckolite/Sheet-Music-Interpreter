// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original upper stem with a second beam meeting an oval crossed by a ledger rule. */
public class HeadBeamOcclusionTest {
    static final int W = 320, H = 280, G = 16;

    static int count(boolean mirrored) throws Exception {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int y = 151; y <= 169; y++)
            for (int x = 138; x <= 162; x++)
                if (Math.pow((x - 150) / 12., 2) + Math.pow((y - 160) / 9., 2) <= 1) {
                    gray[y * W + x] = 25;
                    labels[y * W + x] = 2;
                }
        for (int y = 135; y <= 160; y++) {
            gray[y * W + 162] = 25;
            labels[y * W + 162] = 1;
        }
        for (int first : new int[] {130, 148})
            for (int y = first; y <= first + 6; y++)
                for (int x = 162; x <= 225; x++) {
                    gray[y * W + x] = 25;
                    labels[y * W + x] = 1;
                }
        for (int x = 90; x <= 235; x++) gray[160 * W + x] = 25;
        if (mirrored) {
            byte[] g = gray.clone(), l = labels.clone();
            for (int i = 0; i < g.length; i++) {
                gray[g.length - 1 - i] = g[i];
                labels[l.length - 1 - i] = l[i];
            }
        }
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> h = hc.getDeclaredConstructors()[0];
        h.setAccessible(true);
        Object head =
                mirrored
                        ? h.newInstance(330, 157, 181, 110, 128, 169f, 119f)
                        : h.newInstance(330, 138, 162, 151, 169, 150f, 160f);
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        Constructor<?> s = sc.getDeclaredConstructor(float.class, float.class, float.class);
        s.setAccessible(true);
        Object staff = mirrored ? s.newInstance(135f, 199f, 16f) : s.newInstance(80f, 144f, 16f);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        boolean.class,
                        boolean.class,
                        int[].class);
        m.setAccessible(true);
        return (int) m.invoke(null, labels, gray, W, H, head, staff, false, false, null);
    }

    @Test
    public void upperHeadOcclusionCannotSplitTheSecondBeam() throws Exception {
        assertEquals(2, count(false));
    }

    @Test
    public void lowerHeadOcclusionCannotSplitTheSecondBeam() throws Exception {
        assertEquals(2, count(true));
    }
}
