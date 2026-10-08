// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A complete narrow shaft transported by an independently verified staff. */
final class TransportedBeamShaft {
    private TransportedBeamShaft() {}

    static int[] detect(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int top,
            int bottom,
            float cx,
            float cy,
            float gap,
            StaffPitchTrack track) {
        if (track == null
                || !track.verified()
                || gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || left < 0
                || right >= w
                || top < 0
                || bottom >= h
                || right < left
                || bottom < top
                || !Float.isFinite(cx)
                || !Float.isFinite(cy)
                || !Float.isFinite(gap)
                || gap < 4
                || cx < left
                || cx > right
                || cy < top
                || cy > bottom
                || gap > Math.min(w, h) * .25f) return null;
        int span = Math.max(4, Math.round(gap));
        float slope = (track.at(cx + span)[0] - track.at(cx - span)[0]) / (2 * span);
        int metadata =
                PrintedStemMetadata.detect(
                        gray, w, h, left, right, top, bottom, cx, cy, gap, slope);
        if (metadata == 0) return null;
        int direction = metadata == 1 ? -1 : 1, edge = direction < 0 ? right : left;
        int margin = Math.max(2, Math.round(gap * .22f)),
                flank = Math.max(3, Math.round(gap * .4f));
        int start = Math.max(2, Math.round(gap * .25f)), limit = Math.round(gap * 5.5f);
        int[] best = null;
        int longest = 0;
        for (int origin = edge - margin; origin <= edge + margin; origin++) {
            int hits = 0, narrow = 0, total = 0, blank = 0, end = 0, endX = origin;
            int firstHits = 0, firstNarrow = 0, firstTotal = 0;
            for (int d = start; d <= limit; d++) {
                int row = Math.round(cy + direction * d),
                        x = Math.round(origin - slope * direction * d);
                if (row < 1 || row >= h - 1 || x - flank < 0 || x + flank >= w) break;
                int radius = Math.max(4, Math.round(gap * .65f)),
                        half = Math.max(2, Math.round(gap * .3f));
                int[] values = new int[(radius * 2 + 1) * (half * 2 + 1)];
                int n = 0;
                for (int yy = Math.max(0, row - half); yy <= Math.min(h - 1, row + half); yy++)
                    for (int xx = Math.max(0, x - radius); xx <= Math.min(w - 1, x + radius); xx++)
                        values[n++] = gray[yy * w + xx] & 255;
                Arrays.sort(values, 0, n);
                int paper = values[(n - 1) * 85 / 100], core = 255;
                for (int xx = x - 1; xx <= x + 1; xx++)
                    core = Math.min(core, gray[row * w + xx] & 255);
                total++;
                boolean first = d <= Math.round(gap * 2.25f);
                if (first) firstTotal++;
                if (paper - core >= 24) {
                    hits++;
                    if (first) firstHits++;
                    blank = 0;
                    end = d;
                    endX = x;
                    int l = gray[row * w + x - flank] & 255, r = gray[row * w + x + flank] & 255;
                    int contrast = Math.max(16, Math.round((paper - core) * .3f));
                    if (l - core >= contrast && r - core >= contrast) {
                        narrow++;
                        if (first) firstNarrow++;
                    }
                } else if (++blank > Math.max(1, Math.round(gap * .12f))) break;
            }
            // The complete first segment is narrow; its independently attached
            // beam end can be wider than the shaft it joins.
            if (firstTotal >= gap * 1.9f
                    && firstHits >= firstTotal * .92f
                    && firstNarrow >= firstTotal * .6f
                    && end >= gap * 2.3f
                    && end < limit - 2
                    && hits >= total * .9f
                    && end > longest) {
                longest = end;
                best = new int[] {endX, Math.round(cy + direction * end), direction};
            }
        }
        return best;
    }
}
