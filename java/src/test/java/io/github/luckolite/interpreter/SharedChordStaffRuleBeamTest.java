// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original shared chord shaft with five rules and independent beam ink. */
public class SharedChordStaffRuleBeamTest {
    static final int W = 480, H = 440, G = 14, X = 250, Y = 333, TOP = 280;
    byte[] gray = new byte[W * H], labels = new byte[W * H];
    boolean mirror;
    float frameOffset;

    void rect(int l, int r, int t, int b, int value, int label) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = (byte) value;
                labels[y * W + x] = (byte) label;
            }
    }

    void oval(int cy) {
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = X - 12; x <= X + 12; x++)
                if (Math.pow((x - X) / 12., 2) + Math.pow((y - cy) / 8., 2) <= 1)
                    rect(x, x, y, y, 25, 2);
    }

    void build(int kind) {
        Arrays.fill(gray, (byte) 250);
        for (int i = 0; i < 5; i++)
            if (!(kind == 2 && i >= 2))
                rect(40, 435, TOP + i * G, TOP + i * G + 2, 70, i == 1 ? 0 : 4);
        oval(Y);
        oval(Y + G);
        rect(X + 11, X + 13, TOP, Y + G, 25, 1);
        rect(70, 390, TOP, TOP + 8, 25, 1);
        if (kind == 1) rect(70, 390, TOP + G, TOP + G + 7, 25, 1);
        if (kind == 3)
            for (int i = 2; i < 5; i++) rect(285, 435, TOP + i * G, TOP + i * G + 2, 250, 0);
        if (kind == 4) rect(220, 263, TOP + G, TOP + G + 7, 25, 1);
    }

    Object component(int cy) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        return ct.newInstance(
                280,
                mirror ? W - 1 - (X + 12) : X - 12,
                mirror ? W - 1 - (X - 12) : X + 12,
                mirror ? H - 1 - (cy + 8) : cy - 8,
                mirror ? H - 1 - (cy - 8) : cy + 8,
                mirror ? W - 1 - (float) X : (float) X,
                mirror ? H - 1 - (float) cy : (float) cy);
    }

    int count(boolean lower) throws Exception {
        if (mirror) {
            byte[] g = gray.clone(), l = labels.clone();
            for (int i = 0; i < g.length; i++) {
                gray[g.length - 1 - i] = g[i];
                labels[l.length - 1 - i] = l[i];
            }
        }
        Object upper = component(Y), bottom = component(Y + G);
        Class<?> st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ct = st.getDeclaredConstructor(float.class, float.class, float.class);
        ct.setAccessible(true);
        float top = (mirror ? H - 1 - (TOP + 4 * G) : TOP) + frameOffset;
        Object staff = ct.newInstance(top, top + 4 * G, (float) G);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        upper.getClass(),
                        st,
                        List.class,
                        int.class);
        m.setAccessible(true);
        int value =
                (int)
                        m.invoke(
                                null,
                                labels,
                                gray,
                                W,
                                H,
                                lower ? bottom : upper,
                                staff,
                                List.of(upper, bottom),
                                3);
        var pitch = st.getDeclaredField("pitchTrack");
        pitch.setAccessible(true);
        assertNull(pitch.get(staff));
        return value;
    }

    @Test
    public void upperChordHeadDoesNotCountStaffRule() throws Exception {
        build(0);
        assertEquals(1, count(false));
    }

    @Test
    public void lowerChordHeadDoesNotCountStaffRule() throws Exception {
        build(0);
        assertEquals(1, count(true));
    }

    @Test
    public void mirroredChordRetainsSingleBeam() throws Exception {
        mirror = true;
        build(0);
        assertEquals(1, count(false));
    }

    @Test
    public void trueDoubleBeamOnStaffRuleRemains() throws Exception {
        build(1);
        assertEquals(2, count(false));
    }

    @Test
    public void missingRuleReferencesCannotEraseRail() throws Exception {
        build(2);
        assertEquals(2, count(false));
    }

    @Test
    public void missingSideReferencesCannotEraseRail() throws Exception {
        build(3);
        assertEquals(2, count(false));
    }

    @Test
    public void shortTrueSecondaryBeamRemains() throws Exception {
        build(4);
        assertEquals(2, count(false));
    }

    @Test
    public void shiftedSeedNeedsFreshPrintedRuleProof() throws Exception {
        frameOffset = 5;
        build(0);
        assertEquals(1, count(false));
    }

    @Test
    public void originalPixelsRemainUnchanged() throws Exception {
        build(0);
        byte[] g = gray.clone(), l = labels.clone();
        count(false);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
