// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete tapered bow outside proved staff rules; caller still owns pitch and voice. */
final class DetachedCompactTieRidge {
    private DetachedCompactTieRidge() {}

    record Head(int left, int right, float x, float y) {}

    static boolean proved(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Head first,
            Head last,
            int staffStep,
            StaffPitchTrack track) {
        if (labels == null
                || gray == null
                || width < 1
                || height < 1
                || (long) width * height > 200000
                || labels.length != (long) width * height
                || gray.length != labels.length
                || first == null
                || last == null
                || track == null
                || !track.verifiedAt(first.x)
                || !track.verifiedAt(last.x)
                || !LocalTieStaffAlignment.same(track, first.x, first.y, last.x, last.y, staffStep))
            return false;
        float[] a = track.at(first.x), b = track.at(last.x);
        float gap = (a[1] + b[1]) * .5f;
        if (!Float.isFinite(gap)
                || gap < 8
                || gap > 64
                || first.left < 0
                || first.right >= width
                || first.left > first.x
                || first.right < first.x
                || last.left < 0
                || last.right >= width
                || last.left > last.x
                || last.right < last.x
                || last.left <= first.right
                || last.x - first.x < gap * 1.8f
                || last.x - first.x > gap * 4.5f) return false;
        var rules = new PrintedCurveRuleInk(labels, gray, width, height, 205, track);
        byte[] residual = new byte[gray.length];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) residual[y * width + x] = (byte) rules.sample(x, y);
        if (!rules.corroboratedCrossing()) return false;
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        int qualified = 0;
        for (int seed = 0; seed < residual.length; seed++) {
            if (seen[seed] || (residual[seed] & 255) >= 165) continue;
            int count = 1, take = 0, left = width, right = -1, top = height, bottom = -1;
            boolean head = false;
            queue[0] = seed;
            seen[seed] = true;
            while (take < count) {
                int at = queue[take++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                head |= labels[at] == OmrMeasurePostProcessor.NOTEHEAD;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue;
                        int next = yy * width + xx;
                        if (seen[next] || (residual[next] & 255) >= 165) continue;
                        seen[next] = true;
                        queue[count++] = next;
                    }
            }
            // Every cell of the complete residual component participates in the proof.
            // Never crop a staff, letter, stem, beam or larger phrase into a convenient cap.
            if (head
                    || left == 0
                    || right == width - 1
                    || top == 0
                    || bottom == height - 1
                    || left < first.x - gap * .35f
                    || right > last.x + gap * .35f
                    || left > first.right + gap * .65f
                    || right < last.left - gap * .65f
                    || right - left < gap
                    || right - left > gap * 3.5f
                    || bottom - top < 2
                    || bottom - top > gap * .85f) continue;
            int span = right - left + 1;
            int[] min = new int[span], max = new int[span], cells = new int[span];
            Arrays.fill(min, height);
            Arrays.fill(max, -1);
            for (int i = 0; i < count; i++) {
                int at = queue[i], x = at % width - left, y = at / width;
                min[x] = Math.min(min[x], y);
                max[x] = Math.max(max[x], y);
                cells[x]++;
            }
            boolean complete = true;
            float[] center = new float[span], relative = new float[span];
            int thick = 0;
            for (int x = 0; x < span; x++) {
                if (cells[x] == 0
                        || max[x] - min[x] + 1 != cells[x]
                        || cells[x] > Math.max(3, gap * .45f)) {
                    complete = false;
                    break;
                }
                thick = Math.max(thick, cells[x]);
                center[x] = (min[x] + max[x]) * .5f;
                float[] f = track.at(left + x);
                if (!track.verifiedAt(left + x) || !Float.isFinite(f[0] + f[1]) || f[1] < 8) {
                    complete = false;
                    break;
                }
                float t = (left + x - first.x) / (last.x - first.x);
                float residualA = first.y - (a[0] - staffStep * a[1] * .5f);
                float residualB = last.y - (b[0] - staffStep * b[1] * .5f);
                float base = f[0] - staffStep * f[1] * .5f + residualA * (1 - t) + residualB * t;
                relative[x] = (center[x] - base) / f[1];
                if (!Float.isFinite(relative[x])) {
                    complete = false;
                    break;
                }
                if (!contrastedColumn(gray, width, height, left + x, min[x], max[x], f[1])) {
                    complete = false;
                    break;
                }
            }
            if (!complete || cells[0] > thick * .7f || cells[span - 1] > thick * .7f) continue;
            for (int side : new int[] {-1, 1}) {
                boolean outside = true;
                for (int x = 0; x < span; x++) {
                    float[] f = track.at(left + x);
                    float rule = side < 0 ? f[0] - 4 * f[1] : f[0];
                    if (side < 0 ? max[x] > rule - f[1] * .18f : min[x] < rule + f[1] * .18f)
                        outside = false;
                    if (side * relative[x] < .25f || side * relative[x] > 2.6f) outside = false;
                }
                if (!outside || !rounded(relative, side)) continue;
                float[] sampled = new float[50];
                for (int x = 0; x < 50; x++) sampled[x] = center[Math.round(x * (span - 1) / 49f)];
                if (TieArcBranchInk.outwardStems(
                        residual, width, height, left, right, sampled, side, gap, 165)) continue;
                if (++qualified > 1) return false;
            }
        }
        return qualified == 1;
    }

    /** Raw local paper must contrast with most of the stroke, not one dark speckle. */
    private static boolean contrastedColumn(
            byte[] gray, int width, int height, int x, int top, int bottom, float gap) {
        int[] paper = new int[18];
        int count = 0;
        for (float scale : new float[] {.2f, .3f, .4f}) {
            int distance = Math.max(2, Math.round(gap * scale));
            for (int dx = -1; dx <= 1; dx++)
                for (int y : new int[] {top - distance, bottom + distance}) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= width || y < 0 || y >= height) continue;
                    paper[count++] = gray[y * width + xx] & 255;
                }
        }
        if (count < 12) return false;
        Arrays.sort(paper, 0, count);
        int[] stroke = new int[bottom - top + 1];
        for (int y = top; y <= bottom; y++) stroke[y - top] = gray[y * width + x] & 255;
        Arrays.sort(stroke);
        return paper[(count - 1) * 2 / 3] - stroke[stroke.length / 2] >= 20;
    }

    private static boolean rounded(float[] p, int side) {
        int n = p.length, quarter = Math.max(3, n / 4), edge = Math.max(2, n / 6);
        float first = mean(p, 0, edge), last = mean(p, n - edge, n);
        float middle = mean(p, n * 2 / 5, n * 3 / 5);
        if (Math.abs(first - last) > .2f
                || side * (middle - first) < .15f
                || side * (middle - last) < .15f) return false;
        float lo = side * slope(p, 0, quarter), li = side * slope(p, quarter, n / 2);
        float ro = -side * slope(p, n - quarter, n), ri = -side * slope(p, n / 2, n - quarter);
        if (!Float.isFinite(lo + li + ro + ri)
                || lo <= .015f
                || ro <= .015f
                || li < -.015f
                || ri < -.015f
                || Math.abs(li) > lo * .65f + .006f
                || Math.abs(ri) > ro * .65f + .006f) return false;
        for (int i = 1; i < n; i++) if (Math.abs(p[i] - p[i - 1]) > .15f) return false;
        return true;
    }

    private static float mean(float[] p, int first, int end) {
        float total = 0;
        for (int i = first; i < end; i++) total += p[i];
        return total / (end - first);
    }

    private static float slope(float[] p, int first, int end) {
        double x = 0, y = 0, xx = 0, xy = 0;
        int n = end - first;
        for (int i = first; i < end; i++) {
            x += i;
            y += p[i];
            xx += (double) i * i;
            xy += i * p[i];
        }
        double divisor = n * xx - x * x;
        return divisor > 0 ? (float) ((n * xy - x * y) / divisor) : Float.NaN;
    }
}
