// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rotated raster primitives; no score images or score coordinates. */
public class RotatedAugmentationBodyTest {
    static final int W = 240, H = 160;
    byte[] gray = new byte[W * H];

    public RotatedAugmentationBodyTest() {
        Arrays.fill(gray, (byte) 250);
    }

    void ellipse(int cx, int cy, double major, double minor, double angle, int tone) {
        double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 8; x <= cx + 8; x++) {
                double u = (x - cx) * c + (y - cy) * s;
                double v = -(x - cx) * s + (y - cy) * c;
                if (u * u / (major * major) + v * v / (minor * minor) <= 1)
                    gray[y * W + x] = (byte) tone;
            }
    }

    int count() throws Exception {
        return count(16f);
    }

    int count(float gap) throws Exception {
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(180, 90, 110, 73, 87, 100f, 80f);
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
        return (int) m.invoke(null, List.of(), head, gap, gray, W, H, false);
    }

    @Test
    public void compactDiagonalEndsAreNotDots() throws Exception {
        for (double angle : new double[] {35, 45, 55, 125, 135, 145}) {
            Arrays.fill(gray, (byte) 250);
            ellipse(126, 80, 4.5, 1.8, angle, 120);
            assertEquals("angle=" + angle, 0, count());
        }
    }

    @Test
    public void paleDiagonalBodyAndCoreAreNotDots() throws Exception {
        for (double angle : new double[] {45, 135}) {
            Arrays.fill(gray, (byte) 250);
            ellipse(126, 80, 4.5, 2, angle, 170);
            ellipse(126, 80, 3.5, 1.5, angle, 145);
            assertEquals("angle=" + angle, 0, count());
        }
    }

    @Test
    public void rotatedRoundDotsRemain() throws Exception {
        for (double angle : new double[] {0, 30, 45, 60, 90, 135}) {
            Arrays.fill(gray, (byte) 250);
            ellipse(126, 80, 3.2, 2.6, angle, 120);
            assertEquals("angle=" + angle, 1, count());
        }
    }

    @Test
    public void separateTrueDotSurvivesNextToDiagonal() throws Exception {
        ellipse(122, 80, 2.7, 2.7, 0, 100);
        ellipse(135, 80, 4.5, 1.8, 45, 120);
        assertEquals(1, count());
    }

    @Test
    public void faintRoundBodyAndCoreRemain() throws Exception {
        ellipse(126, 80, 3.5, 3.2, 45, 195);
        ellipse(126, 80, 2.2, 2.2, 45, 165);
        assertEquals(1, count());
    }

    @Test
    public void diagonalInkIsNeverErased() throws Exception {
        ellipse(126, 80, 4.5, 1.8, 45, 120);
        byte[] before = gray.clone();
        count();
        assertArrayEquals(before, gray);
    }

    void smallUnresolvedComponent(int size) throws Exception {
        Arrays.fill(gray, (byte) 250);
        gray[80 * W + 116] = 100;
        if (size == 2) gray[81 * W + 116] = 100;
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(30, 100, 110, 76, 84, 105f, 80f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findDarkDotComponents",
                        byte[].class,
                        int.class,
                        int.class,
                        type,
                        float.class,
                        int.class);
        m.setAccessible(true);
        assertEquals(1, ((List<?>) m.invoke(null, gray, W, H, head, 6f, 135)).size());
    }

    @Test
    public void onePixelComponentKeepsExistingAdmission() throws Exception {
        smallUnresolvedComponent(1);
    }

    @Test
    public void twoPixelComponentKeepsExistingAdmission() throws Exception {
        smallUnresolvedComponent(2);
    }

    @Test
    public void threePixelRoundDotAtSmallScaleKeepsExactBoundary() throws Exception {
        gray[80 * W + 116] = 100;
        gray[80 * W + 117] = 100;
        gray[81 * W + 116] = 100;
        assertEquals(1, count(7f));
    }
}
