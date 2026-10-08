// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

/** Original synthetic drawings; no private photograph pixels or score coordinates. */
public final class RegionalStaffSeedsTest {
    private record Page(int width, int height, byte[] labels, byte[] gray) {}

    private static Page page(int width, int height, int paper) {
        byte[] labels = new byte[width * height], gray = new byte[labels.length];
        Arrays.fill(gray, (byte) paper);
        return new Page(width, height, labels, gray);
    }

    private static void rule(
            Page p,
            float center,
            float gap,
            float slope,
            int firstLine,
            int lineCount,
            boolean label,
            boolean print) {
        for (int line = firstLine; line < firstLine + lineCount; line++)
            for (int x = 45; x < p.width - 35; x++) {
                int y = Math.round(center + line * gap + slope * (x - p.width * .5f));
                if (y >= 0 && y < p.height) {
                    if (label) p.labels[y * p.width + x] = 4;
                    if (print) p.gray[y * p.width + x] = 40;
                }
            }
    }

    private static void assertNine(float[] slopes) {
        Page p = page(520, 1400, 185);
        for (int i = 0; i < 9; i++)
            rule(p, 105 + i * 135, 9 + i * .25f, slopes[i], 0, 5, true, true);
        var seeds = RegionalStaffSeeds.detect(p.labels, p.gray, p.width, p.height);
        assertEquals(9, seeds.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(slopes[i], seeds.get(i).slope(), .002f);
            assertEquals(9 + i * .25f, seeds.get(i).gap(), .5f);
            assertEquals(105 + i * 135, seeds.get(i).top(), 1.1f);
        }
    }

    @Test
    public void positivePerspectiveRowsKeepTheirIndependentSlopeOnDarkPaper() {
        assertNine(new float[] {.018f, .024f, .031f, .038f, .045f, .052f, .058f, .064f, .07f});
    }

    @Test
    public void negativePerspectiveRowsRecoverFirstAndLastWithoutMeasureSeeds() {
        assertNine(
                new float[] {
                    -.103f, -.094f, -.085f, -.076f, -.066f, -.057f, -.048f, -.038f, -.025f
                });
    }

    @Test
    public void fourPrintedRulesCannotProveASemanticFiveRulePhase() {
        Page p = page(480, 360, 180);
        rule(p, 130, 10, .044f, 0, 5, true, false);
        rule(p, 130, 10, .044f, 0, 4, false, true);
        assertTrue(RegionalStaffSeeds.detect(p.labels, p.gray, p.width, p.height).isEmpty());
    }

    @Test
    public void OneGapShiftCannotBorrowFourRulesFromTheActualStaff() {
        Page p = page(480, 360, 180);
        rule(p, 130, 10, -.062f, 0, 5, false, true);
        rule(p, 130, 10, -.062f, 1, 5, true, false);
        assertTrue(RegionalStaffSeeds.detect(p.labels, p.gray, p.width, p.height).isEmpty());
    }

    @Test
    public void exteriorCarpetLabelsCannotBecomeAnotherStaff() {
        Page p = page(520, 600, 180);
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < p.width; x++) {
                p.gray[y * p.width + x] = (byte) (((x + y) & 1) == 0 ? 30 : 150);
                p.labels[y * p.width + x] = 4;
            }
        rule(p, 310, 10, .032f, 0, 5, true, true);
        var seeds = RegionalStaffSeeds.detect(p.labels, p.gray, p.width, p.height);
        assertEquals(1, seeds.size());
        assertEquals(310, seeds.get(0).top(), 1.1f);
    }

    @Test
    public void broadSemanticPaintWithoutThinPrintedRulesIsRejected() {
        Page p = page(520, 600, 70);
        Arrays.fill(p.labels, (byte) 4);
        assertTrue(RegionalStaffSeeds.detect(p.labels, p.gray, p.width, p.height).isEmpty());
    }

    @Test
    public void malformedInputDoesNotGenerateGeometry() {
        assertTrue(RegionalStaffSeeds.detect(null, new byte[20], 5, 4).isEmpty());
        assertTrue(RegionalStaffSeeds.detect(new byte[20], new byte[19], 5, 4).isEmpty());
    }
}
