// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original round dot joined to a lighter sloped curve by downsampling. */
public class DarkDotCoreTest {
    static final int W = 300, H = 220;
    byte[] g = new byte[W * H];

    public DarkDotCoreTest() {
        Arrays.fill(g, (byte) 255);
    }

    void dot(int radius) {
        for (int y = 123 - radius - 1; y <= 123 + radius + 1; y++)
            for (int x = 136 - radius; x <= 136 + radius; x++)
                if (Math.pow((x - 136) / (double) radius, 2)
                                + Math.pow((y - 123) / (double) (radius + 1), 2)
                        <= 1) g[y * W + x] = 0;
    }

    void curve(int shade) {
        for (int x = 124; x <= 170; x++) {
            int y = 117 - (int) Math.round((x - 136) * .3);
            for (int dy = 0; dy <= 1; dy++) g[(y + dy) * W + x] = (byte) shade;
        }
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
        return (int) m.invoke(null, List.of(), head, 16f, g, W, H, true, List.of(), List.of(head));
    }

    @Test
    public void darkRoundCoreSurvivesLighterCurveContact() throws Exception {
        curve(50);
        dot(3);
        assertEquals(1, read());
    }

    @Test
    public void isolatedDotRemainsOne() throws Exception {
        dot(3);
        assertEquals(1, read());
    }

    @Test
    public void curveAloneIsNotDot() throws Exception {
        curve(50);
        assertEquals(0, read());
    }

    @Test
    public void inseparableDarkCurveCannotProveDot() throws Exception {
        curve(0);
        dot(3);
        assertEquals(0, read());
    }

    @Test
    public void tinyCoreIsNotEnough() throws Exception {
        curve(50);
        dot(1);
        assertEquals(0, read());
    }

    @Test
    public void sourcePixelsRemainUnchanged() throws Exception {
        curve(50);
        dot(3);
        byte[] old = g.clone();
        read();
        assertArrayEquals(old, g);
    }

    @Test
    public void darkerSegmentOfSlurIsNotDot() throws Exception {
        curve(50);
        for (int x = 133; x <= 139; x++) {
            int y = 117 - (int) Math.round((x - 136) * .3);
            for (int dy = 0; dy <= 1; dy++) g[(y + dy) * W + x] = 0;
        }
        assertEquals(0, read());
    }

    @Test
    public void darkerThickSegmentOfSlurIsNotDot() throws Exception {
        curve(50);
        for (int x = 133; x <= 139; x++) {
            int y = 117 - (int) Math.round((x - 136) * .3);
            for (int dy = 0; dy <= 2; dy++) g[(y + dy) * W + x] = 0;
        }
        assertEquals(0, read());
    }
}
