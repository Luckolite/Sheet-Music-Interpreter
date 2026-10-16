// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original small paired beams with a mask cap touching the first shaft. */
public class BorrowedGraceShaftFixture {
    static final int W = 300, H = 240;
    byte[] gray = new byte[W * H];

    public BorrowedGraceShaftFixture() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 70; y <= 150; y += 20) for (int x = 20; x < 280; x++) gray[y * W + x] = 0;
        note(125, 160);
        note(149, 152);
        for (int x = 132; x <= 156; x++) {
            int top = 118 - Math.round((x - 132) * .25f);
            for (int k = 0; k < 2; k++)
                for (int y = top + k * 10; y < top + k * 10 + 5; y++) gray[y * W + x] = 0;
        }
        for (int y = 118; y <= 160; y++) gray[y * W + 132] = 0;
        for (int y = 112; y <= 152; y++) gray[y * W + 156] = 0;
    }

    void note(int cx, int cy) {
        for (int y = cy - 6; y <= cy + 6; y++)
            for (int x = cx - 7; x <= cx + 7; x++)
                if (Math.pow((x - cx) / 7d, 2) + Math.pow((y - cy) / 6d, 2) <= 1)
                    gray[y * W + x] = 0;
    }

    int count(boolean right) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object a = c.newInstance(130, 118, 132, 154, 166, 125f, 160f),
                b = c.newInstance(130, 142, 156, 146, 158, 149f, 152f),
                cap = c.newInstance(190, 133, 156, 113, 129, 142f, 120f);
        var sc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                        .getDeclaredConstructors()[0];
        sc.setAccessible(true);
        var staff = sc.newInstance(70f, 150f, 20f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "reducedPairBorrowedShaftHeads",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return ((List<?>)
                        m.invoke(
                                null,
                                gray,
                                W,
                                H,
                                right ? List.of(a, cap, b) : List.of(a, cap),
                                List.of(staff)))
                .size();
    }

    @Test
    public void beamCapBorrowsExistingShaft() throws Exception {
        assertEquals(1, count(true));
    }

    @Test
    public void secondRealHeadRequired() throws Exception {
        assertEquals(0, count(false));
    }

    @Test
    public void independentlyLongShaftKeepsUpperNote() throws Exception {
        for (int y = 160; y < 215; y++) gray[y * W + 132] = 0;
        assertEquals(0, count(true));
    }

    @Test
    public void filledHeadBulgeKeepsUpperNote() throws Exception {
        note(143, 123);
        assertEquals(0, count(true));
    }

    @Test
    public void pixelsUnchanged() throws Exception {
        byte[] before = gray.clone();
        count(true);
        assertArrayEquals(before, gray);
    }
}
