// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Complete offset spines and four connectors prove two vertically joined naturals. */
final class StackedNaturalColumn {
    static float[] centers(
            byte[] pixels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            byte label,
            float gap) {
        int w = right - left + 1, h = bottom - top + 1;
        if (pixels == null
                || pixels.length != width * height
                || !Float.isFinite(gap)
                || gap < 4
                || left < 0
                || right >= width
                || top < 0
                || bottom >= height
                || w < gap * .48f
                || w > gap * 2.8f
                || h < gap * 3.7f
                || h > gap * 6.4f) return new float[0];
        int[] columns = new int[w];
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (ink(pixels[y * width + x], label)) columns[x - left]++;
        float[] staggered = sharedSpine(pixels, width, top, bottom, left, w, columns, label, gap);
        if (staggered.length == 2) return staggered;
        if (w > gap * 1.55f) return new float[0];
        int a = 0, b = w / 2;
        for (int x = 1; x < w / 2; x++) if (columns[x] > columns[a]) a = x;
        for (int x = w / 2 + 1; x < w; x++) if (columns[x] > columns[b]) b = x;
        if (b - a < w * .28f || columns[a] < h * .5f || columns[b] < h * .5f) return new float[0];
        int radius = Math.max(0, Math.round(w * .09f));
        int[] la = endpoints(pixels, width, top, bottom, left, w, a, radius, label);
        int[] rb = endpoints(pixels, width, top, bottom, left, w, b, radius, label);
        if (rb[0] - la[0] < gap * .25f
                || rb[1] - la[1] < gap * .25f
                || la[2] < (la[1] - la[0] + 1) * .75f
                || rb[2] < (rb[1] - rb[0] + 1) * .75f) return new float[0];
        int il = a + radius + 1, ir = b - radius - 1;
        if (il > ir) return new float[0];
        for (float slope : new float[] {0, -.3f, -.6f, .3f, .6f}) {
            List<int[]> bridges = new ArrayList<>();
            int start = -1;
            for (int y = top; y <= bottom + 1; y++) {
                int count = 0;
                if (y <= bottom)
                    for (int x = il; x <= ir; x++) {
                        int yy = y + Math.round((x - a) * slope);
                        if (yy >= top && yy <= bottom && ink(pixels[yy * width + left + x], label))
                            count++;
                    }
                boolean filled = count >= Math.max(1, (int) Math.ceil((ir - il + 1) * .75f));
                if (filled && start < 0) start = y;
                if (!filled && start >= 0) {
                    bridges.add(new int[] {start, y - 1});
                    start = -1;
                }
            }
            if (bridges.size() != 4) continue;
            float[] c = new float[4];
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                int[] bridge = bridges.get(i);
                c[i] = (bridge[0] + bridge[1]) * .5f;
                if (bridge[1] - bridge[0] + 1 > gap * .5f) valid = false;
                if (i > 0 && bridge[0] - bridges.get(i - 1)[1] - 1 < Math.max(1, gap * .1f))
                    valid = false;
            }
            float separation1 = c[1] - c[0], separation2 = c[3] - c[2];
            if (!valid
                    || separation1 < gap * .45f
                    || separation1 > gap * 1.6f
                    || separation2 < gap * .45f
                    || separation2 > gap * 1.6f
                    || Math.abs(separation1 - separation2) > gap * .35f) continue;
            float first = (c[0] + c[1]) * .5f, second = (c[2] + c[3]) * .5f;
            float dy = second - first;
            int shift = Math.round((b - a) * slope);
            if (dy < gap
                    || dy > gap * 2.5f
                    || Math.abs(c[0] + shift - rb[0]) > gap * .35f
                    || Math.abs(la[1] - c[3]) > gap * .35f
                    || c[0] - la[0] < gap * .35f
                    || rb[1] - (c[3] + shift) < gap * .35f) continue;
            return new float[] {first, second};
        }
        return new float[0];
    }

    /** Opposite outer spines terminate each natural; the middle spine is shared. */
    private static float[] sharedSpine(
            byte[] pixels,
            int width,
            int top,
            int bottom,
            int left,
            int w,
            int[] columns,
            byte label,
            float gap) {
        int h = bottom - top + 1;
        if (w < gap * 1.05f) return new float[0];
        int middle = w / 2;
        for (int x = Math.max(1, w / 4); x <= Math.min(w - 2, w * 3 / 4); x++)
            if (columns[x] > columns[middle]) middle = x;
        int distance = Math.max(3, Math.round(gap * .25f));
        int a = 0, b = w - 1;
        for (int x = 1; x <= middle - distance; x++) if (columns[x] > columns[a]) a = x;
        for (int x = middle + distance; x < w; x++) if (columns[x] > columns[b]) b = x;
        if (middle - a < distance
                || b - middle < distance
                || columns[middle] < h * .75f
                || columns[a] < gap * 1.5f
                || columns[b] < gap * 1.5f) return new float[0];
        int radius = Math.max(0, Math.round(gap * .1f));
        int[] la = endpoints(pixels, width, top, bottom, left, w, a, radius, label);
        int[] mid = endpoints(pixels, width, top, bottom, left, w, middle, radius, label);
        int[] rb = endpoints(pixels, width, top, bottom, left, w, b, radius, label);
        if (la[2] < (la[1] - la[0] + 1) * .75f
                || rb[2] < (rb[1] - rb[0] + 1) * .75f
                || mid[2] < (mid[1] - mid[0] + 1) * .85f) return new float[0];
        for (float slope : new float[] {0, -.3f, -.6f, .3f, .6f}) {
            float[] upper =
                    connectorPair(
                            pixels, width, top, bottom, left, middle, b, radius, label, gap, slope);
            float[] lower =
                    connectorPair(
                            pixels, width, top, bottom, left, a, middle, radius, label, gap, slope);
            if (upper.length != 2 || lower.length != 2) continue;
            float first = (upper[0] + upper[1]) * .5f;
            float second = (lower[0] + lower[1]) * .5f;
            int upperShift = Math.round((b - middle) * slope);
            int lowerShift = Math.round((middle - a) * slope);
            if (second - first < gap
                    || second - first > gap * 2.5f
                    || Math.abs((upper[1] - upper[0]) - (lower[1] - lower[0])) > gap * .35f
                    || Math.abs(upper[0] + upperShift - rb[0]) > gap * .35f
                    || Math.abs(la[1] - lower[1]) > gap * .35f
                    || upper[0] - mid[0] < gap * .35f
                    || mid[1] - (lower[1] + lowerShift) < gap * .35f
                    || rb[1] - (upper[1] + upperShift) < gap * .35f
                    || lower[0] - la[0] < gap * .35f) continue;
            return new float[] {first, second};
        }
        return new float[0];
    }

    private static float[] connectorPair(
            byte[] pixels,
            int width,
            int top,
            int bottom,
            int left,
            int a,
            int b,
            int radius,
            byte label,
            float gap,
            float slope) {
        int il = a + radius + 1, ir = b - radius - 1;
        if (il > ir) return new float[0];
        List<int[]> bridges = new ArrayList<>();
        int start = -1;
        for (int y = top; y <= bottom + 1; y++) {
            int count = 0;
            if (y <= bottom)
                for (int x = il; x <= ir; x++) {
                    int yy = y + Math.round((x - a) * slope);
                    if (yy >= top && yy <= bottom && ink(pixels[yy * width + left + x], label))
                        count++;
                }
            boolean filled = count >= Math.max(1, (int) Math.ceil((ir - il + 1) * .75f));
            if (filled && start < 0) start = y;
            if (!filled && start >= 0) {
                bridges.add(new int[] {start, y - 1});
                start = -1;
            }
        }
        if (bridges.size() != 2) return new float[0];
        int[] first = bridges.get(0), second = bridges.get(1);
        float dy = (second[0] + second[1] - first[0] - first[1]) * .5f;
        if (first[1] - first[0] + 1 > gap * .5f
                || second[1] - second[0] + 1 > gap * .5f
                || second[0] - first[1] - 1 < Math.max(2, gap * .15f)
                || dy < gap * .45f
                || dy > gap * 1.6f) return new float[0];
        return new float[] {(first[0] + first[1]) * .5f, (second[0] + second[1]) * .5f};
    }

    private static int[] endpoints(
            byte[] pixels,
            int width,
            int top,
            int bottom,
            int left,
            int w,
            int spine,
            int radius,
            byte label) {
        int first = bottom + 1, last = top - 1, rows = 0;
        for (int y = top; y <= bottom; y++) {
            boolean found = false;
            for (int x = Math.max(0, spine - radius); x <= Math.min(w - 1, spine + radius); x++)
                if (ink(pixels[y * width + left + x], label)) found = true;
            if (found) {
                first = Math.min(first, y);
                last = y;
                rows++;
            }
        }
        return new int[] {first, last, rows};
    }

    private static boolean ink(byte pixel, byte label) {
        return label == 0 ? pixel == 3 || pixel == 5 : pixel == label;
    }
}
