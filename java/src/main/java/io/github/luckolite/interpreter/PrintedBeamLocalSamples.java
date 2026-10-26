// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Candidate geometry from all fixed dyadic raw projections, with their real sample baselines. */
final class PrintedBeamLocalSamples {
    private PrintedBeamLocalSamples() {}

    private record Sample(float x, int[] rows) {}

    private static final class Side {
        final Sample[] samples;
        int size;
        boolean ambiguous;

        Side(Sample[] samples) {
            this.samples = samples;
        }
    }

    static float[] at(
            byte[] raw,
            int width,
            int height,
            float x,
            int left,
            int right,
            PlayingTechniqueDetector.Staff staff,
            FaintQuarterRestInk.Budget budget) {
        if (budget == null) throw new IllegalArgumentException("Missing sample budget");
        FaintQuarterRestInk.charge(budget, 64, 0);
        if (raw == null
                || width < 3
                || height < 3
                || raw.length != (long) width * height
                || raw.length > 32000000
                || !Float.isFinite(x)
                || x < 0
                || x >= width
                || left > x
                || right < x
                || left < -64
                || right > width + 64
                || staff == null) throw new IllegalArgumentException("Invalid local samples");
        float gap = staff.gap();
        if (!Float.isFinite(staff.top() + staff.bottom() + gap)
                || gap < 3
                || gap > 64
                || staff.top() < 0
                || staff.bottom() >= height
                || staff.top() >= staff.bottom())
            throw new IllegalArgumentException("Invalid local sample staff");
        int a = Math.max(0, Math.round(left - gap * 12)),
                b = Math.max(0, Math.round(left - gap * .75f));
        int c = Math.min(width, Math.round(right + gap * .75f)),
                d = Math.min(width, Math.round(right + gap * 12));
        if (b - a < gap || d - c < gap) return null;
        int flank = Math.max(2, Math.round(gap * .32f));
        int first = Math.max(flank, (int) Math.floor(staff.top() - gap * .8f));
        int last = Math.min(height - flank, (int) Math.ceil(staff.bottom() + gap * .8f));
        if (last <= first) return null;
        Side l = side(raw, width, a, b, first, last, flank, staff, budget);
        if (l.ambiguous || l.size == 0) return null;
        Side r = side(raw, width, c, d, first, last, flank, staff, budget);
        if (r.ambiguous || r.size == 0) return null;
        FaintQuarterRestInk.charge(budget, 12, 2 * (32L + 8L * 5));
        float[] low = new float[5], high = new float[5];
        float[] chosen = null;
        for (int i = 0; i < l.size; i++) {
            FaintQuarterRestInk.charge(budget, 2, 0);
            for (int j = 0; j < r.size; j++) {
                FaintQuarterRestInk.charge(budget, 8, 0);
                float[] pair = fit(l.samples[i], r.samples[j], x, gap, budget);
                if (pair == null) return null;
                for (int line = 0; line < 5; line++) {
                    FaintQuarterRestInk.charge(budget, 8, 0);
                    float row = pair[0] - (4 - line) * pair[1];
                    if (chosen == null) low[line] = high[line] = row;
                    else {
                        low[line] = Math.min(low[line], row);
                        high[line] = Math.max(high[line], row);
                    }
                    if (high[line] - low[line] > gap * .12f + .5f) return null;
                }
                if (chosen == null) chosen = pair;
            }
        }
        return chosen;
    }

    private static Side side(
            byte[] raw,
            int width,
            int left,
            int right,
            int first,
            int last,
            int flank,
            PlayingTechniqueDetector.Staff staff,
            FaintQuarterRestInk.Budget budget) {
        int height = last - first, span = right - left;
        int divisions = 1;
        while (true) {
            FaintQuarterRestInk.charge(budget, 8, 0);
            if (span / (divisions * 2) < Math.ceil(staff.gap())) break;
            divisions *= 2;
        }
        FaintQuarterRestInk.charge(
                budget, 32, 64 + 32L + 8L * (2 * divisions - 1) + 32L + 8L * divisions);
        Side out = new Side(new Sample[2 * divisions - 1]);
        int[][] quarter = new int[divisions][];
        for (int p = 0; p < divisions; p++) {
            FaintQuarterRestInk.charge(budget, (long) height + 8, 32L + 8L * height);
            quarter[p] = new int[height];
            int a = left + p * span / divisions, b = left + (p + 1) * span / divisions;
            for (int y = first; y < last; y++) {
                FaintQuarterRestInk.charge(budget, 2, 0);
                for (int x = a; x < b; x++) {
                    FaintQuarterRestInk.charge(budget, 4, 0);
                    if (PrintedBeamLocalFrame.thin(raw, width, x, y, flank, staff.gap(), budget))
                        quarter[p][y - first]++;
                }
            }
        }
        // Fixed partitions cover the complete flank; no window is selected by head pitch.
        for (int parts = 1; parts <= divisions; parts *= 2) {
            FaintQuarterRestInk.charge(budget, 2, 0);
            for (int p = 0; p < parts; p++) {
                FaintQuarterRestInk.charge(budget, 16, 0);
                int a = left + p * span / parts, b = left + (p + 1) * span / parts;
                if (b - a < staff.gap()) continue;
                int[] strength;
                if (parts == divisions) strength = quarter[p];
                else {
                    FaintQuarterRestInk.charge(budget, (long) height + 1, 32L + 8L * height);
                    strength = new int[height];
                    for (int y = 0; y < height; y++) {
                        FaintQuarterRestInk.charge(budget, 2, 0);
                        for (int k = p * (divisions / parts);
                                k < (p + 1) * (divisions / parts);
                                k++) {
                            FaintQuarterRestInk.charge(budget, 4, 0);
                            strength[y] += quarter[k][y];
                        }
                    }
                }
                var group =
                        PrintedBeamRuleCandidates.inspect(
                                strength,
                                Math.max(4, Math.round((b - a) * .5f)),
                                height,
                                first,
                                staff.bottom(),
                                staff.gap(),
                                budget);
                if (group.ambiguous) {
                    out.ambiguous = true;
                    return out;
                }
                if (group.rows == null) continue;
                for (int i = 0; i < 5; i++) {
                    FaintQuarterRestInk.charge(budget, 2, 0);
                    group.rows[i] += first;
                }
                FaintQuarterRestInk.charge(budget, 16, 64);
                out.samples[out.size++] = new Sample((a + b) * .5f, group.rows);
            }
        }
        return out;
    }

    private static float[] fit(
            Sample left, Sample right, float x, float gap, FaintQuarterRestInk.Budget budget) {
        FaintQuarterRestInk.charge(budget, 16, 0);
        float distance = right.x - left.x;
        if (distance <= 0 || x <= left.x || x >= right.x) return null;
        float fraction = (x - left.x) / distance;
        FaintQuarterRestInk.charge(budget, 6, 32L + 8L * 5);
        float[] rows = new float[5];
        float mean = 0;
        for (int i = 0; i < 5; i++) {
            FaintQuarterRestInk.charge(budget, 16, 0);
            if (Math.abs(left.rows[i] - right.rows[i]) > distance * .2f) return null;
            rows[i] = left.rows[i] + (right.rows[i] - left.rows[i]) * fraction;
            mean += rows[i] / 5;
        }
        float fittedGap = 0;
        for (int i = 0; i < 5; i++) {
            FaintQuarterRestInk.charge(budget, 8, 0);
            fittedGap += (i - 2) * (rows[i] - mean) / 10;
        }
        if (fittedGap < gap * .88f || fittedGap > gap * 1.12f) return null;
        for (int i = 0; i < 5; i++) {
            FaintQuarterRestInk.charge(budget, 8, 0);
            if (Math.abs(rows[i] - (mean + (i - 2) * fittedGap)) > gap * .12f + .5f) return null;
        }
        FaintQuarterRestInk.charge(budget, 4, 32L + 8L * 2);
        return new float[] {mean + 2 * fittedGap, fittedGap};
    }
}
