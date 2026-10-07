// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Excludes only a printed thick horizontal bar with two bilateral vertical caps. */
final class PrintedRestBarHeads {
    static byte[] withoutBars(byte[] labels, byte[] gray, int w, int h) {
        if (labels == null
                || gray == null
                || labels.length != (long) w * h
                || gray.length != labels.length) return labels;
        boolean[] seen = new boolean[labels.length];
        int[] queue = new int[labels.length];
        byte[] result = labels;
        for (int origin = 0; origin < labels.length; origin++) {
            if (seen[origin] || labels[origin] != 2) continue;
            int read = 0, size = 1, minX = origin % w, maxX = minX, minY = origin / w, maxY = minY;
            queue[0] = origin;
            seen[origin] = true;
            while (read < size) {
                int at = queue[read++], x = at % w, y = at / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int next : new int[] {at - 1, at + 1, at - w, at + w})
                    if (next >= 0
                            && next < labels.length
                            && !seen[next]
                            && labels[next] == 2
                            && Math.abs(next % w - x) + Math.abs(next / w - y) == 1) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
            }
            if (!proved(gray, w, h, minX, minY, maxX, maxY)) continue;
            if (result == labels) result = labels.clone();
            for (int i = 0; i < size; i++) result[queue[i]] = 0;
        }
        return result;
    }

    static boolean proved(byte[] gray, int w, int h, int left, int top, int right, int bottom) {
        int bw = right - left + 1, bh = bottom - top + 1;
        if (bh < 4 || bh > 48 || bw < bh * 3 || bw > bh * 12 || top - bh < 0 || bottom + bh >= h)
            return false;
        int limit =
                Math.min(
                        150,
                        BeamInkThreshold.at(
                                gray, w, h, (left + right) / 2, top, bottom, Math.max(8, bh)));
        int columns = 0;
        for (int x = left; x <= right; x++) {
            int ink = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * w + x] & 255) < limit) ink++;
            if (ink >= bh * .5f) columns++;
        }
        if (columns < bw * .8f) return false;
        for (int edge : new int[] {left, right}) {
            boolean cap = false;
            int radius = Math.max(2, Math.round(bh * .15f));
            for (int x = Math.max(0, edge - radius); x <= Math.min(w - 1, edge + radius); x++) {
                int above = 0, below = 0, n = 0;
                for (int dy = Math.max(2, Math.round(bh * .18f));
                        dy <= Math.max(3, Math.round(bh * .48f));
                        dy++) {
                    n++;
                    if ((gray[(top - dy) * w + x] & 255) < limit) above++;
                    if ((gray[(bottom + dy) * w + x] & 255) < limit) below++;
                }
                if (above >= n * .7f && below >= n * .7f) {
                    cap = true;
                    break;
                }
            }
            if (!cap) return false;
        }
        return true;
    }
}
