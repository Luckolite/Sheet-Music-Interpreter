// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Compare tie endpoints against locally proved rules on a sloping printed staff. */
final class LocalTieStaffAlignment {
    private LocalTieStaffAlignment() {}

    static boolean same(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float ax,
            float ay,
            int al,
            int ar,
            float ag,
            float bx,
            float by,
            int bl,
            int br,
            float bg,
            int step) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || labels.length != (long) width * height
                || gray.length != labels.length
                || !Float.isFinite(ax)
                || !Float.isFinite(ay)
                || !Float.isFinite(bx)
                || !Float.isFinite(by)
                || !Float.isFinite(ag)
                || !Float.isFinite(bg)
                || ag < 5
                || bg < 5) return false;
        float gap = (ag + bg) * .5f;
        if (Math.abs(ay - by) > gap * 3 || Math.abs(ag - bg) > gap * .12f) return false;
        float[] a =
                StaffPitchTrack.localRules(
                        labels, gray, width, height, ax, al, ar, ay + step * ag * .5f, ag);
        float[] b =
                StaffPitchTrack.localRules(
                        labels, gray, width, height, bx, bl, br, by + step * bg * .5f, bg);
        if (matching(gray, width, height, ax, ay, bx, by, gap, step, a, b)) return true;
        // A photographed staff may lose a labelled outer rule. Retain the ordinary
        // phase/pitch bounds, and recover only independently printed five-rule groups
        // at both endpoints, each also supported by a majority of semantic rules.
        a =
                StaffPitchTrack.localCurledEdgeRules(
                        gray, width, height, ax, al, ar, ay + step * ag * .5f, ag);
        b =
                StaffPitchTrack.localCurledEdgeRules(
                        gray, width, height, bx, bl, br, by + step * bg * .5f, bg);
        return semanticRules(labels, width, height, ax, al, ar, gap, a)
                && semanticRules(labels, width, height, bx, bl, br, gap, b)
                && matching(gray, width, height, ax, ay, bx, by, gap, step, a, b);
    }

    private static boolean matching(
            byte[] gray,
            int width,
            int height,
            float ax,
            float ay,
            float bx,
            float by,
            float gap,
            int step,
            float[] a,
            float[] b) {
        if (a == null || b == null || Math.abs(a[1] - b[1]) > gap * .08f) return false;
        if (ambiguous(gray, width, height, ax, a) || ambiguous(gray, width, height, bx, b))
            return false;
        float ap = (a[0] - ay) / a[1], bp = (b[0] - by) / b[1];
        return Math.abs(ap - bp) <= .22f
                && Math.abs(ap - step * .5f) <= .22f
                && Math.abs(bp - step * .5f) <= .22f;
    }

    private static boolean semanticRules(
            byte[] labels,
            int width,
            int height,
            float x,
            int headLeft,
            int headRight,
            float gap,
            float[] rules) {
        if (rules == null) return false;
        int left = Math.max(0, Math.round(x - gap * 3)),
                right = Math.min(width - 1, Math.round(x + gap * 3));
        int exclusion = Math.max(1, Math.round(gap * .45f)), supported = 0;
        int excludedLeft = headLeft - exclusion, excludedRight = headRight + exclusion;
        for (int rule = 0; rule < 5; rule++) {
            int hits = 0, samples = 0;
            float y = rules[0] - rule * rules[1];
            int top = Math.max(0, Math.round(y - gap * .35f)),
                    bottom = Math.min(height - 1, Math.round(y + gap * .35f));
            for (int xx = left; xx <= right; xx++) {
                if (xx >= excludedLeft && xx <= excludedRight) continue;
                samples++;
                for (int yy = top; yy <= bottom; yy++)
                    if (labels[yy * width + xx] == 4) {
                        hits++;
                        break;
                    }
            }
            if (samples >= 8 && hits >= Math.max(4, samples * .45f)) supported++;
        }
        return supported >= 3;
    }

    private static boolean ambiguous(byte[] gray, int width, int height, float x, float[] rules) {
        int left = Math.max(0, Math.round(x - rules[1] * 3)),
                right = Math.min(width - 1, Math.round(x + rules[1] * 3));
        int radius = Math.max(1, Math.round(rules[1] * .2f)),
                flank = Math.max(2, Math.round(rules[1] * .32f));
        for (float outside : new float[] {rules[0] + rules[1], rules[0] - 5 * rules[1]}) {
            int columns = 0;
            int top = Math.max(flank, Math.round(outside) - radius),
                    bottom = Math.min(height - 1 - flank, Math.round(outside) + radius);
            for (int xx = left; xx <= right; xx++)
                for (int y = top; y <= bottom; y++) {
                    int value = gray[y * width + xx] & 255;
                    if (value <= 205
                            && (gray[(y - flank) * width + xx] & 255) >= value + 12
                            && (gray[(y + flank) * width + xx] & 255) >= value + 12) {
                        columns++;
                        break;
                    }
                }
            if (columns >= (right - left + 1) * .6f) return true;
        }
        return false;
    }
}
