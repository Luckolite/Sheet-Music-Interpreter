// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.Pattern;

/** Volta evidence combines a connected straight roof/downward hook with its printed selectors. */
final class PrintedEndingBrackets {
    record Mark(
            int start,
            int end,
            List<Integer> passes,
            String text,
            float x,
            int staffIndex,
            int staffCount) {}

    private static final Pattern SELECTOR =
            Pattern.compile("\\d{1,3}\\.?\\s*(?:,\\s*\\d{1,3}\\.?\\s*)*");
    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private PrintedEndingBrackets() {}

    static List<Mark> detect(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<PlayingTechniqueDetector.Word> words) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || staffs == null
                || measures == null
                || words == null) return List.of();
        var result = new ArrayList<Mark>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap) || gap < 5 || gap > Math.min(width, height) / 8f) continue;
            int top = Math.max(0, Math.round(staff.top() - gap * 8));
            int bottom = Math.min(height - 1, Math.round(staff.top() - gap * .3f));
            if (bottom <= top) continue;
            int rows = bottom - top + 1;
            boolean[] seen = new boolean[width * rows];
            int[] queue = new int[seen.length];
            for (int seed = 0; seed < seen.length; seed++) {
                if (seen[seed] || (gray[top * width + seed] & 255) >= 170) continue;
                int take = 0, size = 1, left = width, right = -1, first = rows, last = -1;
                queue[0] = seed;
                seen[seed] = true;
                while (take < size) {
                    int at = queue[take++], x = at % width, y = at / width;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    first = Math.min(first, y);
                    last = Math.max(last, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || xx >= width || yy < 0 || yy >= rows) continue;
                            int next = yy * width + xx;
                            if (!seen[next] && (gray[(top + yy) * width + xx] & 255) < 170) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                int w = right - left + 1, h = last - first + 1;
                if (w <= gap * 6 || h <= gap * .5f || h >= gap * 7 || first == 0) continue;
                int[] roof = new int[w], floor = new int[w], count = new int[w];
                Arrays.fill(roof, h);
                for (int i = 0; i < size; i++) {
                    int x = queue[i] % width - left, y = queue[i] / width - first;
                    roof[x] = Math.min(roof[x], y);
                    floor[x] = Math.max(floor[x], y);
                    count[x]++;
                }
                int margin = Math.round(gap), n = 0;
                double sx = 0, sy = 0, sxx = 0, sxy = 0;
                for (int x = margin; x < w - margin; x++)
                    if (roof[x] < h) {
                        n++;
                        sx += x;
                        sy += roof[x];
                        sxx += (double) x * x;
                        sxy += (double) x * roof[x];
                    }
                if (n < (w - 2 * margin) * .97 || n < 3) continue;
                double denominator = n * sxx - sx * sx;
                if (denominator <= 0) continue;
                double slope = (n * sxy - sx * sy) / denominator, intercept = (sy - slope * sx) / n;
                if (Math.abs(slope) > .12) continue;
                int straight = 0, thin = 0;
                for (int x = margin; x < w - margin; x++)
                    if (roof[x] < h) {
                        if (Math.abs(roof[x] - (slope * x + intercept)) <= gap * .15) straight++;
                        if (floor[x] - roof[x] + 1 <= Math.max(2, gap * .35f)) thin++;
                    }
                if (straight < n * .98 || thin < n * .95) continue;
                int hook = 0;
                for (int x = 0; x < Math.min(w, Math.round(gap * .4f)); x++) {
                    int depth = floor[x] - (int) Math.round(slope * x + intercept);
                    if (depth >= gap && count[x] >= depth * .85) hook = Math.max(hook, depth);
                }
                if (hook < gap) continue;
                PlayingTechniqueDetector.Word matched = null;
                List<Integer> passes = null;
                boolean ambiguous = false;
                for (var word : words) {
                    var parsed = passes(word.text());
                    if (parsed.isEmpty()) continue;
                    float l = word.left() * width,
                            r = word.right() * width,
                            t = word.top() * height,
                            b = word.bottom() * height;
                    double roofY = top + first + slope * (l - left) + intercept;
                    if (l < left - gap * .3f
                            || l > left + 5 * gap
                            || r >= right
                            || t < roofY - gap * .3f
                            || b > roofY + Math.min(4 * gap, hook)) continue;
                    if (matched != null) {
                        ambiguous = true;
                        break;
                    }
                    matched = word;
                    passes = parsed;
                }
                if (matched == null || ambiguous) continue;
                int start =
                        PrintedRepeatGlyphs.boundary(measures, staff, left, false, width, height);
                int end = PrintedRepeatGlyphs.boundary(measures, staff, right, true, width, height);
                if (start < 0 || end <= start) continue;
                var mark =
                        new Mark(
                                start,
                                end,
                                passes,
                                matched.text(),
                                left / (float) width,
                                staff.index(),
                                staff.count());
                if (!result.contains(mark)) result.add(mark);
            }
        }
        result.sort(Comparator.comparingInt(Mark::start).thenComparingInt(Mark::end));
        return List.copyOf(result);
    }

    static List<Integer> passes(String text) {
        if (text == null || !SELECTOR.matcher(text.trim()).matches()) return List.of();
        var values = new ArrayList<Integer>();
        var matcher = INTEGER.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group());
            if (value < 1 || value > 128 || values.contains(value)) return List.of();
            values.add(value);
        }
        return List.copyOf(values);
    }
}
