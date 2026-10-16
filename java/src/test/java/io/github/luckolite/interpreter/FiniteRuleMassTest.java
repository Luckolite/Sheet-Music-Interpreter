// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original equal-ink rule resampling and independently darker beam bodies. */
public class FiniteRuleMassTest {
    static final int W = 360, H = 180;
    byte[] gray = new byte[W * H];

    void draw(int paper, int bodyDeficit) {
        Arrays.fill(gray, (byte) paper);
        for (int x = 0; x < W; x++) {
            boolean body = x >= 110 && x <= 250;
            int[] deficit =
                    body
                            ? (bodyDeficit == 0
                                    ? new int[] {110, 200, 200, 110}
                                    : new int[] {
                                        bodyDeficit, bodyDeficit, bodyDeficit, bodyDeficit
                                    })
                            : new int[] {210, 200, 210};
            for (int dy = 0; dy < deficit.length; dy++)
                gray[(80 + dy) * W + x] = (byte) (paper - deficit[dy]);
        }
    }

    boolean finite() throws Exception {
        var s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = s.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "finiteBeamOverRule",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        s,
                        int.class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(null, gray, W, H, 180, 80, 83, c.newInstance(52f, 108f, 14f), 165);
    }

    @Test
    public void thresholdWidthChangeWithoutExtraInkIsNotBeam() throws Exception {
        for (int paper : new int[] {240, 250, 255}) {
            draw(paper, 0);
            assertFalse(finite());
        }
    }

    @Test
    public void darkBeamRetainsAddedInk() throws Exception {
        draw(250, 220);
        assertTrue(finite());
    }

    @Test
    public void lighterBeamRetainsAddedInk() throws Exception {
        draw(250, 175);
        assertTrue(finite());
    }

    @Test
    public void sourcePixelsRemainExact() throws Exception {
        draw(250, 0);
        byte[] copy = gray.clone();
        finite();
        assertArrayEquals(copy, gray);
    }
}
