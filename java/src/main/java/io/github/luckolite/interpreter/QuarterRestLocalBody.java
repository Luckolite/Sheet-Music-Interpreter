// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Complete isolated rest bodies with a nearby five-rule phase proved on both flanks. */
final class QuarterRestLocalBody {
    record Body(int left, int right, int top, int bottom, SixteenthRestDetector.Staff staff) {}

    private QuarterRestLocalBody() {}

    static List<Body> find(
            byte[] paper,
            byte[] original,
            int width,
            int height,
            List<SixteenthRestDetector.Staff> staffs) {
        if (paper == null
                || original == null
                || width <= 0
                || height <= 0
                || paper.length != (long) width * height
                || original.length != paper.length) return List.of();
        List<Body> result = new ArrayList<>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap) || gap < 4) continue;
            int first = Math.max(0, (int) Math.floor(staff.top() - gap * .8f));
            int last = Math.min(height, (int) Math.ceil(staff.bottom() + gap * .6f));
            if (last <= first) continue;
            int size = width * (last - first);
            boolean[] visited = new boolean[size];
            int[] queue = new int[size];
            for (int at = 0; at < size; at++) {
                if (visited[at] || (paper[first * width + at] & 255) >= 170) continue;
                int count = 1, cursor = 0;
                queue[0] = at;
                visited[at] = true;
                int left = width, right = -1, top = height, bottom = -1;
                while (cursor < count) {
                    int pixel = queue[cursor++], x = pixel % width, y = pixel / width + first;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || xx >= width || yy < first || yy >= last) continue;
                            int next = (yy - first) * width + xx;
                            if (!visited[next] && (paper[yy * width + xx] & 255) < 170) {
                                visited[next] = true;
                                queue[count++] = next;
                        }
                        }
                }
                // A connected stem or rule must never be cropped into a putative rest.
                if (top == first
                        || bottom == last - 1
                        || left == 0
                        || right == width - 1
                        || right - left + 1 < gap * .7f
                        || right - left + 1 > gap * 1.6f
                        || bottom - top + 1 < gap * 2.1f
                        || bottom - top + 1 > gap * 3.6f) continue;
                float[] frame = frame(original, width, height, left, right, staff);
                if (frame == null) continue;
                var local =
                        new SixteenthRestDetector.Staff(
                                frame[0] - 4 * frame[1],
                                frame[0],
                                frame[1],
                                staff.index(),
                                staff.count());
                result.add(new Body(left, right, top, bottom, local));
            }
        }
        return List.copyOf(result);
    }

    static float[] frame(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            SixteenthRestDetector.Staff staff) {
        float gap = staff.gap();
        int a = Math.max(0, Math.round(left - gap * 3)),
                b = Math.max(0, Math.round(left - gap * .75f));
        int c = Math.min(width, Math.round(right + gap * .75f)),
                d = Math.min(width, Math.round(right + gap * 3));
        if (b - a < gap || d - c < gap) return null;
        int flank = Math.max(2, Math.round(gap * .32f));
        int first = Math.max(flank, (int) Math.floor(staff.top() - gap * .8f));
        int last = Math.min(height - flank, (int) Math.ceil(staff.bottom() + gap * .8f));
        if (last <= first) return null;
        var leftRules = rules(gray, width, a, b, first, last, flank, staff);
        var rightRules = rules(gray, width, c, d, first, last, flank, staff);
        if (leftRules == null || rightRules == null) return null;
        float fraction = ((left + right) * .5f - (a + b) * .5f) / ((c + d - a - b) * .5f);
        float[] rows = new float[5];
        float mean = 0;
        for (int i = 0; i < 5; i++) {
            if (Math.abs(leftRules[i] - rightRules[i]) > gap * .2f) return null;
            rows[i] = leftRules[i] + (rightRules[i] - leftRules[i]) * fraction;
            mean += rows[i] / 5;
        }
        float fittedGap = 0;
        for (int i = 0; i < 5; i++) fittedGap += (i - 2) * (rows[i] - mean) / 10;
        if (fittedGap < gap * .88f || fittedGap > gap * 1.12f) return null;
        for (int i = 0; i < 5; i++)
            if (Math.abs(rows[i] - (mean + (i - 2) * fittedGap)) > gap * .12f + .5f) return null;
        return new float[] {mean + 2 * fittedGap, fittedGap};
    }

    private static int[] rules(
            byte[] gray,
            int width,
            int left,
            int right,
            int first,
            int last,
            int flank,
            SixteenthRestDetector.Staff staff) {
        int[] strength = new int[last - first];
        for (int y = first; y < last; y++)
            for (int x = left; x < right; x++) {
                int ink = gray[y * width + x] & 255;
                if (ink <= 225
                        && (gray[(y - flank) * width + x] & 255) >= ink + 12
                        && (gray[(y + flank) * width + x] & 255) >= ink + 12) strength[y - first]++;
            }
        int[] found = null;
        for (var lines :
                RawStaffLineDetector.detectFromStrength(
                        strength, Math.max(4, Math.round((right - left) * .5f)), last - first)) {
            if (lines.gap() < staff.gap() * .88f
                    || lines.gap() > staff.gap() * 1.12f
                    || Math.abs(lines.bottom() + first - staff.bottom()) > staff.gap() * .8f)
                continue;
            int[] rows = lines.rows().clone();
            for (int i = 0; i < rows.length; i++) rows[i] += first;
            if (found != null && !java.util.Arrays.equals(found, rows)) return null;
            found = rows;
        }
        return found;
    }
}
