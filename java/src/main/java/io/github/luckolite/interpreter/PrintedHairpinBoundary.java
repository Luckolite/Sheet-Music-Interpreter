// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A near-boundary wedge end needs a complete printed separator, not a nearby note stem. */
final class PrintedHairpinBoundary {
    private PrintedHairpinBoundary() {}

    static boolean proved(
            byte[] gray,
            int width,
            int height,
            PlayingTechniqueDetector.Staff owner,
            MeasureRegion measure,
            float endX) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || owner == null
                || measure == null
                || !Float.isFinite(endX + owner.top() + owner.bottom() + owner.gap())
                || owner.gap() < 3
                || owner.bottom() <= owner.top()) return false;
        float gap = owner.gap();
        int left = Math.max(0, (int) Math.floor(measure.left() * width - gap));
        int right = Math.min(width - 1, (int) Math.ceil(measure.right() * width + gap * 2));
        int top = Math.max(0, (int) Math.floor(owner.top() - gap));
        int bottom = Math.min(height - 1, (int) Math.ceil(owner.bottom() + gap));
        int w = right - left + 1, h = bottom - top + 1;
        if (w < gap * 4 || h < gap * 5 || (long) w * h > 200000) return false;
        byte[] raw = new byte[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(gray, (top + y) * width + left, raw, y * w, w);
        var track =
                StaffPitchTrack.forPrintedSlurs(
                        raw, w, h, owner.top() - top, owner.bottom() - top, gap);
        if (track == null) return false;
        int center = Math.round(measure.right() * width), radius = Math.max(2, Math.round(gap));
        for (int x = Math.max(left, center - radius); x <= Math.min(right, center + radius); x++) {
            if (x < endX || x - endX > gap * 1.5f) continue;
            int previous = x - Math.round(gap);
            // Certify the remaining distance by following all five printed rules;
            // an extrapolated track alone never proves the separator's frame.
            while (previous >= left
                    && x - previous <= gap * 4
                    && !track.verifiedAt(previous - left)) previous--;
            if (previous < left || x - previous > gap * 4 || !track.verifiedAt(previous - left))
                continue;
            float[] f = track.at(x - left);
            if (!Float.isFinite(f[0] + f[1]) || f[1] < 3) continue;
            boolean connected = true;
            for (int line = 0; line < 5; line++) {
                int hits = 0;
                for (int xx = previous; xx <= x; xx++) {
                    float[] at = track.at(xx - left);
                    if (!Float.isFinite(at[0] + at[1]) || at[1] < 3) {
                        connected = false;
                        break;
                    }
                    int yy = Math.round(at[0] - line * at[1]) + top;
                    boolean ink = false;
                    for (int dy = -1; dy <= 1; dy++)
                        if (yy + dy >= 0
                                && yy + dy < height
                                && (gray[(yy + dy) * width + xx] & 255) < 180) ink = true;
                    if (ink) hits++;
                }
                if (hits < (x - previous + 1) * .9f) connected = false;
            }
            if (!connected) continue;
            int a = Math.round(f[0] - 4 * f[1]) + top, b = Math.round(f[0]) + top;
            if (a < 0 || b >= height || b <= a) continue;
            int hits = 0;
            for (int y = a; y <= b; y++) if ((gray[y * width + x] & 255) < 180) hits++;
            if (hits < (b - a + 1) * .95f) continue;
            boolean clean = true;
            int first = Math.max(2, Math.round(gap * .3f)),
                    last = Math.max(first + 2, Math.round(gap * .8f));
            for (int edge : new int[] {a, b}) {
                int white = 0, samples = 0;
                for (int d = first; d <= last; d++) {
                    int y = edge == a ? edge - d : edge + d;
                    if (y < 0 || y >= height) {
                        clean = false;
                        break;
                    }
                    samples++;
                    if ((gray[y * width + x] & 255) >= 220) white++;
                }
                if (white < samples - 1) clean = false;
            }
            if (clean) return true;
        }
        return false;
    }
}
