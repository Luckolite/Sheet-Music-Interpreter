// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Separates a complete compact glyph using independently observed staff background contrast. */
final class StaffJoinedCompactApproach {
    record Certificate(NoteSlideDetector.Stroke stroke, List<Integer> pixels) {}

    private StaffJoinedCompactApproach() {}

    static Certificate find(
            byte[] raw,
            int w,
            int h,
            int[] original,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks,
            AttachedApproachBranches.Budget budget) {
        if (raw == null
                || w <= 0
                || h <= 0
                || raw.length != (long) w * h
                || original == null
                || count > original.length
                || left < 0
                || right >= w
                || top < 0
                || bottom >= h
                || left > right
                || top > bottom
                || count < 12
                || count > 4096
                || budget.refused
                || !budget.take(heads.size() * 16L)) return null;
        int destination = -1;
        for (int i = 0; i < heads.size(); i++) {
            var n = heads.get(i);
            float g = n.gap();
            if (n.staff() < 0
                    || n.staff() >= staffs.size()
                    || !Float.isFinite(n.x() + n.y() + g)
                    || g < 6
                    || g > 64
                    || right - left > 3 * g
                    || bottom - top > 7 * g
                    || n.x() < right + g * .25f
                    || n.x() > right + 3 * g
                    || n.y() < top - 1.2f * g
                    || n.y() > bottom) continue;
            if (destination >= 0) return null;
            destination = i;
        }
        if (destination < 0) return null;
        var target = heads.get(destination);
        float gap = target.gap();
        int margin = Math.max(3, (int) Math.ceil(gap * .35f));
        int l = Math.max(0, left - margin),
                r = Math.min(w - 1, right + margin),
                t = Math.max(0, top - margin),
                b = Math.min(h - 1, bottom + margin);
        int sw = r - l + 1, sh = b - t + 1, area = sw * sh;
        if (area > 65536
                || right - Math.round(gap * 3) >= left
                || right - Math.round(gap * 5) < 0
                || right + Math.round(gap * 5) >= w) return null;
        long rowBound = Math.min(sh, 5L * (2 * (int) Math.ceil(Math.max(1, gap * .13f + .5f)) + 1));
        if (!budget.take(
                area * 32L
                        + rowBound * ((long) Math.ceil(gap * 4) + 2048)
                        + (long) Math.ceil(gap * gap * 160))) return null;
        if (!tracks.containsKey(target.staff())) {
            var staff = staffs.get(target.staff());
            if (!Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                    || staff.gap() < 3
                    || staff.gap() > 64
                    || staff.bottom() <= staff.top()) return null;
            tracks.put(
                    target.staff(),
                    StaffPitchTrack.forPrintedSlurs(
                            raw, w, h, staff.top(), staff.bottom(), staff.gap()));
        }
        var track = tracks.get(target.staff());
        if (track == null || !track.verifiedAt(right)) return null;
        float[] frame = track.at(right);
        if (!Float.isFinite(frame[0] + frame[1]) || Math.abs(frame[1] - gap) > gap * .15f)
            return null;
        int[] threshold = new int[sh];
        Arrays.fill(threshold, 150);
        boolean[] rule = new boolean[sh];
        for (int line = 0; line < 5; line++) {
            float center = frame[0] - line * frame[1];
            int cy = Math.round(center);
            if (cy < t - 3 || cy > b + 3) continue;
            if (!FlagRootStaffRule.matches(raw, w, h, cy, right, gap, 150)) continue;
            float radius = Math.max(1, gap * .13f + .5f);
            int first = Math.max(t, (int) Math.ceil(center - radius)),
                    last = Math.min(b, (int) Math.floor(center + radius));
            int[] proposed = new int[Math.max(0, last - first + 1)];
            boolean verified = true;
            for (int y = first; y <= last; y++) {
                int a = background(raw, w, y, right, gap, -1),
                        z = background(raw, w, y, right, gap, 1);
                if (a < 0 || z < 0 || Math.abs(a - z) > Math.max(12, Math.max(a, z) * .08f)) {
                    verified = false;
                    break;
                }
                int base = Math.min(a, z);
                proposed[y - first] = (int) Math.floor(base - Math.max(16, base * .18f));
            }
            if (verified)
                for (int y = first; y <= last; y++) {
                    threshold[y - t] = proposed[y - first];
                    rule[y - t] = true;
                }
        }
        boolean[] ink = new boolean[area];
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                ink[(y - t) * sw + x - l] = (raw[y * w + x] & 255) < threshold[y - t];
        var accepted = new ArrayList<Certificate>();
        int[] queue = new int[area];
        for (int seed = 0; seed < area; seed++) {
            if (!ink[seed]) continue;
            int n = 1, cursor = 0;
            queue[0] = seed;
            ink[seed] = false;
            int x0 = w, x1 = 0, y0 = h, y1 = 0;
            double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
            boolean clipped = false;
            while (cursor < n) {
                int p = queue[cursor++], xx = p % sw, yy = p / sw, x = l + xx, y = t + yy;
                clipped |= xx == 0 || xx == sw - 1 || yy == 0 || yy == sh - 1;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                sx += x;
                sy += y;
                sxx += (double) x * x;
                syy += (double) y * y;
                sxy += (double) x * y;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = yy + dy;
                        if (nx < 0 || nx >= sw || ny < 0 || ny >= sh) continue;
                        int q = ny * sw + nx;
                        if (ink[q]) {
                            ink[q] = false;
                            queue[n++] = q;
                        }
                    }
            }
            if (clipped
                    || n < 12
                    || n > 256
                    || x1 - x0 < 3
                    || x1 - x0 > gap * .65f
                    || y1 - y0 < 5
                    || y1 - y0 > gap * 1.2f) continue;
            int[] pixels = new int[n];
            boolean contact = false, isolation = true;
            var owned = new HashSet<Integer>();
            for (int i = 0; i < n; i++) {
                int p = queue[i];
                pixels[i] = (t + p / sw) * w + l + p % sw;
                owned.add(pixels[i]);
                contact |= rule[p / sw];
            }
            if (!contact) continue;
            for (int p : pixels)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = p % w + dx, y = p / w + dy, q = y * w + x;
                        if (x < l || x > r || y < t || y > b) {
                            isolation = false;
                            continue;
                        }
                        if ((raw[q] & 255) < 150
                                && !owned.contains(q)
                                && (!rule[y - t] || (raw[q] & 255) < threshold[y - t]))
                            isolation = false;
                    }
            if (!isolation
                    || !budget.take(
                            n * heads.size() * 4L
                                    + n * 16L
                                    + heads.size() * 64L
                                    + w * 2L
                                    + (long) Math.ceil(gap * gap * 64))) continue;
            double cx = sx / n,
                    cy = sy / n,
                    vx = sxx / n - cx * cx,
                    vy = syy / n - cy * cy,
                    cov = sxy / n - cx * cy;
            if (vx <= 0 || vy <= 0) continue;
            double slope = cov / vx,
                    correlation = cov / Math.sqrt(vx * vy),
                    residual =
                            Math.sqrt(
                                    Math.max(
                                            0,
                                            (vx
                                                            + vy
                                                            - Math.sqrt(
                                                                    (vx - vy) * (vx - vy)
                                                                            + 4 * cov * cov))
                                                    * .5));
            var stroke =
                    CompactApproachStrokes.supportedStroke(
                            raw,
                            w,
                            h,
                            pixels,
                            n,
                            x0,
                            x1,
                            y0,
                            y1,
                            cx,
                            cy,
                            slope,
                            correlation,
                            residual,
                            staffs,
                            heads,
                            tracks);
            if (stroke == null)
                stroke =
                        CompactApproachStrokes.profileSupportedStroke(
                                raw, w, h, pixels, n, x0, x1, y0, y1, gap, staffs, heads, tracks);
            if (stroke != null && stroke.noteIndex() == destination)
                accepted.add(new Certificate(stroke, Arrays.stream(pixels).boxed().toList()));
        }
        return !budget.refused && accepted.size() == 1 ? accepted.get(0) : null;
    }

    private static int background(byte[] raw, int w, int y, int origin, float gap, int direction) {
        int[] histogram = new int[256];
        int n = 0;
        for (int d = Math.round(gap * 3); d <= Math.round(gap * 5); d++) {
            histogram[raw[y * w + origin + direction * d] & 255]++;
            n++;
        }
        int sum = 0, median = 0;
        for (; median < 256; median++) {
            sum += histogram[median];
            if (sum > n / 2) break;
        }
        int stable = 0;
        for (int v = Math.max(0, median - 12); v <= Math.min(255, median + 12); v++)
            stable += histogram[v];
        return stable >= n * .8f ? median : -1;
    }
}
