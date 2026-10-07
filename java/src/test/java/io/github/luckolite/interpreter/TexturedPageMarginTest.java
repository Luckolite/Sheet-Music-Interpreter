// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated texture and rule geometry; no score or photograph pixels. */
public class TexturedPageMarginTest {
    private static final int W = 800, H = 700;

    private byte[][] page() {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        Random random = new Random(7307);
        for (int y = 15; y < 112; y++)
            for (int x = 0; x < W; x++) gray[y * W + x] = (byte) (55 + random.nextInt(125));
        for (int top : new int[] {35, 210, 390, 570}) {
            int gap = top == 35 ? 15 : 12;
            for (int line = 0; line < 5; line++)
                for (int x = 25; x < 775; x++) {
                    int at = (top + line * gap) * W + x;
                    labels[at] = 4;
                    if (top != 35) gray[at] = 100;
                }
            int cx = 200, cy = top + gap * 3;
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if (Math.pow((x - cx) / 7., 2) + Math.pow((y - cy) / 4., 2) <= 1) {
                        labels[y * W + x] = 2;
                        gray[y * W + x] = 0;
                    }
            for (int y = cy - gap * 3; y < cy; y++) {
                labels[y * W + cx + 7] = 1;
                gray[y * W + cx + 7] = 0;
            }
        }
        return new byte[][] {labels, gray};
    }

    @Test
    public void texturedMarginDoesNotAddAStaffOrMeasure() {
        var a = page();
        assertEquals(3, OmrMeasurePostProcessor.process(a[0], a[1], W, H).size());
    }

    @Test
    public void texturedMarginDoesNotEmitAnOpeningNote() {
        var a = page();
        var regions = OmrMeasurePostProcessor.process(a[0], a[1], W, H);
        assertEquals(3, OmrScoreInterpreter.analyze(a[0], a[1], W, H, regions).notes().size());
    }

    @Test
    public void preservesCallerPixelsAndLabels() {
        var a = page();
        byte[] labels = a[0].clone(), gray = a[1].clone();
        var regions = OmrMeasurePostProcessor.process(a[0], a[1], W, H);
        OmrScoreInterpreter.analyze(a[0], a[1], W, H, regions);
        assertArrayEquals(labels, a[0]);
        assertArrayEquals(gray, a[1]);
    }

    @Test
    public void enclosedWeakSemanticRowSurvivesObscuredPrintedRules() {
        var e = new PrintedStaffAdmission.Evidence(500, 0, 0, 36, 6000, 6000, 100);
        assertFalse(e.admitted());
        assertTrue(PrintedStaffAdmission.admitted(e, 300, 100, 500));
    }

    @Test
    public void exteriorTextureIsNotAnEnclosedStaff() {
        var e = new PrintedStaffAdmission.Evidence(500, 0, 0, 36, 6000, 6000, 100);
        assertFalse(PrintedStaffAdmission.admitted(e, 50, 100, 500));
        assertFalse(PrintedStaffAdmission.admitted(e, 550, 100, 500));
    }
}
