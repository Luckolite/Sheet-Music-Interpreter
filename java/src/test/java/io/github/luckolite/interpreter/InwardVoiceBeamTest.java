// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two inward stems lead into separate single and double beam groups. */
public class InwardVoiceBeamTest {
    static final int W = 400, H = 280, G = 16;
    byte[] gray, labels;

    void rect(int l, int r, int t, int b, int kind) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = 25;
                if (kind > 0) labels[y * W + x] = (byte) kind;
            }
    }

    void oval(int x, int y) {
        for (int yy = y - 9; yy <= y + 9; yy++)
            for (int xx = x - 12; xx <= x + 12; xx++)
                if (Math.pow((xx - x) / 12., 2) + Math.pow((yy - y) / 9., 2) <= 1) {
                    gray[yy * W + xx] = 25;
                    labels[yy * W + xx] = 2;
                }
    }

    void draw(boolean mirror) {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(200, 100);
        oval(239, 148);
        rect(188, 189, 100, 116, 1);
        rect(128, 189, 110, 116, 1);
        rect(250, 251, 118, 148, 1);
        rect(170, 251, 118, 124, 1);
        rect(227, 251, 130, 136, 1);
        if (mirror) {
            byte[] g = gray.clone(), l = labels.clone();
            for (int i = 0; i < g.length; i++) {
                gray[g.length - 1 - i] = g[i];
                labels[l.length - 1 - i] = l[i];
            }
        }
    }

    int count(boolean lower, boolean mirror) throws Exception {
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> h = hc.getDeclaredConstructors()[0];
        h.setAccessible(true);
        Object upper =
                mirror
                        ? h.newInstance(
                                330,
                                W - 1 - 212,
                                W - 1 - 188,
                                H - 1 - 109,
                                H - 1 - 91,
                                (float) (W - 1 - 200),
                                (float) (H - 1 - 100))
                        : h.newInstance(330, 188, 212, 91, 109, 200f, 100f);
        Object low =
                mirror
                        ? h.newInstance(
                                330,
                                W - 1 - 251,
                                W - 1 - 227,
                                H - 1 - 157,
                                H - 1 - 139,
                                (float) (W - 1 - 239),
                                (float) (H - 1 - 148))
                        : h.newInstance(330, 227, 251, 139, 157, 239f, 148f);
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        Constructor<?> c = sc.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        Object staff = mirror ? c.newInstance(119f, 183f, 16f) : c.newInstance(96f, 160f, 16f);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class);
        m.setAccessible(true);
        return (int)
                m.invoke(null, labels, gray, W, H, lower ? low : upper, staff, List.of(upper, low));
    }

    @Test
    public void upperSingleBeamDoesNotBorrowLowerVoiceRail() throws Exception {
        draw(false);
        assertEquals(1, count(false, false));
    }

    @Test
    public void mirroredSingleBeamDoesNotBorrowOppositeRail() throws Exception {
        draw(true);
        assertEquals(1, count(false, true));
    }

    @Test
    public void lowerVoiceKeepsBothOfItsPrintedBeams() throws Exception {
        draw(false);
        assertEquals(2, count(true, false));
    }

    @Test
    public void mirroredLowerVoiceKeepsBothOfItsBeams() throws Exception {
        draw(true);
        assertEquals(2, count(true, true));
    }

    @Test
    public void sourcePixelsAreReadOnly() throws Exception {
        draw(false);
        byte[] g = gray.clone(), l = labels.clone();
        count(false, false);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    int proof(boolean mirror, float staffTop, float staffBottom) {
        return mirror
                ? InwardVoiceBeamOwnership.count(
                        gray,
                        labels,
                        W,
                        H,
                        G,
                        staffTop,
                        staffBottom,
                        W - 1 - 212,
                        W - 1 - 188,
                        H - 1 - 109,
                        H - 1 - 91,
                        H - 1 - 100,
                        W - 1 - 251,
                        W - 1 - 227,
                        H - 1 - 157,
                        H - 1 - 139,
                        H - 1 - 148)
                : InwardVoiceBeamOwnership.count(
                        gray,
                        labels,
                        W,
                        H,
                        G,
                        staffTop,
                        staffBottom,
                        188,
                        212,
                        91,
                        109,
                        100,
                        227,
                        251,
                        139,
                        157,
                        148);
    }

    void clear(int l, int r, int t, int b) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = (byte) 250;
                labels[y * W + x] = 0;
            }
    }

    @Test
    public void continuousOwnShaftKeepsGenuinePartialBeam() throws Exception {
        draw(false);
        rect(188, 189, 117, 124, 1);
        assertEquals(-1, proof(false, 96, 160));
        assertEquals(2, count(false, false));
    }

    @Test
    public void oppositeOutwardShaftCannotStealRail() {
        draw(false);
        clear(250, 251, 118, 148);
        rect(227, 228, 118, 148, 1);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void brokenOtherShaftCannotClaimRail() {
        draw(false);
        clear(247, 254, 137, 140);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void ownTwoTerminalRailsAreNotReduced() {
        draw(false);
        clear(170, 251, 118, 124);
        rect(128, 189, 122, 128, 1);
        rect(170, 251, 134, 140, 1);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void foreignThroughRailRequiresContinuousSpan() {
        draw(false);
        clear(213, 218, 118, 124);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void ownDisconnectedHeadCannotClaimRail() {
        draw(false);
        clear(186, 191, 107, 110);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void separatePhysicalStaffCannotClaimOwnership() {
        draw(false);
        assertEquals(-1, proof(false, 32, 96));
    }

    @Test
    public void invalidImageCannotClaimOwnership() {
        draw(false);
        gray = new byte[4];
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void nonfiniteStaffCannotClaimOwnership() {
        draw(false);
        assertEquals(-1, proof(false, Float.NaN, 160));
    }

    @Test
    public void mirroredContinuousOwnShaftKeepsPartialBeam() throws Exception {
        draw(false);
        rect(188, 189, 117, 124, 1);
        byte[] g = gray.clone(), l = labels.clone();
        for (int i = 0; i < g.length; i++) {
            gray[g.length - 1 - i] = g[i];
            labels[l.length - 1 - i] = l[i];
        }
        assertEquals(-1, proof(true, 119, 183));
        assertEquals(2, count(false, true));
    }

    @Test
    public void missingThroughRailCannotChangeSingleBeam() {
        draw(false);
        clear(170, 251, 118, 124);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void singleDarkRowIsNotAnOppositeShaft() {
        draw(false);
        clear(247, 254, 137, 140);
        rect(250, 251, 138, 138, 1);
        assertEquals(-1, proof(false, 96, 160));
    }

    @Test
    public void isolatedThinStrokeCannotMoveOwnedRailEnd() {
        draw(false);
        rect(128, 189, 132, 132, 1);
        assertEquals(1, proof(false, 96, 160));
    }

    @Test
    public void isolatedThinStrokeCannotInventOwnedRail() {
        draw(false);
        clear(128, 189, 110, 116);
        rect(128, 189, 110, 110, 1);
        assertEquals(-1, proof(false, 96, 160));
    }
}
