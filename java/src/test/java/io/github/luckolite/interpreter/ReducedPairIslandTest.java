// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original reduced paired beams with a stemless semantic island and physical-note controls. */
public class ReducedPairIslandTest {
    static final int W = 340, H = 240;
    byte[] gray = new byte[W * H];

    public ReducedPairIslandTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 90; y <= 170; y += 20) for (int x = 20; x < 320; x++) gray[y * W + x] = 0;
        note(125, 160);
        note(157, 170);
        for (int x = 132; x <= 164; x++) {
            int top = 118 + Math.round((x - 132) * .15625f);
            for (int k = 0; k < 2; k++)
                for (int y = top + k * 10; y < top + k * 10 + 5; y++) gray[y * W + x] = 0;
        }
        for (int y = 118; y <= 160; y++) gray[y * W + 132] = 0;
        for (int y = 123; y <= 170; y++) gray[y * W + 164] = 0;
    }

    void note(int cx, int cy) {
        for (int y = cy - 6; y <= cy + 6; y++)
            for (int x = cx - 7; x <= cx + 7; x++)
                if (Math.pow((x - cx) / 7d, 2) + Math.pow((y - cy) / 6d, 2) <= 1)
                    gray[y * W + x] = 0;
    }

    int count(boolean right, boolean broad) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object a = c.newInstance(130, 118, 132, 154, 166, 125f, 160f),
                b = c.newInstance(130, 150, 164, 164, 176, 157f, 170f),
                island =
                        broad
                                ? c.newInstance(150, 137, 153, 126, 138, 145f, 132f)
                                : c.newInstance(44, 141, 148, 128, 135, 145f, 132f);
        var sc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                        .getDeclaredConstructors()[0];
        sc.setAccessible(true);
        Object staff = sc.newInstance(90f, 170f, 20f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "reducedPairBeamIslands",
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
                                right ? List.of(a, island, b) : List.of(a, island),
                                List.of(staff)))
                .size();
    }

    @Test
    public void pairedInkOwnsTinyStemlessIsland() throws Exception {
        assertEquals(1, count(true, false));
    }

    @Test
    public void missingSecondOwnerKeepsIsland() throws Exception {
        assertEquals(0, count(false, false));
    }

    @Test
    public void broadHeadIsPreserved() throws Exception {
        assertEquals(0, count(true, true));
    }

    @Test
    public void independentShaftPreservesNote() throws Exception {
        for (int y = 132; y <= 185; y++) gray[y * W + 142] = 0;
        assertEquals(0, count(true, false));
    }

    @Test
    public void oneBeamCannotOwnIsland() throws Exception {
        for (int x = 133; x < 164; x++) {
            int top = 118 + Math.round((x - 132) * .15625f);
            for (int y = top; y < top + 5; y++) gray[y * W + x] = (byte) 255;
        }
        assertEquals(0, count(true, false));
    }

    @Test
    public void pixelsRemainUnchanged() throws Exception {
        byte[] before = gray.clone();
        count(true, false);
        assertArrayEquals(before, gray);
    }
}
