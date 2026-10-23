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
        double endY = cy + (right - cx) * slope;
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
                    || n.x() - right < g * .65
                    || n.x() - right > g * 1.65
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
        if (!meets
                || PhraseOwnedSlideComponents.owned(raw, w, h, staffs, heads, owner, pixels, count))
            return null;
        return new NoteSlideDetector.Stroke(
                left, (float) (cy + (left - cx) * slope), right, (float) endY, 1, owner, false);
    }
}
