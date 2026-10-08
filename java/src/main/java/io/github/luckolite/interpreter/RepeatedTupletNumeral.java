// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.function.Predicate;

/** Compare a joined numeral with a clearly read printed 3 on the same page. */
final class RepeatedTupletNumeral {
    private RepeatedTupletNumeral() {}

    static int[] find(
            byte[] gray,
            int w,
            int h,
            int[] ref,
            float x1,
            float x3,
            float y1,
            float y3,
            float gap,
            Predicate<int[]> owner) {
        if (gray == null
                || w < 1
                || h < 1
                || (long) w * h != gray.length
                || ref == null
                || ref.length != 4
                || owner == null
                || !Float.isFinite(gap)
                || gap < 4
                || !Float.isFinite(x1)
                || !Float.isFinite(x3)
                || !Float.isFinite(y1)
                || !Float.isFinite(y3)
                || x1 >= x3
                || y1 > y3
                || ref[0] < 0
                || ref[1] < 0
                || ref[2] >= w
                || ref[3] >= h
                || ref[0] > ref[2]
                || ref[1] > ref[3]) return null;
        int rw = ref[2] - ref[0] + 1, rh = ref[3] - ref[1] + 1;
        if (rw < 4 || rh < gap * .7f || rh > gap * 1.8f || rw > gap || rw < rh * .3f) return null;
        int[] ink = new int[rw * rh], paper = new int[rw * rh];
        int ni = 0, np = 0;
        for (int yy = 0; yy < rh; yy++)
            for (int xx = 0; xx < rw; xx++) {
                int v = gray[(ref[1] + yy) * w + ref[0] + xx] & 255;
                if (v < 120) ink[ni++] = yy * rw + xx;
                else if (v > 225) paper[np++] = yy * rw + xx;
            }
        if (ni < rw * rh * .12f || np < rw * rh * .25f) return null;
        int cx = Math.round((x1 + x3) * .5f),
                left = Math.max(0, Math.round(cx - gap * 1.2f)),
                right = Math.min(w - rw, Math.round(cx + gap * 1.2f));
        int top = Math.max(0, Math.round(y1 - gap * 7)),
                bottom = Math.min(h - rh, Math.round(y3 + gap * 7));
        for (int y = top; y <= bottom; y++) {
            if (y + rh >= y1 - gap && y <= y3 + gap) continue;
            for (int x = left; x <= right; x++) {
                int hit = 0;
                for (int i = 0; i < ni; i++) {
                    int p = ink[i];
                    if (nearInk(gray, w, h, x + p % rw, y + p / rw)) hit++;
                    if (hit + ni - i - 1 < ni * .93f) break;
                }
                if (hit < ni * .93f) continue;
                int[] box = {x, y, x + rw - 1, y + rh - 1};
                if (!owner.test(box)) continue;
                boolean[] covered = new boolean[rh];
                int hidden = 0;
                for (int yy = 0; yy < rh; yy++) {
                    for (int angle = -10; angle <= 10 && !covered[yy]; angle++) {
                        float slope = angle * .05f;
                        int valid = 0, total = 0;
                        for (int dx = -Math.round(gap * 1.5f); dx <= rw + gap * 1.5f; dx++) {
                            if (dx >= -1 && dx <= rw) continue;
                            int xx = x + dx, cy = Math.round(y + yy + slope * (dx - rw * .5f));
                            if (xx < 0 || xx >= w || cy < 0 || cy >= h) continue;
                            total++;
                            if ((gray[cy * w + xx] & 255) < 190
                                    || nearThinRule(gray, w, h, xx, cy, gap)) valid++;
                        }
                        covered[yy] = total >= gap * 2 && valid >= total * .9f;
                    }
                    if (covered[yy]) hidden++;
                }
                if (hidden > rh * .45f) continue;
                int visibleInk = 0, visiblePaper = 0, clearPaper = 0;
                for (int i = 0; i < ni; i++) if (!covered[ink[i] / rw]) visibleInk++;
                for (int i = 0; i < np; i++)
                    if (!covered[paper[i] / rw]) {
                        visiblePaper++;
                        if ((gray[(y + paper[i] / rw) * w + x + paper[i] % rw] & 255) > 165)
                            clearPaper++;
                    }
                if (visibleInk < ni * .5f
                        || visiblePaper < np * .5f
                        || clearPaper < visiblePaper * .90f) continue;
                if (!visibleOpening(gray, w, x, y, rw, rh, covered, .15f, .38f)
                        || !visibleOpening(gray, w, x, y, rw, rh, covered, .58f, .82f)) continue;
                if (!TupletNumeralInk.hasGlyphContrast(
                        gray, w, h, x, y, x + rw - 1, y + rh - 1, gap)) continue;
                if (TupletNumeralNeighbors.joinedText(gray, w, h, x, y, x + rw - 1, y + rh - 1))
                    continue;
                return box;
            }
        }
        return null;
    }

    private static boolean visibleOpening(
            byte[] gray,
            int w,
            int x,
            int y,
            int rw,
            int rh,
            boolean[] covered,
            float start,
            float end) {
        int rows = 0, open = 0;
        for (int yy = Math.round(rh * start); yy <= Math.min(rh - 1, Math.round(rh * end)); yy++) {
            if (covered[yy]) continue;
            rows++;
            int min = rw, max = -1;
            for (int xx = 0; xx < rw; xx++)
                if ((gray[(y + yy) * w + x + xx] & 255) < 165) {
                    min = Math.min(min, xx);
                    max = xx;
                }
            if (min >= rw * .25f && max >= rw * .5f) open++;
        }
        return rows < 2 || open >= 1;
    }

    private static boolean nearThinRule(byte[] gray, int w, int h, int x, int y, float gap) {
        int margin = Math.max(1, Math.round(gap * .12f)),
                limit = Math.max(2, Math.round(gap * .25f));
        for (int yy = Math.max(0, y - margin); yy <= Math.min(h - 1, y + margin); yy++) {
            if ((gray[yy * w + x] & 255) >= 190) continue;
            int top = yy, bottom = yy;
            while (top > 0 && yy - top <= limit && (gray[(top - 1) * w + x] & 255) < 190) top--;
            while (bottom < h - 1
                    && bottom - yy <= limit
                    && (gray[(bottom + 1) * w + x] & 255) < 190) bottom++;
            if (bottom - top + 1 <= limit) return true;
        }
        return false;
    }

    private static boolean nearInk(byte[] gray, int w, int h, int x, int y) {
        for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++)
            for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++)
                if ((gray[yy * w + xx] & 255) < 165) return true;
        return false;
    }
}
