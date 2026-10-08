// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Read attached beam rails when nearby voices make shaft ownership ambiguous. */
final class SharedStemBeamOwnership {
    private SharedStemBeamOwnership() {}

    private record Band(float center, int size) {}

    private record Rail(float root, float slope, int side, int direction, int stem, int end) {}

    static int count(
            byte[] gray,
            byte[] labels,
            int w,
            int h,
            float gap,
            int left,
            int right,
            int top,
            int bottom,
            float cx,
            float cy) {
        if (gray == null
                || labels == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || labels.length != gray.length
                || !Float.isFinite(gap + cx + cy)
                || gap < 8
                || gap > h * .1f
                || left < 0
                || right >= w
                || top < 0
                || bottom >= h
                || left > right
                || top > bottom
                || right - left < gap * .8f
                || right - left > gap * 1.8f
                || bottom - top > gap * 1.15f) return -1;
        List<Rail> rails = new ArrayList<>();
        int ownedDirections = 0;
        int upMin = w, upMax = -1, downMin = w, downMax = -1;
        for (int direction : new int[] {-1, 1}) {
            for (int stem = Math.max(2, Math.round(left - gap * .4f));
                    stem <= Math.min(w - 3, Math.round(right + gap * .4f));
                    stem++) {
                if ((stem - cx) * direction > -gap * .45f
                        || !attachedFace(gray, w, h, stem, cx, cy, gap)) continue;
                int start = Math.round(cy), end = start, blank = 0;
                for (int d = 0; d <= gap * 12; d++) {
                    int y = start + direction * d;
                    if (y < 1 || y >= h - 1) break;
                    if (ink(gray, w, h, stem, y)) {
                        end = y;
                        blank = 0;
                    } else if (++blank > Math.max(1, Math.round(gap * .3f))) break;
                }
                if (Math.abs(end - start) < gap * 1.5f) continue;
                int dark = 0;
                for (int y = Math.min(start, end); y <= Math.max(start, end); y++)
                    if (ink(gray, w, h, stem, y)) dark++;
                if (dark < .85f * (Math.abs(end - start) + 1)) continue;
                boolean displaced = false;
                int limit =
                        direction < 0
                                ? Math.min(w - 3, Math.round(right + gap * .4f))
                                : Math.max(2, Math.round(left - gap * .4f));
                for (int outer = stem - direction;
                        direction < 0 ? outer <= limit : outer >= limit;
                        outer -= direction) {
                    if (!attachedFace(gray, w, h, outer, cx, cy, gap)) continue;
                    int outsideEnd = start, missing = 0, inkCount = 0;
                    for (int d = 0; d <= gap * 12; d++) {
                        int y = start + direction * d;
                        if (y < 1 || y >= h - 1) break;
                        if (ink(gray, w, h, outer, y)) {
                            outsideEnd = y;
                            missing = 0;
                            inkCount++;
                        } else if (++missing > Math.max(1, Math.round(gap * .3f))) break;
                    }
                    if (Math.abs(outsideEnd - start) >= gap * 1.5f
                            && inkCount >= .85f * (Math.abs(outsideEnd - start) + 1)
                            && Math.abs(outsideEnd - end) > gap * .65f) {
                        displaced = true;
                        break;
                    }
                }
                if (displaced) continue;
                ownedDirections |= direction < 0 ? 1 : 2;
                if (direction < 0) {
                    upMin = Math.min(upMin, stem);
                    upMax = Math.max(upMax, stem);
                } else {
                    downMin = Math.min(downMin, stem);
                    downMax = Math.max(downMax, stem);
                }
                boolean nearbyHead = false;
                int scanTop = Math.max(1, start - Math.round(gap * 12)),
                        scanBottom = Math.min(h - 2, start + Math.round(gap * 12));
                for (int y = scanTop; y <= scanBottom && !nearbyHead; y++)
                    if (Math.abs(y - start) > gap * 1.3f)
                        for (int x = Math.max(0, stem - Math.round(gap * 1.8f));
                                x <= Math.min(w - 1, stem + Math.round(gap * 1.8f));
                                x++)
                            if (labels[y * w + x] == OmrMeasurePostProcessor.NOTEHEAD) {
                                nearbyHead = true;
                                break;
                            }
                if (!nearbyHead) continue;
                int a = Math.max(1, Math.min(start, end) - Math.round(gap * .75f)),
                        b = Math.min(h - 2, Math.max(start, end) + Math.round(gap * .75f));
                for (int side : new int[] {-1, 1}) {
                    int x0 = stem + side * Math.round(gap * .55f);
                    for (Band band : bands(gray, labels, w, h, x0, a, b, gap)) {
                        if ((band.center - cy) * direction < gap * 1.2f) continue;
                        for (float distance : new float[] {1.5f, 1f}) {
                            int x1 = stem + side * Math.round(gap * distance);
                            int x2 = stem + side * Math.round(gap * (distance + .5f));
                            int x3 = stem + side * Math.round(gap * (distance + 1f));
                            int lo = Math.max(1, Math.round(band.center - gap * 1.2f)),
                                    hi = Math.min(h - 2, Math.round(band.center + gap * 1.2f));
                            List<Band> firstBands = bands(gray, labels, w, h, x1, lo, hi, gap);
                            List<Band> middleBands = bands(gray, labels, w, h, x2, lo, hi, gap);
                            List<Band> lastBands = bands(gray, labels, w, h, x3, lo, hi, gap);
                            for (Band firstBand : firstBands)
                                for (Band lastBand : lastBands) {
                                    float slope = (lastBand.center - firstBand.center) / (x3 - x1);
                                    if (Math.abs(slope) > .5f) continue;
                                    float predicted = firstBand.center + (x2 - x1) * slope;
                                    Band middle = nearest(middleBands, predicted);
                                    if (middle == null
                                            || Math.abs(middle.center - predicted) > 1.5f) continue;
                                    float fitted = firstBand.center + (x0 - x1) * slope;
                                    if (Math.abs(fitted - band.center) > band.size * .5f + .5f)
                                        continue;
                                    float root = firstBand.center + (stem - x1) * slope;
                                    if ((root - end) * direction > gap * .15f) continue;
                                    int ry = Math.round(root);
                                    if (!ink(gray, w, h, stem, ry)
                                            || !ink(
                                                    gray,
                                                    w,
                                                    h,
                                                    stem + side * 2,
                                                    ry + Math.round(side * 2 * slope))) continue;
                                    rails.add(new Rail(root, slope, side, direction, stem, end));
                                }
                        }
                    }
                }
            }
        }
        if (rails.isEmpty()
                || (ownedDirections == 3
                        && (upMin > downMax + gap * .3f || downMin > upMax + gap * .3f))) return -1;
        Rail first =
                rails.stream()
                        .min(Comparator.comparingDouble(r -> Math.abs(r.root - cy)))
                        .orElseThrow();
        List<Float> roots = new ArrayList<>();
        roots.add(first.root);
        for (Rail r : rails) {
            if (r.direction != first.direction
                    || r.side != first.side
                    || Math.abs(r.stem - first.stem) > gap * .3f
                    || Math.abs(r.slope - first.slope) > .08f) continue;
            float d = (r.root - first.root) * first.direction;
            if (d < gap * .3f || d > gap * 1.8f) continue;
            if (roots.stream().noneMatch(y -> Math.abs(y - r.root) < gap * .28f)) roots.add(r.root);
        }
        for (int toward : new int[] {first.direction, -first.direction})
            for (int secondary = 1; secondary <= 2 && roots.size() < 3; secondary++) {
                float previous = first.root + toward * gap * .75f * (secondary - 1);
                if (roots.stream().noneMatch(y -> Math.abs(y - previous) <= 2f)) break;
                float expected = first.root + toward * gap * .75f * secondary;
                if (roots.stream().anyMatch(y -> Math.abs(y - expected) <= 2f)) continue;
                int x0 = first.stem + first.side * Math.round(gap * .55f),
                        x1 = first.stem + first.side * Math.round(gap * .85f);
                Band a =
                        nearest(
                                bands(
                                        gray,
                                        labels,
                                        w,
                                        h,
                                        x0,
                                        Math.max(1, Math.round(expected - gap * .28f)),
                                        Math.min(h - 2, Math.round(expected + gap * .28f)),
                                        gap),
                                expected);
                if (a == null
                        || Math.abs(a.center - (expected + (x0 - first.stem) * first.slope)) > 2f)
                    break;
                float center1 = a.center + (x1 - x0) * first.slope;
                Band b =
                        nearest(
                                bands(
                                        gray,
                                        labels,
                                        w,
                                        h,
                                        x1,
                                        Math.max(1, Math.round(center1 - gap * .28f)),
                                        Math.min(h - 2, Math.round(center1 + gap * .28f)),
                                        gap),
                                center1);
                if (b == null || Math.abs(b.center - center1) > 1.5f) break;
                float railRoot = a.center + (first.stem - x0) * first.slope;
                if ((railRoot - first.end) * first.direction > gap * .15f) break;
                if (!ink(gray, w, h, first.stem, Math.round(railRoot))) continue;
                if (rails.stream()
                        .anyMatch(
                                r ->
                                        r.side == first.side
                                                && r.direction == first.direction
                                                && Math.abs(r.stem - first.stem) < gap * .3f
                                                && Math.abs(r.slope - first.slope) > .08f
                                                && Math.abs(r.root - railRoot) < gap * .3f)) break;
                if (roots.stream().noneMatch(y -> Math.abs(y - railRoot) < gap * .28f))
                    roots.add(railRoot);
            }
        return Math.min(3, roots.size());
    }

    private static boolean attachedFace(
            byte[] gray, int w, int h, int stem, float cx, float cy, float gap) {
        int attached = 0;
        for (float offset : new float[] {-.3f, -.2f, -.1f, .1f, .2f, .3f}) {
            int y = Math.round(cy + gap * offset), blank = 0;
            for (int x = Math.min(stem, Math.round(cx)); x <= Math.max(stem, Math.round(cx)); x++)
                if (!ink(gray, w, h, x, y)) blank++;
            if (blank <= 1) attached++;
        }
        return attached >= 2;
    }

    private static Band nearest(List<Band> bands, float expected) {
        return bands.stream()
                .min(Comparator.comparingDouble(b -> Math.abs(b.center - expected)))
                .orElse(null);
    }

    private static List<Band> bands(
            byte[] gray, byte[] labels, int w, int h, int x, int top, int bottom, float gap) {
        List<Band> bands = new ArrayList<>();
        if (x < 1 || x >= w - 1) return bands;
        top = Math.max(1, top);
        bottom = Math.min(h - 2, bottom);
        if (top > bottom) return bands;
        while (top > 1 && bandInk(gray, labels, w, h, x, top - 1)) top--;
        while (bottom < h - 2 && bandInk(gray, labels, w, h, x, bottom + 1)) bottom++;
        int start = -1;
        for (int y = top; y <= bottom + 1; y++) {
            boolean dark = y <= bottom && bandInk(gray, labels, w, h, x, y);
            if (dark && start < 0) start = y;
            if (!dark && start >= 0) {
                int size = y - start;
                if (size >= Math.max(3, Math.round(gap * .22f)) && size <= gap * .85f)
                    bands.add(new Band((start + y - 1) * .5f, size));
                start = -1;
            }
        }
        return bands;
    }

    private static boolean bandInk(byte[] gray, byte[] labels, int w, int h, int x, int y) {
        return ink(gray, w, h, x - 1, y)
                && ink(gray, w, h, x, y)
                && ink(gray, w, h, x + 1, y)
                && labels[y * w + x] != OmrMeasurePostProcessor.NOTEHEAD
                && labels[y * w + x] != OmrMeasurePostProcessor.STAFF;
    }

    private static boolean ink(byte[] gray, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && (gray[y * w + x] & 255) < 165;
    }
}
