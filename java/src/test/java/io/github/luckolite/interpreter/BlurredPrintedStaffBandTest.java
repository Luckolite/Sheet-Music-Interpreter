// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original five-rule raster with a broadened local Gaussian shoulder. */
public class BlurredPrintedStaffBandTest {
    static final int W = 480, H = 200, G = 16, X = 220, CY = 88;
    byte[] gray, labels;

    void draw(int mode, int otherRules, int paper) {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        int n = 0;
        for (int cy = 40; cy <= 104; cy += G) {
            boolean present = cy == CY || n++ < otherRules;
            if (!present) continue;
            for (int y = cy - 1; y <= cy + 1; y++)
                for (int x = 20; x < W - 20; x++) {
                    gray[y * W + x] = 60;
                    labels[y * W + x] = 4;
                }
        }
        int[] profile = {100, 80, 65, 80, 100};
        for (int x = X - 14; x <= X + 14; x++)
            for (int dy = -2; dy <= 2; dy++) gray[(CY + dy) * W + x] = (byte) profile[dy + 2];
        if (mode > 0) {
            int length = mode == 2 ? 160 : 24, ink = mode == 3 ? 65 : 20;
            for (int x = X - length / 2; x <= X + length / 2; x++)
                for (int y = CY - 2; y <= CY + 2; y++) gray[y * W + x] = (byte) ink;
        }
    }

    int count() throws Exception {
        var s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = s.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        var staff = c.newInstance(40f, 104f, 16f);
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
        return (int) m.invoke(null, gray, labels, W, H, X, CY - 9, CY + 9, staff, X - 3);
    }

    @Test
    public void shadedBlurShoulderDoesNotSupplyABeam() throws Exception {
        draw(0, 4, 140);
        assertEquals(0, count());
    }

    @Test
    public void threeOtherRulesAreEnoughToConfirmTheShoulder() throws Exception {
        draw(0, 3, 140);
        assertEquals(0, count());
    }

    @Test
    public void shortDarkBeamOverRuleIsPreserved() throws Exception {
        draw(1, 4, 140);
        assertEquals(1, count());
    }

    @Test
    public void longFiniteBeamOverRuleIsPreserved() throws Exception {
        draw(2, 4, 140);
        assertEquals(1, count());
    }

    @Test
    public void shortPaleBeamStillHasMoreInkThanTheRules() throws Exception {
        draw(3, 4, 140);
        assertEquals(1, count());
    }

    @Test
    public void twoOtherRulesCannotPruneTheBand() throws Exception {
        draw(0, 2, 140);
        assertEquals(1, count());
    }

    @Test
    public void ordinaryWhitePaperDoesNotUseShadedRecovery() {
        draw(0, 4, 250);
        assertFalse(LocalPrintedStaffBand.matches(gray, W, H, X, 40, G, 165, CY - 2, CY + 2));
    }

    @Test
    public void absentRasterCannotProvePrintedRule() {
        assertFalse(LocalPrintedStaffBand.matches(null, W, H, X, 40, G, 108, CY - 2, CY + 2));
    }

    @Test
    public void incompleteFrameCannotProvePrintedRule() {
        draw(0, 4, 140);
        assertFalse(LocalPrintedStaffBand.matches(gray, W, H, 10, 40, G, 108, CY - 2, CY + 2));
    }

    @Test
    public void rasterAndSemanticArraysRemainUnchanged() throws Exception {
        draw(0, 4, 140);
        var g = gray.clone();
        var l = labels.clone();
        count();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
