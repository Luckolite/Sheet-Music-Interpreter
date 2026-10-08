// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Recovers row-local staff seeds without changing the source coordinate frame. */
final class RegionalStaffSeeds {
    record Seed(float top, float bottom, float gap, float slope) {}

    /** Follows a proved staff's changing printed spacing in the original source coordinates. */
    static StaffPitchTrack track(byte[] labels, byte[] gray, int width, int height, Seed seed) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != labels.length
                || labels.length != gray.length
                || seed == null
                || !Float.isFinite(seed.top)
                || !Float.isFinite(seed.bottom)
                || !Float.isFinite(seed.gap)
                || !Float.isFinite(seed.slope)
                || seed.gap < 3) return null;
        int stripWidth = Math.min(width, Math.max(64, Math.round(seed.gap * 16)));
        int first = Math.round(seed.top - seed.gap * 2);
        int localHeight = Math.max(24, Math.round(seed.gap * 8));
        List<float[]> samples = new ArrayList<>();
        for (int strip = 0; strip < 13; strip++) {
            float requested = width * (.08f + strip * (.86f / 12));
            int left =
                    Math.max(
                            0,
                            Math.min(width - stripWidth, Math.round(requested - stripWidth * .5f)));
            float centerX = left + stripWidth * .5f;
            byte[] localGray = new byte[stripWidth * localHeight];
            byte[] localLabels = new byte[localGray.length];
            Arrays.fill(localGray, (byte) 255);
            for (int y = 0; y < localHeight; y++)
                for (int x = 0; x < stripWidth; x++) {
                    int worldX = left + x;
                    int worldY = Math.round(first + y + seed.slope * (worldX - width * .5f));
                    if (worldY >= 0 && worldY < height) {
                        localGray[y * stripWidth + x] = gray[worldY * width + worldX];
                        localLabels[y * stripWidth + x] = labels[worldY * width + worldX];
                    }
                }
            int[] semantic = new int[localHeight], contrasted = new int[localHeight];
            int flank = Math.max(2, Math.round(seed.gap * .32f));
            for (int y = flank; y < localHeight - flank; y++)
                for (int x = 0; x < stripWidth; x++) {
                    int at = y * stripWidth + x, ink = localGray[at] & 255;
                    if (localLabels[at] == OmrMeasurePostProcessor.STAFF) semantic[y]++;
                    if (ink <= 238
                            && (localGray[at - flank * stripWidth] & 255) >= ink + 12
                            && (localGray[at + flank * stripWidth] & 255) >= ink + 12)
                        contrasted[y]++;
                }
            List<RawStaffLineDetector.StaffLines> proposals =
                    new ArrayList<>(
                            RawStaffLineDetector.detectFromStrength(
                                    semantic,
                                    Math.max(6, Math.round(stripWidth * .14f)),
                                    localHeight));
            proposals.addAll(
                    RawStaffLineDetector.detectFromStrength(
                            contrasted, Math.max(6, Math.round(stripWidth * .14f)), localHeight));
            float[] best = null;
            float bestDistance = Float.POSITIVE_INFINITY;
            for (var lines : proposals) {
                if (lines.gap() < seed.gap * .75f
                        || lines.gap() > seed.gap * 1.25f
                        || Math.abs(lines.bottom() + first - seed.bottom) > seed.gap * .75f
                        || Math.abs(lines.top() + first - seed.top) > seed.gap * 1.2f
                        || !fivePrintedRules(
                                localGray,
                                stripWidth,
                                localHeight,
                                lines.rows(),
                                0,
                                lines.gap(),
                                0)) continue;
                float[] refined =
                        fittedRules(localGray, stripWidth, localHeight, lines.rows(), lines.gap());
                if (refined == null) continue;
                float distance = Math.abs(refined[0] + first - seed.bottom);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best =
                            new float[] {
                                centerX,
                                refined[0] + first + seed.slope * (centerX - width * .5f),
                                refined[1]
                            };
                }
            }
            if (best != null && (samples.isEmpty() || best[0] > samples.get(samples.size() - 1)[0]))
                samples.add(best);
        }
        if (samples.size() < 6) return null;
        float bottomSlope = sampleSlope(samples, 1), gapSlope = sampleSlope(samples, 2);
        float bottomBase = sampleBase(samples, 1, bottomSlope),
                gapBase = sampleBase(samples, 2, gapSlope);
        samples.removeIf(
                p ->
                        Math.abs(p[1] - bottomBase - bottomSlope * p[0]) > seed.gap * .35f
                                || Math.abs(p[2] - gapBase - gapSlope * p[0])
                                        > Math.max(.3f, seed.gap * .06f));
        if (samples.size() < 6
                || samples.get(samples.size() - 1)[0] - samples.get(0)[0] < width * .5f)
            return null;
        return StaffPitchTrack.fromVerifiedSamples(samples);
    }

    private static float[] fittedRules(byte[] gray, int width, int height, int[] rows, float gap) {
        int probe = Math.max(2, Math.round(gap * .32f));
        int radius = Math.max(1, Math.round(gap * .10f));
        float[] centers = new float[5];
        for (int line = 0; line < 5; line++) {
            double weighted = 0, weights = 0;
            for (int x = 0; x < width; x++)
                for (int dy = -radius; dy <= radius; dy++) {
                    int y = rows[line] + dy;
                    if (y - probe < 0 || y + probe >= height) continue;
                    int ink = gray[y * width + x] & 255;
                    int above = gray[(y - probe) * width + x] & 255;
                    int below = gray[(y + probe) * width + x] & 255;
                    if (ink <= 238 && above >= ink + 12 && below >= ink + 12) {
                        double weight = (above + below) * .5 - ink;
                        weighted += y * weight;
                        weights += weight;
                    }
                }
            if (weights == 0) return null;
            centers[line] = (float) (weighted / weights);
        }
        float mean = 0, spacing = 0;
        for (float center : centers) mean += center * .2f;
        for (int i = 0; i < 5; i++) spacing += (i - 2) * centers[i] * .1f;
        for (int i = 0; i < 5; i++)
            if (Math.abs(centers[i] - (mean + (i - 2) * spacing)) > gap * .12f) return null;
        return spacing >= 3 ? new float[] {mean + spacing * 2, spacing} : null;
    }

    private static float sampleSlope(List<float[]> samples, int field) {
        List<Float> slopes = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++)
            for (int j = i + 1; j < samples.size(); j++) {
                float[] a = samples.get(i), b = samples.get(j);
                slopes.add((b[field] - a[field]) / (b[0] - a[0]));
            }
        slopes.sort(Float::compare);
        return slopes.get(slopes.size() / 2);
    }

    private static float sampleBase(List<float[]> samples, int field, float slope) {
        List<Float> bases = new ArrayList<>();
        for (float[] p : samples) bases.add(p[field] - slope * p[0]);
        bases.sort(Float::compare);
        return bases.get(bases.size() / 2);
    }

    static List<Seed> detect(byte[] labels, byte[] gray, int width, int height) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != labels.length
                || labels.length != gray.length) return List.of();
        int[] strength = new int[height];
        int left = Math.round(width * .08f), right = Math.round(width * .94f);
        for (int y = 0; y < height; y++)
            for (int x = left; x < right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STAFF) strength[y]++;
        int minimum = Math.max(10, width / 80), hole = Math.max(3, width / 170);
        int margin = Math.max(8, width / 50);
        int maximumEnvelope = Math.max(width / 3, height / 4);
        List<Seed> result = new ArrayList<>();
        int first = -1, lastInk = -1, total = 0;
        for (int y = 0; y <= height; y++) {
            boolean ink = y < height && strength[y] >= minimum;
            if (ink) {
                if (first < 0) first = y;
                lastInk = y;
                total += strength[y];
            }
            if (first >= 0 && ((!ink && y - lastInk > hole) || y == height)) {
                if (lastInk - first >= hole
                        && lastInk - first < maximumEnvelope
                        && total >= width * 2)
                    recover(
                            labels,
                            gray,
                            width,
                            height,
                            Math.max(0, first - margin),
                            Math.min(height, lastInk + margin + 1),
                            result);
                first = -1;
                lastInk = -1;
                total = 0;
            }
        }
        result.sort(Comparator.comparingDouble(Seed::top));
        return List.copyOf(result);
    }

    private static void recover(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int first,
            int last,
            List<Seed> result) {
        int localHeight = last - first;
        if (localHeight <= 0) return;
        byte[] local = Arrays.copyOfRange(labels, first * width, last * width);
        float slope = OmrMeasurePostProcessor.estimateStaffSlope(local, width, localHeight);
        int[] projection = new int[localHeight];
        for (int y = first; y < last; y++)
            for (int x = 0; x < width; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STAFF) {
                    int row = Math.round(y - first - slope * (x - width * .5f));
                    if (row >= 0 && row < localHeight) projection[row]++;
                }
        for (var lines :
                RawStaffLineDetector.detectFromStrength(
                        projection, Math.max(10, width / 80), localHeight)) {
            Seed candidate =
                    new Seed(lines.top() + first, lines.bottom() + first, lines.gap(), slope);
            if (!fivePrintedRules(gray, width, height, lines.rows(), first, lines.gap(), slope))
                continue;
            boolean represented = false;
            float center = (candidate.top + candidate.bottom) * .5f;
            for (Seed old : result) {
                float gap = Math.min(old.gap, candidate.gap);
                if (Math.abs((old.top + old.bottom) * .5f - center) <= gap * 2
                        && Math.abs(old.gap - candidate.gap) <= gap * .18f) {
                    represented = true;
                    break;
                }
            }
            if (!represented) result.add(candidate);
        }
    }

    /** Each of the five proposed rules needs its own long, thin raw-ink proof. */
    static boolean fivePrintedRules(
            byte[] gray, int width, int height, int[] rows, int offset, float gap, float slope) {
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || rows == null
                || rows.length != 5
                || gap < 3
                || !Float.isFinite(slope)) return false;
        int probe = Math.max(2, Math.round(gap * .32f));
        int radius = Math.max(0, Math.round(gap * .10f));
        int minimum = Math.max(24, Math.round(width * .08f));
        int minimumRun = Math.max(8, Math.round(gap * 3));
        for (int line : rows) {
            int count = 0, run = 0, longest = 0;
            for (int x = 0; x < width; x++) {
                int center = Math.round(line + offset + slope * (x - width * .5f));
                boolean supported = false;
                for (int dy = -radius; dy <= radius; dy++) {
                    int y = center + dy;
                    if (y - probe < 0 || y + probe >= height) continue;
                    int ink = gray[y * width + x] & 255;
                    if (ink <= 238
                            && (gray[(y - probe) * width + x] & 255) >= ink + 12
                            && (gray[(y + probe) * width + x] & 255) >= ink + 12) {
                        supported = true;
                        break;
                    }
                }
                if (supported) {
                    count++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            }
            if (count < minimum || longest < minimumRun) return false;
        }
        return true;
    }
}
