// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic down-stem silhouettes; no score image or derived pixel fixture. */
public final class DownStemSymbolBarlineTest {
    @Test
    public void upperRightHeadOwnsDownwardStem() throws Exception {
        Page page = new Page();
        page.upperHead(300, 42);
        assertEquals(List.of(50, 150, 450), page.boundaries());
    }

    @Test
    public void upperRightHeadAtStaffTopOwnsDownwardStem() throws Exception {
        Page page = new Page();
        page.upperHead(300, 59);
        assertEquals(List.of(50, 150, 450), page.boundaries());
    }

    @Test
    public void isolatedRuleSurvivesNearbyUpperSymbol() throws Exception {
        Page page = new Page();
        page.upperHead(150, 42);
        assertEquals(List.of(50, 150, 300, 450), page.boundaries());
    }

    @Test
    public void clippedUpperCurveDoesNotOwnStem() throws Exception {
        Page page = new Page();
        for (int y = 36; y <= 45; y++)
            for (int x = 300; x <= 360; x++) {
                page.labels[y * page.width + x] = OmrMeasurePostProcessor.SYMBOL;
                page.gray[y * page.width + x] = 0;
            }
        assertEquals(List.of(50, 150, 300, 450), page.boundaries());
    }

    @Test
    public void detachedUpperHeadDoesNotOwnStroke() throws Exception {
        Page page = new Page();
        page.upperHead(311, 42);
        assertEquals(List.of(50, 150, 300, 450), page.boundaries());
    }

    private static final class Page {
        final int width = 500, height = 180;
        final byte[] labels = new byte[width * height], gray = new byte[width * height];
        final int[] rows = {60, 74, 88, 102, 116};

        Page() {
            Arrays.fill(gray, (byte) 255);
            for (int row : rows)
                for (int x = 50; x <= 450; x++) {
                    labels[row * width + x] = OmrMeasurePostProcessor.STAFF;
                    gray[row * width + x] = 0;
                }
            for (int y = 60; y <= 116; y++) {
                labels[y * width + 150] = OmrMeasurePostProcessor.STEM_OR_REST;
                gray[y * width + 150] = 0;
            }
            for (int y = 45; y <= 125; y++) {
                labels[y * width + 300] = OmrMeasurePostProcessor.STEM_OR_REST;
                gray[y * width + 300] = 0;
            }
        }

        void upperHead(int stem, int cy) {
            int cx = stem + 9;
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10.0, 2) + Math.pow((y - cy) / 6.0, 2) <= 1) {
                        labels[y * width + x] = OmrMeasurePostProcessor.SYMBOL;
                        gray[y * width + x] = 0;
                    }
        }

        @SuppressWarnings("unchecked")
        List<Integer> boundaries() throws Exception {
            Method method =
                    OmrMeasurePostProcessor.class.getDeclaredMethod(
                            "findBoundaries",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            int[].class,
                            float.class,
                            int.class,
                            int.class,
                            float.class);
            method.setAccessible(true);
            return (List<Integer>)
                    method.invoke(null, labels, gray, width, height, rows, 14f, 50, 450, 0f);
        }
    }
}
