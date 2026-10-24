// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete, raw-isolated short approach slashes with an independently supported destination. */
final class CompactApproachStrokes {
    private CompactApproachStrokes() {}

    static NoteSlideDetector.Stroke find(
            byte[] raw,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            double correlation,
            double residual,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks) {
        if (count < 12
                || count > 256
                || (long) count * heads.size() > 1_000_000
                || right - left < 3
                || bottom - top < 5
                || !Double.isFinite(slope)
                || slope > -.6
                || slope < -2.5
                || Math.abs(correlation) < .8
                || residual > 1.3) return null;
        // Staff cleanup must not manufacture a slash by cutting a flag, rule, stem or curve.
        var owned = new HashSet<Integer>();
        for (int i = 0; i < count; i++) owned.add(pixels[i]);
        for (int at : owned) {
            int x = at % w, y = at / w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) return null;
                    int next = ny * w + nx;
                    if ((raw[next] & 255) < 150 && !owned.contains(next)) return null;
                }
        }
        return supportedStroke(
                raw,
                w,
                h,
                pixels,
                count,
                left,
                right,
                top,
                bottom,
                cx,
                cy,
                slope,
                correlation,
                residual,
                staffs,
                heads,
                tracks);
    }

    /** Caller must independently certify the full raw or staff-residual component isolation. */
    static NoteSlideDetector.Stroke supportedStroke(
            byte[] raw,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            double correlation,
            double residual,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks) {
        if (count < 12
                || count > 256
                || (long) count * heads.size() > 1_000_000
                || right - left < 3
                || bottom - top < 5
                || !Double.isFinite(slope)
                || slope > -.6
                || slope < -2.5
                || Math.abs(correlation) < .8
                || residual > 1.3) return null;
        var owned = new HashSet<Integer>();
        for (int i = 0; i < count; i++) owned.add(pixels[i]);
        double endY = cy + (right - cx) * slope;
        return supportedDestination(
                raw, w, h, pixels, count, left, right, top, bottom, cx, cy, slope, residual, staffs,
                heads, tracks, right, endY);
    }

    private static NoteSlideDetector.Stroke supportedDestination(
            byte[] raw,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            double residual,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks,
            double endX,
            double endY) {
        var owned = new HashSet<Integer>();
        for (int i = 0; i < count; i++) owned.add(pixels[i]);
        int owner = -1;
        for (int i = 0; i < heads.size(); i++) {
            var n = heads.get(i);
            float g = n.gap();
            if (n.staff() < 0
                    || n.staff() >= staffs.size()
                    || !Float.isFinite(n.x() + n.y() + g)
                    || g < 6
                    || g > 64
                    || right - left < g * .25
                    || right - left > g * .65
                    || bottom - top < g * .4
                    || bottom - top > g * 1.2
                    || residual > Math.max(1, g * .08)
                    || n.x() - endX < g * .65
                    || n.x() - endX > g * 1.65
                    || endY - n.y() < g * .2
                    || endY - n.y() > g * .9) continue;
            // Nomination precedes raw-body queries; ink cannot break a destination ambiguity.
            if (owner >= 0) return null;
            owner = i;
        }
        if (owner < 0) return null;
        var n = heads.get(owner);
        float g = n.gap();
        for (int i = 0; i < heads.size(); i++)
            if (i != owner) {
                var other = heads.get(i);
                if (other.staff() == n.staff()
                        && Math.abs(other.x() - n.x()) < g * .4
                        && Math.abs(other.y() - n.y()) < g * .4) return null;
                for (int at : owned)
                    if (Math.abs(other.x() - at % w) < g * .85
                            && Math.abs(other.y() - at / w) < g * .8) return null;
            }
        StaffPitchTrack track = tracks.get(n.staff());
        if (!tracks.containsKey(n.staff())) {
            var s = staffs.get(n.staff());
            if (!Float.isFinite(s.top() + s.bottom() + s.gap())
                    || s.gap() < 3
                    || s.gap() > 64
                    || s.bottom() <= s.top()) return null;
            track = StaffPitchTrack.forPrintedSlurs(raw, w, h, s.top(), s.bottom(), s.gap());
            tracks.put(n.staff(), track);
        }
        if (track == null) return null;
        var body =
                PrintedSlurOwners.headEvidence(
                        List.of(
                                new PrintedSlurOwners.GeometryHead(
                                        owner, n.x(), n.y(), n.staff(), staffs.size())),
                        raw,
                        w,
                        h,
                        track);
        boolean meets = false;
        for (int x = Math.max(0, Math.round(n.x() - g * .75f)); x < w && x <= n.x(); x++) {
            double y = cy + (x - cx) * slope;
            for (int dy = -(int) Math.ceil(g * .12); dy <= Math.ceil(g * .12); dy++) {
                int iy = (int) Math.round(y) + dy;
                if (iy >= 0 && iy < h && body.contains(x, iy)) meets = true;
            }
        }
        if (!meets) return null;
        // The unchanged phrase extractor owns raw150 ink. Additional antialiased residual
        // samples must not defeat its exact core comparison merely by being lighter.
        int[] phrasePixels = pixels;
        int phraseCount = count;
        for (int i = 0; i < count; i++)
            if ((raw[pixels[i]] & 255) >= 150) {
                phrasePixels = new int[count];
                phraseCount = 0;
                for (int j = 0; j < count; j++)
                    if ((raw[pixels[j]] & 255) < 150) phrasePixels[phraseCount++] = pixels[j];
                break;
            }
        if (PhraseOwnedSlideComponents.owned(
                raw, w, h, staffs, heads, owner, phrasePixels, phraseCount)) return null;
        return new NoteSlideDetector.Stroke(
                left,
                (float) (cy + (left - cx) * slope),
                (float) endX,
                (float) endY,
                1,
                owner,
                false,
                false);
    }

    /**
     * A staff-isolated miniature uses its complete contiguous row profile, including every ink
     * pixel.
     */
    static NoteSlideDetector.Stroke profileSupportedStroke(
            byte[] raw,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks) {
        int rows = bottom - top + 1;
        if (count < 12
                || count > 256
                || (long) count * heads.size() > 1_000_000
                || rows < 6
                || rows > 80
                || right - left < gap * .25f
                || right - left > gap * .65f
                || bottom - top < gap * .4f
                || bottom - top > gap * 1.2f) return null;
        int[] lo = new int[rows], hi = new int[rows], size = new int[rows];
        Arrays.fill(lo, w);
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % w, j = pixels[i] / w - top;
            lo[j] = Math.min(lo[j], x);
            hi[j] = Math.max(hi[j], x);
            size[j]++;
        }
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0, prior = Double.POSITIVE_INFINITY;
        for (int j = 0; j < rows; j++) {
            if (size[j] == 0
                    || size[j] != hi[j] - lo[j] + 1
                    || size[j] > Math.max(3, Math.ceil(gap * .35f))) return null;
            double x = (lo[j] + hi[j]) * .5, y = top + j;
            if (x > prior + Math.max(1, gap * .08f)) return null;
            prior = x;
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }
        double endX = (lo[0] + hi[0]) * .5, startX = (lo[rows - 1] + hi[rows - 1]) * .5;
        if (endX - startX < gap * .25f) return null;
        double cx = sx / rows,
                cy = sy / rows,
                vx = sxx / rows - cx * cx,
                vy = syy / rows - cy * cy,
                cov = sxy / rows - cx * cy;
        if (vx <= 0 || vy <= 0) return null;
        double slope = cov / vx,
                corr = cov / Math.sqrt(vx * vy),
                residual =
                        Math.sqrt(
                                Math.max(
                                        0,
                                        (vx + vy - Math.sqrt((vx - vy) * (vx - vy) + 4 * cov * cov))
                                                * .5));
        if (slope > -.6 || slope < -2.5 || corr > -.9 || residual > Math.max(1, gap * .08f))
            return null;
        var result =
                supportedDestination(
                        raw, w, h, pixels, count, left, right, top, bottom, cx, cy, slope, residual,
                        staffs, heads, tracks, endX, top);
        return result == null
                ? null
                : new NoteSlideDetector.Stroke(
                        (float) startX,
                        bottom,
                        (float) endX,
                        top,
                        1,
                        result.noteIndex(),
                        false,
                        false);
    }
}
