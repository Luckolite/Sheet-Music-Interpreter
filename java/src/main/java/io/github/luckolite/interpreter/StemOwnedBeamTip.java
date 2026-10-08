// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A tiny semantic island at one end of a complete beam owned by two real stems. */
final class StemOwnedBeamTip {
    private StemOwnedBeamTip() {}

    static boolean pairedCorner(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            int boxWidth,
            int boxHeight,
            int area,
            float gap) {
        return pairedCorner(gray, w, h, a, b, cx, cy, boxWidth, boxHeight, area, gap, false);
    }

    static boolean pairedCorner(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            int boxWidth,
            int boxHeight,
            int area,
            float gap,
            boolean completeNearStrip) {
        if (gray == null
                || a == null
                || b == null
                || gap < 8
                || a[2] != b[2]
                || !((boxWidth <= gap * .65f && boxHeight <= gap * 1.1f && area <= gap * gap * .36f)
                        || (boxWidth <= gap * .85f
                                && boxHeight <= gap * .85f
                                && area <= gap * gap * .5f))) return false;
        return ownedPair(gray, w, h, a, b, cx, cy, gap, 1.1f, completeNearStrip);
    }

    static boolean mergedCorner(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            int boxWidth,
            int boxHeight,
            int area,
            float gap) {
        if (gray == null
                || a == null
                || b == null
                || gap < 8
                || a[2] != b[2]
                || !((boxWidth <= gap * 2.05f
                                && boxHeight >= gap * 1.05f
                                && boxHeight <= gap * 1.6f
                                && area <= gap * gap * 1.8f)
                        || (boxWidth <= gap * .65f
                                && boxHeight <= gap * 1.1f
                                && area <= gap * gap * .36f))) return false;
        int[] left = a[0] < b[0] ? a : b, right = a[0] < b[0] ? b : a;
        int span = right[0] - left[0];
        float shaftSlope = (right[1] - left[1]) / (float) span;
        if (span < gap * 2.5f
                || span > gap * 6
                || Math.abs(shaftSlope) > .6f
                || Math.min(Math.abs(cx - left[0]), Math.abs(cx - right[0])) > gap * .55f
                || Math.abs(cy - (left[1] + shaftSlope * (cx - left[0]))) > gap * 1.7f)
            return false;
        int local =
                BeamInkThreshold.at(
                        gray,
                        w,
                        h,
                        Math.round(cx),
                        Math.round(cy - gap * 2),
                        Math.round(cy + gap * 2),
                        gap);
        int margin = Math.max(3, Math.round(gap * .3f)), radius = Math.round(gap * 2);
        for (int angle = -4; angle <= 4; angle++) {
            if (boxHeight < gap * 1.05f && angle != 0) continue;
            float slope = shaftSlope + angle * .05f;
            if (Math.abs(slope) > .6f) continue;
            for (int threshold : new int[] {Math.min(165, local + 15), local, 70})
                for (int shift = -Math.round(gap * .4f); shift <= Math.round(gap * .4f); shift++) {
                    int total = 0, valid = 0, near = 0, nearValid = 0;

                    java.util.ArrayList<Float> tops = new java.util.ArrayList<>(),
                            bottoms = new java.util.ArrayList<>();
                    java.util.ArrayList<Integer> columns = new java.util.ArrayList<>();
                    for (int x = left[0] + margin; x <= right[0] - margin; x++) {
                        int y = Math.round(cy + shift + slope * (x - cx));
                        if (x < 0 || x >= w || y - radius < 0 || y + radius >= h) return false;
                        total++;
                        boolean okay = false;
                        if ((gray[y * w + x] & 255) < threshold) {
                            int top = y, bottom = y;
                            while (top > y - radius && (gray[(top - 1) * w + x] & 255) < threshold)
                                top--;
                            while (bottom < y + radius
                                    && (gray[(bottom + 1) * w + x] & 255) < threshold) bottom++;
                            int size = bottom - top + 1;
                            okay =
                                    size >= gap * .85f
                                            && size <= gap * 1.8f
                                            && Math.abs((top + bottom) * .5f - y) <= gap * .45f;
                            if (okay) {
                                tops.add(top - slope * (x - cx));
                                bottoms.add(bottom - slope * (x - cx));
                                columns.add(x);
                            }
                        }
                        if (okay) valid++;
                        if (Math.abs(x - cx) <= gap * .85f) {
                            near++;
                            if (okay) nearValid++;
                        }
                    }
                    if (total < gap * 2
                            || valid < total * .9f
                            || near < 3
                            || nearValid < Math.max(3, near - Math.max(1, Math.round(gap * .18f))))
                        continue;
                    java.util.ArrayList<Float> tt = new java.util.ArrayList<>(tops),
                            bb = new java.util.ArrayList<>(bottoms);
                    tt.sort(Float::compare);
                    bb.sort(Float::compare);
                    float mt = tt.get(tt.size() / 2), mb = bb.get(bb.size() / 2);
                    int aligned = 0, nearAligned = 0;
                    for (int i = 0; i < tops.size(); i++)
                        if (Math.abs(tops.get(i) - mt) <= gap * .18f
                                && Math.abs(bottoms.get(i) - mb) <= gap * .18f) {
                            aligned++;
                            if (Math.abs(columns.get(i) - cx) <= gap * .85f) nearAligned++;
                        }
                    if (aligned >= valid * .8f
                            && nearAligned
                                    >= (boxHeight < gap * 1.05f
                                            ? nearValid
                                            : Math.max(
                                                    3,
                                                    nearValid
                                                            - Math.max(1, Math.round(gap * .18f)))))
                        return true;
                }
        }
        return false;
    }

    private static boolean ownedPair(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            float gap,
            float maximumOffset,
            boolean completeNearStrip) {
        int[] left = a[0] < b[0] ? a : b, right = a[0] < b[0] ? b : a;
        float dx = right[0] - left[0];
        if (dx < gap * 1.2f
                || dx > gap * 16
                || Math.min(Math.abs(cx - left[0]), Math.abs(cx - right[0])) > gap * .5f)
            return false;
        float slope = (right[1] - left[1]) / dx;
        if (Math.abs(slope) > .85f
                || Math.abs(cy - (left[1] + slope * (cx - left[0]))) > gap * maximumOffset)
            return false;
        int margin = Math.max(3, Math.round(gap * .3f));
        int local =
                BeamInkThreshold.at(
                        gray,
                        w,
                        h,
                        Math.round(cx),
                        Math.round(cy - gap * 2),
                        Math.round(cy + gap * 2),
                        gap);
        int[] thresholds =
                local < 185 - 32 ? new int[] {local + 15, local, 70} : new int[] {165, 70};
        for (int threshold : thresholds) {
            int valid = 0, total = 0, near = 0, nearValid = 0;
            for (int x = left[0] + margin; x <= right[0] - margin; x++) {
                int end = Math.round(left[1] + slope * (x - left[0]));
                int top = Math.round(end - gap * (a[2] < 0 ? .3f : 1.8f));
                int bottom = Math.round(end + gap * (a[2] < 0 ? 1.8f : .3f));
                if (x < 0 || x >= w || top < 0 || bottom >= h) return false;
                int bands = 0, start = -1;
                for (int y = top; y <= bottom + 1; y++) {
                    boolean ink = y <= bottom && (gray[y * w + x] & 255) < threshold;
                    if (ink && start < 0) start = y;
                    if (!ink && start >= 0) {
                        int span = y - start;
                        if (span >= gap * .2f && span <= gap * .8f) bands++;
                        start = -1;
                    }
                }
                total++;
                if (bands == 2) valid++;
                if (Math.abs(x - cx) <= gap * .85f) {
                    near++;
                    if (bands == 2) nearValid++;
                }
            }
            if (total >= gap * .65f
                    && valid >= total * .9f
                    && near >= 3
                    && nearValid
                            >= (completeNearStrip
                                    ? near
                                    : Math.max(3, near - Math.max(1, Math.round(gap * .18f)))))
                return true;
        }
        return false;
    }

    static boolean matches(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            int boxWidth,
            int boxHeight,
            int area,
            float gap) {
        if (gray == null
                || gap < 8
                || a == null
                || b == null
                || a[2] != b[2]
                || boxWidth > gap * .85f
                || boxHeight > gap * .85f
                || area > gap * gap * .5f) return false;
        int[] left = a[0] < b[0] ? a : b, right = a[0] < b[0] ? b : a;
        float dx = right[0] - left[0], slope = (right[1] - left[1]) / dx;
        if (dx < gap * 2
                || dx > gap * 6
                || Math.abs(slope) > .6f
                || Math.min(Math.abs(cx - left[0]), Math.abs(cx - right[0])) > gap * .5f)
            return false;
        int threshold =
                BeamInkThreshold.at(
                        gray,
                        w,
                        h,
                        Math.round(cx),
                        Math.round(cy - gap * 2),
                        Math.round(cy + gap * 2),
                        gap);
        int margin = Math.max(3, Math.round(gap * .22f)), radius = Math.round(gap);
        for (int shift = -Math.round(gap * .5f); shift <= Math.round(gap * .5f); shift++) {
            if (Math.abs(left[1] + slope * (cx - left[0]) + shift - cy) > gap * .22f) continue;
            int total = 0, valid = 0, near = 0, nearValid = 0;
            int[] spans = new int[Math.max(1, right[0] - left[0] + 1)];
            int spanCount = 0, nearMaximum = 0;
            for (int x = left[0] + margin; x <= right[0] - margin; x++) {
                int y = Math.round(left[1] + slope * (x - left[0]) + shift);
                if (x < 0 || x >= w || y - radius < 0 || y + radius >= h) return false;
                total++;
                boolean okay = false;
                if ((gray[y * w + x] & 255) < threshold) {
                    int top = y, bottom = y;
                    while (top > y - radius && (gray[(top - 1) * w + x] & 255) < threshold) top--;
                    while (bottom < y + radius && (gray[(bottom + 1) * w + x] & 255) < threshold)
                        bottom++;
                    int span = bottom - top + 1;
                    okay = span >= gap * .25f && span <= gap * .9f;
                    spans[spanCount++] = span;
                    if (Math.abs(x - cx) <= gap * .85f) nearMaximum = Math.max(nearMaximum, span);
                }
                if (okay) valid++;
                if (Math.abs(x - cx) <= gap * .85f) {
                    near++;
                    if (okay) nearValid++;
                }
            }
            if (total >= gap * 1.5f
                    && valid >= total * .9f
                    && near >= 4
                    && nearValid >= near * .9f) {
                java.util.Arrays.sort(spans, 0, spanCount);
                if (spanCount > 0
                        && nearMaximum
                                <= spans[spanCount / 2] + Math.max(1, Math.round(gap * .12f)))
                    return true;
            }
        }
        return false;
    }
}
