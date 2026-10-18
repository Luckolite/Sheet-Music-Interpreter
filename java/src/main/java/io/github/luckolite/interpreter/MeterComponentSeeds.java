// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Complete semantic components propose crops only; no numeral value or meter is inferred. */
final class MeterComponentSeeds {
    record Hint(int firstLine, float gap, float slope) {}

    private record Body(int left, int right, int top, int bottom) {}

    private MeterComponentSeeds() {}

    static List<MeterChangeDetector.Crop> crops(
            byte[] labels, byte[] gray, int w, int h, List<Hint> hints) {
        if (labels == null
                || gray == null
                || w <= 0
                || h <= 0
                || labels.length != (long) w * h
                || gray.length != labels.length
                || hints == null
                || hints.isEmpty()) return List.of();
        var result = new ArrayList<MeterChangeDetector.Crop>();
        boolean[] visited = new boolean[labels.length];
        int[] stack = new int[Math.min(256, labels.length)];
        for (int seed = 0; seed < labels.length; seed++) {
            if (visited[seed] || labels[seed] != OmrMeasurePostProcessor.SYMBOL) continue;
            int size = 1;
            stack[0] = seed;
            visited[seed] = true;
            int left = w, right = -1, top = h, bottom = -1;
            while (size > 0) {
                int at = stack[--size], x = at % w, y = at / w;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int next = yy * w + xx;
                        if (visited[next] || labels[next] != OmrMeasurePostProcessor.SYMBOL)
                            continue;
                        visited[next] = true;
                        if (size == stack.length)
                            stack =
                                    Arrays.copyOf(
                                            stack,
                                            (int) Math.min(labels.length, stack.length * 2L));
                        stack[size++] = next;
                    }
            }
            Body body = new Body(left, right, top, bottom);
            if (left < 1 || right >= w - 1 || top < 1 || bottom >= h - 1) continue;
            MeterChangeDetector.Crop agreed = null;
            boolean conflict = false;
            for (var hint : hints) {
                var crop = fromBody(labels, gray, w, h, body, hint);
                if (crop == null) continue;
                if (agreed != null
                        && (Math.abs(crop.firstLine() - agreed.firstLine()) > hint.gap() * .15f
                                || Math.abs(crop.gap() - agreed.gap()) > hint.gap() * .06f)) {
                    conflict = true;
                    break;
                }
                agreed = crop;
            }
            if (!conflict && agreed != null && !result.contains(agreed)) result.add(agreed);
        }
        return List.copyOf(result);
    }

    private static MeterChangeDetector.Crop fromBody(
            byte[] labels, byte[] gray, int w, int h, Body body, Hint hint) {
        float gap = hint.gap();
        if (!Float.isFinite(gap + hint.slope()) || gap < 6 || gap > Math.min(w, h) / 8f)
            return null;
        int span = body.right - body.left + 1, height = body.bottom - body.top + 1;
        // The SAME existing projection size bounds, now applied to a complete component.
        if (span < gap * .5f || span > gap * 3.2f || height < gap * 3.1f) return null;
        float center = (body.left + body.right) * .5f;
        int expected = hint.firstLine() + Math.round(hint.slope() * (center - w * .5f));
        if (Math.abs((body.top + body.bottom) * .5f - (expected + 2 * gap)) > gap * .75f)
            return null;
        var a =
                PrintedDirectionStaff.printedRules(
                        gray, w, h, body.left - gap, expected + 4 * gap, gap);
        var b =
                PrintedDirectionStaff.printedRules(
                        gray, w, h, body.right + gap, expected + 4 * gap, gap);
        // A broad witness remains mandatory; a missing broad side is NOT accepted alone.
        if (a == null && b == null) return null;
        if (a != null
                && b != null
                && (Math.abs((a[0] - 4 * a[1]) - (b[0] - 4 * b[1])) > gap * .15f
                        || Math.abs(a[1] - b[1]) > gap * .06f)) return null;
        float rawFirst =
                a == null
                        ? b[0] - 4 * b[1]
                        : b == null
                                ? a[0] - 4 * a[1]
                                : ((a[0] - 4 * a[1]) + (b[0] - 4 * b[1])) * .5f;
        int first = Math.round(rawFirst);
        float localGap = a == null ? b[1] : b == null ? a[1] : (a[1] + b[1]) * .5f;
        if (Math.abs(first - expected) > gap * .75f || Math.abs(localGap - gap) > gap * .12f)
            return null;
        if ((a == null || b == null) && !completeNearFlanks(gray, w, h, body, rawFirst, localGap))
            return null;
        int pad = Math.max(2, Math.round(localGap * .18f));
        int left = body.left - pad, right = body.right + pad + 1;
        int top = first - pad, bottom = Math.round(first + 4 * localGap) + pad + 1;
        if (left < 0
                || right > w
                || top < 0
                || bottom > h
                || body.top < top
                || body.bottom >= bottom) return null;
        var crop = new MeterChangeDetector.Crop(left, top, right, bottom, first, localGap);
        if (!stackedRawBody(labels, gray, w, body, crop)) return null;
        if (MeterHeaderInkGuard.owns(crop, labels, gray, w, h)) return null;
        return crop;
    }

    /** Two disjoint local strips corroborate the surviving broad physical-staff witness. */
    private static boolean completeNearFlanks(
            byte[] gray, int w, int h, Body body, float first, float gap) {
        int near = Math.max(1, (int) Math.ceil(gap * .25f));
        int reach = (int) Math.floor(gap);
        if (reach - near + 1 < 8)
            return false; // Existing strict-rule witness minimum sample count.
        return completeStrip(gray, w, h, body.left - reach, body.left - near, first, gap)
                && completeStrip(gray, w, h, body.right + near, body.right + reach, first, gap);
    }

    /** Keep every maximal raw run/column; never choose an alternative convenient phase. */
    private static boolean completeStrip(
            byte[] gray, int w, int h, int left, int right, float first, float gap) {
        int band = Math.max(1, Math.round(gap * .2f));
        int flank = Math.max(2, Math.round(gap * .3f));
        if (left < 0 || right >= w || left > right) return false;
        int[] previousTop = new int[5], previousBottom = new int[5];
        for (int x = left; x <= right; x++) {
            int lastBottom = -1;
            for (int k = 0; k < 5; k++) {
                int center = Math.round(first + k * gap), lo = center - band, hi = center + band;
                if (lo < 1 || hi >= h - 1) return false;
                int start = -1, end = -1;
                for (int y = lo; y <= hi; y++) {
                    if ((gray[y * w + x] & 255) >= 170) continue;
                    int a = y, b = y;
                    while (a > 0 && (gray[(a - 1) * w + x] & 255) < 170) a--;
                    while (b + 1 < h && (gray[(b + 1) * w + x] & 255) < 170) b++;
                    // A merged vertical object, another run or a clipped path is UNKNOWN.
                    if (a < lo || b > hi || start >= 0) return false;
                    start = a;
                    end = b;
                    y = b;
                }
                if (start < 0 || start <= lastBottom) return false;
                int midpoint = Math.round((start + end) * .5f), tone = 255;
                for (int y = start; y <= end; y++) tone = Math.min(tone, gray[y * w + x] & 255);
                if (midpoint - flank < 0
                        || midpoint + flank >= h
                        || (gray[(midpoint - flank) * w + x] & 255) <= tone + 20
                        || (gray[(midpoint + flank) * w + x] & 255) <= tone + 20) return false;
                if (x > left && (start > previousBottom[k] + 1 || end < previousTop[k] - 1))
                    return false;
                previousTop[k] = start;
                previousBottom[k] = end;
                lastBottom = end;
            }
        }
        return true;
    }

    /** Preserve existing two-half raw support, full-sized head, key and rhythmic-stem exclusions. */
    private static boolean stackedRawBody(
            byte[] labels, byte[] gray, int w, Body body, MeterChangeDetector.Crop crop) {
        float gap = crop.gap();
        int first = crop.firstLine();
        int radius = Math.max(1, Math.round(gap * .12f));
        int upper = 0,
                lower = 0,
                head = 0,
                key = 0,
                symbols = 0,
                us = 0,
                ls = 0,
                minY = Integer.MAX_VALUE,
                maxY = -1;
        long upperX = 0, lowerX = 0;
        int[] stems = new int[body.right - body.left + 1];
        for (int y = crop.top(); y < crop.bottom(); y++)
            for (int x = body.left; x <= body.right; x++) {
                byte label = labels[y * w + x];
                if (label == OmrMeasurePostProcessor.NOTEHEAD) head++;
                if (label == OmrMeasurePostProcessor.CLEF_OR_KEY) key++;
                if (label == OmrMeasurePostProcessor.STEM_OR_REST) stems[x - body.left]++;
                if (label == OmrMeasurePostProcessor.SYMBOL) {
                    symbols++;
                    if (y < first + gap * 1.8f) us++;
                    if (y > first + gap * 2.2f) ls++;
                }
                boolean rule = false;
                for (int k = 0; k < 5; k++)
                    if (Math.abs(y - (first + k * gap)) <= radius) rule = true;
                if (rule || (gray[y * w + x] & 255) >= 155) continue;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (y < first + gap * 1.8f) {
                    upper++;
                    upperX += x - body.left;
                }
                if (y > first + gap * 2.2f) {
                    lower++;
                    lowerX += x - body.left;
                }
            }
        if (head > gap * gap * .20f
                || upper < gap * 2
                || lower < gap * 2
                || maxY - minY + 1 < gap * 3.1f) return false;
        if (key > gap * gap * .5f
                && key > symbols * 2
                && Math.abs(upperX / (float) upper - lowerX / (float) lower) > gap * .5f)
            return false;
        if (symbols >= gap * 2 && Math.min(us, ls) < gap)
            for (int count : stems) if (count > (4 * gap + 1) * .75f) return false;
        return true;
    }
}
