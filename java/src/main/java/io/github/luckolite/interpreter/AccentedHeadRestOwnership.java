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
        var origin = RestFigureOrigins.origin(notes, note, g, w, h);
        if (origin == null
                || origin.originalGray == null
                || origin.originalGray.length != (long) w * h) return false;
        g = origin.originalGray;
        boolean[] rules = new boolean[line.length];
        for (int y = top; y <= scanBottom; y++) rules[y - top] = rule(g, w, h, left, right, y, gap);
        int margin = (int) Math.ceil(gap),
                l = Math.max(0, left - margin),
                r = (int) Math.min(w - 1L, (long) right + margin),
                t = Math.max(0, top - margin),
                b = (int) Math.min(h - 1L, (long) scanBottom + margin);
        long area = (long) (r - l + 1) * (b - t + 1);
        if (area > 1000000 || right - left > gap * 3 || maxY - minY > gap * 4) return false;
        int seed = -1;
        outer:
        for (int y = top; y <= scanBottom; y++)
            if (!rules[y - top])
                for (int x = left; x <= right; x++)
                    if ((g[y * w + x] & 255) < 170) {
                        seed = y * w + x;
                        break outer;
                    }
        if (seed < 0) return false;
        int stride = r - l + 1;
        boolean[] seen = new boolean[(int) area];
        int[] q = new int[(int) area];
        int take = 0, n = 1;
        q[0] = seed;
        seen[(seed / w - t) * stride + seed % w - l] = true;
        int al = seed % w, ar = al, at = seed / w, ab = at;
        while (take < n) {
            int z = q[take++], x = z % w, y = z / w;
            if (x == l || x == r || y == t || y == b) return false;
            al = Math.min(al, x);
            ar = Math.max(ar, x);
            at = Math.min(at, y);
            ab = Math.max(ab, y);
            for (int yy = y - 1; yy <= y + 1; yy++)
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    int a = (yy - t) * stride + xx - l;
                    if (!seen[a] && (g[yy * w + xx] & 255) < 170) {
                        seen[a] = true;
                        q[n++] = yy * w + xx;
                    }
                }
        }
        if (ar - al > gap * 2.5f
                || ab - at > gap * 1.5f
                || !OpenChevron.matches(q, w, al, at, ar, ab, n)) return false;
        int sl = w, sr = -1, sy = h;
        for (int y = top; y <= scanBottom; y++)
            if (!rules[y - top])
                for (int x = left; x <= right; x++)
                    if ((g[y * w + x] & 255) < 170 && !seen[(y - t) * stride + x - l]) {
                        sl = Math.min(sl, x);
                        sr = Math.max(sr, x);
                        sy = Math.min(sy, y);
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
        for (int y = sy; y <= end; y++) {
            boolean dark = false;
            for (int x = sl; x <= sr; x++)
                if ((g[y * w + x] & 255) < 170) {
                    dark = true;
                    if (origin.contains(x, y)) attached = true;
                }
            if (!dark) return false;
            if (attached) return true;
        }
        return false;
    }

    private static boolean core(byte[] g, int w, int h, int l, int r, int y) {
        if (y < 0 || y >= h || l < 0 || r >= w) return false;
        for (int x = l; x <= r; x++) if ((g[y * w + x] & 255) >= 170) return false;
        return true;
    }

    private static boolean rule(byte[] g, int w, int h, int left, int right, int y, float gap) {
        int margin = (int) Math.ceil(gap), l = left - margin, r = right + margin;
        if (!core(g, w, h, l, r, y)) return false;
        for (int ordinal = 0; ordinal < 5; ordinal++) {
            boolean all = true;
            for (int k = 0; k < 5; k++) {
                int target = Math.round(y + (k - ordinal) * gap);
                boolean supported = false;
                for (int dy = -1; dy <= 1; dy++)
                    if (core(g, w, h, l, r, target + dy)) supported = true;
                if (!supported) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }
}
