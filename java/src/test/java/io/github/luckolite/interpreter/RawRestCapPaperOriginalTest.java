// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

/** Rule-adjacent paper remains a real component separator at both raster densities. */
public class RawRestCapPaperOriginalTest {
    private void closed(int scale) {
        var s = new RawBeamedTwoBulbRestTest.Scene(scale, 8, false);
        s.box(201, 118, 207, 120);
        s.positive();
    }

    @Test
    public void rawPaperBelowRuleSeparatesAnUnrelatedUpperStroke() {
        closed(1);
    }

    @Test
    public void rawPaperSeparationSurvivesDoubledRasterDensity() {
        closed(2);
    }
}
