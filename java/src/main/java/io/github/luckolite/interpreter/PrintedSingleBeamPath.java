// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A continuous thick rail between two independently proved, same-direction stems. */
final class PrintedSingleBeamPath {
    private PrintedSingleBeamPath() {}

    static boolean connects(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 8
                || Math.abs(a[2]) != 1
                || a[2] != b[2]) return false;
        if (a[0] > b[0]) {
            int[] t = a;
            a = b;
            b = t;
        }
        int span = b[0] - a[0];
        if (span < gap * 2 || span > gap * 5 || Math.abs(b[1] - a[1]) > gap * .75f) return false;
        int margin = Math.max(3, Math.round(gap * .3f)), radius = Math.round(gap);
        int reach = Math.max(2, Math.round(gap * .5f));
        boolean[] previous = new boolean[height];
        boolean first = true;
        for (int x = a[0] + margin; x <= b[0] - margin; x++) {
            int expected = Math.round(a[1] + (b[1] - a[1]) * (x - a[0]) / (float) span);
            if (x < 1
                    || x >= width - 1
                    || expected - radius - reach < 0
                    || expected + radius + reach >= height) return false;
            int[] values = new int[radius * 2 + 1];
            for (int d = -radius; d <= radius; d++)
                values[d + radius] = gray[(expected + d) * width + x] & 255;
            java.util.Arrays.sort(values);
            int paper = values[values.length * 4 / 5];
            if (paper < 225) return false;
            int limit = Math.min(205, paper - 30);
            boolean[] current = new boolean[height];
            boolean any = false;
            for (int y = expected - reach; y <= expected + reach; y++) {
                if ((gray[(y - 1) * width + x] & 255) >= limit
                        || (gray[y * width + x] & 255) >= limit
                        || (gray[(y + 1) * width + x] & 255) >= limit) continue;
                int top = y, bottom = y;
                while (top > y - radius && (gray[(top - 1) * width + x] & 255) < limit) top--;
                while (bottom < y + radius && (gray[(bottom + 1) * width + x] & 255) < limit)
                    bottom++;
                int thickness = bottom - top + 1;
                if (thickness < Math.max(4, Math.ceil(gap * .25f)) || thickness > gap * .8f)
                    continue;
                if (first || previous[y - 1] || previous[y] || previous[y + 1]) {
                    current[y] = true;
                    any = true;
                }
            }
            if (!any) return false;
            previous = current;
            first = false;
        }
        return !first;
    }

    /**
     * A printed rule can extend a raw shaft beyond its actual beam root. Resolve only that
     * bounded contact, using independently attached shafts, an existing exclusive rail count,
     * and a straight ink corridor. The ordinary continuous-path contract above stays unchanged.
     */
    static boolean connectsAfterRuleContact(byte[] gray, int w, int h, int[] a, int[] b,
            float aHeadY, float bHeadY, float gap, float staffTop, StaffPitchTrack track) {
        if (gray == null || gray.length != (long) w * h || w < 3 || h < 3
                || a == null || b == null || a.length != 3 || b.length != 3
                || Math.abs(a[2]) != 1 || a[2] != b[2] || !Float.isFinite(gap + staffTop + aHeadY + bHeadY)
                || gap < 8 || Math.abs(aHeadY - bHeadY) > gap * 2) return false;
        for (int i = 0; i < 2; i++) {
            int[] stem = i == 0 ? a : b;
            float head = i == 0 ? aHeadY : bHeadY;
            float length = (stem[1] - head) * stem[2];
            if (stem[0] < 1 || stem[0] >= w - 1 || stem[1] < 1 || stem[1] >= h - 1
                    || length < gap * 2.3f || length > gap * 5.5f) return false;
        }
        boolean contactA = ruleContact(gray, w, h, a, gap, staffTop, track);
        boolean contactB = ruleContact(gray, w, h, b, gap, staffTop, track);
        if (!contactA && !contactB) return false;
        // Stay inside the existing .35-gap primary-root envelope so a second rail cannot replace it.
        int maximum = (int) Math.floor(gap * .30f);
        for (int da = 0; da <= (contactA ? maximum : 0); da++)
            for (int db = 0; db <= (contactB ? maximum : 0); db++) {
                if (da == 0 && db == 0) continue;
                int[] own = {a[0], a[1] - a[2] * da, a[2]};
                int[] pair = {b[0], b[1] - b[2] * db, b[2]};
                if (connects(gray, w, h, own, pair, gap)
                        && straightRoot(gray, w, h, own, pair, gap)
                        && PairedGraceBeamInk.countSinglePrinted(
                                gray, w, h, own, pair, gap, staffTop, track) == 1) return true;
            }
        return false;
    }

    private static boolean ruleContact(byte[] g, int w, int h, int[] stem, float gap,
            float top, StaffPitchTrack track) {
        float[] frame = track == null ? new float[] {top + 4 * gap, gap} : track.at(stem[0]);
        if (frame == null || !Float.isFinite(frame[0] + frame[1]) || frame[1] < gap * .8f
                || frame[1] > gap * 1.2f) return false;
        top = frame[0] - 4 * frame[1];
        int line = Math.round((stem[1] - top) / frame[1]);
        if (line < 0 || line > 4 || Math.abs(stem[1] - top - line * frame[1]) > gap * .30f)
            return false;
        int witnessedLines = 0;
        for (int k = 0; k < 5; k++) {
            int sides = 0;
            for (int side : new int[] {-1, 1}) {
                boolean seen = false;
                for (int distance : new int[] {2, 3}) {
                    int x = stem[0] + side * Math.round(gap * distance);
                    if (x < 1 || x >= w - 1) continue;
                    float[] local = track == null ? frame : track.at(x);
                    if (local == null) continue;
                    int y = Math.round(local[0] - (4 - k) * local[1]);
                    if (y < 3 || y >= h - 3) continue;
                    for (int dy = -2; dy <= 2; dy++)
                        if ((g[(y + dy) * w + x] & 255) < 165) seen = true;
                }
                if (seen) sides++;
            }
            if (sides == 2) witnessedLines++;
        }
        return witnessedLines >= 3;
    }

    /**
     * The affine corridor must intersect the thick body in every column. A rule may widen one
     * edge of that body at a crossing, so its center is not a reliable rail position there.
     */
    private static boolean straightRoot(byte[] g, int w, int h, int[] a, int[] b, float gap) {
        if (a[0] > b[0]) { int[] t = a; a = b; b = t; }
        int span = b[0] - a[0], margin = Math.max(3, Math.round(gap * .3f));
        for (int x = a[0] + margin; x <= b[0] - margin; x++) {
            float root = a[1] + (b[1] - a[1]) * (x - a[0]) / (float) span;
            float expected = root - a[2] * 2;
            int radius = Math.round(gap * .5f), start = -1;
            boolean found = false;
            int bottom = Math.min(h - 2, Math.round(expected) + radius);
            for (int y = Math.max(1, Math.round(expected) - radius); y <= bottom + 1; y++) {
                boolean ink = y <= bottom && (g[y * w + x] & 255) < 205;
                if (ink && start < 0) start = y;
                if (!ink && start >= 0) {
                    int size = y - start;
                    if (size >= Math.max(4, Math.ceil(gap * .25f)) && size <= gap * .8f
                            && expected >= start - 1.5f && expected <= y + .5f)
                        found = true;
                    start = -1;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
