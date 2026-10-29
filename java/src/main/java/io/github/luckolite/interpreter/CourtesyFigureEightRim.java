// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Finite raw counter/rim ownership for sparse model mistakes on a stacked meter denominator. */
final class CourtesyFigureEightRim {
    record Proof(int left, int top, int width, int height, boolean[] members) {
        boolean owns(int x, int y) {
            return x >= left
                    && x < left + width
                    && y >= top
                    && y < top + height
                    && members[(y - top) * width + x - left];
        }
    }

    private record Hole(int left, int right, int top, int bottom, float x, float y) {}

    static Proof capture(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float gap,
            int headTop,
            int headBottom) {
        int top = Math.max(0, headTop), bottom = Math.min(height - 1, headBottom);
        int w = right - left + 1, h = bottom - top + 1;
        if (labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || left < 0
                || right >= width
                || w < 3
                || h < 3
                || !Float.isFinite(gap)
                || gap < 6
                || w > gap * 6 + 1
                || (long) w * h > 65_536) return new Proof(0, 0, 0, 0, new boolean[0]);
        boolean[] seen = new boolean[w * h], owned = new boolean[w * h];
        int[] queue = new int[w * h];
        List<Hole> holes = new ArrayList<>();
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(top + seed / w) * width + left + seed % w] & 255) < 200)
                continue;
            int read = 0, end = 1, x0 = w, x1 = -1, y0 = h, y1 = -1;
            long sx = 0, sy = 0;
            seen[seed] = true;
            queue[0] = seed;
            while (read < end) {
                int at = queue[read++], x = at % w, y = at / w;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                sx += left + x;
                sy += top + y;
                for (int d : new int[] {-1, 1, -w, w}) {
                    int n = at + d;
                    if (n < 0
                            || n >= seen.length
                            || seen[n]
                            || Math.abs(n % w - x) + Math.abs(n / w - y) != 1) continue;
                    if ((gray[(top + n / w) * width + left + n % w] & 255) >= 200) {
                        seen[n] = true;
                        queue[end++] = n;
                    }
                }
            }
            if (x0 == 0
                    || x1 == w - 1
                    || y0 == 0
                    || y1 == h - 1
                    || end < gap * gap * .055f
                    || end > gap * gap * .5f
                    || x1 - x0 + 1 > gap
                    || y1 - y0 + 1 > gap * .8f) continue;
            if (holes.size() >= 512) return new Proof(0, 0, 0, 0, new boolean[0]);
            holes.add(
                    new Hole(
                            left + x0,
                            left + x1,
                            top + y0,
                            top + y1,
                            sx / (float) end,
                            sy / (float) end));
        }
        // All five independent thin rules identify the denominator's physical staff band.
        List<Integer> rules = new ArrayList<>();
        boolean[] ruleRows = new boolean[h];
        for (int y = top; y <= bottom; y++) {
            int dark = 0, samples = 0;
            for (int x = Math.round(left + gap * .3f); x <= right; x++) {
                samples++;
                if ((gray[y * width + x] & 255) < 180) dark++;
            }
            ruleRows[y - top] = samples > 0 && dark >= samples * .9f;
        }
        for (int y = 0; y < h; y++) {
            if (!ruleRows[y]) continue;
            int end = y + 1;
            while (end < h && ruleRows[end]) end++;
            if (end - y <= Math.max(2, Math.round(gap * .3f))) {
                if (rules.size() >= 96) return new Proof(0, 0, 0, 0, new boolean[0]);
                rules.add(top + (y + end - 1) / 2);
            }
            y = end - 1;
        }
        for (int first = 0; first + 4 < rules.size(); first++) {
            boolean staff = true;
            for (int line = 1; line < 5; line++)
                staff &=
                        Math.abs(rules.get(first + line) - rules.get(first + line - 1) - gap)
                                <= gap * .2f;
            if (!staff) continue;
            int staffTop = rules.get(first), staffBottom = rules.get(first + 4);
            List<Hole> lower =
                    holes.stream()
                            .filter(
                                    a ->
                                            a.top >= staffTop + gap * 1.65f
                                                    && a.bottom <= staffBottom + gap * .2f)
                            .toList();
            if (lower.size() != 2) continue;
            Hole a = lower.get(0), b = lower.get(1);
            float dy = Math.abs(a.y - b.y), cx = (a.x + b.x) * .5f;
            if (Math.abs(a.x - b.x) > gap * .3f
                    || dy < gap * .55f
                    || dy > gap * 1.3f
                    || a.bottom - a.top + 1 < (a.right - a.left + 1) * .55f
                    || b.bottom - b.top + 1 < (b.right - b.left + 1) * .55f) continue;
            int upper1 = 0, upper2 = 0;
            for (int y = staffTop; y < staffTop + gap * 1.65f; y++) {
                if (y < top || y > bottom || ruleRows[y - top]) continue;
                for (int x = Math.max(left, Math.round(cx - gap * 1.4f));
                        x <= Math.min(right, Math.round(cx + gap * 1.4f));
                        x++)
                    if ((gray[y * width + x] & 255) < 165 && labels[y * width + x] == 5) {
                        if (y < staffTop + gap * .85f) upper1++;
                        else upper2++;
                    }
            }
            if (upper1 < gap * gap * .2f
                    || upper2 < gap * gap * .2f
                    || upper1 + upper2 < gap * gap * .7f) continue;
            int x0 = Math.max(left, Math.min(a.left, b.left) - Math.round(gap * .45f));
            int x1 = Math.min(right, Math.max(a.right, b.right) + Math.round(gap * .45f));
            int y0 = Math.max(top, Math.round(staffTop + gap * 1.8f));
            int y1 = Math.min(bottom, Math.round(staffBottom + gap * .2f));
            int paper = 0, rows = 0, headPixels = 0, fringe = 0;
            boolean outside = false;
            for (int y = y0; y <= y1; y++) {
                if (!ruleRows[y - top]) {
                    rows++;
                    if (x0 > left
                            && x1 < right
                            && (gray[y * width + x0 - 1] & 255) >= 200
                            && (gray[y * width + x1 + 1] & 255) >= 200) paper++;
                }
                for (int x = x0; x <= x1; x++)
                    if (labels[y * width + x] == 2) {
                        headPixels++;
                        if ((gray[y * width + x] & 255) >= 180) fringe++;
                        outside |= !nearRimInk(gray, width, height, x, y);
                    }
            }
            // Only small mislabeled rim fragments are eligible; an independently complete
            // head retains its veto even if it happens to overlap the numeral's rectangle.
            if (outside
                    || rows < gap
                    || paper < rows * .8f
                    || headPixels == 0
                    || headPixels > gap * gap * .2f
                    || fringe > Math.max(2, gap * gap * .04f)) continue;
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    if (labels[y * width + x] == 2 && nearRimInk(gray, width, height, x, y))
                        owned[(y - top) * w + x - left] = true;
        }
        return new Proof(left, top, w, h, owned);
    }

    /** A model halo can extend one pixel into antialiased paper beside the proved raw rim. */
    private static boolean nearRimInk(byte[] gray, int width, int height, int x, int y) {
        if ((gray[y * width + x] & 255) < 180) return true;
        int dark = 0;
        for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
            for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++)
                if ((gray[yy * width + xx] & 255) < 180) dark++;
        return dark >= 2;
    }
}
