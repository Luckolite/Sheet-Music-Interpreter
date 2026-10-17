// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic slur and terminal rail; no scanned score coordinates or pixels. */
public class SingleBlankSlurRailTest {
    static final int W = 320, H = 280, X = 164;
    byte[] gray, labels;
    boolean rotated;

    void pixel(int x, int y, int ink, int label) {
        if (rotated) {
            x = W - 1 - x;
            y = H - 1 - y;
        }
        gray[y * W + x] = (byte) ink;
        labels[y * W + x] = (byte) label;
    }

    void draw(boolean rotate, boolean bowed, boolean connected) {
        rotated = rotate;
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < 300; x++)
                for (int y = 160 + line * 16; y <= 161 + line * 16; y++) pixel(x, y, 120, 4);
        for (int y = 193; y <= 207; y++)
            for (int x = 144; x <= 164; x++)
                if (Math.pow((x - 154) / 10., 2) + Math.pow((y - 200) / 7., 2) <= 1)
                    pixel(x, y, 20, 2);
        for (int y = 143; y < 200; y++) pixel(X, y, 20, 1);
        for (int x = 110; x <= X; x++) {
            int center = 146 + Math.round((X - x) * .2f);
            for (int y = center - 3; y <= center + 3; y++) pixel(x, y, 20, 5);
        }
        for (int x = X - 48; x <= X + 48; x++) {
            int dx = x - X;
            int center = bowed ? 139 + Math.round(dx * dx * .002f - dx * .10f) : 139;
            for (int y = center - 2; y <= center + 2; y++) pixel(x, y, 20, 5);
        }
        if (connected) for (int y = 137; y <= 144; y++) pixel(X, y, 20, 1);
    }

    boolean recovery() throws Exception {
        Class<?> type = OmrScoreInterpreter.class;
        Class<?> headType = Class.forName(type.getName() + "$Component");
        Class<?> staffType = Class.forName(type.getName() + "$Staff");
        var headConstructor = headType.getDeclaredConstructors()[0];
        headConstructor.setAccessible(true);
        Object head =
                rotated
                        ? headConstructor.newInstance(210, 155, 175, 72, 86, 165f, 79f)
                        : headConstructor.newInstance(210, 144, 164, 193, 207, 154f, 200f);
        var staffConstructor =
                staffType.getDeclaredConstructor(float.class, float.class, float.class);
        staffConstructor.setAccessible(true);
        Object staff =
                rotated
                        ? staffConstructor.newInstance(55f, 119f, 16f)
                        : staffConstructor.newInstance(160f, 224f, 16f);
        var method =
                type.getDeclaredMethod(
                        "fadedDoubleBeamJunction",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        headType,
                        staffType,
                        int[].class);
        method.setAccessible(true);
        int[] pale = rotated ? new int[] {155, 142, 1} : new int[] {164, 137, -1};
        return (boolean) method.invoke(null, labels, gray, W, H, head, staff, pale);
    }

    @Test
    public void upperSingleBlankCurveCannotPromoteTerminalRail() throws Exception {
        draw(false, true, false);
        assertFalse(recovery());
    }

    @Test
    public void lowerSingleBlankCurveCannotPromoteTerminalRail() throws Exception {
        draw(true, true, false);
        assertFalse(recovery());
    }

    @Test
    public void continuousUpperShaftRetainsRecovery() throws Exception {
        draw(false, true, true);
        assertTrue(recovery());
    }

    @Test
    public void continuousLowerShaftRetainsRecovery() throws Exception {
        draw(true, true, true);
        assertTrue(recovery());
    }

    @Test
    public void separatedStraightSecondaryRailRetainsRecovery() throws Exception {
        draw(false, false, false);
        assertTrue(recovery());
    }

    @Test
    public void inkAtWhiteCutEdgePreventsAdmission() throws Exception {
        draw(false, true, false);
        pixel(X + 1, 142, 20, 1);
        assertTrue(recovery());
    }

    @Test
    public void brokenOuterCurveCannotSupplyPositiveProof() throws Exception {
        draw(false, true, false);
        for (int y = 130; y <= 150; y++) pixel(X + 30, y, 255, 0);
        assertTrue(recovery());
    }

    @Test
    public void proofPreservesBothRasterInputs() throws Exception {
        draw(false, true, false);
        byte[] before = gray.clone(), masks = labels.clone();
        assertFalse(recovery());
        assertArrayEquals(before, gray);
        assertArrayEquals(masks, labels);
    }
}
