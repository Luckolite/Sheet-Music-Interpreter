// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Four complete thin rules can locate one staff rule hidden beneath a thick beam. */
final class BeamOccludedStaffPhase {
    private BeamOccludedStaffPhase() {}

    private static final class ProbeSlopes {
        private static final float[] VALUES = {0, -.04f, .04f, -.08f, .08f, -.12f, .12f};
    }

    private static final class ProbeScales {
        private static final float[] VALUES = {.96f, 1f, 1.04f};
    }

    static float[] resolve(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float x,
            int headLeft,
            int headRight,
            float referenceBottom,
            float gap) {
        if (labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || gap < 6
                || !Float.isFinite(gap)
                || !Float.isFinite(referenceBottom)
                || !Float.isFinite(x)
                || headLeft < 0
                || headRight >= width
                || headLeft > headRight
                || x < headLeft
                || x > headRight) return null;
        int left = Math.max(0, Math.round(x - gap * 3.5f)),
                right = Math.min(width - 1, Math.round(x + gap * 3.5f));
        int exclusion = Math.max(2, Math.round(gap * .45f));
        if (headLeft - exclusion - left < 8 || right - headRight - exclusion < 8) return null;
        var candidates = new ArrayList<float[]>();
        int shiftLimit = Math.round(gap * 2.6f);
        for (float slope : ProbeSlopes.VALUES) {
            for (float scale : ProbeScales.VALUES) {
                for (int shift = -shiftLimit; shift <= shiftLimit; shift++) {
                    float base = referenceBottom + shift * .5f;
                    float[] rows = new float[5];
                    int missing = -1;
                    boolean rejected = false;
                    for (int line = 0; line < 5; line++) {
                        float row = base - line * gap * scale;
                        rows[line] =
                                thinCenter(
                                        labels, gray, width, height, x, left, right, headLeft,
                                        headRight, exclusion, row, slope, gap);
                        if (!Float.isFinite(rows[line])) {
                            if (missing >= 0) {
                                rejected = true;
                                break;
                            }
                            missing = line;
                        }
                    }
                    if (rejected || missing < 0) continue;
                    var gaps = new ArrayList<Float>();
                    var bottoms = new ArrayList<Float>();
                    for (int a = 0; a < 5; a++)
                        for (int b = a + 1; b < 5; b++)
                            if (a != missing && b != missing)
                                gaps.add((rows[a] - rows[b]) / (b - a));
                    Collections.sort(gaps);
                    float spacing = gaps.get(gaps.size() / 2);
                    if (Math.abs(spacing - gap) > gap * .07f) continue;
                    for (int line = 0; line < 5; line++)
                        if (line != missing) bottoms.add(rows[line] + line * spacing);
                    Collections.sort(bottoms);
                    float bottom = bottoms.get(bottoms.size() / 2);
                    boolean consistent = true;
                    for (float value : bottoms)
                        if (Math.abs(value - bottom) > gap * .12f) consistent = false;
                    if (!consistent
                            || Math.abs(bottom - referenceBottom) < gap * .3f
                            || Math.abs(bottom - referenceBottom) > gap * 1.2f) continue;
                    if (!beamCovered(
                            labels,
                            gray,
                            width,
                            height,
                            x,
                            left,
                            right,
                            headLeft,
                            headRight,
                            exclusion,
                            bottom - missing * spacing,
                            slope,
                            gap)) continue;
                    if (extraThinRule(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    x,
                                    left,
                                    right,
                                    headLeft,
                                    headRight,
                                    exclusion,
                                    bottom + spacing,
                                    gap)
                            || extraThinRule(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    x,
                                    left,
                                    right,
                                    headLeft,
                                    headRight,
                                    exclusion,
                                    bottom - 5 * spacing,
                                    gap)) continue;
                    candidates.add(new float[] {bottom, spacing});
                }
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(a -> a[0]));
        float[] result = candidates.get(candidates.size() / 2);
        for (float[] candidate : candidates)
            if (Math.abs(candidate[0] - result[0]) > gap * .25f
                    || Math.abs(candidate[1] - result[1]) > gap * .06f) return null;
        return result;
    }

    private static boolean extraThinRule(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            float x,
            int left,
            int right,
            int headLeft,
            int headRight,
            int exclusion,
            float row,
            float gap) {
        // The fourth-to-fifth-rule extrapolation amplifies small fitted slope and
        // spacing errors. Search that uncertainty before accepting an outer edge.
        int uncertainty = Math.max(1, Math.round(gap * .3f));
        for (float slope : ProbeSlopes.VALUES)
            for (int offset = -uncertainty; offset <= uncertainty; offset++)
                if (Float.isFinite(
                        thinCenter(
                                labels,
                                gray,
                                w,
                                h,
                                x,
                                left,
                                right,
                                headLeft,
                                headRight,
                                exclusion,
                                row + offset,
                                slope,
                                gap))) return true;
        return false;
    }

    private static float thinCenter(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            float x,
            int left,
            int right,
            int headLeft,
            int headRight,
            int exclusion,
            float row,
            float slope,
            float gap) {
        int band = Math.max(1, Math.round(gap * .14f)), roundedFlank = Math.round(gap * .28f);
        int flank = Math.max(2, roundedFlank), semanticBand = Math.max(band, roundedFlank);
        var centers = new ArrayList<Float>();
        for (int side = 0; side < 2; side++) {
            int first = side == 0 ? left : headRight + exclusion + 1,
                    last = side == 0 ? headLeft - exclusion - 1 : right;
            int samples = 0, hits = 0, semantic = 0;
            for (int xx = first; xx <= last; xx++) {
                samples++;
                int at = Math.round(row + slope * (xx - x)), best = -1, value = 256;
                for (int y = Math.max(flank, at - band);
                        y <= Math.min(h - 1 - flank, at + band);
                        y++) {
                    int ink = gray[y * w + xx] & 255;
                    if (ink <= 205
                            && (gray[(y - flank) * w + xx] & 255) >= ink + 20
                            && (gray[(y + flank) * w + xx] & 255) >= ink + 20
                            && ink < value) {
                        best = y;
                        value = ink;
                    }
                }
                if (best >= 0) {
                    hits++;
                    centers.add(best - slope * (xx - x));
                    for (int y = Math.max(0, best - semanticBand);
                            y <= Math.min(h - 1, best + semanticBand);
                            y++)
                        if (labels[y * w + xx] == 4) {
                            semantic++;
                            break;
                        }
                }
            }
            if (samples < 8 || hits < samples * .65f || semantic < samples * .45f) return Float.NaN;
        }
        Collections.sort(centers);
        return centers.get(centers.size() / 2);
    }

    private static boolean beamCovered(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            float x,
            int left,
            int right,
            int headLeft,
            int headRight,
            int exclusion,
            float row,
            float slope,
            float gap) {
        int radius = Math.max(3, Math.round(gap * .55f)),
                minimum = Math.max(4, Math.round(gap * .38f));
        int inkLimit =
                Math.min(
                        140,
                        BeamInkThreshold.at(
                                gray,
                                w,
                                h,
                                Math.round(x),
                                Math.round(row - radius),
                                Math.round(row + radius),
                                gap));
        for (int side = 0; side < 2; side++) {
            int first = side == 0 ? left : headRight + exclusion + 1,
                    last = side == 0 ? headLeft - exclusion - 1 : right;
            int samples = 0, covered = 0;
            for (int xx = first; xx <= last; xx++) {
                samples++;
                int center = Math.round(row + slope * (xx - x)), run = 0, longest = 0;
                for (int y = Math.max(0, center - radius);
                        y <= Math.min(h - 1, center + radius);
                        y++) {
                    if ((gray[y * w + xx] & 255) < inkLimit) {
                        run++;
                        longest = Math.max(longest, run);
                    } else run = 0;
                }
                if (longest >= minimum) covered++;
            }
            if (samples < 8 || covered < samples * .55f) return false;
        }
        return true;
    }
}
