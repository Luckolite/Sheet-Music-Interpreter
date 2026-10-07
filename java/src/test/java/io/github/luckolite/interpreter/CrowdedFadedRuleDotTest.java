// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

/** Original staff-rule occlusions; no private score pixels or coordinates. */
public class CrowdedFadedRuleDotTest extends FadedRuleDotTest {
    void crowd(int width) {
        for (int line : new int[] {80, 144})
            for (int x = 128 - width / 2; x < 128 + (width + 1) / 2; x++)
                for (int y = line - 7; y <= line + 7; y++) gray[y * W + x] = 80;
    }

    @Test
    public void surroundingGlyphInkDoesNotTurnRuleFleckIntoDot() throws Exception {
        for (int width : new int[] {25, 27, 29}) {
            Arrays.fill(gray, (byte) 255);
            rules(5);
            crowd(width);
            fleck(112);
            assertEquals("occlusion=" + width, 0, dots(104));
        }
    }

    @Test
    public void realSmallSpaceDotSurvivesCrowdedRules() throws Exception {
        for (int width : new int[] {25, 27, 29}) {
            Arrays.fill(gray, (byte) 255);
            rules(5);
            crowd(width);
            fleck(104);
            assertEquals(1, dots(104));
        }
    }

    @Test
    public void realRoundDotAcrossRuleSurvivesCrowdedRules() throws Exception {
        rules(5);
        crowd(29);
        dot(128, 112, 3);
        assertEquals(1, dots(104));
    }

    @Test
    public void onlyTwoActualRulesCannotSupplyStaffPhase() throws Exception {
        rules(2);
        fleck(96);
        assertEquals(1, dots(104));
    }

    @Test
    public void crowdedRuleAnalysisLeavesPixelsUntouched() throws Exception {
        rules(5);
        crowd(27);
        fleck(112);
        byte[] before = gray.clone();
        dots(104);
        assertArrayEquals(before, gray);
    }
}
