// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original diagonal dark fragments and round cores in lighter curve ink. */
public class DiagonalDotCoreTest {
    static final int W = 300, H = 220;
    byte[] g = new byte[W * H];

    public DiagonalDotCoreTest() {
        Arrays.fill(g, (byte) 255);
    }

    void dot(int radius) {
        for (int y = 123 - radius - 1; y <= 123 + radius + 1; y++)
            for (int x = 136 - radius; x <= 136 + radius; x++)
                if (Math.pow((x - 136) / (double) radius, 2)
                                + Math.pow((y - 123) / (double) (radius + 1), 2)
                        <= 1) g[y * W + x] = 0;
    }

    int read() throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        Object head = ct.newInstance(220, 100, 120, 122, 138, 110f, 130f);
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
        return (int) m.invoke(null, List.of(), head, 14f, g, W, H, true, List.of(), List.of(head));
    }

    void diagonal(boolean mirror) {
        for (int dx = -10; dx <= 10; dx++)
            for (int dy = -1; dy <= 1; dy++)
                g[(127 + (mirror ? dx : -dx) + dy) * W + 138 + dx] = 100;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                if (Math.abs(dx + (mirror ? -dy : dy)) <= 1) g[(127 + dy) * W + 138 + dx] = 50;
    }

    @Test
    public void descendingFragmentIsNotDot() throws Exception {
        diagonal(false);
        assertEquals(0, read());
    }

    @Test
    public void ascendingFragmentIsNotDot() throws Exception {
        diagonal(true);
        assertEquals(0, read());
    }

    @Test
    public void realRoundDotRemains() throws Exception {
        dot(3);
        assertEquals(1, read());
    }

    @Test
    public void darkCurvePixelsRemainUnchanged() throws Exception {
        diagonal(false);
        byte[] old = g.clone();
        read();
        assertArrayEquals(old, g);
    }

    void attachedRoundCore(int rx, int ry) {
        diagonal(false);
        for (int y = 120; y <= 134; y++)
            for (int x = 131; x <= 145; x++) if (g[y * W + x] == 50) g[y * W + x] = 100;
        for (int y = -ry; y <= ry; y++)
            for (int x = -rx; x <= rx; x++)
                if (x * x / (double) (rx * rx) + y * y / (double) (ry * ry) <= 1)
                    g[(127 + y) * W + 138 + x] = 50;
    }

    @Test
    public void roundCoreOnLighterCurveRemainsDot() throws Exception {
        attachedRoundCore(2, 2);
        assertEquals(1, read());
    }

    @Test
    public void smallEllipticalCoreRemainsDot() throws Exception {
        attachedRoundCore(2, 3);
        assertEquals(1, read());
    }
}
