// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independently supported thin stroke through a stacked-meter crop. */
final class MeterCrossingStroke {
    record Line(float y, float slope) {}

    static boolean[] mask(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float firstLine,
            float gap) {
        int cw = right - left, ch = bottom - top;
        if (gray == null
                || gray.length != (long) width * height
                || gap < 8
                || !Float.isFinite(gap)
                || !Float.isFinite(firstLine)
                || left < 0
                || right > width
                || top < 0
                || bottom > height
                || cw < gap * 1.35f
                || cw > gap * 4
                || ch < gap * 3
                || firstLine < top
                || firstLine + 4 * gap > bottom + gap * .5f) return null;
        int reach = Math.round(gap * 1.15f),
                separation = Math.max(2, Math.round(gap * .2f)),
                radius = Math.max(1, Math.round(gap * .1f));
        if (left - reach < 0
                || right + reach >= width
                || top - reach < 0
                || bottom + reach >= height) return null;
        float cx = (left + right) * .5f;
        List<Line> candidates = new ArrayList<>();
        boolean independentFlanks = false;
        for (float y = firstLine; y < firstLine + 2 * gap; y += .25f)
            for (int si = -40; si <= 40; si++) {
                float slope = si * .01f;
                if (Math.abs(slope) * (cw + 2 * reach) < gap * .7f) continue;
                int[] hits = {0, 0}, offSides = {0, 0};
                int off = 0, full = 0;
                boolean broad = false;
                for (int x = left - reach; x < right + reach; x++) {
                    int cy = Math.round(y + slope * (x - cx));
                    boolean ink = false;
                    if (cy - Math.round(gap * .35f) < 0 || cy + Math.round(gap * .35f) >= height)
                        return null;
                    for (int dy = -radius; dy <= radius; dy++)
                        if ((gray[(cy + dy) * width + x] & 255) < 150) ink = true;
                    if (ink) full++;
                    int side = x < left - separation ? 0 : x >= right + separation ? 1 : -1;
                    if (side >= 0) {
                        if (ink) hits[side]++;
                        float staffDistance =
                                Math.abs(
                                        cy
                                                - (firstLine
                                                        + Math.round((cy - firstLine) / gap)
                                                                * gap));
                        if (ink && staffDistance > gap * .2f) {
                            off++;
                            offSides[side]++;
                            int extent = 0;
                            for (int dy = -Math.round(gap * .35f);
                                    dy <= Math.round(gap * .35f);
                                    dy++) if ((gray[(cy + dy) * width + x] & 255) < 150) extent++;
                            if (extent > gap * .55f) broad = true;
                        }
                    }
                }
                int flank = reach - separation;
                if (!broad
                        && hits[0] >= Math.ceil(flank * .9f)
                        && hits[1] >= Math.ceil(flank * .9f)
                        && off >= gap * .4f
                        && full >= Math.ceil((cw + 2 * reach) * .94f)) {
                    candidates.add(new Line(y, slope));
                    if (offSides[0] >= Math.ceil(gap * .12f)
                            && offSides[1] >= Math.ceil(gap * .12f)) independentFlanks = true;
                }
            }
        if (candidates.isEmpty() || !independentFlanks) return null;
        float minY = Float.POSITIVE_INFINITY, maxY = -minY, minS = minY, maxS = -minY;
        for (var l : candidates) {
            minY = Math.min(minY, l.y);
            maxY = Math.max(maxY, l.y);
            minS = Math.min(minS, l.slope);
            maxS = Math.max(maxS, l.slope);
        }
        if (maxY - minY > gap * .4f || maxS - minS > .15f) return null;
        var ys = new ArrayList<Float>();
        var slopes = new ArrayList<Float>();
        for (var l : candidates) {
            ys.add(l.y);
            slopes.add(l.slope);
        }
        Collections.sort(ys);
        Collections.sort(slopes);
        float y = ys.get(ys.size() / 2), slope = slopes.get(slopes.size() / 2);
        boolean[] mask = new boolean[cw * ch];
        for (int yy = 0; yy < ch; yy++)
            for (int x = 0; x < cw; x++)
                mask[yy * cw + x] = Math.abs(top + yy - (y + slope * (left + x - cx))) <= radius;
        return mask;
    }
}
