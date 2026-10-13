// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A printed three can thicken an outer staff rule beside an independently printed beam. */
final class TupletNumeralBeamBand {
    private TupletNumeralBeamBand() {}

    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int x,
            int first,
            int last,
            int scanTop,
            int scanBottom,
            float lineTop,
            float gap,
            int threshold) {
        if (gray == null
                || gray.length != width * height
                || gap < 8
                || x < gap * 3
                || x + gap * 3 >= width
                || first < 0
                || last >= height
                || last - first + 1 > gap * .8f) return false;
        float center = (first + last) * .5f;
        float edge =
                Math.abs(center - lineTop) <= gap * .45f
                        ? lineTop
                        : Math.abs(center - (lineTop + 4 * gap)) <= gap * .45f
                                ? lineTop + 4 * gap
                                : Float.NaN;
        if (!Float.isFinite(edge)) return false;
        // A dark-core proof leaves antialiased beam/numeral fringes out of ownership.
        int level = Math.min(165, threshold);
        Rule rule = rule(gray, width, height, x, edge, gap, level);
        if (rule == null) return false;
        int radius = Math.round(gap * 1.65f), vertical = Math.round(gap * 2.4f);
        int left = x - radius, top = rule.center - vertical;
        int w = radius * 2 + 1, h = vertical * 2 + 1;
        if (top < 1 || top + h >= height) return false;
        boolean[] ink = new boolean[w * h];
        int ruleFirst = rule.first - top, ruleLast = rule.last - top;
        for (int y = 0; y < h; y++)
            for (int xx = 0; xx < w; xx++) {
                int yy = top + y + Math.round(rule.slope * (left + xx - x));
                if (yy < 0 || yy >= height) return false;
                ink[y * w + xx] =
                        (y < ruleFirst || y > ruleLast) && dark(gray, width, left + xx, yy, level);
            }
        boolean[] visited = new boolean[ink.length];
        int[] queue = new int[ink.length];
        for (int origin = 0; origin < ink.length; origin++) {
            if (!ink[origin] || visited[origin]) continue;
            int pending = 1, count = 0, minX = w, maxX = -1, minY = h, maxY = -1;
            queue[0] = origin;
            visited[origin] = true;
            while (pending > 0) {
                int current = queue[--pending], xx = current % w, y = current / w;
                minX = Math.min(minX, xx);
                maxX = Math.max(maxX, xx);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                count++;
                // Reconnect only across the proved rule rows, never across blank paper.
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = y + dy;
                        if (dy < 0 && y == ruleLast + 1) ny = ruleFirst - 1;
                        if (dy > 0 && y == ruleFirst - 1) ny = ruleLast + 1;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (ink[next] && !visited[next]) {
                            visited[next] = true;
                            queue[pending++] = next;
                        }
                    }
            }
            int gw = maxX - minX + 1, gh = maxY - minY + 1;
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || gw < gap * .35f
                    || gw > gap * 1.4f
                    || gh < gap * .8f
                    || gh > gap * 2.15f
                    || gw < gh * .30f
                    || gw > gh * 1.05f
                    || count < gw * gh * .15f
                    || count > gw * gh * .72f
                    || x < left + minX
                    || x > left + maxX
                    || first > top + maxY
                    || last < top + minY
                    || minY > ruleFirst
                    || maxY < ruleLast) continue;
            boolean above = edge == lineTop;
            if (above ? ruleFirst - minY < gh * .60f : maxY - ruleLast < gh * .60f) continue;
            if (!three(ink, w, minX, minY, gw, gh, ruleFirst, ruleLast)) continue;
            int glyphTop = top + minY, glyphBottom = top + maxY;
            if (independentBeam(
                    gray,
                    width,
                    height,
                    x,
                    left + minX,
                    left + maxX,
                    glyphTop,
                    glyphBottom,
                    scanTop,
                    scanBottom,
                    gap,
                    level,
                    above)) return true;
        }
        return false;
    }

    private record Rule(int center, int first, int last, float slope) {}

    /** Both distant tails must be thin; a finite thick beam cannot supply the rule proof. */
    private static Rule rule(
            byte[] gray, int width, int height, int x, float edge, float gap, int level) {
        int near = Math.round(gap * 1.8f), far = Math.round(gap * 2.8f);
        int thin = Math.max(2, (int) Math.floor(gap * .24f));
        for (int slopeIndex = 0; slopeIndex <= 12; slopeIndex++) {
            float slope =
                    slopeIndex == 0
                            ? 0
                            : (slopeIndex + 1) / 2 * .025f * (slopeIndex % 2 == 1 ? 1 : -1);
            for (int cy = Math.round(edge - gap * .4f); cy <= Math.round(edge + gap * .4f); cy++) {
                int[] hits = new int[2];
                int ruleFirst = Integer.MAX_VALUE, ruleLast = -1;
                for (int side = -1; side <= 1; side += 2)
                    for (int dx = near; dx <= far; dx++) {
                        int xx = x + side * dx, y = cy + Math.round(slope * side * dx);
                        if (y < thin + 1
                                || y + thin + 1 >= height
                                || !dark(gray, width, xx, y, level)) continue;
                        int first = y, last = y;
                        while (first > y - thin && dark(gray, width, xx, first - 1, level)) first--;
                        while (last < y + thin && dark(gray, width, xx, last + 1, level)) last++;
                        if (last - first + 1 > thin) continue;
                        hits[(side + 1) / 2]++;
                        ruleFirst = Math.min(ruleFirst, first - Math.round(slope * side * dx));
                        ruleLast = Math.max(ruleLast, last - Math.round(slope * side * dx));
                    }
                if (hits[0] >= (far - near + 1) * .88f
                        && hits[1] >= (far - near + 1) * .88f
                        && ruleLast - ruleFirst + 1 <= thin + 1)
                    return new Rule(cy, ruleFirst, ruleLast, slope);
            }
        }
        return null;
    }

    private static boolean three(
            boolean[] ink,
            int stride,
            int left,
            int top,
            int w,
            int h,
            int ruleFirst,
            int ruleLast) {
        int[] min = new int[h], max = new int[h];
        Arrays.fill(min, w);
        Arrays.fill(max, -1);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (ink[(top + y) * stride + left + x]) {
                    min[y] = Math.min(min[y], x);
                    max[y] = Math.max(max[y], x);
                }
        // Interpolate only the missing staff rows inside this connected glyph.
        int before = ruleFirst - top - 1, after = ruleLast - top + 1;
        if (before < 0 || after >= h || max[before] < 0 || max[after] < 0) return false;
        for (int y = before + 1; y < after; y++) {
            float f = (y - before) / (float) (after - before);
            min[y] = Math.round(min[before] + f * (min[after] - min[before]));
            max[y] = Math.round(max[before] + f * (max[after] - max[before]));
        }
        int upperOpen = 0, lowerOpen = 0, upperEdge = -1, lowerEdge = -1, waist = w, foot = -1;
        for (int y = 0; y < h; y++) {
            if (max[y] < 0) continue;
            float f = y / (float) h;
            if ((y + 1) / (float) h > .15f && f <= .36f) {
                upperEdge = Math.max(upperEdge, max[y]);
                if (min[y] >= w * .4f) upperOpen++;
            }
            if ((y + 1) / (float) h > .60f && f <= .82f) {
                lowerEdge = Math.max(lowerEdge, max[y]);
                if (min[y] >= Math.min(w * .4f, max[y] * .5f)) lowerOpen++;
            }
            if (f >= .37f && f <= .55f) waist = Math.min(waist, max[y]);
            if (f >= .90f) foot = Math.max(foot, max[y]);
        }
        int required = Math.max(2, h / 12), indentation = Math.max(1, (int) Math.floor(w * .08f));
        return upperOpen >= required
                && lowerOpen >= required
                && upperEdge - waist >= indentation
                && lowerEdge - waist >= indentation
                && lowerEdge - foot >= indentation;
    }

    private static boolean independentBeam(
            byte[] gray,
            int width,
            int height,
            int x,
            int glyphLeft,
            int glyphRight,
            int glyphTop,
            int glyphBottom,
            int scanTop,
            int scanBottom,
            float gap,
            int level,
            boolean above) {
        int left = glyphLeft - Math.round(gap), right = glyphRight + Math.round(gap);
        int first = Math.max(1, scanTop), last = Math.min(height - 2, scanBottom);
        for (int y = first; y <= last; y++) {
            if (above ? y <= glyphBottom + 2 : y >= glyphTop - 2) continue;
            for (int slopeIndex = -4; slopeIndex <= 4; slopeIndex++) {
                float slope = slopeIndex * .05f;
                int supported = 0;
                boolean separate = true;
                for (int xx = left; xx <= right; xx++) {
                    int yy = y + Math.round(slope * (xx - x));
                    if (yy < 1 || yy + 1 >= height) {
                        separate = false;
                        break;
                    }
                    if (xx >= glyphLeft
                            && xx <= glyphRight
                            && (above ? yy - 1 <= glyphBottom + 1 : yy + 1 >= glyphTop - 1)) {
                        separate = false;
                        break;
                    }
                    if (dark(gray, width, xx, yy - 1, level)
                            && dark(gray, width, xx, yy, level)
                            && dark(gray, width, xx, yy + 1, level)) supported++;
                }
                if (separate && supported >= (right - left + 1) * .94f) return true;
            }
        }
        return false;
    }

    private static boolean dark(byte[] gray, int width, int x, int y, int level) {
        return (gray[y * width + x] & 255) < level;
    }
}
