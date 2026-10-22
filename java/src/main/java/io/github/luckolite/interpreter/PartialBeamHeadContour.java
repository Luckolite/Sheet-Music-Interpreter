// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** A finite secondary beam cannot supply a notehead when two other heads own its shafts. */
final class PartialBeamHeadContour {
    private PartialBeamHeadContour() {}

    private record Sample(
            int x, int top, int bottom, float reference, boolean topClear, boolean bottomClear) {}

    static boolean proved(
            byte[] gray,
            int w,
            int h,
            int[] a,
            int[] b,
            float cx,
            float cy,
            int left,
            int top,
            int right,
            int bottom,
            int area,
            float gap) {
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || a[2] != b[2]
                || Math.abs(a[2]) != 1
                || a[0] < 0
                || a[0] >= w
                || b[0] < 0
                || b[0] >= w
                || a[1] < 0
                || a[1] >= h
                || b[1] < 0
                || b[1] >= h
                || !Float.isFinite(gap)
                || gap < 8
                || gap > Math.min(w, h) * .2f
                || !Float.isFinite(cx)
                || !Float.isFinite(cy)
                || left < 0
                || top < 0
                || right >= w
                || bottom >= h
                || right < left
                || bottom < top
                || cx < left
                || cx > right
                || cy < top
                || cy > bottom
                || right - left + 1 > gap * 1.5f
                || bottom - top + 1 > gap * 1.05f
                || area < 1
                || area > gap * gap * 1.2f) return false;
        int[] first = a[0] < b[0] ? a : b, last = a[0] < b[0] ? b : a;
        float span = last[0] - first[0], slope = (last[1] - first[1]) / span;
        if (span < gap * 2
                || span > gap * 6
                || Math.abs(slope) > .65f
                || left < first[0] - gap * .15f
                || right > last[0] + gap * .15f
                || Math.min(Math.abs(cx - first[0]), Math.abs(cx - last[0])) > gap * 1.25f)
            return false;
        int threshold =
                BeamInkThreshold.at(
                        gray,
                        w,
                        h,
                        Math.round(cx),
                        Math.max(0, Math.round(cy - gap * 2)),
                        Math.min(h - 1, Math.round(cy + gap * 2)),
                        gap);
        int margin = Math.max(3, Math.round(gap * .22f));
        for (int shift = -Math.round(gap * .6f); shift <= Math.round(gap * .3f); shift++) {
            float primaryAtHead = first[1] + slope * (cx - first[0]) + shift;
            float depth = (primaryAtHead - cy) * a[2];
            boolean primaryHead = Math.abs(depth) <= gap * .5f;
            if (!primaryHead && (depth < gap * .5f || depth > gap * 1.8f)) continue;
            List<Sample> primary = new ArrayList<>();
            int total = 0;
            for (int x = first[0] + margin; x <= last[0] - margin; x++) {
                float y = first[1] + slope * (x - first[0]) + shift;
                total++;
                Sample sample = sample(gray, w, h, x, y, gap, threshold);
                if (sample != null) primary.add(sample);
            }
            if (total < gap
                    || primary.size() < total * .94f
                    || !stable(primary, gap)
                    || primaryHead && !stableAcrossHead(primary, gap, left, right)) continue;
            boolean nearFirst = Math.abs(cx - first[0]) <= Math.abs(cx - last[0]);
            int owner = nearFirst ? first[0] : last[0], direction = nearFirst ? 1 : -1;
            float secondaryCenter = primaryHead ? primaryAtHead - a[2] * gap : cy;
            for (int phase = -Math.round(gap * (primaryHead ? .5f : .3f));
                    phase <= Math.round(gap * (primaryHead ? .5f : .3f));
                    phase++) {
                List<Sample> partial = new ArrayList<>();
                int endDistance = margin;
                for (int distance = margin; distance < span - margin; distance++) {
                    int x = owner + direction * distance;
                    float y = secondaryCenter + slope * (x - cx) + phase;
                    Sample sample = sample(gray, w, h, x, y, gap, threshold);
                    if (sample == null) {
                        endDistance = distance;
                        break;
                    }
                    partial.add(sample);
                    endDistance = distance + 1;
                }
                if (partial.size() < gap * .65f
                        || endDistance > span * .8f
                        || !stable(partial, gap)) continue;
                if (primaryHead
                        && !ownedLanes(
                                gray, w, h, left, right, top, bottom, first, last, slope, shift,
                                partial, gap, threshold)) continue;
                int endX = owner + direction * (endDistance - 1);
                int farEdge = direction > 0 ? right : left;
                if (!ownedExterior(
                        gray, w, h, endX, farEdge, direction, top, bottom, first[0], first[1],
                        slope, shift, gap, threshold)) continue;
                int clear = 0;
                for (int distance = endDistance; distance < span - margin; distance++) {
                    int x = owner + direction * distance;
                    if (sample(
                                    gray,
                                    w,
                                    h,
                                    x,
                                    secondaryCenter + slope * (x - cx) + phase,
                                    gap,
                                    threshold)
                            != null) break;
                    clear++;
                }
                if (clear >= Math.max(4, Math.round(gap * .4f))) return true;
            }
        }
        return false;
    }

    /** A semantic box can include a crossing staff rule or the already proved primary lane. */
    private static boolean ownedExterior(
            byte[] gray,
            int w,
            int h,
            int endX,
            int farEdge,
            int direction,
            int top,
            int bottom,
            int primaryX,
            int primaryY,
            float slope,
            int shift,
            float gap,
            int threshold) {
        for (int x = endX + direction; (farEdge - x) * direction >= 0; x += direction) {
            Sample primary =
                    sample(
                            gray,
                            w,
                            h,
                            x,
                            primaryY + slope * (x - primaryX) + shift,
                            gap,
                            threshold);
            if (primary == null) return false;
            for (int y = top; y <= bottom; y++)
                if ((gray[y * w + x] & 255) <= threshold
                        && (y < primary.top || y > primary.bottom)
                        && !FlagRootStaffRule.matches(gray, w, h, y, x, gap, threshold))
                    return false;
        }
        return true;
    }

    /** A mask can join both proved lanes at the shaft, but cannot supply any extra oval ink. */
    private static boolean ownedLanes(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int top,
            int bottom,
            int[] first,
            int[] last,
            float slope,
            int shift,
            List<Sample> partial,
            float gap,
            int threshold) {
        int margin = Math.max(1, Math.round(gap * .16f));
        for (int x = Math.max(left, first[0] + margin + 1);
                x <= Math.min(right, last[0] - margin - 1);
                x++) {
            Sample primary =
                    sample(
                            gray,
                            w,
                            h,
                            x,
                            first[1] + slope * (x - first[0]) + shift,
                            gap,
                            threshold);
            if (primary == null) return false;
            Sample secondary = null;
            for (var lane : partial)
                if (lane.x == x) {
                    secondary = lane;
                    break;
                }
            for (int y = top; y <= bottom; y++)
                if ((gray[y * w + x] & 255) <= threshold
                        && (y < primary.top || y > primary.bottom)
                        && (secondary == null || y < secondary.top || y > secondary.bottom)
                        && !FlagRootStaffRule.matches(gray, w, h, y, x, gap, threshold))
                    return false;
        }
        return true;
    }

    private static Sample sample(
            byte[] gray, int w, int h, int x, float reference, float gap, int threshold) {
        int y = Math.round(reference), radius = Math.max(2, Math.round(gap * .85f));
        if (x < 0
                || x >= w
                || y - radius < 0
                || y + radius >= h
                || (gray[y * w + x] & 255) > threshold) return null;
        int first = y, last = y;
        while (first > y - radius && (gray[(first - 1) * w + x] & 255) <= threshold) first--;
        while (last < y + radius && (gray[(last + 1) * w + x] & 255) <= threshold) last++;
        boolean topCut = false, bottomCut = false;
        // A long printed staff rule can join the two otherwise separate beam lanes.
        // Split only that independently proved horizontal bridge, keeping its edge unknown.
        if (last - first + 1 > gap * .85f) {
            if (FlagRootStaffRule.matches(gray, w, h, y, x, gap, threshold)) return null;
            first = y;
            last = y;
            while (first > y - radius && (gray[(first - 1) * w + x] & 255) <= threshold) {
                if (FlagRootStaffRule.matches(gray, w, h, first - 1, x, gap, threshold)) {
                    topCut = true;
                    break;
                }
                first--;
            }
            while (last < y + radius && (gray[(last + 1) * w + x] & 255) <= threshold) {
                if (FlagRootStaffRule.matches(gray, w, h, last + 1, x, gap, threshold)) {
                    bottomCut = true;
                    break;
                }
                last++;
            }
        }
        int thickness = last - first + 1;
        if (thickness < Math.max(3, (int) Math.ceil(gap * .25f)) || thickness > gap * .85f)
            return null;
        boolean topClear =
                !topCut && !FlagRootStaffRule.matches(gray, w, h, first, x, gap, threshold);
        boolean bottomClear =
                !bottomCut && !FlagRootStaffRule.matches(gray, w, h, last, x, gap, threshold);
        if (!topClear && !bottomClear) return null;
        return new Sample(x, first, last, reference, topClear, bottomClear);
    }

    private static boolean stable(List<Sample> samples, float gap) {
        if (samples.isEmpty()) return false;
        var tops = new ArrayList<Float>();
        var bottoms = new ArrayList<Float>();
        for (var sample : samples) {
            if (sample.topClear) tops.add(sample.top - sample.reference);
            if (sample.bottomClear) bottoms.add(sample.bottom - sample.reference);
        }
        if (tops.size() < 3 || bottoms.size() < 3) return false;
        tops.sort(Float::compare);
        bottoms.sort(Float::compare);
        float middleTop = tops.get(tops.size() / 2), middleBottom = bottoms.get(bottoms.size() / 2);
        int aligned = 0;
        float tolerance = Math.max(1, gap * .08f);
        for (var sample : samples)
            if ((!sample.topClear
                            || Math.abs(sample.top - sample.reference - middleTop) <= tolerance)
                    && (!sample.bottomClear
                            || Math.abs(sample.bottom - sample.reference - middleBottom)
                                    <= tolerance)) aligned++;
        return aligned >= samples.size() * .9f;
    }

    /** The suspected head may not widen contours proved away from its own semantic box. */
    private static boolean stableAcrossHead(List<Sample> samples, float gap, int left, int right) {
        var tops = new ArrayList<Float>();
        var bottoms = new ArrayList<Float>();
        for (var sample : samples)
            if (sample.x < left || sample.x > right) {
                if (sample.topClear) tops.add(sample.top - sample.reference);
                if (sample.bottomClear) bottoms.add(sample.bottom - sample.reference);
            }
        if (tops.size() < 3 || bottoms.size() < 3) return false;
        tops.sort(Float::compare);
        bottoms.sort(Float::compare);
        float top = tops.get(tops.size() / 2), bottom = bottoms.get(bottoms.size() / 2);
        float tolerance = Math.max(1, gap * .08f);
        int witnesses = 0;
        for (var sample : samples)
            if (sample.x >= left && sample.x <= right) {
                if (sample.topClear && Math.abs(sample.top - sample.reference - top) > tolerance
                        || sample.bottomClear
                                && Math.abs(sample.bottom - sample.reference - bottom) > tolerance)
                    return false;
                witnesses++;
            }
        return witnesses >= 3;
    }
}
