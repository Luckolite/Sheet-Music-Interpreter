// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** An isolated strong cap still belongs to its complete connected italic f on shaded paper. */
final class ShadedItalicFCap {
    private ShadedItalicFCap() {}

    static boolean proved(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            float gap,
            int seedLeft,
            int seedTop,
            int seedRight,
            int seedBottom) {
        if (gray == null
                || labels == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8
                || gap > height * .15f
                || seedLeft < 0
                || seedTop < 0
                || seedRight >= width
                || seedBottom >= height
                || seedLeft > seedRight
                || seedTop > seedBottom
                || seedRight - seedLeft + 1 > gap * .55f
                || seedBottom - seedTop + 1 > gap * .55f) return false;
        int cx = (seedLeft + seedRight) / 2, cy = (seedTop + seedBottom) / 2;
        int left = Math.round(cx - gap * 2.5f), right = Math.round(cx + gap * 2.5f);
        int top = Math.round(cy - gap * 3.5f), bottom = Math.round(cy + gap * 3.5f);
        if (left < 0 || right >= width || top < 0 || bottom >= height) return false;
        int w = right - left + 1, h = bottom - top + 1;
        int[] tones = new int[w * h];
        int n = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) tones[n++] = gray[y * width + x] & 255;
        Arrays.sort(tones);
        int paper = tones[(n - 1) * 75 / 100];
        if (paper < 96 || paper >= 185) return false;
        int threshold = Math.max(40, paper - 40);
        boolean[] ink = new boolean[w * h];
        int[] queue = new int[w * h];
        int read = 0, size = 0;
        for (int y = seedTop; y <= seedBottom; y++)
            for (int x = seedLeft; x <= seedRight; x++) {
                int p = (y - top) * w + x - left;
                if (!ink[p] && (gray[y * width + x] & 255) < threshold) {
                    ink[p] = true;
                    queue[size++] = p;
                }
            }
        int minX = w, maxX = -1, minY = h, maxY = -1, notation = 0;
        while (read < size) {
            int p = queue[read++], x = p % w, y = p / w;
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return false;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int label = labels[(top + y) * width + left + x];
            if (label == OmrMeasurePostProcessor.NOTEHEAD
                    || label == OmrMeasurePostProcessor.STEM_OR_REST
                    || label == OmrMeasurePostProcessor.CLEF_OR_KEY
                    || label == OmrMeasurePostProcessor.STAFF) notation++;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy, next = yy * w + xx;
                    if (!ink[next] && (gray[(top + yy) * width + left + xx] & 255) < threshold) {
                        ink[next] = true;
                        queue[size++] = next;
                    }
                }
        }
        int gh = maxY - minY + 1, gw = maxX - minX + 1;
        if (gh < gap * 2.2f
                || gh > gap * 3.6f
                || gw < gap * .75f
                || gw > gap * 2.2f
                || size < gh * gw * .15f
                || size > gh * gw * .55f
                || notation > size * .15f) return false;
        float relativeY = (cy - top - minY) / (float) gh;
        if (relativeY > .15f && relativeY < .85f) return false;
        float upper = capCenter(ink, w, minX, maxX, minY, minY + Math.round(gh * .22f));
        float lower = capCenter(ink, w, minX, maxX, minY + Math.round(gh * .8f), maxY);
        if (!Float.isFinite(upper)
                || !Float.isFinite(lower)
                || upper - lower < gap * .6f
                || upper < minX + gw * .55f
                || lower > minX + gw * .45f
                || maxX - upper < gap * .35f
                || Math.abs(cx - left - (relativeY <= .15f ? upper : lower)) > gap * .5f)
            return false;
        int[] bodyWidths = new int[gh];
        int bodyCount = 0, widestBar = 0;
        for (int y = minY + Math.round(gh * .2f); y <= minY + Math.round(gh * .75f); y++) {
            int first = w, last = -1;
            for (int x = minX; x <= maxX; x++)
                if (ink[y * w + x]) {
                    first = Math.min(first, x);
                    last = Math.max(last, x);
                }
            if (last < first) return false;
            int span = last - first + 1;
            float phase = (y - minY) / (float) gh;
            if (phase <= .5f) widestBar = Math.max(widestBar, span);
            if (phase >= .5f) bodyWidths[bodyCount++] = span;
        }
        if (bodyCount == 0) return false;
        Arrays.sort(bodyWidths, 0, bodyCount);
        int shaftWidth = bodyWidths[bodyCount / 2];
        return shaftWidth <= gap * .6f
                && widestBar >= gap * .6f
                && widestBar >= shaftWidth + gap * .2f;
    }

    private static float capCenter(
            boolean[] ink, int width, int left, int right, int top, int bottom) {
        int sum = 0, count = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (ink[y * width + x]) {
                    sum += x;
                    count++;
                }
        return count == 0 ? Float.NaN : sum / (float) count;
    }
}
