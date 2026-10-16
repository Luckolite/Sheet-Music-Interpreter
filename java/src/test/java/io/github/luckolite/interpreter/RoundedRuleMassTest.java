// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original obscured-rule geometry; no score pixels. */
public class RoundedRuleMassTest {
    static final int W = 400, H = 180, X = 200, G = 14, TOP = 60, CY = 88;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    void setup(int rules, boolean beam) {
        Arrays.fill(gray, (byte) 250);
        int n = 0;
        for (int y = TOP; y <= TOP + 4 * G; y += G) {
            if (y != CY && n++ >= rules) continue;
            for (int x = 10; x < W - 10; x++)
                for (int dy = 0; dy < 2; dy++) {
                    gray[(y + dy) * W + x] = 0;
                    labels[(y + dy) * W + x] = 4;
                }
            if (y != CY)
                for (int side : new int[] {-1, 1})
                    for (int d : new int[] {16, 32})
                        for (int dx = 0; dx < 7; dx++)
                            for (int dy = -3; dy <= 5; dy++)
                                gray[(y + dy) * W + X + side * (d + dx)] = 0;
        }
        for (int side : new int[] {-1, 1})
            for (int d = 18; d <= 45; d++)
                for (int dy = -2; dy <= 3; dy++) {
                    gray[(CY + dy) * W + X + side * d] = (byte) 250;
                    labels[(CY + dy) * W + X + side * d] = 0;
                }
        for (int x = X - 12; x <= X + 12; x++)
            for (int dy = -2; dy <= 2; dy++) gray[(CY + dy) * W + x] = (byte) (beam ? 0 : 153);
    }

    int count() throws Exception {
        var s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = s.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        var staff = c.newInstance((float) TOP, (float) (TOP + 4 * G), (float) G);
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
                        s,
                        int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, gray, labels, W, H, X, CY - 6, CY + 6, staff, X - 3);
    }

    @Test
    public void roundedFivePixelRuleRetainsIndependentFrameEvidence() throws Exception {
        setup(4, false);
        assertNull(BeamRuleLocalSlope.findBeyondStem(gray, W, H, X, TOP, G, 165, CY + .5f));
        assertEquals(0, count());
        assertTrue(
                LocalPrintedStaffBand.matchesBeyondStem(
                        gray, W, H, X, TOP, G, 165, CY - 2, CY + 2, 0, null));
    }

    @Test
    public void realDarkBeamSurvivesOccludedRules() throws Exception {
        setup(4, true);
        assertEquals(1, count());
    }

    @Test
    public void twoRulesCannotEraseBand() throws Exception {
        setup(2, false);
        assertFalse(
                LocalPrintedStaffBand.matchesBeyondStem(
                        gray, W, H, X, TOP, G, 165, CY - 2, CY + 2, 0, null));
        assertEquals(1, count());
    }

    @Test
    public void inputsRemainExact() throws Exception {
        setup(4, false);
        byte[] g = gray.clone(), l = labels.clone();
        count();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
