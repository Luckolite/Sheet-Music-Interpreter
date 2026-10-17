// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Compact complete raw components with symbolic head and independently labelled stem evidence. */
final class SymbolStemHeadRecovery {
    record Head(int left, int top, int right, int bottom, int area, float x, float y) {}

    static List<Head> find(
            byte[] labels, byte[] gray, int w, int h, float gap, int top, int bottom) {
        List<Head> result = new ArrayList<>();
        if (gray == null
                || labels == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8) return result;
        top = Math.max(0, top);
        bottom = Math.min(h - 1, bottom);
        if (top >= bottom) return result;
        int size = w * (bottom - top + 1);
        boolean[] seen = new boolean[size];
        int[] queue = new int[size];
        for (int seed = 0; seed < size; seed++) {
            if (seen[seed] || (gray[top * w + seed] & 255) >= 205) continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            int l = w, r = 0, t = h, b = 0, sy = 0, nh = 0;
            long sx = 0, yy = 0;
            while (read < end) {
                int p = queue[read++], x = p % w, y = top + p / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                sx += x;
                yy += y;
                if (labels[y * w + x] == 5) sy++;
                if (labels[y * w + x] == 2) nh++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, ny = y + dy;
                        if (xx < 0 || xx >= w || ny < top || ny > bottom) continue;
                        int next = (ny - top) * w + xx;
                        if (!seen[next] && (gray[ny * w + xx] & 255) < 205) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            int ww = r - l + 1, hh = b - t + 1;
            if (t == top
                    || b == bottom
                    || l == 0
                    || r == w - 1
                    || ww < gap * .65f
                    || ww > gap * 1.9f
                    || hh < gap * .55f
                    || hh > gap * 1.5f
                    || end < gap * gap * .32f
                    || sy < end * .4f
                    || nh > end * .2f) continue;
            float cy = yy / (float) end;
            boolean stem = false;
            for (int direction : new int[] {-1, 1}) {
                int edge = direction == 1 ? l : r;
                for (int x = Math.max(0, edge - 3); x <= Math.min(w - 1, edge + 3); x++) {
                    int ink = 0, semantic = 0, total = 0;
                    for (int d = Math.round(gap * .7f); d < Math.round(gap * 2.7f); d++) {
                        int y = Math.round(cy + direction * d);
                        if (y < 0 || y >= h) continue;
                        total++;
                        if ((gray[y * w + x] & 255) < 205) ink++;
                        if (labels[y * w + x] == 1) semantic++;
                    }
                    if (total >= gap && ink >= total * .85f && semantic >= total * .5f) stem = true;
                }
            }
            if (stem) result.add(new Head(l, t, r, b, end, sx / (float) end, cy));
        }
        return result;
    }

    /**
     * Broken oval rims still need opposing edge contrast and an owned labelled shaft. The caller
     * must independently prove a complete beam to a retained note before admission.
     */
    static List<Head> sparseOvals(
            byte[] labels, byte[] gray, int w, int h, float gap, int top, int bottom) {
        List<Head> out = new ArrayList<>();
        if (gray == null
                || labels == null
                || w < 3
                || h < 3
                || gray.length != (long) w * h
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8) return out;
        List<Integer> scores = new ArrayList<>();
        float rx = gap * .69f, ry = gap * .4f;
        int margin = (int) Math.ceil(gap * 1.4f),
                stride = Math.max(1, Math.round(gap * .14f)),
                pixelRadius = Math.max(1, Math.round(gap * .07f));
        for (int y = Math.max(top + margin, margin);
                y <= Math.min(bottom - margin, h - 1 - margin);
                y += stride)
            for (int x = margin; x < w - margin; x += stride) {
                if (!ownedShaft(labels, gray, w, h, x, y, rx, gap)) continue;
                int[] quadrants = new int[4];
                int score = 0;
                for (int k = 0; k < 48; k++) {
                    double angle = k * Math.PI / 24;
                    float u = rx * (float) Math.cos(angle),
                            v = ry * (float) Math.sin(angle) - .4f * u;
                    int xx = Math.round(x + u), yy = Math.round(y + v), ink = 255;
                    for (int dy = -pixelRadius; dy <= pixelRadius; dy++)
                        for (int dx = -pixelRadius; dx <= pixelRadius; dx++)
                            ink = Math.min(ink, gray[(yy + dy) * w + xx + dx] & 255);
                    int outer = gray[Math.round(y + v * 1.6f) * w + Math.round(x + u * 1.6f)] & 255;
                    if (ink < 205 && outer - ink >= 30) {
                        score++;
                        quadrants[k / 12]++;
                    }
                }
                if (score < 39
                        || quadrants[0] < 8
                        || quadrants[1] < 8
                        || quadrants[2] < 8
                        || quadrants[3] < 8) continue;
                int hh = (int) Math.ceil(Math.sqrt(ry * ry + .16f * rx * rx));
                int area = 0;
                for (int dy = -hh; dy <= hh; dy++)
                    for (int dx = -(int) rx; dx <= rx; dx++)
                        if (dx * dx / (rx * rx) + (dy + .4f * dx) * (dy + .4f * dx) / (ry * ry) <= 1
                                && (gray[(y + dy) * w + x + dx] & 255) < 225) area++;
                if (area < gap * gap * .3f) continue;
                Head candidate =
                        new Head(
                                Math.round(x - rx), y - hh, Math.round(x + rx), y + hh, area, x, y);
                int duplicate = -1;
                for (int i = 0; i < out.size(); i++)
                    if (Math.abs(out.get(i).x - x) < gap * .8f
                            && Math.abs(out.get(i).y - y) < gap * .65f) {
                        duplicate = i;
                        break;
                    }
                if (duplicate < 0) {
                    out.add(candidate);
                    scores.add(score);
                } else if (score > scores.get(duplicate)) {
                    out.set(duplicate, candidate);
                    scores.set(duplicate, score);
                }
            }
        return out;
    }

    private static boolean ownedShaft(
            byte[] labels, byte[] gray, int w, int h, int cx, int cy, float rx, float gap) {
        int radius = Math.max(3, Math.round(gap * .22f));
        for (int direction : new int[] {-1, 1})
            for (int dx = -radius; dx <= radius; dx++) {
                int x = Math.round(cx - direction * rx) + dx,
                        mid = Math.round(cy + direction * gap * 1.5f);
                if (x < 0
                        || x >= w
                        || mid < 0
                        || mid >= h
                        || labels[mid * w + x] != 1
                        || (gray[mid * w + x] & 255) >= 205) continue;
                int raw = 0, semantic = 0, total = 0;
                for (int d = Math.round(gap * .8f); d <= Math.round(gap * 2.5f); d++) {
                    int y = cy + direction * d;
                    if (y < 0 || y >= h) continue;
                    total++;
                    if ((gray[y * w + x] & 255) < 205) raw++;
                    if (labels[y * w + x] == 1) semantic++;
                }
                if (total >= gap && raw >= total * .85f && semantic >= total * .5f) return true;
            }
        return false;
    }
}
