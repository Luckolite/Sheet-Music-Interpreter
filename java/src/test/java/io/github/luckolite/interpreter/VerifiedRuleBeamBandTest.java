// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw blurred rails and independently printed beam bodies; no retained pixels. */
public final class VerifiedRuleBeamBandTest {
    static final int W = 480, H = 240, X = 220, G = 16, TOP = 80;
    final PrintedRuleInkOwnershipTest draw = new PrintedRuleInkOwnershipTest();

    void body(byte[] p, int first, int last, int shade, float slope, int span) {
        for (int x = X - span; x <= X + span; x++)
            for (int y = first; y <= last; y++)
                p[(y + Math.round(slope * (x - X))) * W + x] = (byte) shade;
    }

    int count(byte[] p, float slope, boolean verified, int bottom) throws Exception {
        Class<?> st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = st.getDeclaredConstructor(float.class, float.class, float.class);
        ctor.setAccessible(true);
        Object staff = ctor.newInstance((float) TOP, (float) (TOP + 4 * G), (float) G);
        Field tf = st.getDeclaredField("pitchTrack");
        tf.setAccessible(true);
        tf.set(staff, draw.track(slope, verified));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBandsAtThreshold",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        st,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, p, new byte[p.length], W, H, X, 102, bottom, staff, 158, X);
    }

    byte[] rail(float slope) {
        byte[] p = draw.page(slope, 190, 5);
        for (int i = 0; i < p.length; i++) if ((p[i] & 255) == 90) p[i] = 110;
        int[] blur = {157, 153, 140, 110, 140, 153, 157};
        for (int x = X - 20; x <= X + 20; x++)
            for (int dy = -3; dy <= 3; dy++)
                p[(112 + Math.round(slope * (x - X)) + dy) * W + x] = (byte) blur[dy + 3];
        return p;
    }

    byte[] two(float slope) {
        byte[] p = rail(slope);
        body(p, 121, 125, 20, slope, 25);
        return p;
    }

    @Test
    public void verifiedRuleCannotAddSecondBeam() throws Exception {
        assertEquals(1, count(two(0), 0, true, 130));
    }

    @Test
    public void risingRuleCannotAddSecondBeam() throws Exception {
        assertEquals(1, count(two(.12f), .12f, true, 130));
    }

    @Test
    public void fallingRuleCannotAddSecondBeam() throws Exception {
        assertEquals(1, count(two(-.12f), -.12f, true, 130));
    }

    @Test
    public void soleLegacyBandIsPreserved() throws Exception {
        assertEquals(1, count(rail(0), 0, true, 118));
    }

    @Test
    public void genuineDarkerBeamCrossingRuleRemains() throws Exception {
        byte[] p = two(0);
        body(p, 110, 114, 20, 0, 25);
        assertEquals(2, count(p, 0, true, 130));
    }

    @Test
    public void unverifiedGeometryKeepsLegacyCounterfactual() throws Exception {
        assertEquals(2, count(two(0), 0, false, 130));
    }

    @Test
    public void threeIndependentDarkBodiesRemain() throws Exception {
        byte[] p = two(0);
        body(p, 110, 114, 20, 0, 25);
        body(p, 133, 137, 20, 0, 25);
        assertEquals(3, count(p, 0, true, 138));
    }

    @Test
    public void sourcePixelsRemainImmutable() throws Exception {
        byte[] p = two(0), before = p.clone();
        count(p, 0, true, 130);
        assertArrayEquals(before, p);
    }
}
