// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete accent plus independently anchored pitched shaft, never rhythmic gap inference. */
final class AccentedHeadRestOwnership {
    static boolean owns(
            byte[] g,
            int w,
            int h,
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent note,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] line,
            int top,
            float gap) {
        return owns(g, w, h, notes, note, left, right, minY, maxY, line, top, gap, null);
    }

    static boolean owns(
            byte[] g,
            int w,
            int h,
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent note,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] line,
            int top,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (g == null
                || w <= 0
                || h <= 0
                || g.length != (long) w * h
                || notes == null
                || note == null
                || note.kind() != ScoreNoteEvent.Kind.PITCHED
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || right >= w
                || left > right
                || minY < 0
                || maxY >= h
                || minY > maxY
                || line == null
                || minY < top
                || (long) maxY - top >= line.length) return false;
        if (top < 0 || (long) top + line.length > h || Math.ceil(gap) > Math.min(w, h))
            return false;
        int scanBottom = top + line.length - 1;
        if (scanBottom - top > gap * 6) return false;
        var origin =
                (quarterBudget == null || quarterBudget.allocate(64L + 8L * notes.size(), 0)
                        ? RestFigureOrigins.origin(notes, note, g, w, h)
                        : null);
        if (origin == null
                || origin.originalGray == null
                || origin.originalGray.length != (long) w * h) return false;
        g = origin.originalGray;
        boolean[] rules =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (line.length))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (line.length))))
                        ? new boolean[line.length]
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = top; y <= scanBottom; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                rules[y - top] = rule(g, w, h, left, right, y, gap, quarterBudget);
            }
        }

        int margin = (int) Math.ceil(gap),
                l = Math.max(0, left - margin),
                r = (int) Math.min(w - 1L, (long) right + margin),
                t = Math.max(0, top - margin),
                b = (int) Math.min(h - 1L, (long) scanBottom + margin);
        long area = (long) (r - l + 1) * (b - t + 1);
        if (area > 1000000 || right - left > gap * 3 || maxY - minY > gap * 4) return false;
        int seed = -1;
        outer:
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = top; y <= scanBottom; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (!rules[y - top]) {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = left; x <= right; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((g[y * w + x] & 255) < 170) {
                            seed = y * w + x;
                            break outer;
                        }
                    }
                }
            }
        }

        if (seed < 0) return false;
        int stride = r - l + 1;
        boolean[] seen =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) ((int) area))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) ((int) area))))
                        ? new boolean[(int) area]
                        : null);
        int[] q =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) ((int) area))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) ((int) area))))
                        ? new int[(int) area]
                        : null);
        int take = 0, n = 1;
        q[0] = seed;
        seen[(seed / w - t) * stride + seed % w - l] = true;
        int al = seed % w, ar = al, at = seed / w, ab = at;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (take < n) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int z = q[take++], x = z % w, y = z / w;
                if (x == l || x == r || y == t || y == b) return false;
                al = Math.min(al, x);
                ar = Math.max(ar, x);
                at = Math.min(at, y);
                ab = Math.max(ab, y);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int yy = y - 1; yy <= y + 1; yy++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int xx = x - 1; xx <= x + 1; xx++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                int a = (yy - t) * stride + xx - l;
                                if (!seen[a] && (g[yy * w + xx] & 255) < 170) {
                                    seen[a] = true;
                                    q[n++] = yy * w + xx;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (ar - al > gap * 2.5f
                || ab - at > gap * 1.5f
                || !((quarterBudget == null
                                || quarterBudget.allocate(
                                        512L
                                                + 128L
                                                        * (Math.max(0L, (long) (ar - al + 1))
                                                                * Math.max(
                                                                        0L, (long) (ab - at + 1)))
                                                + 16L * n,
                                        512L
                                                + 16L
                                                        * (Math.max(0L, (long) (ar - al + 1))
                                                                * Math.max(
                                                                        0L, (long) (ab - at + 1)))))
                        && OpenChevron.matches(q, w, al, at, ar, ab, n))) return false;
        int sl = w, sr = -1, sy = h;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = top; y <= scanBottom; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (!rules[y - top]) {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = left; x <= right; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((g[y * w + x] & 255) < 170 && !seen[(y - t) * stride + x - l]) {
                            sl = Math.min(sl, x);
                            sr = Math.max(sr, x);
                            sy = Math.min(sy, y);
                        }
                    }
                }
            }
        }

        if (sr < sl || sr - sl + 1 > gap * .4f || sy <= ab + 1 || maxY - sy < gap * .5f)
            return false;
        float nx = note.positionInMeasure(), ny = note.pageY() * h;
        if (!Float.isFinite(nx)
                || !Float.isFinite(ny)
                || ny - maxY < gap * .65f
                || ny - maxY > gap * 4.5f) return false;
        int end = Math.min(h - 1, Math.round(ny + gap * .4f));
        boolean attached = false;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = sy; y <= end; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                boolean dark = false;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = sl; x <= sr; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((g[y * w + x] & 255) < 170) {
                            dark = true;
                            if (origin.contains(x, y, quarterBudget)) attached = true;
                        }
                    }
                }

                if (!dark) return false;
                if (attached) return true;
            }
        }

        return false;
    }

    private static boolean core(byte[] g, int w, int h, int l, int r, int y) {
        return core(g, w, h, l, r, y, null);
    }

    private static boolean core(
            byte[] g, int w, int h, int l, int r, int y, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (y < 0 || y >= h || l < 0 || r >= w) return false;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = l; x <= r; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if ((g[y * w + x] & 255) >= 170) return false;
            }
        }

        return true;
    }

    private static boolean rule(byte[] g, int w, int h, int left, int right, int y, float gap) {
        return rule(g, w, h, left, right, y, gap, null);
    }

    private static boolean rule(
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            int y,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int margin = (int) Math.ceil(gap), l = left - margin, r = right + margin;
        if (!core(g, w, h, l, r, y, quarterBudget)) return false;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int ordinal = 0; ordinal < 5; ordinal++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                boolean all = true;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int k = 0; k < 5; k++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int target = Math.round(y + (k - ordinal) * gap);
                        boolean supported = false;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int dy = -1; dy <= 1; dy++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                if (core(g, w, h, l, r, target + dy, quarterBudget))
                                    supported = true;
                            }
                        }

                        if (!supported) {
                            all = false;
                            break;
                        }
                    }
                }

                if (all) return true;
            }
        }

        return false;
    }
}
