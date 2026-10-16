// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original finite beam fixtures across bright paper levels. */
public class BrightShortRuleBeamTest {
    @Test
    public void darkShortBeamRetainsInkBeyondRuleBudget() throws Exception {
        for (int paper : new int[] {190, 220, 250}) {
            var fixture = new BlurredPrintedStaffBandTest();
            fixture.draw(1, 4, paper);
            assertEquals("paper=" + paper, 1, fixture.count());
        }
    }

    @Test
    public void paleShortBeamRetainsInkBeyondRuleBudget() throws Exception {
        for (int paper : new int[] {190, 220, 250}) {
            var fixture = new BlurredPrintedStaffBandTest();
            fixture.draw(3, 4, paper);
            assertEquals("paper=" + paper, 1, fixture.count());
        }
    }
}
