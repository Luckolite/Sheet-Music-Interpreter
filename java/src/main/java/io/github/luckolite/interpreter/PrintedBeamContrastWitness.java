// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.function.LongConsumer;

/** Exact-column faint-rule bands, corroborated at both neighboring columns. */
final class PrintedBeamContrastWitness {
    private PrintedBeamContrastWitness() {}

    static PrintedBeamRuleWitness.Witness at(
            byte[] raw,
            int width,
            int height,
            int x,
            double bottom,
            double gap,
            LongConsumer charge) {
        if (charge == null) throw new IllegalArgumentException("Missing witness budget");
        var established = PrintedBeamRuleWitness.at(raw, width, height, x, bottom, gap);
        if (established != null) return established;
        int radius = Math.max(2, (int) Math.ceil(gap * .4));
        int flank = Math.max(2, (int) Math.round(gap * .45));
        if (x < flank || x + flank >= width) return null;
        charge.accept(96);
        var reader = new Reader(raw, width, gap, flank, charge);
        var bands = new ArrayList<PrintedBeamRuleWitness.Band>(5);
        for (int i = 0; i < 5; i++) {
            int center = (int) Math.floor(bottom - (4 - i) * gap + .5);
            int low = center - radius, high = center + radius;
            if (low - flank < 0 || high + flank >= height) return null;
            var band = reader.profile(x, low, high);
            if (band == null) return null;
            for (int sx : new int[] {x - flank, x + flank}) {
                var adjacent = reader.profile(sx, low, high);
                if (adjacent == null
                        || Math.abs(
                                        (band.top()
                                                        + band.bottom()
                                                        - adjacent.top()
                                                        - adjacent.bottom())
                                                * .5)
                                > 2) return null;
            }
            bands.add(band);
        }
        charge.accept(192);
        var provisional = new PrintedBeamRuleWitness.Witness(bands, bottom, gap);
        var fit = PrintedBeamRuleFrame.fit(provisional, provisional, gap);
        if (fit == null || Math.abs(fit.leftBottom() - bottom) > gap * .4) return null;
        return new PrintedBeamRuleWitness.Witness(bands, fit.leftBottom(), fit.gap());
    }

    private static final class Reader {
        final byte[] raw;
        final int width, flank, maximum;
        final LongConsumer charge;

        Reader(byte[] raw, int width, double gap, int flank, LongConsumer charge) {
            this.raw = raw;
            this.width = width;
            this.flank = flank;
            this.maximum = Math.max(1, (int) Math.ceil(gap * .3));
            this.charge = charge;
        }

        int pixel(int x, int y) {
            charge.accept(1);
            return raw[y * width + x] & 255;
        }

        PrintedBeamRuleWitness.Band profile(int x, int low, int high) {
            PrintedBeamRuleWitness.Band found = null;
            for (int y = low + 1; y < high; y++) {
                charge.accept(8);
                int ink = pixel(x, y);
                if (ink > 229 || ink > pixel(x, y - 1) || ink > pixel(x, y + 1)) continue;
                int paper = Math.min(pixel(x, y - flank), pixel(x, y + flank));
                if (paper < 210 || paper - ink < 30) continue;
                double threshold = paper - (paper - ink) * .5;
                int a = y, b = y;
                while (a > low && pixel(x, a - 1) < threshold) a--;
                while (b < high && pixel(x, b + 1) < threshold) b++;
                if (a <= low || b >= high || b - a + 1 > maximum) continue;
                if (found != null && (found.top() != a || found.bottom() != b)) return null;
                if (found == null) {
                    charge.accept(16);
                    found = new PrintedBeamRuleWitness.Band(a, b);
                }
            }
            return found;
        }
    }
}
