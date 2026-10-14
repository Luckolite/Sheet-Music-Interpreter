// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InternalMeterCropTest {
    static final int W = 320, H = 180, T = 50, G = 16, B = 114;

    static class Page {
        byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
        }

        void rect(int x, int y, int w, int h, int label) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    labels[yy * W + xx] = (byte) label;
                    gray[yy * W + xx] = 0;
                }
        }

        void digits() {
            rect(130, 53, 18, 22, OmrMeasurePostProcessor.SYMBOL);
            rect(130, 89, 18, 22, OmrMeasurePostProcessor.SYMBOL);
        }

        void bar() {
            rect(110, T, 1, B - T + 1, 0);
        }
    }

    @SuppressWarnings("unchecked")
    List<MeterChangeDetector.Crop> crops(Page p, int right) throws Exception {
        var m =
                MeterChangeDetector.class.getDeclaredMethod(
                        "stackedSymbolMeterCrops",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        boolean.class,
                        boolean.class,
                        byte[].class);
        m.setAccessible(true);
        return (List<MeterChangeDetector.Crop>)
                m.invoke(
                        null, p.labels, W, H, 120, right, T, B, (float) G, 0f, false, true, p.gray);
    }

    @Test
    public void independentStackedInkAfterBarlineIsEligible() throws Exception {
        var p = new Page();
        p.digits();
        p.bar();
        assertEquals(1, crops(p, 170).size());
    }

    @Test
    public void trailingCandidateIsFlushedAtRunBoundary() throws Exception {
        var p = new Page();
        p.digits();
        p.bar();
        assertEquals(1, crops(p, 147).size());
    }

    @Test
    public void noHeaderOrBarlineCannotClaimInternalCrop() throws Exception {
        var p = new Page();
        p.digits();
        assertTrue(crops(p, 170).isEmpty());
    }

    @Test
    public void earlierClefCannotReplaceInternalBarline() throws Exception {
        var p = new Page();
        p.digits();
        p.rect(65, 55, 8, 30, OmrMeasurePostProcessor.CLEF_OR_KEY);
        assertTrue(crops(p, 170).isEmpty());
    }

    @Test
    public void partialBarlineCannotAuthorizeCrop() throws Exception {
        var p = new Page();
        p.digits();
        p.rect(110, T, 1, 32, 0);
        assertTrue(crops(p, 170).isEmpty());
    }

    @Test
    public void upperNoteheadContaminationRejectsCrop() throws Exception {
        var p = new Page();
        p.digits();
        p.bar();
        p.rect(135, 60, 8, 8, OmrMeasurePostProcessor.NOTEHEAD);
        assertTrue(crops(p, 170).isEmpty());
    }

    @Test
    public void absentLowerDigitRejectsCrop() throws Exception {
        var p = new Page();
        p.bar();
        p.rect(130, 53, 18, 22, OmrMeasurePostProcessor.SYMBOL);
        assertTrue(crops(p, 170).isEmpty());
    }

    @Test
    public void candidateSearchPreservesInputs() throws Exception {
        var p = new Page();
        p.digits();
        p.bar();
        var l = p.labels.clone();
        var g = p.gray.clone();
        crops(p, 170);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
