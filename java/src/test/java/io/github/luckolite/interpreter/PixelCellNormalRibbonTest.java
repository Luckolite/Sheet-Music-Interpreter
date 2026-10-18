// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Distinct conservative metric original; first point-centre5.6 original remains unchanged. */
public class PixelCellNormalRibbonTest {
    @Test
    public void completeRibbonFitsEvenTheFullUnitSquareNormalBudget() {
        // Every entire unit square lies <=2.25+sqrt(.5) from the authored finite curve.
        // Thus full Euclidean tube width<=4.5+sqrt(2)<6, including raster phase.
        assertTrue(4.5 + Math.sqrt(2) < 6);
        for (boolean mirror : new boolean[] {false, true})
            assertTrue(
                    "Complete finite returning ribbon with full pixel-cell budget<6",
                    FiniteNormalRibbonTest.complete(
                            FiniteNormalRibbonTest.find(
                                    FiniteNormalRibbonTest.ribbon(2.25, mirror, false))));
    }
}
