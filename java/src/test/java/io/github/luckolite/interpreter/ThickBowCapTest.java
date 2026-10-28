// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

/** Procedural thick V with a short cap overtrace; no score-derived pixels. */
public final class ThickBowCapTest {
    private static List<ScoreTechniqueChange> read(byte[] raw) {
        int width = BowArmCompletenessTest.W, height = BowArmCompletenessTest.H;
        byte[] original = raw.clone();
        byte[] labels = new byte[raw.length];
        var result =
                BowDirectionDetector.detect(
                        labels,
                        raw,
                        width,
                        height,
                        List.of(new PlayingTechniqueDetector.Staff(160, 240, 20, 0, 1)),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(BowArmCompletenessTest.note()));
        assertArrayEquals(original, raw);
        return result;
    }

    private static void dark(byte[] raw, int y, int left, int right) {
        for (int x = left; x <= right; x++) raw[y * BowArmCompletenessTest.W + x] = 0;
    }

    @Test
    public void thickVWithLiteralCapOvertrace() {
        byte[] raw = BowArmCompletenessTest.scene(9);
        dark(raw, 82, 247, 250);
        dark(raw, 83, 246, 251);
        dark(raw, 84, 244, 251);
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                read(raw));
    }

    @Test
    public void mediumVWithLiteralCapOvertrace() {
        byte[] raw = BowArmCompletenessTest.scene(1);
        dark(raw, 82, 245, 248);
        dark(raw, 83, 244, 249);
        dark(raw, 84, 241, 249);
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                read(raw));
    }

    @Test
    public void sustainedCentralThirdArmRefuses() {
        byte[] raw = BowArmCompletenessTest.scene(9);
        for (int y = 82; y <= 93; y++) dark(raw, y, 248, 251);
        dark(raw, 94, 242, 251);
        assertTrue(read(raw).isEmpty());
    }

    @Test
    public void shortCentralCrossToBothRefuses() {
        byte[] raw = BowArmCompletenessTest.scene(9);
        dark(raw, 82, 247, 250);
        dark(raw, 83, 246, 251);
        dark(raw, 84, 243, 258);
        assertTrue(read(raw).isEmpty());
    }

    @Test
    public void diagonalCrossContactBeforeFinalMergeRefuses() {
        byte[] raw = BowArmCompletenessTest.scene(9);
        dark(raw, 82, 247, 250);
        dark(raw, 83, 246, 251);
        dark(raw, 84, 244, 251);
        dark(raw, 102, 250, 251);
        raw[102 * BowArmCompletenessTest.W + 252] = (byte) 255;
        assertTrue(read(raw).isEmpty());
    }
}
