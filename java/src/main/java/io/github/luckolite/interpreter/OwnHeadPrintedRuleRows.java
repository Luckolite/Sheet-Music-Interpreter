// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Local printed-rule observations inside a complete head, never complete beam authority. */
final class OwnHeadPrintedRuleRows {
    private OwnHeadPrintedRuleRows() {}

    private record Stroke(int top, int bottom, int mass) {
        int width() {
            return bottom - top + 1;
        }
    }

    static boolean[] observe(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int top,
            int bottom,
            float staffTop,
            float staffBottom,
            float gap,
            int[] shaft) {
        if (gray == null
                || labels == null
                || gray.length != (long) w * h
                || labels.length != gray.length
                || w < 3
                || h < 3
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 64
                || !Float.isFinite(staffTop)
                || !Float.isFinite(staffBottom)
                || Math.abs(staffBottom - staffTop - 4 * gap) > .2f * gap
                || left < 1
                || right >= w - 1
                || top < 1
                || bottom >= h - 1
                || right < left
                || bottom < top
                || right - left + 1 < gap * .65f
                || right - left + 1 > gap * 1.8f
                || bottom - top + 1 < gap * .55f
                || bottom - top + 1 > gap * 1.6f
                || shaft == null
                || shaft.length != 3
                || Math.abs(shaft[2]) != 1
                || shaft[0] < 0
                || shaft[0] >= w
                || shaft[1] < 0
                || shaft[1] >= h
                || Math.abs(shaft[0] - (shaft[2] < 0 ? right : left)) > gap * .25f
                || (shaft[2] < 0 ? top - shaft[1] : shaft[1] - bottom) < gap * .8f
                || (shaft[2] < 0 ? top - shaft[1] : shaft[1] - bottom) > gap * 2.5f
                || !filled(gray, w, left, right, top, bottom)) return null;
        int center = (left + right) / 2;
        Stroke[] rules = new Stroke[5];
        int[] widths = new int[5], masses = new int[5];
        for (int line = 0; line < 5; line++) {
            int y = Math.round(staffTop + line * gap);
            Stroke[] samples = new Stroke[6];
            for (int side = 0; side < 2; side++)
                for (int distance = 2; distance <= 4; distance++) {
                    int x = center + (side == 0 ? -1 : 1) * Math.round(distance * gap);
                    Stroke stroke = stroke(gray, w, h, x, y, gap);
                    if (stroke == null) continue;
                    boolean clean = true;
                    for (int row = stroke.top; row <= stroke.bottom; row++) {
                        int label = labels[row * w + x] & 255;
                        if (label != 0 && label != 4) clean = false;
                    }
                    if (clean) samples[side * 3 + distance - 2] = stroke;
                }
            Stroke chosen = null;
            int best = 0;
            for (Stroke sample : samples) {
                if (sample == null) continue;
                int votes = 0;
                boolean a = false, b = false;
                for (int j = 0; j < samples.length; j++)
                    if (same(sample, samples[j])) {
                        votes++;
                        if (j < 3) a = true;
                        else b = true;
                    }
                if (a && b && votes > best) {
                    chosen = sample;
                    best = votes;
                }
            }
            if (chosen == null || best < 2) return null;
            rules[line] = chosen;
            widths[line] = chosen.width();
            masses[line] = chosen.mass;
        }
        Arrays.sort(widths);
        Arrays.sort(masses);
        boolean[] rows = new boolean[bottom - top + 1];
        for (int line = -1; line <= 5; line++) {
            float phase = staffTop + line * gap;
            if (phase < top - gap * .2f || phase > bottom + gap * .2f) continue;
            Stroke observed = line >= 0 && line < 5 ? rules[line] : null;
            if (observed != null) {
                if (!staffFlanks(labels, gray, w, h, left, right, phase, gap, observed)) continue;
            } else {
                if (Math.abs((top + bottom) * .5f - phase) > gap * .28f) continue;
                observed = ledgerFlanks(gray, w, h, left, right, phase, gap, widths[2], masses[2]);
                if (observed == null) continue;
            }
            for (int y = Math.max(top, observed.top); y <= Math.min(bottom, observed.bottom); y++)
                rows[y - top] = true;
        }
        return rows;
    }

    private static boolean same(Stroke a, Stroke b) {
        return b != null
                && a.top == b.top
                && a.bottom == b.bottom
                && Math.abs(a.mass - b.mass) <= 2;
    }

    private static Stroke stroke(byte[] gray, int w, int h, int x, int seed, float gap) {
        if (x < 0 || x >= w || seed < 1 || seed >= h - 1) return null;
        int radius = Math.max(1, Math.round(gap * .2f));
        int first = -1, last = -1;
        for (int y = Math.max(0, seed - radius); y <= Math.min(h - 1, seed + radius); y++)
            if ((gray[y * w + x] & 255) < 205) {
                if (first < 0) first = y;
                last = y;
            }
        if (first < 1
                || last >= h - 1
                || last - first + 1 > Math.ceil(gap * .3f)
                || (gray[(first - 1) * w + x] & 255) < 205
                || (gray[(last + 1) * w + x] & 255) < 205) return null;
        int mass = 0;
        for (int y = first; y <= last; y++) {
            int tone = gray[y * w + x] & 255;
            if (tone >= 205) return null;
            mass += 255 - tone;
        }
        return new Stroke(first, last, mass);
    }

    private static boolean staffFlanks(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            float phase,
            float gap,
            Stroke reference) {
        int padding = Math.max(2, Math.round(gap * .14f));
        int reach = Math.max(padding + 2, Math.round(gap * .45f));
        for (int side = -1; side <= 1; side += 2)
            for (int d = padding + 1; d <= reach; d++) {
                int x = side < 0 ? left - d : right + d;
                if (!same(reference, stroke(gray, w, h, x, Math.round(phase), gap))) return false;
                for (int y = reference.top; y <= reference.bottom; y++) {
                    int label = labels[y * w + x] & 255;
                    if (label != 0 && label != 4) return false;
                }
            }
        return true;
    }

    private static Stroke ledgerFlanks(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            float phase,
            float gap,
            int width,
            int mass) {
        int padding = Math.max(2, Math.round(gap * .14f));
        int reach = Math.max(padding + 3, Math.round(gap * .65f));
        Stroke common = null;
        int[] lengths = new int[2];
        for (int side = 0; side < 2; side++) {
            boolean paper = false;
            int ink = 0;
            for (int d = padding + 1; d <= reach + 2; d++) {
                int x = side == 0 ? left - d : right + d;
                if (x < 0 || x >= w) return null;
                Stroke current = stroke(gray, w, h, x, Math.round(phase), gap);
                if (current == null) {
                    // A malformed, thick or detached rail is not paper.
                    int radius = Math.max(1, Math.round(gap * .2f));
                    for (int y = Math.max(0, Math.round(phase) - radius);
                            y <= Math.min(h - 1, Math.round(phase) + radius);
                            y++) if ((gray[y * w + x] & 255) < 205) return null;
                    paper = true;
                    continue;
                }
                if (paper
                        || d > reach
                        || current.width() > width
                        || current.mass > mass
                        || Math.abs((current.top + current.bottom) * .5f - phase) > gap * .18f)
                    return null;
                if (common != null
                        && (current.top != common.top || current.bottom != common.bottom))
                    return null;
                common = current;
                ink++;
                lengths[side] = d;
            }
            if (!paper || ink < 2) return null;
        }
        return Math.abs(lengths[0] - lengths[1]) <= Math.max(1, Math.round(gap * .14f))
                ? common
                : null;
    }

    private static boolean filled(byte[] gray, int w, int left, int right, int top, int bottom) {
        int[] counts = new int[bottom - top + 1];
        int total = 0, peak = 0;
        for (int y = top; y <= bottom; y++) {
            int count = 0;
            for (int x = left; x <= right; x++) if ((gray[y * w + x] & 255) < 165) count++;
            counts[y - top] = count;
            total += count;
            peak = Math.max(peak, count);
        }
        int tip = Math.round(counts.length * .12f);
        return total >= counts.length * (right - left + 1) * .48f
                && peak >= (right - left + 1) * .75f
                && Math.min(counts[tip], counts[counts.length - 1 - tip])
                        <= Math.round(peak * .86f);
    }
}
