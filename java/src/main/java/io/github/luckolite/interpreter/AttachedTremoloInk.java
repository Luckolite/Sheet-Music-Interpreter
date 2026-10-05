// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Confirms three short, equally spaced diagonal strokes across an owning stem. */
final class AttachedTremoloInk {
    private AttachedTremoloInk() {}

    private static final class StemDirections {
        private static final int[] VALUES = {1, -1};
    }

    private static final class StrokeThresholds {
        private static final int[] VALUES = {150, 185, 210};
    }

    private static final class EdgeSigns {
        private static final int[] VALUES = {-1, 1};
    }

    record Mark(int left, int top, int right, int bottom, int beams) {}

    static Mark fadedStem(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, float gap) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || gap < 5
                || !Float.isFinite(gap)) return null;
        int margin = Math.max(2, Math.round(gap * .25f)), cy = (top + bottom) / 2;
        for (int direction : StemDirections.VALUES) {
            int edge = direction > 0 ? left : right;
            for (int x = Math.max(1, edge - margin); x <= Math.min(w - 2, edge + margin); x++) {
                int ink = 0, last = cy, blank = 0;
                for (int distance = 0; distance <= gap * 7; distance++) {
                    int y = cy + direction * distance;
                    if (y < 1 || y >= h - 1) break;
                    boolean dark = (gray[y * w + x] & 255) < 220;
                    if (dark) {
                        ink++;
                        last = y;
                        blank = 0;
                    } else if (++blank > Math.max(2, Math.round(gap * .6f))) break;
                }
                int length = Math.abs(last - cy);
                if (length < gap * 3 || ink < length * .7f) continue;
                Mark mark = find(gray, w, h, x, direction > 0 ? bottom : top, last, direction, gap);
                if (mark != null) return mark;
            }
        }
        return null;
    }

    static Mark find(
            byte[] gray,
            int w,
            int h,
            int stemX,
            int headEdge,
            int stemEnd,
            int direction,
            float gap) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || gap < 5
                || !Float.isFinite(gap)
                || Math.abs(direction) != 1) return null;
        int side = Math.max(3, Math.round(gap * .45f)), far = Math.round(gap * 1.5f);
        if (stemX - far < 0 || stemX + far >= w) return null;
        int a = headEdge + direction * Math.round(gap * .7f),
                b = stemEnd - direction * Math.round(gap * .1f);
        int first = Math.max(1, Math.min(a, b)), last = Math.min(h - 2, Math.max(a, b));
        if ((stemEnd - headEdge) * direction < gap * 2.5f) return null;
        for (int threshold : StrokeThresholds.VALUES) {
            List<float[]> strokes = new ArrayList<>();
            for (int y = first; y <= last; y++) {
                float[] left =
                        run(gray, w, h, stemX - side, y + Math.round(side * .3f), gap, threshold);
                float[] right =
                        run(gray, w, h, stemX + side, y - Math.round(side * .3f), gap, threshold);
                if (left == null
                        || right == null
                        || left[0] - right[0] < Math.max(1, Math.floor(gap * .1f))
                        || left[0] - right[0] > gap * .7f) continue;
                float center = (left[0] + right[0]) * .5f;
                if (!strokes.isEmpty() && center - strokes.get(strokes.size() - 1)[0] < gap * .3f)
                    continue;
                boolean bounded = true;
                for (int sign : EdgeSigns.VALUES) {
                    int x = stemX + sign * far, yy = Math.round(center - sign * far * .3f);
                    int consecutive = 0;
                    for (int row = Math.max(0, Math.round(yy - gap * .45f));
                            row <= Math.min(h - 1, Math.round(yy + gap * .45f));
                            row++) {
                        consecutive = ink(gray, w, h, x, row, gap, threshold) ? consecutive + 1 : 0;
                        if (consecutive >= Math.max(4, Math.round(gap * .3f))) bounded = false;
                    }
                }
                if (bounded)
                    strokes.add(
                            new float[] {
                                center, Math.min(left[1], right[1]), Math.max(left[2], right[2])
                            });
            }
            for (int i = 0; i + 2 < strokes.size(); i++) {
                float[] one = strokes.get(i), two = strokes.get(i + 1), three = strokes.get(i + 2);
                float d1 = two[0] - one[0], d2 = three[0] - two[0];
                if (d1 < gap * .5f
                        || d1 > gap * 1.15f
                        || d2 < gap * .5f
                        || d2 > gap * 1.15f
                        || Math.abs(d1 - d2) > gap * .28f) continue;
                return new Mark(
                        stemX - Math.round(gap),
                        Math.round(one[1] - gap * .3f),
                        stemX + Math.round(gap),
                        Math.round(three[2] + gap * .3f),
                        3);
            }
        }
        return null;
    }

    private static float[] run(byte[] gray, int w, int h, int x, int y, float gap, int threshold) {
        if (x < 0 || x >= w || y < 1 || y >= h - 1 || !ink(gray, w, h, x, y, gap, threshold))
            return null;
        int top = y, bottom = y, max = Math.round(gap * .8f);
        while (top > 0 && y - top <= max && ink(gray, w, h, x, top - 1, gap, threshold)) top--;
        while (bottom < h - 1
                && bottom - y <= max
                && ink(gray, w, h, x, bottom + 1, gap, threshold)) bottom++;
        int count = bottom - top + 1;
        if (count < Math.max(3, Math.round(gap * .18f)) || count > gap * .8f) return null;
        int flank = Math.max(2, Math.round(gap * .2f));
        if (top < flank || bottom + flank >= h) return null;
        int darkest = 255;
        for (int yy = top; yy <= bottom; yy++) darkest = Math.min(darkest, gray[yy * w + x] & 255);
        boolean above = false, below = false;
        for (int delta = 2; delta <= Math.max(flank, Math.round(gap * .5f)); delta++) {
            if (top - delta >= 0 && (gray[(top - delta) * w + x] & 255) >= darkest + 20)
                above = true;
            if (bottom + delta < h && (gray[(bottom + delta) * w + x] & 255) >= darkest + 20)
                below = true;
        }
        if (!above || !below) return null;
        return new float[] {(top + bottom) * .5f, top, bottom};
    }

    private static boolean ink(byte[] gray, int w, int h, int x, int y, float gap, int threshold) {
        if ((gray[y * w + x] & 255) >= threshold) return false;
        int reach = Math.round(gap * 2.2f), flank = Math.max(2, Math.round(gap * .25f));
        if (x - reach < 0 || x + reach >= w || y < flank || y + flank >= h) return true;
        for (int xx : new int[] {x - reach, x + reach}) {
            if ((gray[y * w + xx] & 255) >= 210
                    || (gray[(y - flank) * w + xx] & 255) < 210
                    || (gray[(y + flank) * w + xx] & 255) < 210) return true;
        }
        return false;
    }
}
