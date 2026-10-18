// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original faint five-rule counterproof with an independently paper-separated central patch. */
public class FaintParallelTenutoRailTest {
    private static final int W = 1000, H = 400, X = 260;

    private static int mark(int tone) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {160, 176, 192, 208, 224})
            for (int x = 80; x <= 620; x++) {
                if (y == 192 && (x == 248 || x == 249 || x == 271 || x == 272)) continue;
                gray[y * W + x] = (byte) tone;
                labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        for (int y = 191; y <= 193; y++)
            for (int x = 250; x <= 270; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        // A whole-note oval on the bottom staff line: no missing ledger or invented shaft.
        for (int dy = -6; dy <= 6; dy++)
            for (int dx = -9; dx <= 9; dx++) {
                double outer = dx * dx / 81.0 + dy * dy / 36.0;
                if (outer <= 1) {
                    boolean counter = dx * dx / 36.0 + dy * dy / 9.0 < 1;
                    gray[(224 + dy) * W + X + dx] = counter ? (byte) 255 : 0;
                    labels[(224 + dy) * W + X + dx] =
                            counter ? 0 : OmrMeasurePostProcessor.NOTEHEAD;
                }
            }
        byte[] g = gray.clone(), l = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(X, 224, 16, 0)))[0];
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
        return result;
    }

    @Test
    public void faintFiveRulesKeepTheirSeparatedCentralPatch() {
        assertEquals(0, mark(200) & NoteArticulation.TENUTO);
        assertEquals(0, mark(230) & NoteArticulation.TENUTO);
    }
}
