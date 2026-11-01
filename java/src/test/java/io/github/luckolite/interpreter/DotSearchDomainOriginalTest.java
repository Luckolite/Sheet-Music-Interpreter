// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rounded dots with real thin left/right connections and a foreign rule crossing. */
public final class DotSearchDomainOriginalTest {
    private static final int W = 280, H = 170;
    private final byte[] gray = new byte[W * H];

    public DotSearchDomainOriginalTest() {
        Arrays.fill(gray, (byte) 250);
        for (int y = 77; y <= 83; y++)
            for (int x = 123; x <= 129; x++)
                if ((x - 126) * (x - 126) + (y - 80) * (y - 80) <= 10) gray[y * W + x] = 0;
    }

    private void line(int left, int right, int y) {
        for (int x = left; x <= right; x++) gray[y * W + x] = 0;
    }

    private int count() throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(260, 90, 112, 71, 89, 101f, 80f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        c,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return (int)
                m.invoke(
                        null, List.of(), head, 13.75f, gray, W, H, false, List.of(), List.of(head));
    }

    @Test
    public void genuineDiskWithLeftThinTieRemains() throws Exception {
        line(110, 126, 80);
        assertEquals(1, count());
    }

    @Test
    public void genuineDiskWithRightThinTieRemains() throws Exception {
        line(126, 210, 80);
        assertEquals(1, count());
    }

    @Test
    public void genuineDiskAcrossThinRuleRemains() throws Exception {
        line(20, 245, 82);
        assertEquals(1, count());
    }

    @Test
    public void genuineRuleLinkedDiskBesideForeignStemRemains() throws Exception {
        line(20, 245, 82);
        for (int y = 40; y <= 82; y++) gray[y * W + 117] = 0;
        assertEquals(1, count());
    }
}
