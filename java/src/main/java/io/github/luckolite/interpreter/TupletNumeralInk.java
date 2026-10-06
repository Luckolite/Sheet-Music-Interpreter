// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Small local contrast/rule-cleaned windows for retrying an already bounded numeral search. */
final class TupletNumeralInk {
    private TupletNumeralInk() {}

    record Window(byte[] pixels, int width, int height, int left, int top) {}

    /** A binary-looking paper stain is not a printed numeral. Use original gray
     * values, not the normalized mask, to require local glyph-to-paper contrast. */
    static boolean hasGlyphContrast(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, float gap) {
        if (gray == null
                || left < 0
                || top < 0
                || right >= w
                || bottom >= h
                || left > right
                || top > bottom) return false;
        int[] ink = new int[256], paper = new int[256];
        int inside = 0, outside = 0;
        int margin = Math.max(3, Math.round(gap * .45f));
        for (int y = Math.max(0, top - margin); y <= Math.min(h - 1, bottom + margin); y++)
            for (int x = Math.max(0, left - margin); x <= Math.min(w - 1, right + margin); x++) {
                int v = gray[y * w + x] & 255;
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    ink[v]++;
                    inside++;
                } else {
                    paper[v]++;
                    outside++;
                }
            }
        if (inside < 6 || outside < 6) return false;
        return percentile(paper, outside, .85f) - percentile(ink, inside, .10f) >= 32;
    }

    private static int percentile(int[] histogram, int count, float fraction) {
        int sum = 0;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i];
            if (sum >= count * fraction) return i;
        }
        return 255;
    }

    static Window window(
            byte[] gray,
            int w,
            int h,
            float x1,
            float x3,
            float y1,
            float y3,
            float gap,
            int requested) {
        return window(gray, w, h, x1, x3, y1, y3, gap, requested, false);
    }

    static Window ruleEdgesWindow(
            byte[] gray,
            int w,
            int h,
            float x1,
            float x3,
            float y1,
            float y3,
            float gap,
            int requested) {
        return window(gray, w, h, x1, x3, y1, y3, gap, requested, true);
    }

    private static Window window(
            byte[] gray,
            int w,
            int h,
            float x1,
            float x3,
            float y1,
            float y3,
            float gap,
            int requested,
            boolean ruleEdges) {
        if (gray == null || gap < 4) return null;
        int left = Math.max(0, Math.round(x1 - gap * 2)),
                right = Math.min(w - 1, Math.round(x3 + gap * 2));
        int top = Math.max(0, Math.round(y1 - gap * 7.5f)),
                bottom = Math.min(h - 1, Math.round(y3 + gap * 7.5f));
        int ww = right - left + 1, hh = bottom - top + 1;
        if (ww < 3 || hh < 3) return null;
        int[] histogram = new int[256];
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) histogram[gray[y * w + x] & 255]++;
        int paper = 255, sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += histogram[i];
            if (sum >= ww * hh * .85f) {
                paper = i;
                break;
            }
        }
        int threshold = Math.min(requested, paper - 22);
        if (threshold < 100) return null;
        byte[] pixels = new byte[ww * hh];
        int[] occupancy = new int[hh];
        for (int y = 0; y < hh; y++)
            for (int x = 0; x < ww; x++) {
                boolean ink = (gray[(y + top) * w + x + left] & 255) <= threshold;
                pixels[y * ww + x] = ink ? 0 : (byte) 255;
                if (ink) occupancy[y]++;
            }
        byte[] original = pixels.clone();
        if (ruleEdges) removeLevelRuleEdges(pixels, ww, hh, gap);
        for (int y = 1; y < hh - 1; y++) {
            if (occupancy[y] < ww * .83f) continue;
            int end = y;
            while (end + 1 < hh - 1 && occupancy[end + 1] >= ww * .83f) end++;
            if (end - y + 1 <= Math.max(2, Math.round(gap * .23f))) {
                for (int x = 0; x < ww; x++) {
                    boolean above = false, below = false;
                    for (int dx = -1; dx <= 1; dx++)
                        if (x + dx >= 0 && x + dx < ww) {
                            above |= original[(y - 1) * ww + x + dx] == 0;
                            below |= original[(end + 1) * ww + x + dx] == 0;
                        }
                    boolean crossing = above || below;
                    if (!crossing)
                        for (int yy = y; yy <= end; yy++) pixels[yy * ww + x] = (byte) 255;
                }
            }
            y = end;
        }
        removeSlopedRules(pixels, ww, hh, gap);
        return new Window(pixels, ww, hh, left, top);
    }

    private static void removeLevelRuleEdges(byte[] pixels, int w, int h, float gap) {
        byte[] original = pixels.clone();
        int max = Math.max(2, Math.round(gap * .30f));
        for (int cy = max + 2; cy < h - max - 2; cy++) {
            int valid = 0, thin = 0;
            for (int x = 0; x < w; x++) {
                if (original[cy * w + x] != 0) continue;
                valid++;
                int top = cy, bottom = cy;
                while (top > cy - max && original[(top - 1) * w + x] == 0) top--;
                while (bottom < cy + max && original[(bottom + 1) * w + x] == 0) bottom++;
                if (bottom - top + 1 <= max) thin++;
            }
            if (valid < w * .90f || thin < w * .82f) continue;
            for (int x = 1; x < w - 1; x++) {
                if (original[cy * w + x] != 0) continue;
                int top = cy, bottom = cy;
                while (top > cy - max && original[(top - 1) * w + x] == 0) top--;
                while (bottom < cy + max && original[(bottom + 1) * w + x] == 0) bottom++;
                if (bottom - top + 1 > max) continue;
                boolean continuation = false;
                for (int dx = -1; dx <= 1; dx++)
                    continuation |=
                            original[(top - 1) * w + x + dx] == 0
                                    || original[(bottom + 1) * w + x + dx] == 0;
                if (!continuation)
                    for (int yy = top; yy <= bottom; yy++) pixels[yy * w + x] = (byte) 255;
            }
        }
    }

    private static void removeSlopedRules(byte[] pixels, int w, int h, float gap) {
        byte[] original = pixels.clone();
        int max = Math.max(2, Math.round(gap * .30f));
        float center = w * .5f, validThreshold = w * .90f, thinThreshold = w * .82f;
        for (int angle = -4; angle <= 4; angle++) {
            if (angle == 0) continue;
            float slope = angle * .025f;
            for (int cy = max + 2; cy < h - max - 2; cy++) {
                int valid = 0, thin = 0;
                for (int x = 0; x < w; x++) {
                    // Each remaining column can supply at most one observation.
                    // Reject only when the original coverage thresholds are unreachable.
                    int remaining = w - x;
                    if (valid + remaining < validThreshold || thin + remaining < thinThreshold)
                        break;
                    int y = Math.round(cy + slope * (x - center));
                    if (y - max < 0 || y + max >= h) continue;
                    if (original[y * w + x] != 0) continue;
                    valid++;
                    int top = y, bottom = y;
                    while (top > y - max && original[(top - 1) * w + x] == 0) top--;
                    while (bottom < y + max && original[(bottom + 1) * w + x] == 0) bottom++;
                    if (bottom - top + 1 <= max) thin++;
                }
                if (valid < validThreshold || thin < thinThreshold) continue;
                for (int x = 1; x < w - 1; x++) {
                    int y = Math.round(cy + slope * (x - center));
                    if (y - max - 1 < 0 || y + max + 1 >= h || original[y * w + x] != 0) continue;
                    int top = y, bottom = y;
                    while (top > y - max && original[(top - 1) * w + x] == 0) top--;
                    while (bottom < y + max && original[(bottom + 1) * w + x] == 0) bottom++;
                    if (bottom - top + 1 > max) continue;
                    boolean above = false, below = false;
                    for (int dx = -1; dx <= 1; dx++) {
                        above |= original[(top - 1) * w + x + dx] == 0;
                        below |= original[(bottom + 1) * w + x + dx] == 0;
                    }
                    boolean crossing = above && below;
                    if (!crossing)
                        for (int yy = top; yy <= bottom; yy++) pixels[yy * w + x] = (byte) 255;
                }
            }
        }
    }
}
