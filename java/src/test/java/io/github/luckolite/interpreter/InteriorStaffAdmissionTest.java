// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Generated independent pixels and semantic labels exercise actual measure admission. */
public class InteriorStaffAdmissionTest {
    static final int W = 800, H = 700;

    static byte[][] page(boolean printedCue, int cueGap) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 250);
        for (int top : new int[] {100, 300, 500}) {
            int gap = top == 300 ? cueGap : 14;
            for (int n = 0; n < 5; n++)
                for (int x = 25; x < 775; x++) {
                    int i = (top + n * gap) * W + x;
                    l[i] = 4;
                    if (top != 300 || printedCue) g[i] = (byte) (top == 300 ? 246 : 80);
                }
            int cx = 200, cy = top + 3 * gap;
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if ((x - cx) * (x - cx) / 49. + (y - cy) * (y - cy) / 16. <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                    }
            for (int y = cy - gap * 3; y < cy; y++) {
                l[y * W + cx + 7] = 1;
                g[y * W + cx + 7] = 0;
            }
        }
        return new byte[][] {l, g};
    }

    @Test
    public void unsupportedCompressedInteriorCannotAddMeasure() {
        var p = page(false, 8);
        assertEquals(2, OmrMeasurePostProcessor.process(p[0], p[1], W, H).size());
    }

    @Test
    public void unsupportedInteriorCannotEmitExtraNote() {
        var p = page(false, 8);
        var m = OmrMeasurePostProcessor.process(p[0], p[1], W, H);
        assertEquals(2, OmrScoreInterpreter.analyze(p[0], p[1], W, H, m).notes().size());
    }

    @Test
    public void faintPrintedSmallStaffSurvives() {
        var p = page(true, 8);
        assertEquals(3, OmrMeasurePostProcessor.process(p[0], p[1], W, H).size());
    }

    @Test
    public void sameScaleObscuredStaffSurvives() {
        var p = page(false, 14);
        assertEquals(3, OmrMeasurePostProcessor.process(p[0], p[1], W, H).size());
    }

    @Test
    public void inputPixelsAndLabelsRemainExact() {
        var p = page(false, 8);
        var l = p[0].clone();
        var g = p[1].clone();
        OmrMeasurePostProcessor.process(p[0], p[1], W, H);
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }
}
