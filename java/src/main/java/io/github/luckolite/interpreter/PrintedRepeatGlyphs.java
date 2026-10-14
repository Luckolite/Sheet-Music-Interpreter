// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Printed repeat candidates require isolated paired dots and ordered thin/thick full staff bars. */
final class PrintedRepeatGlyphs {
    record Mark(int boundary, boolean outgoing, float x, int staffIndex, int staffCount) {}

    private record Dot(float x, float y, int width, int height, int area) {}

    private record Bar(int left, int right) {
        float center() {
            return (left + right) * .5f;
        }

        int width() {
            return right - left + 1;
        }
    }

    private PrintedRepeatGlyphs() {}

    static List<Mark> detect(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || staffs == null
                || measures == null) return List.of();
        var result = new ArrayList<Mark>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap) || gap < 5 || gap > Math.min(width, height) / 8f) continue;
            int top = Math.max(0, Math.round(staff.top() - gap));
            int bottom = Math.min(height - 1, Math.round(staff.bottom() + gap));
            if (bottom <= top) continue;
            var dots = dots(gray, width, top, bottom, gap);
            for (var a : dots)
                for (var b : dots) {
                    if (Math.abs(a.x - b.x) > gap * .2f
                            || b.y - a.y < gap * .7f
                            || b.y - a.y > gap * 1.3f) continue;
                    float cx = (a.x + b.x) * .5f, cy = (a.y + b.y) * .5f, measuredGap = b.y - a.y;
                    if (Math.abs(cy - (staff.top() + staff.bottom()) * .5f) > gap) continue;
                    var sides = new ArrayList<Mark>();
                    for (int side : new int[] {-1, 1}) {
                        // Read the five staff rules on the music side, away from the terminal bar
                        // edge.
                        if (!staffRails(gray, width, height, cx, cy, measuredGap, side)) continue;
                        var bars = bars(gray, width, height, cx, cy, measuredGap, gap, side);
                        if (bars.size() != 2) continue;
                        bars.sort(Comparator.comparingDouble(bar -> Math.abs(bar.center() - cx)));
                        var inner = bars.get(0);
                        var outer = bars.get(1);
                        if (inner.width() > gap * .35f
                                || outer.width() < Math.max(2, inner.width() * 1.5f)
                                || outer.width() > gap * .85f) continue;
                        int boundary =
                                boundary(measures, staff, inner.center(), side > 0, width, height);
                        if (boundary >= 0)
                            sides.add(
                                    new Mark(
                                            boundary,
                                            side > 0,
                                            inner.center() / width,
                                            staff.index(),
                                            staff.count()));
                    }
                    if (sides.size() == 1 && !result.contains(sides.get(0)))
                        result.add(sides.get(0));
                }
        }
        result.sort(Comparator.comparingInt(Mark::boundary).thenComparing(Mark::outgoing));
        return List.copyOf(result);
    }

    private static boolean staffRails(
            byte[] gray, int width, int height, float cx, float cy, float gap, int barSide) {
        int flank = Math.max(2, Math.round(gap * .3f)), band = Math.max(1, Math.round(gap * .2f));
        for (int line = 0; line < 5; line++) {
            int samples = 0, hits = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 6); dx++) {
                int x = Math.round(cx - barSide * dx), center = Math.round(cy + (line - 2) * gap);
                if (x < 0
                        || x >= width
                        || center - band - flank < 0
                        || center + band + flank >= height) return false;
                samples++;
                boolean ink = false;
                for (int y = center - band; y <= center + band; y++) {
                    int tone = gray[y * width + x] & 255;
                    if (tone < 220
                            && (gray[(y - flank) * width + x] & 255) > tone + 15
                            && (gray[(y + flank) * width + x] & 255) > tone + 15) {
                        ink = true;
                        break;
                    }
                }
                if (ink) hits++;
            }
            if (samples < 8 || hits < samples * .7f) return false;
        }
        return true;
    }

    static int boundary(
            List<MeasureRegion> measures,
            PlayingTechniqueDetector.Staff staff,
            float x,
            boolean outgoing,
            int width,
            int height) {
        int chosen = -1;
        float nearest = Float.MAX_VALUE;
        float center = (staff.top() + staff.bottom()) * .5f / height;
        for (int i = 0; i < measures.size(); i++) {
            var m = measures.get(i);
            if (center < m.top() - staff.gap() / height
                    || center > m.bottom() + staff.gap() / height) continue;
            float distance = Math.abs(x - (outgoing ? m.right() : m.left()) * width);
            // Expanded silent bars share a rectangle; outgoing belongs to the last logical bar.
            if (distance < nearest || outgoing && distance == nearest) {
                nearest = distance;
                chosen = i + (outgoing ? 1 : 0);
            }
        }
        return nearest <= staff.gap() * 2.5f ? chosen : -1;
    }

    private static ArrayList<Bar> bars(
            byte[] gray,
            int width,
            int height,
            float cx,
            float cy,
            float measuredGap,
            float gap,
            int side) {
        var result = new ArrayList<Bar>();
        int start = -1;
        int left = Math.max(0, Math.round(cx - gap * 2.5f)),
                right = Math.min(width - 1, Math.round(cx + gap * 2.5f));
        int top = Math.round(cy - 2 * measuredGap), bottom = Math.round(cy + 2 * measuredGap);
        if (top < 0 || bottom >= height) return result;
        for (int x = left; x <= right + 1; x++) {
            int hits = 0;
            if (x <= right && (x - cx) * side > gap * .35f)
                for (int y = top; y <= bottom; y++) if ((gray[y * width + x] & 255) < 170) hits++;
            boolean solid = hits >= (bottom - top + 1) * .8f;
            if (solid && start < 0) start = x;
            if (!solid && start >= 0) {
                result.add(new Bar(start, x - 1));
                start = -1;
            }
        }
        return result;
    }

    private static List<Dot> dots(byte[] gray, int width, int top, int bottom, float gap) {
        int rows = bottom - top + 1;
        boolean[] seen = new boolean[width * rows];
        int[] queue = new int[seen.length];
        var result = new ArrayList<Dot>();
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[top * width + seed] & 255) >= 140) continue;
            int take = 0, size = 1, left = width, right = -1, first = rows, last = -1;
            long sx = 0, sy = 0;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                first = Math.min(first, y);
                last = Math.max(last, y);
                sx += x;
                sy += y;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= width || yy < 0 || yy >= rows) continue;
                        int next = yy * width + xx;
                        if (!seen[next] && (gray[(top + yy) * width + xx] & 255) < 140) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            int w = right - left + 1, h = last - first + 1;
            if (first == 0
                    || last == rows - 1
                    || w < gap * .15f
                    || w > gap * .7f
                    || h < gap * .15f
                    || h > gap * .7f
                    || w / (float) h < .5f
                    || w / (float) h > 2
                    || size < w * h * .5f) continue;
            result.add(new Dot(sx / (float) size, top + sy / (float) size, w, h, size));
        }
        return result;
    }
}
