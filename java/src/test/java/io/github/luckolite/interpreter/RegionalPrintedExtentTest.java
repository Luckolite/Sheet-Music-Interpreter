// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original procedural staff tails, without private score pixels or coordinates. */
public final class RegionalPrintedExtentTest {
    private record Page(int width, int height, byte[] labels, byte[] gray) {}

    private static Page drawing(float slope, int faintRules, boolean texture, boolean interrupted) {
        Page p = new Page(720, 480, new byte[720 * 480], new byte[720 * 480]);
        Arrays.fill(p.gray, (byte) 240);
        for (int line = 0; line < 5; line++)
            for (int x = 55; x <= 520; x++) {
                int y =
                        Math.round(
                                80 + line * 10 - Math.signum(slope) * .087f * (x - p.width * .5f));
                p.gray[y * p.width + x] = 40;
                p.labels[y * p.width + x] = 4;
            }
        for (int line = 0; line < 5; line++)
            for (int x = 55; x <= 675; x++) {
                float dx = (x - p.width * .5f) / p.width;
                int y = Math.round(280 + line * 10 + slope * (x - p.width * .5f) + dx * dx * 10);
                boolean tail = x >= 540;
                if (!tail || line < faintRules) {
                    if (!interrupted || !tail || x % 43 >= 9)
                        p.gray[y * p.width + x] = (byte) (tail ? 210 : 40);
                    if (!tail) p.labels[y * p.width + x] = 4;
                }
            }
        if (texture)
            for (int y = 235; y < 370; y++)
                for (int x = 550; x < 710; x++)
                    p.gray[y * p.width + x] = (byte) (((x + y) & 1) == 0 ? 190 : 240);
        for (int x : new int[] {55, 325})
            for (int lineY = 0; lineY <= 40; lineY++) {
                float dx = (x - p.width * .5f) / p.width;
                int y = Math.round(280 + lineY + slope * (x - p.width * .5f) + dx * dx * 10);
                p.gray[y * p.width + x] = 35;
                p.labels[y * p.width + x] = 1;
            }
        if (faintRules == 5 && !texture)
            for (int lineY = 0; lineY <= 40; lineY++) {
                float dx = (675 - p.width * .5f) / p.width;
                int y = Math.round(280 + lineY + slope * (675 - p.width * .5f) + dx * dx * 10);
                p.gray[y * p.width + 675] = 35;
                p.labels[y * p.width + 675] = 1;
            }
        return p;
    }

    private static float right(Page p) {
        var measures = OmrMeasurePostProcessor.process(p.labels, p.gray, p.width, p.height);
        assertFalse(measures.isEmpty());
        return measures.get(measures.size() - 1).right() * p.width;
    }

    @Test
    public void faintFiveRuleTailKeepsClosingExtentOnPositiveSlope() {
        assertTrue(right(drawing(.047f, 5, false, false)) >= 660);
    }

    @Test
    public void faintFiveRuleTailKeepsClosingExtentOnNegativeSlope() {
        assertTrue(right(drawing(-.053f, 5, false, false)) >= 660);
    }

    @Test
    public void occludedFaintRulesKeepTheirPhysicalExtent() {
        assertTrue(right(drawing(.047f, 5, false, true)) >= 660);
    }

    @Test
    public void twoPaleBeamRowsCannotExtendStaffToTheFarBar() {
        assertTrue(right(drawing(.047f, 2, false, false)) < 560);
    }

    @Test
    public void fourPaleRulesCannotSupplyTheMissingFifth() {
        assertTrue(right(drawing(-.053f, 4, false, false)) < 560);
    }

    @Test
    public void denseExteriorTextureCannotSupplyFiveRules() {
        assertTrue(right(drawing(.047f, 0, true, false)) < 560);
    }

    @Test
    public void semanticClosingWithoutPrintedInkCannotExtendAStaff() {
        Page p = drawing(.047f, 5, false, false);
        for (int y = 0; y < p.height; y++) p.gray[y * p.width + 675] = (byte) 240;
        assertTrue(right(p) < 560);
    }

    @Test
    public void incompletePrintedClosingCannotExtendAStaff() {
        Page p = drawing(.047f, 5, false, false);
        for (int y = 310; y < p.height; y++) {
            p.gray[y * p.width + 675] = (byte) 240;
            p.labels[y * p.width + 675] = 0;
        }
        assertTrue(right(p) < 560);
    }

    @Test
    public void paleClosingKeepsBothStemOwnedDyadCentersInsideFinalMeasure() {
        float slope = .047f;
        Page p = drawing(slope, 5, false, false);
        int cx = 620;
        float dx = (cx - p.width * .5f) / p.width;
        int top = Math.round(280 + slope * (cx - p.width * .5f) + dx * dx * 10);
        for (int cy : new int[] {top - 5, top + 5})
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if ((x - cx) * (x - cx) / 49f + (y - cy) * (y - cy) / 25f <= 1) {
                        p.gray[y * p.width + x] = 30;
                        p.labels[y * p.width + x] = 2;
                    }
        for (int y = top - 5; y <= top + 40; y++) {
            p.gray[y * p.width + cx - 6] = 30;
            p.labels[y * p.width + cx - 6] = 1;
        }
        var measures = OmrMeasurePostProcessor.process(p.labels, p.gray, p.width, p.height);
        assertEquals(3, measures.size());
        var last = measures.get(measures.size() - 1);
        assertTrue(last.left() * p.width < cx && last.right() * p.width > cx + 7);
        var score = OmrScoreInterpreter.analyze(p.labels, p.gray, p.width, p.height, measures);
        assertEquals(2, score.notes().size());
    }

    @Test
    public void closingBarBeyondQuarterPageExtensionCapIsIgnored() {
        float slope = .047f;
        Page p = drawing(slope, 5, false, false);
        for (int line = 0; line < 5; line++)
            for (int x = 450; x < 540; x++) {
                float dx = (x - p.width * .5f) / p.width;
                int y = Math.round(280 + line * 10 + slope * (x - p.width * .5f) + dx * dx * 10);
                p.gray[y * p.width + x] = (byte) 210;
                p.labels[y * p.width + x] = 0;
            }
        assertTrue(right(p) < 500);
    }

    @Test
    public void courtesySharpAfterPaleTailCannotSupplyAClosingBar() {
        float slope = .047f;
        Page p = drawing(slope, 5, false, false);
        for (int y = 0; y < p.height; y++) {
            p.gray[y * p.width + 675] = (byte) 240;
            p.labels[y * p.width + 675] = 0;
        }
        int cx = 647;
        float dx = (cx - p.width * .5f) / p.width;
        int top = Math.round(280 + slope * (cx - p.width * .5f) + dx * dx * 10);
        for (int y = top + 4; y <= top + 31; y++)
            for (int x : new int[] {cx, cx + 6}) {
                p.gray[y * p.width + x] = 40;
                p.labels[y * p.width + x] = 3;
            }
        for (int y : new int[] {top + 12, top + 22})
            for (int x = cx - 3; x <= cx + 10; x++) {
                p.gray[y * p.width + x] = 40;
                p.labels[y * p.width + x] = 3;
            }
        assertTrue(right(p) < 560);
    }

    @Test
    public void fullHeightPlayedStemCannotSupplyAClosingBar() {
        float slope = .047f;
        Page p = drawing(slope, 5, false, false);
        for (int y = 0; y < p.height; y++) {
            p.gray[y * p.width + 675] = (byte) 240;
            p.labels[y * p.width + 675] = 0;
        }
        int cx = 645;
        float dx = (cx - p.width * .5f) / p.width;
        int top = Math.round(280 + slope * (cx - p.width * .5f) + dx * dx * 10);
        int cy = top + 40;
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 6; x <= cx + 6; x++)
                if ((x - cx) * (x - cx) / 36f + (y - cy) * (y - cy) / 16f <= 1) {
                    p.gray[y * p.width + x] = 30;
                    p.labels[y * p.width + x] = 2;
                }
        for (int y = top - 8; y <= cy; y++) {
            p.gray[y * p.width + cx + 5] = 30;
            p.labels[y * p.width + cx + 5] = 1;
        }
        assertTrue(right(p) < 560);
    }
}
