// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Two thick parallel beam cores continuing away from a stem-owned mask fragment. */
final class ParallelBeamTip {
    private ParallelBeamTip() {}

    static boolean matches(byte[] gray, int w, int h, float x, float y, float gap) {
        return matches(gray, w, h, x, y, gap, null);
    }

    static boolean matches(
            byte[] gray, int w, int h, float x, float y, float gap, StaffPitchTrack track) {
        if (gray == null || gap < 8) return false;
        int threshold =
                BeamInkThreshold.at(
                        gray,
                        w,
                        h,
                        Math.round(x),
                        Math.round(y - gap * 2),
                        Math.round(y + gap * 2),
                        gap);
        int radius = Math.max(1, Math.round(gap * .12f));
        for (int side : new int[] {-1, 1})
            for (int shift = -3; shift <= 3; shift++)
                for (int angle = -10; angle <= 10; angle++) {
                    int supported = 0, seams = 0, total = 0;
                    float slope = angle * .05f;
                    for (int d = Math.round(gap * .8f); d <= Math.round(gap * 2.8f); d++) {
                        int xx = Math.round(x) + side * d,
                                cy = Math.round(y + shift + slope * d),
                                offset = Math.round(gap * .42f);
                        if (xx < 0
                                || xx >= w
                                || cy - offset - radius < 0
                                || cy + offset + radius >= h) break;
                        boolean cores = true;
                        for (int dy = -radius; dy <= radius; dy++)
                            if ((gray[(cy - offset + dy) * w + xx] & 255) >= threshold
                                    || (gray[(cy + offset + dy) * w + xx] & 255) >= threshold)
                                cores = false;
                        total++;
                        if (cores) supported++;
                        if (cores && (gray[cy * w + xx] & 255) >= threshold) seams++;
                    }
                    if (total < gap * 1.9f || supported < total * .94f || seams < total * .55f)
                        continue;
                    // The fragment must stay inside the same pair of straight outer edges.
                    // A rounded chord head protruding above/below them remains a real note.
                    int clear = 0, checked = 0;
                    for (int d = Math.round(gap * .25f); d <= Math.round(gap * .8f); d++) {
                        int xx = Math.round(x) + side * d,
                                cy = Math.round(y + shift + slope * d),
                                outer = Math.round(gap * .9f);
                        if (xx < 0 || xx >= w || cy - outer < 0 || cy + outer >= h) break;
                        checked++;
                        if (clearOrRule(gray, w, h, xx, cy - outer, gap, threshold)
                                && clearOrRule(gray, w, h, xx, cy + outer, gap, threshold)) clear++;
                    }
                    if (checked > 0
                            && clear >= checked * .8f
                            && !printedCore(gray, w, h, x, y, gap, side, shift, slope, -1, track)
                            && !printedCore(gray, w, h, x, y, gap, side, shift, slope, 1, track))
                        return true;
                }
        return false;
    }

    private static boolean printedCore(
            byte[] gray,
            int w,
            int h,
            float x,
            float y,
            float gap,
            int side,
            int shift,
            float slope,
            int rail,
            StaffPitchTrack track) {
        if (track == null || !track.verified()) return false;
        int hits = 0, radius = Math.max(1, Math.round(gap * .12f)), offset = Math.round(gap * .42f);
        for (float off : new float[] {.9f, 1.5f, 2.5f}) {
            int d = Math.round(gap * off), xx = Math.round(x) + side * d;
            int cy = Math.round(y + shift + slope * d) + rail * offset;
            float[] f = track.at(xx);
            float top = f[0] - 4 * f[1];
            int line = Math.round((cy - top) / f[1]);
            int printedCenter = Math.round(top + line * f[1]);
            boolean samePrintedRule =
                    line >= 0 && line <= 4 && Math.abs(cy - top - line * f[1]) <= f[1] * .25f;
            if (PrintedRuleInkOwnership.matchesWithNarrowReference(
                            gray, w, h, xx, cy - radius, cy + radius, top, f[1], 0, track)
                    || samePrintedRule
                            && PrintedRuleInkOwnership.matchesWithNarrowReference(
                                    gray,
                                    w,
                                    h,
                                    xx,
                                    printedCenter - radius,
                                    printedCenter + radius,
                                    top,
                                    f[1],
                                    0,
                                    track)) hits++;
        }
        return hits >= 2;
    }

    static boolean clearOrRule(byte[] gray, int w, int h, int x, int y, float gap) {
        return clearOrRule(gray, w, h, x, y, gap, 165);
    }

    static boolean clearOrRule(byte[] gray, int w, int h, int x, int y, float gap, int threshold) {
        if ((gray[y * w + x] & 255) >= threshold) return true;
        int radius = Math.round(gap * 4), ink = 0;
        if (x - radius < 0 || x + radius >= w) return false;
        for (int xx = x - radius; xx <= x + radius; xx++)
            if ((gray[y * w + xx] & 255) < threshold) ink++;
        // A staff line crosses the apparent outline but is not a head bulge.
        return ink >= (radius * 2 + 1) * .94f;
    }
}
