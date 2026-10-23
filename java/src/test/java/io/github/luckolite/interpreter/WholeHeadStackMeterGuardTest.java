// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Procedural whole-note stack ownership; independent of retained score pixels. */
public class WholeHeadStackMeterGuardTest {
    private static final int W = 400, H = 240;

    private static final class Page {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(int[] centers) {
            Arrays.fill(gray, (byte) 255);
            for (int y = 80; y <= 144; y += 16)
                for (int x = 20; x < 380; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = 4;
                }
            // Clef-context evidence, kept left of the note stack.
            for (int y = 50; y <= 170; y++)
                for (int x = 40; x <= 75; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = 3;
                }
            for (int cy : centers)
                for (int y = cy - 9; y <= cy + 9; y++)
                    for (int x = 118; x <= 142; x++) {
                        double oval = Math.pow((x - 130) / 12.0, 2) + Math.pow((y - cy) / 9.0, 2);
                        if (oval <= 1) {
                            labels[y * W + x] = 2;
                            double hole =
                                    Math.pow((x - 130) / 5.0, 2) + Math.pow((y - cy) / 4.5, 2);
                            gray[y * W + x] = hole < 1 ? (byte) 255 : 0;
                        }
                    }
        }

        boolean roundedMeter() throws Exception {
            int minY = H, maxY = -1, area = 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    if (labels[y * W + x] == 2) {
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        area++;
                    }
            Object head =
                    construct("Component", area, 118, 142, minY, maxY, 130f, (minY + maxY) * .5f);
            Object staff = construct("Staff", 80f, 144f, 16f);
            Object glyph = construct("Component", 900, 40, 75, 50, 170, 57f, 110f);
            var method =
                    Arrays.stream(OmrScoreInterpreter.class.getDeclaredMethods())
                            .filter(m -> m.getName().equals("isRoundedHeaderMeter"))
                            .findFirst()
                            .orElseThrow();
            method.setAccessible(true);
            return (boolean)
                    method.invoke(null, labels, gray, W, H, head, List.of(staff), List.of(glyph));
        }

        private static Object construct(String type, Object... args) throws Exception {
            var c =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$" + type)
                            .getDeclaredConstructors()[0];
            c.setAccessible(true);
            return c.newInstance(args);
        }
    }

    @Test
    public void threeWholeHeadSpacesOnThePitchGridAreNotRoundedMeter() throws Exception {
        assertFalse(new Page(new int[] {104, 120, 136}).roundedMeter());
    }

    @Test
    public void threeWholeHeadLinesOnThePitchGridAreNotRoundedMeter() throws Exception {
        assertFalse(new Page(new int[] {96, 112, 128}).roundedMeter());
    }

    @Test
    public void twoWholeHeadsAreNotMistakenForACompleteRoundedMeterStack() throws Exception {
        assertFalse(new Page(new int[] {120, 136}).roundedMeter());
    }

    @Test
    public void nonPitchAlignedRoundedTextStillUsesMeterRecognition() throws Exception {
        assertTrue(new Page(new int[] {100, 120, 137}).roundedMeter());
    }
}
