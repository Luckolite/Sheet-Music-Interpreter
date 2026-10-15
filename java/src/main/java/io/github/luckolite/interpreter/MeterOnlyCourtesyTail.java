// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A terminal stacked meter owns no played ink beyond its preceding full staff barline. */
final class MeterOnlyCourtesyTail {
    static boolean matches(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            int headTop,
            int headBottom) {
        if (gray == null
                || labels == null
                || gray.length != (long) width * height
                || labels.length != gray.length
                || gap < 6
                || !Float.isFinite(gap)
                || right - left < gap
                || right - left > gap * 6) return false;
        if (top < 0 || bottom >= height || left < 0 || right >= width) return false;
        int bands = 0;
        boolean previous = false;
        for (int x = Math.max(0, Math.round(left - gap * 1.3f));
                x <= Math.min(width - 1, Math.round(left + gap * .3f));
                x++) {
            int dark = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * width + x] & 255) < 165) dark++;
            boolean bar = dark >= (bottom - top + 1) * .94f;
            if (bar && !previous) bands++;
            previous = bar;
        }
        if (bands != 1) return false;
        int unowned = 0;
        for (int y = Math.max(0, headTop); y <= Math.min(height - 1, headBottom); y++)
            for (int x = left; x < width; x++) {
                if (labels[y * width + x] == 2) return false;
                if (x < left + gap * .3f || (gray[y * width + x] & 255) >= 180) continue;
                boolean owned = false;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1) && !owned; yy++)
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++)
                        if (labels[yy * width + xx] == 3
                                || labels[yy * width + xx] == 4
                                || labels[yy * width + xx] == 5) {
                            owned = true;
                            break;
                        }
                if (!owned && ++unowned > gap * gap * .15f) return false;
            }
        int upper = Math.max(0, Math.round(top + gap * 1.65f)),
                lower = Math.min(height - 1, Math.round(bottom + gap * .2f));
        int w = right - left + 1, h = lower - upper + 1;
        if (w < 3 || h < 3) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        var holes = new ArrayList<float[]>();
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || (gray[(upper + seed / w) * width + left + seed % w] & 255) < 200)
                continue;
            int read = 0, write = 0, minX = w, maxX = -1, minY = h, maxY = -1, area = 0;
            float sx = 0, sy = 0;
            queue[write++] = seed;
            seen[seed] = true;
            while (read < write) {
                int point = queue[read++], px = point % w, py = point / w;
                area++;
                sx += px;
                sy += py;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                for (int step : new int[] {-1, 1, -w, w}) {
                    int next = point + step;
                    if (next < 0
                            || next >= w * h
                            || step == -1 && px == 0
                            || step == 1 && px == w - 1
                            || seen[next]
                            || (gray[(upper + next / w) * width + left + next % w] & 255) < 200)
                        continue;
                    seen[next] = true;
                    queue[write++] = next;
                }
            }
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || area < gap * gap * .055f
                    || area > gap * gap * .5f
                    || maxX - minX + 1 > gap
                    || maxY - minY + 1 > gap * .8f) continue;
            holes.add(
                    new float[] {
                        left + sx / area,
                        upper + sy / area,
                        left + minX,
                        upper + minY,
                        left + maxX,
                        upper + maxY
                    });
        }
        float cx;
        if (holes.size() == 1) {
            var a = holes.get(0);
            if (!OmrMeasurePostProcessor.completeFourCounter(
                    gray, width, height, (int) a[2], (int) a[3], (int) a[4], (int) a[5], top, gap))
                return false;
            cx = a[0];
        } else {
            if (holes.size() != 2) return false;
            var a = holes.get(0);
            var b = holes.get(1);
            float dy = Math.abs(a[1] - b[1]);
            cx = (a[0] + b[0]) * .5f;
            if (Math.abs(a[0] - b[0]) > gap * .3f || dy < gap * .55f || dy > gap * 1.3f)
                return false;
        }
        int first = 0, second = 0;
        for (int y = top; y < top + gap * 1.65f; y++) {
            float rail = (y - top) / gap;
            if (Math.abs(rail - Math.round(rail)) < .14f) continue;
            for (int x = Math.max(left, Math.round(cx - gap * 1.4f));
                    x <= Math.min(right, Math.round(cx + gap * 1.4f));
                    x++)
                if ((gray[y * width + x] & 255) < 165 && labels[y * width + x] == 5) {
                    if (y < top + gap * .85f) first++;
                    else second++;
                }
        }
        return first >= gap * gap * .2f
                && second >= gap * gap * .2f
                && first + second >= gap * gap * .7f;
    }
}
