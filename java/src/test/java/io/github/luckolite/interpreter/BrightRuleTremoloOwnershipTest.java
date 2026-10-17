// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original five rules, a shaft, and separately generated thick/slanted marks. */
public class BrightRuleTremoloOwnershipTest {
    static final int W = 400, H = 240;
    byte[] g = new byte[W * H];
    boolean flip;

    BrightRuleTremoloOwnershipTest init(int kind) {
        Arrays.fill(g, (byte) 250);
        rect(189, 191, 80, 174, 35);
        for (int line = 0; line < 5; line++)
            if (!(kind == 2 && line < 2)) rect(40, 360, 91 + line * 12, 93 + line * 12, 75);
        if (kind != 4) rect(178, 202, 139, 142, 75);
        if (kind == 1) rect(178, 202, 137, 143, 25);
        if (kind == 3) {
            rect(178, 202, 139, 142, 250);
            for (int x = 178; x <= 202; x++) {
                int y = Math.round(140 + (x - 190) * .3f);
                rect(x, x, y - 2, y + 2, 25);
            }
        }
        if (kind == 5) rect(40, 169, 90, 158, 250);
        return this;
    }

    void rect(int l, int r, int t, int b, int v) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) g[(flip ? H - 1 - y : y) * W + x] = (byte) v;
    }

    int count(boolean knownFrame) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        Object h =
                ct.newInstance(
                        220,
                        189,
                        210,
                        flip ? H - 1 - 87 : 73,
                        flip ? H - 1 - 73 : 87,
                        200f,
                        flip ? H - 1 - 80f : 80f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "tremoloStrokeCounts",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        float.class,
                        float.class,
                        List.class,
                        StaffPitchTrack.class);
        m.setAccessible(true);
        return ((int[])
                        m.invoke(
                                null,
                                g,
                                W,
                                H,
                                h,
                                12f,
                                knownFrame ? (flip ? 99f : 92f) : Float.NaN,
                                0f,
                                List.of(h),
                                null))
                [0];
    }

    @Test
    public void finiteThickWhitePaperRuleIsNotTremolo() throws Exception {
        init(0);
        assertEquals(0, count(true));
    }

    @Test
    public void mirroredFiniteRuleIsNotTremolo() throws Exception {
        flip = true;
        init(0);
        assertEquals(0, count(true));
    }

    @Test
    public void genuineHeavyHorizontalStrokeRemains() throws Exception {
        init(1);
        assertEquals(1, count(true));
    }

    @Test
    public void genuineInclinedStrokeRemains() throws Exception {
        init(3);
        assertEquals(1, count(true));
    }

    @Test
    public void missingReferenceRulesCannotProveOwnership() throws Exception {
        init(2);
        assertEquals(1, count(true));
    }

    @Test
    public void missingLeftReferencesCannotProveOwnership() throws Exception {
        init(5);
        assertEquals(1, count(true));
    }

    @Test
    public void unknownFrameRetainsExistingBehavior() throws Exception {
        init(0);
        assertEquals(1, count(false));
    }

    @Test
    public void ordinaryThinRulesHaveNoTremolo() throws Exception {
        init(4);
        assertEquals(0, count(true));
    }

    @Test
    public void pixelsRemainUnchanged() throws Exception {
        init(0);
        byte[] old = g.clone();
        count(true);
        assertArrayEquals(old, g);
    }
}
