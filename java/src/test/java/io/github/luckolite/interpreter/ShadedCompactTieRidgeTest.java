// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

/** Original analytic strokes; no score pixels or source-specific coordinates. */
public class ShadedCompactTieRidgeTest {
    private static final int W = 160, H = 160, LEFT = 50, RIGHT = 82;
    private static final float CY = 80.3f, GAP = 16;

    private record Raster(byte[] labels, byte[] gray) {}

    private static Raster raster(String mode, int side, boolean staff) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) (mode.equals("bright") ? 245 : 136));
        int first = 57, last = 75;
        for (int x = LEFT; x <= RIGHT; x++) {
            float t = (x - first) / (float) (last - first);
            for (int y = 0; y < H; y++) {
                double paper = mode.equals("bright") ? 245 : 136 + (x - LEFT) * .08;
                double value = paper;
                if (staff) {
                    double distance = y - (CY + side * GAP * .5);
                    value -= 40 * Math.exp(-distance * distance / 2.5);
                }
                if (t >= 0 && t <= 1 && !(mode.equals("half") && t > .5)) {
                    double bow = 2.9 * 4 * t * (1 - t);
                    if (mode.equals("straight") || mode.equals("variableWidth")) bow = 0;
                    if (mode.equals("slope")) bow = 6 * t;
                    if (mode.equals("step")) bow = t < .5 ? 0 : 4;
                    double center = CY + side * (GAP * .61 + bow);
                    double sigma = mode.equals("variableWidth") ? 1 + 1.5 * 4 * t * (1 - t) : 1.1;
                    double amplitude = mode.equals("paper") ? 8 : 48;
                    double distance = y - center;
                    value -= amplitude * Math.exp(-distance * distance / (2 * sigma * sigma));
                    if (mode.equals("solid") && y >= CY + GAP * .5 && y <= CY + GAP * 1.2)
                        value = 55;
                    if (mode.equals("head") && Math.abs(distance) < 3) labels[y * W + x] = 2;
                }
                gray[y * W + x] = (byte) Math.max(0, Math.min(255, Math.round(value)));
            }
        }
        return new Raster(labels, gray);
    }

    private static boolean ridge(String mode, int side, boolean staff) {
        Raster r = raster(mode, side, staff);
        return ShadedCompactTieRidge.proved(r.labels, r.gray, W, H, LEFT, RIGHT, CY, GAP);
    }

    private static boolean integrated(String mode, int side, boolean staff) throws Exception {
        Raster r = raster(mode, side, staff);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasPrintedTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, r.labels, r.gray, W, H, LEFT, RIGHT, CY, GAP);
    }

    @Test
    public void shadedShallowReturningRidgeBelow() {
        assertTrue(ridge("bow", 1, false));
    }

    @Test
    public void shadedShallowReturningRidgeAbove() {
        assertTrue(ridge("bow", -1, false));
    }

    @Test
    public void mergedStaffRidgeStillReturnsBelow() {
        assertTrue(ridge("bow", 1, true));
    }

    @Test
    public void mergedStaffRidgeStillReturnsAbove() {
        assertTrue(ridge("bow", -1, true));
    }

    @Test
    public void integratedShallowRidgeBelow() throws Exception {
        assertTrue(integrated("bow", 1, false));
    }

    @Test
    public void integratedShallowRidgeAbove() throws Exception {
        assertTrue(integrated("bow", -1, false));
    }

    @Test
    public void integratedMergedStaffRidgeBelow() throws Exception {
        assertTrue(integrated("bow", 1, true));
    }

    @Test
    public void integratedMergedStaffRidgeAbove() throws Exception {
        assertTrue(integrated("bow", -1, true));
    }

    @Test
    public void straightStrokeHasNoReturn() {
        assertFalse(ridge("straight", 1, false));
    }

    @Test
    public void slopingStrokeHasNoReturn() {
        assertFalse(ridge("slope", 1, false));
    }

    @Test
    public void beamStepCannotReturn() {
        assertFalse(ridge("step", 1, false));
    }

    @Test
    public void varyingWidthCannotBendItsCenter() {
        assertFalse(ridge("variableWidth", 1, false));
    }

    @Test
    public void halfCurveCannotProveBothShoulders() {
        assertFalse(ridge("half", 1, false));
    }

    @Test
    public void weakPaperShadeCannotJoinNotes() {
        assertFalse(ridge("paper", 1, false));
    }

    @Test
    public void brightPaperUsesExistingDetector() {
        assertFalse(ridge("bright", 1, false));
    }

    @Test
    public void solidBulbCannotOwnThinCurve() {
        assertFalse(ridge("solid", 1, false));
    }

    @Test
    public void headLabelsCannotSupplyTieInk() {
        assertFalse(ridge("head", 1, false));
    }

    @Test
    public void missingRawPixelsAbstains() {
        assertFalse(
                ShadedCompactTieRidge.proved(new byte[W * H], null, W, H, LEFT, RIGHT, CY, GAP));
    }
}
