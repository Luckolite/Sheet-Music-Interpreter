// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A closed printed head owns its stem even when the model omitted the grey interior. */
final class ClosedHeadBarlineGuard {
    // Bounded per-thread flood-fill storage; no array escapes this synchronous helper.
    private static final int MAX_CACHED_PIXELS = 65536;
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    private static final class Workspace {
        boolean[] seen = new boolean[0];
        int[] queue = new int[0];

        void ensure(int pixels) {
            if (seen.length < pixels) {
                boolean[] replacementSeen = new boolean[pixels];
                int[] replacementQueue = new int[pixels];
                // Commit both buffers only after allocation succeeds, preserving retry safety.
                seen = replacementSeen;
                queue = replacementQueue;
            }
        }
    }

    private ClosedHeadBarlineGuard() {}

    static java.util.List<Integer> withoutOwnedByOtherStaff(
            java.util.List<Integer> boundaries,
            java.util.List<Integer> other,
            byte[] gray,
            int width,
            int height,
            int top,
            int bottom,
            float gap) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int i = 0; i < boundaries.size(); i++) {
            int column = boundaries.get(i);
            boolean shared = false;
            for (int candidate : other)
                if (Math.abs(candidate - column) <= gap * .75f) {
                    shared = true;
                    break;
                }
            if (i == 0
                    || i == boundaries.size() - 1
                    || shared
                    || !attached(gray, width, height, column, top, bottom, gap)) result.add(column);
        }
        return java.util.List.copyOf(result);
    }

    static boolean attached(
            byte[] gray,
            int width,
            int height,
            int column,
            int staffTop,
            int staffBottom,
            float gap) {
        if (gray == null || gray.length != (long) width * height || gap < 6 || !Float.isFinite(gap))
            return false;
        int halo = Math.max(1, Math.round(gap * .12f));
        for (int actual = Math.max(0, column - halo);
                actual <= Math.min(width - 1, column + halo);
                actual++)
            if (attachedAt(gray, width, height, actual, staffTop, staffBottom, gap, 150)
                    || attachedAt(gray, width, height, actual, staffTop, staffBottom, gap, 120))
                return true;
        return filledBulge(gray, width, height, column, staffTop, staffBottom, gap);
    }

    private static boolean filledBulge(
            byte[] gray,
            int width,
            int height,
            int column,
            int staffTop,
            int staffBottom,
            float gap) {
        int top = Math.max(0, staffTop - Math.round(gap * 1.5f)),
                bottom = Math.min(height - 1, staffBottom + Math.round(gap * 1.5f));
        int reach = Math.round(gap * 2),
                left = Math.max(0, column - reach),
                right = Math.min(width - 1, column + reach);
        int[] tones = new int[256];
        int count = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                tones[gray[y * width + x] & 255]++;
                count++;
            }
        int paper = 0, seen = tones[0], target = (count * 3 + 3) / 4;
        while (seen < target && paper < 255) seen += tones[++paper];
        int limit = Math.min(210, Math.max(0, paper - 25)), span = bottom - top + 1;
        int halo = Math.max(2, Math.round(gap * .2f));
        for (int origin = Math.max(0, column - halo);
                origin <= Math.min(width - 1, column + halo);
                origin++)
            for (int side : new int[] {-1, 1}) {
                float[] extent = new float[span];
                for (int i = 0; i < span; i++) {
                    int y = top + i, d = 0;
                    while (d < reach
                            && origin + side * d >= 0
                            && origin + side * d < width
                            && (gray[y * width + origin + side * d] & 255) <= limit) d++;
                    extent[i] = d >= reach ? -1 : d;
                }
                float[] printed = extent.clone();
                // Only short staff-rule crossings can be occluded; never bridge a
                // broad beam or blank stretch into an oval.
                for (int i = 0; i < span; i++)
                    if (extent[i] < 0) {
                        int first = i;
                        while (i < span && extent[i] < 0) i++;
                        int n = i - first;
                        if (first > 0 && i < span && n <= Math.max(2, Math.round(gap * .3f)))
                            for (int j = first; j < i; j++)
                                extent[j] =
                                        extent[first - 1]
                                                + (extent[i] - extent[first - 1])
                                                        * (j - first + 1)
                                                        / (n + 1f);
                    }
                for (int first = 1; first < span - 1; first++) {
                    if (extent[first] < gap * .35f || extent[first - 1] >= gap * .35f) continue;
                    int last = first, support = 0;
                    float peak = 0;
                    while (last < span && extent[last] >= gap * .35f) {
                        peak = Math.max(peak, extent[last]);
                        if (extent[last] >= gap * .55f) support++;
                        last++;
                    }
                    if (last - first < gap * .65f
                            || last - first > gap * 1.25f
                            || support < gap * .5f
                            || peak < gap * .8f
                            || last >= span) continue;
                    int wide = 0;
                    for (int row = first; row < last; row++) if (printed[row] >= peak * .8f) wide++;
                    if (wide < gap * .45f) continue;
                    if (peak - extent[first] >= gap * .15f && peak - extent[last - 1] >= gap * .15f)
                        return true;
                }
            }
        return false;
    }

    private static boolean attachedAt(
            byte[] gray,
            int width,
            int height,
            int column,
            int staffTop,
            int staffBottom,
            float gap,
            int outline) {
        int left = Math.max(0, column - Math.round(gap * 1.8f)),
                right = Math.min(width - 1, column + Math.round(gap * 1.8f));
        int top = Math.max(0, staffTop - Math.round(gap * 1.5f)),
                bottom = Math.min(height - 1, staffBottom + Math.round(gap * 1.5f));
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 1 || h < 1) return false;
        int pixels = w * h;
        boolean[] seen;
        int[] queue;
        if (pixels > 0 && pixels <= MAX_CACHED_PIXELS) {
            Workspace workspace = WORKSPACE.get();
            workspace.ensure(pixels);
            seen = workspace.seen;
            queue = workspace.queue;
        } else {
            seen = new boolean[pixels];
            queue = new int[pixels];
        }
        // Excluded ink is never part of a bright connected component.
        // Capture membership once so every flood-fill neighbor uses local indexing.
        for (int y = 0; y < h; y++) {
            int sourceRow = (top + y) * width + left, localRow = y * w;
            for (int x = 0; x < w; x++) seen[localRow + x] = (gray[sourceRow + x] & 255) <= outline;
        }
        for (int seed = 0; seed < pixels; seed++) {
            if (seen[seed]) continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            int minX = w, maxX = 0, minY = h, maxY = 0;
            while (read < end) {
                int p = queue[read++], x = p % w, y = p / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                // Preserve left/right/up/down visit order using local offsets.
                if (x > 0 && !seen[p - 1]) {
                    seen[p - 1] = true;
                    queue[end++] = p - 1;
                }
                if (x + 1 < w && !seen[p + 1]) {
                    seen[p + 1] = true;
                    queue[end++] = p + 1;
                }
                if (y > 0 && !seen[p - w]) {
                    seen[p - w] = true;
                    queue[end++] = p - w;
                }
                if (y + 1 < h && !seen[p + w]) {
                    seen[p + w] = true;
                    queue[end++] = p + w;
                }
            }
            int pw = maxX - minX + 1, ph = maxY - minY + 1;
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || pw < gap * .5f
                    || pw > gap * 1.6f
                    || ph < gap * .4f
                    || ph > gap * 1.1f
                    || end < pw * ph * .45f) continue;
            float cx = left + (minX + maxX) * .5f, cy = top + (minY + maxY) * .5f;
            if (Math.abs(cx - column) < gap * .25f || Math.abs(cx - column) > gap * 1.15f) continue;
            int[] shades = new int[end];
            for (int i = 0; i < end; i++)
                shades[i] = gray[(top + queue[i] / w) * width + left + queue[i] % w] & 255;
            java.util.Arrays.sort(shades);
            if (shades[end / 2] > 205) continue;
            int[] surrounding = new int[256];
            int sampleCount = 0;
            for (int sy = Math.max(0, Math.round(cy - gap));
                    sy <= Math.min(height - 1, Math.round(cy + gap));
                    sy++)
                for (int sx = Math.max(0, Math.round(cx - gap * 2));
                        sx <= Math.min(width - 1, Math.round(cx + gap * 2));
                        sx++) {
                    surrounding[gray[sy * width + sx] & 255]++;
                    sampleCount++;
                }
            int paper = 0, paperCount = surrounding[0], paperTarget = (sampleCount * 3 + 3) / 4;
            while (paperCount < paperTarget && paper < 255) paperCount += surrounding[++paper];
            if (paper - shades[end / 2] < 25) continue;
            // A filled oval's sides bend back; rectangular spaces beside a real
            // barline or between two note stems cannot veto that barline.
            int[] rowLeft = new int[ph], rowRight = new int[ph];
            java.util.Arrays.fill(rowLeft, w);
            for (int i = 0; i < end; i++) {
                int x = queue[i] % w, y = queue[i] / w - minY;
                rowLeft[y] = Math.min(rowLeft[y], x);
                rowRight[y] = Math.max(rowRight[y], x);
            }
            int m = (ph - 1) / 2, e = Math.max(0, Math.round(ph * .15f));
            if (Math.min(
                            (rowLeft[e] + rowLeft[ph - 1 - e]) * .5f - rowLeft[m],
                            rowRight[m] - (rowRight[e] + rowRight[ph - 1 - e]) * .5f)
                    < .5f) continue;
            int edge = cx < column ? left + maxX + 1 : left + minX - 1;
            if (Math.abs(edge - column) > Math.max(4, gap * .32f)) continue;
            int connected = 0, total = 0;
            for (int y = Math.max(0, Math.round(cy - gap * .25f));
                    y <= Math.min(height - 1, Math.round(cy + gap * .25f));
                    y++) {
                total++;
                boolean path = true;
                for (int x = Math.min(edge, column); x <= Math.max(edge, column); x++)
                    if ((gray[y * width + x] & 255) > 170) {
                        path = false;
                        break;
                    }
                if (path) connected++;
            }
            if (connected >= Math.max(2, total * .35f)) return true;
        }
        return false;
    }
}
