// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

/** Original wider gray fringes leave the exact same two dark straight arm cores. */
public class FringedHairpinApertureOriginalTest {
    static HairpinApertureOriginalTest.Scene scene(int direction, boolean bar) {
        var s = new HairpinApertureOriginalTest.Scene(direction, bar, false);
        for (int y = 150; y <= 190; y++)
            for (int x = 100; x <= 850; x++) {
                double sum = 0;
                for (int sy = 0; sy < 4; sy++)
                    for (int sx = 0; sx < 4; sx++) {
                        double xx = x + (sx + .5) / 4 - .5,
                                yy = y + (sy + .5) / 4 - .5,
                                t = direction > 0 ? xx - 100 : 850 - xx;
                        double distance = Math.abs(Math.abs(yy - 170) - .012 * t);
                        sum += t >= 0 && t <= 750 && distance <= 2.6 ? 190 : 255;
                    }
                int at = y * s.w + x;
                s.gray[at] = (byte) Math.min(s.gray[at] & 255, Math.round(sum / 16.));
            }
        return s;
    }

    @Test
    public void widerGrayCrescendoFringeDoesNotTurnTheMergedTipIntoInteriorInk() {
        scene(1, true).positive(1);
    }

    @Test
    public void widerGrayDiminuendoFringeDoesNotTurnTheMergedTipIntoInteriorInk() {
        scene(-1, true).positive(-1);
    }

    @Test
    public void actualPaperApertureIsIndependentOfAPrintedBar() {
        scene(1, false).positive(1);
    }
}
