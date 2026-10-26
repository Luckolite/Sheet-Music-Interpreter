// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic gray profiles and connected rivals, with no score pixels. */
public class IntermediatePaleAugmentationTest {
    static final int W = 240, H = 160;
    byte[] gray = new byte[W * H];
    int paper = 245;

    public IntermediatePaleAugmentationTest() {
        clear(245);
    }

    void clear(int tone) {
        paper = tone;
        Arrays.fill(gray, (byte) tone);
    }

    void profile(int cx, int cy, int peak, double sx, double sy, double angle) {
        double c = Math.cos(Math.toRadians(angle)), s = Math.sin(Math.toRadians(angle));
        for (int y = cy - 12; y <= cy + 12; y++)
            for (int x = cx - 12; x <= cx + 12; x++) {
                double dx = x - cx, dy = y - cy;
                double u = dx * c + dy * s, v = -dx * s + dy * c;
                int tone =
                        (int)
                                Math.round(
                                        paper
                                                - (paper - peak)
                                                        * Math.exp(
                                                                -.5
                                                                        * (u * u / (sx * sx)
                                                                                + v * v
                                                                                        / (sy
                                                                                                * sy))));
                gray[y * W + x] = (byte) Math.min(gray[y * W + x] & 255, tone);
            }
    }

    int count() throws Exception {
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(160, 90, 110, 73, 87, 100f, 80f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        type,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        return (int) m.invoke(null, List.of(), head, 14f, gray, W, H, false);
    }

    @Test
    public void paleCompactProfileOnOffWhitePaper() throws Exception {
        clear(235);
        profile(126, 80, 165, 1.8, 2.07, 0);
        assertEquals(1, count());
    }

    @Test
    public void softOvalOnLightPaper() throws Exception {
        profile(126, 80, 165, 2, 2.3, 0);
        assertEquals(1, count());
    }

    @Test
    public void softOvalOnWhitePaper() throws Exception {
        clear(255);
        profile(126, 80, 165, 2, 2.3, 0);
        assertEquals(1, count());
    }

    @Test
    public void tallerRoundedProfileOnWhitePaper() throws Exception {
        clear(255);
        profile(126, 80, 165, 2, 2.6, 0);
        assertEquals(1, count());
    }

    @Test
    public void twoIndependentPaleDotsRemainDistinct() throws Exception {
        profile(122, 80, 165, 2, 2.3, 0);
        profile(133, 80, 165, 2, 2.3, 0);
        assertEquals(2, count());
    }

    @Test
    public void ordinaryDarkProfileStillWorks() throws Exception {
        profile(126, 80, 40, 1.8, 2, 0);
        assertEquals(1, count());
    }

    @Test
    public void uniformPaleDiskHasNoIndependentCore() throws Exception {
        for (int dy = -3; dy <= 3; dy++)
            for (int dx = -3; dx <= 3; dx++)
                if (dx * dx + dy * dy <= 10) gray[(80 + dy) * W + 126 + dx] = (byte) 175;
        assertEquals(0, count());
    }

    @Test
    public void horizontalConnectedStrokeIsNotNewDot() throws Exception {
        profile(126, 80, 165, 2, 2.3, 0);
        for (int x = 60; x <= 190; x++) gray[80 * W + x] = (byte) 160;
        assertEquals(0, count());
    }

    @Test
    public void verticalConnectedShaftIsNotNewDot() throws Exception {
        profile(126, 80, 165, 2, 2.3, 0);
        for (int y = 30; y <= 130; y++) gray[y * W + 126] = (byte) 160;
        assertEquals(0, count());
    }

    @Test
    public void diagonalGrayProfilesRemainStrokes() throws Exception {
        for (int peak : new int[] {140, 155, 170, 185}) {
            clear(245);
            profile(126, 80, peak, 3.2, .75, 45);
            assertEquals(0, count());
        }
    }

    @Test
    public void searchClippingDoesNotMakeIsolatedBody() throws Exception {
        profile(141, 80, 165, 2, 2.3, 0);
        assertEquals(0, count());
    }

    @Test
    public void aboveHeadProfileKeepsItsOtherRole() throws Exception {
        profile(126, 96, 165, 2, 2.3, 0);
        assertEquals(0, count());
    }

    @Test
    public void allCandidateInkIsPreserved() throws Exception {
        profile(126, 80, 165, 2, 2.3, 0);
        byte[] before = gray.clone();
        count();
        assertArrayEquals(before, gray);
    }
}
