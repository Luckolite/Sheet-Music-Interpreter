// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original five-rule geometry with unrelated near-column ink and a short rule fringe. */
public class CrowdedRuleFringeTest {
    static final int W = 400, H = 220, X = 200, TOP = 80, G = 16;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    void rect(int x, int y, int width, int height, int label) {
        for (int yy = y; yy < y + height; yy++)
            for (int xx = x; xx < x + width; xx++) {
                gray[yy * W + xx] = 0;
                labels[yy * W + xx] = (byte) label;
            }
    }

    void prepare() {
        Arrays.fill(gray, (byte) 255);
        for (int i = 0; i < 5; i++) rect(20, TOP + i * G, W - 40, 2, 4);
        for (int y : new int[] {TOP, TOP + G})
            for (int side : new int[] {-1, 1}) rect(X + side * 20 - 4, y - 4, 9, 10, 0);
        rect(X - 6, TOP + 2 * G - 1, 13, 4, 0);
    }

    int bands() throws Exception {
        Class<?> s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = s.getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object staff = c.newInstance((float) TOP, (float) (TOP + 4 * G), (float) G);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBands",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        s);
        m.setAccessible(true);
        return (int) m.invoke(null, gray, labels, W, H, X, TOP + 2 * G - 5, TOP + 2 * G + 7, staff);
    }

    @Test
    public void crowdedNearRulesDoNotTurnFringeIntoBeam() throws Exception {
        prepare();
        assertEquals(0, bands());
    }

    @Test
    public void genuineThickBeamRemains() throws Exception {
        prepare();
        rect(X - 25, TOP + 2 * G - 2, 65, 7, 1);
        assertEquals(1, bands());
    }

    @Test
    public void incompleteRuleContextCannotEraseStroke() throws Exception {
        prepare();
        for (int y : new int[] {TOP, TOP + G, TOP + 3 * G})
            for (int x = 20; x < W - 20; x++)
                for (int dy = -4; dy <= 6; dy++) {
                    gray[(y + dy) * W + x] = (byte) 255;
                    labels[(y + dy) * W + x] = 0;
                }
        assertNull(BeamRuleLocalSlope.findBeyondStem(gray, W, H, X, TOP, G, 165, TOP + 2 * G));
    }

    @Test
    public void fartherRulesRetainIndependentThreeRuleProof() {
        prepare();
        assertNull(BeamRuleLocalSlope.find(gray, W, H, X, TOP, G, 165, TOP + 2 * G));
        assertNotNull(BeamRuleLocalSlope.findBeyondStem(gray, W, H, X, TOP, G, 165, TOP + 2 * G));
    }

    @Test
    public void inputsRemainUnchanged() throws Exception {
        prepare();
        byte[] g = gray.clone(), l = labels.clone();
        bands();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
