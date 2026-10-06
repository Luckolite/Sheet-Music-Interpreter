// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.ArrayList;
import org.junit.Test;

public class OpenChevronTest {
    private interface Ink {
        boolean at(int x, int y);
    }

    private int[] raster(int w, int h, Ink shape) {
        var result = new ArrayList<Integer>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) if (shape.at(x, y)) result.add(y * 64 + x);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean match(int w, int h, Ink shape) {
        return OpenChevron.matches(raster(w, h, shape), 64, 0, 0, w - 1, h - 1);
    }

    private boolean arms(int x, int y, int w, int h) {
        double upper = 1 + (h * .5 - 1.5) * x / (w - 1),
                lower = h - 2 - (h * .5 - 1.5) * x / (w - 1);
        return Math.abs(y - upper) <= 1.25 || Math.abs(y - lower) <= 1.25;
    }

    @Test
    public void independentlyPrintedTwoArmAccent() {
        assertTrue(match(25, 13, (x, y) -> arms(x, y, 25, 13)));
    }

    @Test
    public void smallerPrintedAccent() {
        assertTrue(match(19, 11, (x, y) -> arms(x, y, 19, 11)));
    }

    @Test
    public void reverseChevronIsNotAccent() {
        assertFalse(match(25, 13, (x, y) -> arms(24 - x, y, 25, 13)));
    }

    @Test
    public void solidTriangleHasNoOpenArms() {
        assertFalse(match(25, 13, (x, y) -> Math.abs(y - 6) <= 6 - x * .22));
    }

    @Test
    public void twoParallelDashesDoNotConverge() {
        assertFalse(match(25, 13, (x, y) -> y <= 2 || y >= 10));
    }

    @Test
    public void singleSlopingStrokeHasNoSecondArm() {
        assertFalse(match(25, 13, (x, y) -> Math.abs(y - (1 + x * .2)) <= 1.25));
    }

    @Test
    public void rectangleFails() {
        assertFalse(match(25, 13, (x, y) -> true));
    }

    @Test
    public void disconnectedTipFails() {
        assertFalse(match(25, 13, (x, y) -> x != 18 && arms(x, y, 25, 13)));
    }

    @Test
    public void thirdStrokeFails() {
        assertFalse(match(25, 13, (x, y) -> arms(x, y, 25, 13) || y == 6 && x < 8));
    }

    @Test
    public void pixelsAreUnchanged() {
        int[] p = raster(25, 13, (x, y) -> arms(x, y, 25, 13)), copy = p.clone();
        assertTrue(OpenChevron.matches(p, 64, 0, 0, 24, 12));
        assertArrayEquals(copy, p);
    }

    @Test
    public void ownedPrefixIgnoresPoisonedTailWithoutChangingFullArrayApi() {
        int[] pixels = raster(25, 13, (x, y) -> arms(x, y, 25, 13));
        int[] queue = java.util.Arrays.copyOf(pixels, pixels.length + 7);
        java.util.Arrays.fill(queue, pixels.length, queue.length, Integer.MIN_VALUE);
        int[] before = queue.clone();
        assertTrue(OpenChevron.matches(queue, 64, 0, 0, 24, 12, pixels.length));
        assertFalse(OpenChevron.matches(queue, 64, 0, 0, 24, 12));
        assertFalse(OpenChevron.matches(queue, 64, 0, 0, 24, 12, 0));
        assertArrayEquals(before, queue);
    }
}
