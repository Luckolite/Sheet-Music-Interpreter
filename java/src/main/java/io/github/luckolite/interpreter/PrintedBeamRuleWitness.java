// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A column-local raw five-rule witness for beam/carrier separation only. */
final class PrintedBeamRuleWitness {
    record Band(int top, int bottom) {
        boolean contains(int y) {
            return y >= top && y <= bottom;
        }
    }

    record Witness(List<Band> rules, double bottom, double gap) {
        Witness {
            rules = List.copyOf(rules);
        }

        boolean contains(int y) {
            for (var rule : rules) if (rule.contains(y)) return true;
            return false;
        }
    }

    private PrintedBeamRuleWitness() {}

    /**
     * Every returned band was observed at this exact x. A failed query supplies
     * no carrier pixels; it never certifies interpolation or a pitch track.
     */
    static Witness at(byte[] gray, int width, int height, int x, double bottom, double gap) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || (long) width * height > 32_000_000
                || x < 0
                || x >= width
                || !Double.isFinite(bottom + gap)
                || gap < 3
                || gap > 64) throw new IllegalArgumentException("Invalid local beam rule query");
        int radius = Math.max(2, (int) Math.ceil(gap * .4));
        int maximum = Math.max(1, (int) Math.ceil(gap * .3));
        var rules = new java.util.ArrayList<Band>(5);
        double[] centers = new double[5];
        for (int line = 0; line < 5; line++) {
            int center = (int) Math.floor(bottom - (4 - line) * gap + .5);
            int low = center - radius, high = center + radius;
            if (low < 0 || high >= height) return null;
            Band found = null;
            int start = -1;
            for (int y = low; y <= high + 1; y++) {
                if (y <= high && (gray[y * width + x] & 255) < 165) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    if (start > low && y - 1 < high && y - start <= maximum) {
                        if (found != null) return null;
                        found = new Band(start, y - 1);
                    }
                    start = -1;
                }
            }
            if (found == null) return null;
            rules.add(found);
            centers[line] = (found.top + found.bottom) * .5;
        }
        double mean = 0;
        for (double center : centers) mean += center / 5;
        double spacing = 0;
        for (int i = 0; i < 5; i++) spacing += (i - 2) * (centers[i] - mean) / 10;
        double top = mean - 2 * spacing;
        if (Math.abs(spacing - gap) > gap * .12 || Math.abs(top + 4 * spacing - bottom) > gap * .4)
            return null;
        for (int i = 0; i < 5; i++) if (Math.abs(centers[i] - top - i * spacing) > .75) return null;
        return new Witness(rules, top + 4 * spacing, spacing);
    }
}
