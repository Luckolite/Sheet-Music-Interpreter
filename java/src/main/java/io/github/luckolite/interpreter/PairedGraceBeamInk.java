// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Counts parallel dark cores joining two independently attached stems. */
final class PairedGraceBeamInk {
    private PairedGraceBeamInk() {}

    static int count(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        return count(gray, width, height, a, b, gap, 1.5f, 3f, Float.NaN, null);
    }

    static int countFullSize(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        return count(gray, width, height, a, b, gap, 2.2f, 3f, Float.NaN, null);
    }

    /** Full-size written rails may join neighboring pitches across five staff gaps.
     * Every accepted core still crosses all five existing shaft-to-shaft probes. */
    static int countPrintedSize(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        return count(gray, width, height, a, b, gap, 2.2f, 5f, Float.NaN, null);
    }

    /** Confirm full-size rails against the independent local printed rules. */
    static int countFullSize(
            byte[] gray, int width, int height, int[] a, int[] b, float gap, float staffTop) {
        return count(gray, width, height, a, b, gap, 2.2f, 3f, staffTop, null);
    }

    static int countFullSize(
            byte[] gray,
            int width,
            int height,
            int[] a,
            int[] b,
            float gap,
            float staffTop,
            StaffPitchTrack track) {
        return count(gray, width, height, a, b, gap, 2.2f, 3f, staffTop, track);
    }

    /** Five freestanding probes beyond independently transported full-size shafts. */
    static int countTransported(
            byte[] gray,
            int width,
            int height,
            int[] a,
            int[] b,
            float gap,
            float staffTop,
            StaffPitchTrack track) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || track == null
                || !track.verified()
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 4
                || gap > Math.min(width, height) * .25f
                || a[0] < 1
                || a[0] >= width - 1
                || b[0] < 1
                || b[0] >= width - 1
                || a[1] < 0
                || a[1] >= height
                || b[1] < 0
                || b[1] >= height
                || Math.abs(a[2]) != 1
                || a[2] != b[2]) return 0;
        int span = Math.abs(a[0] - b[0]);
        if (span < gap * .95f || span > gap * 3 || Math.abs(a[1] - b[1]) > gap * .75f) return 0;
        int sample = Math.max(4, Math.round(gap));
        float slopeA = (track.at(a[0] + sample)[0] - track.at(a[0] - sample)[0]) / (2 * sample);
        float slopeB = (track.at(b[0] + sample)[0] - track.at(b[0] - sample)[0]) / (2 * sample);
        float axis = b[0] > a[0] ? 1 : -1;
        float near = (2 + Math.max(0, slopeA * a[2] * axis * gap * 2.2f)) / span;
        float far = 1 - (2 + Math.max(0, -slopeB * b[2] * axis * gap * 2.2f)) / span;
        if (near >= .25f || far <= .75f) return 0;
        float[] probes = {near, .25f, .5f, .75f, far};
        int maximum = 0;
        for (float fraction : new float[] {.5f, .75f, 1f})
            maximum =
                    Math.max(
                            maximum,
                            countAtContrast(
                                    gray, width, height, a, b, gap, fraction, 2.2f, probes,
                                    staffTop, track));
        return maximum;
    }

    private static int count(
            byte[] gray,
            int width,
            int height,
            int[] a,
            int[] b,
            float gap,
            float inside,
            float maximumSpan,
            float staffTop,
            StaffPitchTrack track) {
        if (gray == null || a == null || b == null || gap < 4 || a[2] != b[2]) return 0;
        int span = Math.abs(a[0] - b[0]);
        // Compact ornaments can rise one staff space between their stems. The
        // sampled cores below must still follow that same slope at every column.
        float maximumRise = inside == 1.5f ? gap : gap * .75f;
        if (span < gap * .95f || span > gap * maximumSpan || Math.abs(a[1] - b[1]) > maximumRise)
            return 0;
        int maximum = 0;
        float edge = Math.min(.2f, 2f / span);
        float[] probes =
                inside == 1.5f
                        ? new float[] {.25f, .5f, .75f}
                        : new float[] {edge, .25f, .5f, .75f, 1 - edge};
        for (float fraction : new float[] {.5f, .75f, 1f}) {
            int count =
                    countAtContrast(
                            gray, width, height, a, b, gap, fraction, inside, probes, staffTop,
                            track);
            if (count > 0 && inside == 1.5f) return count;
            maximum = Math.max(maximum, count);
        }
        // A compact blurred pair can close the white channel close to one stem.
        // Require three separated, aligned double cores on the clear inner side;
        // this path is not used for full-size notes or very short connectors.
        if (maximum == 0 && inside == 1.5f && span >= gap * 1.4f)
            for (float[] columns : new float[][] {{.2f, .4f, .6f}, {.4f, .6f, .8f}})
                for (float fraction : new float[] {.5f, .75f}) {
                    int count =
                            countAtContrast(
                                    gray, width, height, a, b, gap, fraction, inside, columns,
                                    staffTop, track);
                    if (count == 2) return count;
                }
        // A returning slur can extend one stem beyond its actual beam tip.
        // Retry bounded inward tip positions only for compact ornaments; every
        // accepted position still proves separate parallel cores at three columns.
        if (maximum == 0 && inside == 1.5f && span >= gap * 1.4f)
            for (float inset : new float[] {.25f, .5f, .75f, 1f})
                for (int side = 0; side < 2; side++) {
                    int[] first = a.clone(), last = b.clone();
                    int[] changed = side == 0 ? first : last;
                    changed[1] -= changed[2] * Math.round(gap * inset);
                    if (Math.abs(first[1] - last[1]) > gap) continue;
                    for (float fraction : new float[] {.5f, .75f}) {
                        int count =
                                countAtContrast(
                                        gray, width, height, first, last, gap, fraction, inside,
                                        probes, staffTop, track);
                        if (count >= 2) return count;
                    }
                }
        return maximum;
    }

    private static int countAtContrast(
            byte[] gray,
            int width,
            int height,
            int[] a,
            int[] b,
            float gap,
            float fraction,
            float inside,
            float[] columns,
            float staffTop,
            StaffPitchTrack track) {
        int wanted = 0;
        float[] previous = null;
        for (float f : columns) {
            int x = Math.round(a[0] + (b[0] - a[0]) * f);
            float end = a[1] + (b[1] - a[1]) * f;
            int top = Math.max(1, Math.round(end - (a[2] < 0 ? .35f : inside) * gap));
            int bottom = Math.min(height - 2, Math.round(end + (a[2] < 0 ? inside : .35f) * gap));
            int threshold = BeamInkThreshold.at(gray, width, height, x, top, bottom, gap);
            int darkest = threshold;
            for (int y = top; y <= bottom; y++)
                darkest = Math.min(darkest, gray[y * width + x] & 255);
            threshold = darkest + Math.round((threshold - darkest) * fraction);
            float[] cores = null;
            int coreCount = 0, start = -1;
            for (int y = top; y <= bottom + 1; y++) {
                boolean ink =
                        y <= bottom
                                && (gray[y * width + x - 1] & 255) < threshold
                                && (gray[y * width + x] & 255) < threshold
                                && (gray[y * width + x + 1] & 255) < threshold;
                if (ink && start < 0) start = y;
                if (!ink && start >= 0) {
                    int size = y - start;
                    if (Float.isFinite(staffTop)
                            && LocalPrintedStaffBand.matches(
                                    gray, width, height, x, staffTop, gap, threshold, start,
                                    y - 1)) {
                        start = -1;
                        continue;
                    }
                    if (track != null && track.verified()) {
                        float[] physical = track.at(x);
                        if (LocalPrintedStaffBand.matches(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        physical[0] - 4 * physical[1],
                                        physical[1],
                                        threshold,
                                        start,
                                        y - 1)
                                || PrintedRuleInkOwnership.matchesWithNarrowReference(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        start,
                                        y - 1,
                                        physical[0] - 4 * physical[1],
                                        physical[1],
                                        0,
                                        track)) {
                            start = -1;
                            continue;
                        }
                    }
                    if (size >= Math.max(3, Math.round(gap * (inside == 1.5f ? .18f : .28f)))
                            && size <= gap * (inside == 1.5f ? .6f : .8f)) {
                        float core = (start + y - 1) * .5f - end;
                        if (cores == null) cores = new float[3];
                        if (coreCount < 3) cores[coreCount] = core;
                        coreCount++;
                    }
                    start = -1;
                }
            }
            if (coreCount < 2 || coreCount > 3) return 0;
            if (wanted != 0 && wanted != coreCount) return 0;
            wanted = coreCount;
            float[] current = cores;
            for (int i = 0; i < wanted; i++) {
                if (i > 0
                        && (current[i] - current[i - 1] < gap * .3f
                                || current[i] - current[i - 1] > gap * .9f)) return 0;
                if (previous != null && Math.abs(current[i] - previous[i]) > gap * .22f) return 0;
            }
            previous = current;
        }
        return wanted;
    }
}
