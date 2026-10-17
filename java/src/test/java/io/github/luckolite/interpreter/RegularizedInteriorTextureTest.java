// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural photo texture; no score pixels or source coordinates. */
public class RegularizedInteriorTextureTest {
    static final int W = 1600, H = 400;
    static final int[] A = {50, 66, 82, 98, 114},
            B = {160, 175, 185, 198, 212},
            C = {270, 286, 302, 318, 334};
    byte[] labels = new byte[W * H], gray = new byte[W * H];

    public RegularizedInteriorTextureTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 135; y < 242; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] =
                        (byte) Math.round(185 + 20 * Math.sin(x / 3.0) + 10 * Math.cos(y / 30.0));
        for (int[] rows : new int[][] {A, B, C})
            for (int y : rows) for (int x = 20; x < W - 20; x++) labels[y * W + x] = 4;
        draw(A, 20, W - 20, 0);
        draw(C, 20, W - 20, 0);
        // Three photo edges support irregular selected peaks, but not their emitted regular frame.
        draw(new int[] {165, 180, 193}, 600, 730, 70);
    }

    void draw(int[] rows, int left, int right, int value) {
        for (int y : rows) for (int x = left; x < right; x++) gray[y * W + x] = (byte) value;
    }

    int count() {
        return OmrMeasurePostProcessor.process(labels, gray, W, H).size();
    }

    @Test
    public void photoEdgesDoNotAuthorizeAnEmptyRegularizedStaff() {
        assertEquals(2, count());
    }

    @Test
    public void actualIrregularFiveRuleStaffIsRetained() {
        draw(B, 20, W - 20, 0);
        assertEquals(3, count());
    }

    @Test
    public void faintShortFiveRulesAreRetained() {
        for (int y = 150; y < 222; y++)
            for (int x = 595; x < 735; x++) gray[y * W + x] = (byte) 255;
        draw(new int[] {160, 173, 186, 199, 212}, 600, 730, 245);
        assertEquals(3, count());
    }

    @Test
    public void emptyPrintedStaffIsRetained() {
        draw(new int[] {160, 173, 186, 199, 212}, 20, W - 20, 0);
        assertEquals(3, count());
    }

    @Test
    public void restOnlyPrintedStaffIsRetained() {
        draw(new int[] {160, 173, 186, 199, 212}, 20, W - 20, 0);
        for (int y = 178; y < 196; y++)
            for (int x = 410; x < 418; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = 1;
            }
        assertEquals(3, count());
    }

    @Test
    public void absentRasterPreservesEstablishedMergedGeometry() {
        assertEquals(1, OmrMeasurePostProcessor.process(labels, null, W, H).size());
    }

    @Test
    public void unchangedRasterAndLabels() {
        byte[] g = gray.clone(), l = labels.clone();
        count();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void onePrintedNeighborCannotProveInteriorTexture() {
        draw(C, 0, W, 255);
        int[] strength = new int[H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) if (labels[y * W + x] == 4) strength[y]++;
        assertEquals(3, RawStaffLineDetector.detectFromStrength(strength, 10, H, gray, W).size());
    }
}
