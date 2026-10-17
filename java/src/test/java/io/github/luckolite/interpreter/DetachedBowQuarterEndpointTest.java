// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Original midtone shaft and detached bow; no score pixels. */
public class DetachedBowQuarterEndpointTest {
    private final PaleStemEndpointTest f = new PaleStemEndpointTest();

    void scene() {
        f.cx = 100;
        f.cy = 120;
        f.rect(109, 111, 58, 120, 185);
        f.rect(109, 111, 53, 57, 235);
        f.rect(90, 112, 25, 30, 185);
        f.rect(90, 92, 31, 52, 185);
        f.rect(110, 112, 31, 52, 185);
        f.rect(0, f.W - 1, 88, 89, 35);
        for (int y = 88; y <= 120; y++) f.l[y * f.W + 110] = OmrMeasurePostProcessor.STEM_OR_REST;
    }

    @Test
    public void detachedBowCannotExtendQuarterShaft() throws Exception {
        scene();
        assertEquals(0, f.beams());
    }

    @Test
    public void realBeamBeneathBowIsPreserved() throws Exception {
        scene();
        f.rect(110, 175, 58, 63, 35);
        assertEquals(1, f.beams());
    }

    @Test
    public void realDoubleBeamBeneathBowIsPreserved() throws Exception {
        scene();
        f.rect(110, 175, 58, 63, 35);
        f.rect(110, 175, 70, 75, 35);
        assertEquals(2, f.beams());
    }
}
