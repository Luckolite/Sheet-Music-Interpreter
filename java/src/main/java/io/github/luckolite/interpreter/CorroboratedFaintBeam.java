// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/**
 * Positive oval refinement and a faint extension of an independently complete
 * beam.
 */
final class CorroboratedFaintBeam {
    /**
     * Complete only the caller's outer strips across the existing coarse-grid phases. Every
     * proposal still passes the unchanged sparse oval/semantic-shaft grammar.
     */
    static List<SymbolStemHeadRecovery.Head> outerSparse(
            byte[] labels, byte[] gray, int w, int h, float gap, int top, int bottom) {
        List<SymbolStemHeadRecovery.Head> out = new ArrayList<>();
        if (!Float.isFinite(gap) || gap < 8 || gap > Math.min(w, h) / 8f || top > bottom)
            return out;
        int margin = (int) Math.ceil(gap * 1.4f), stride = Math.max(1, Math.round(gap * .14f));
        for (int phase = 0; phase < stride; phase++) {
            for (var head :
                    SymbolStemHeadRecovery.sparseOvals(
                            labels, gray, w, h, gap, top - margin + phase, top + 2 * margin - 1))
                if (head.y() >= top && head.y() < top + margin && head.y() <= bottom)
                    if (!out.contains(head)) out.add(head);
            for (var head :
                    SymbolStemHeadRecovery.sparseOvals(
                            labels,
                            gray,
                            w,
                            h,
                            gap,
                            bottom - 2 * margin + 1 + phase,
                            bottom + margin))
                if (head.y() > bottom - margin && head.y() <= bottom && head.y() >= top)
                    if (!out.contains(head)) out.add(head);
        }
        return out;
    }

    /**
     * Bounds retain the observed candidate extent; only the fitted oval center moves by at most
     * .15gap. The caller must supply an already validated sparse oval. No observed pixel/bound is
     * rewritten.
     */
    static SymbolStemHeadRecovery.Head refine(
            byte[] g, int w, int h, SymbolStemHeadRecovery.Head head, float gap) {
        if (g == null
                || head == null
                || w < 3
                || h < 3
                || g.length != (long) w * h
                || gap < 8
                || !Float.isFinite(gap)
                || gap > Math.min(w, h) / 8f
                || head.left() < 0
                || head.right() >= w
                || head.top() < 0
                || head.bottom() >= h
                || head.left() > head.right()
                || head.top() > head.bottom()
                || !Float.isFinite(head.x() + head.y())
                || head.x() < head.left()
                || head.x() > head.right()
                || head.y() < head.top()
                || head.y() > head.bottom()) return null;
        int radius = Math.max(1, Math.round(gap * .15f));
        SymbolStemHeadRecovery.Head best = null;
        int bestDistance = Integer.MAX_VALUE, bestScore = -1;
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++) {
                float x = head.x() + dx, y = head.y() + dy;
                if (x < head.left() || x > head.right() || y < head.top() || y > head.bottom())
                    continue;
                if (!FilledSparseBeamHead.matches(g, w, h, x, y, gap)) continue;
                int score = rim(g, w, h, x, y, gap);
                if (score < 39) continue;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance || distance == bestDistance && score > bestScore) {
                    best =
                            new SymbolStemHeadRecovery.Head(
                                    head.left(),
                                    head.top(),
                                    head.right(),
                                    head.bottom(),
                                    head.area(),
                                    x,
                                    y);
                    bestDistance = distance;
                    bestScore = score;
                }
            }
        return best;
    }

    private static int rim(byte[] g, int w, int h, float x, float y, float gap) {
        float rx = gap * .69f, ry = gap * .4f;
        int pad = Math.max(1, Math.round(gap * .07f));
        if (x - gap * 1.5 < 0 || x + gap * 1.5 >= w || y - gap * 1.5 < 0 || y + gap * 1.5 >= h)
            return 0;
        int score = 0;
        int[] q = new int[4];
        for (int k = 0; k < 48; k++) {
            double angle = k * Math.PI / 24;
            float u = rx * (float) Math.cos(angle), v = ry * (float) Math.sin(angle) - .4f * u;
            int xx = Math.round(x + u), yy = Math.round(y + v), ink = 255;
            for (int dy = -pad; dy <= pad; dy++)
                for (int dx = -pad; dx <= pad; dx++)
                    ink = Math.min(ink, g[(yy + dy) * w + xx + dx] & 255);
            int outer = g[Math.round(y + v * 1.6f) * w + Math.round(x + u * 1.6f)] & 255;
            if (ink < 205 && outer - ink >= 30) {
                score++;
                q[k / 12]++;
            }
        }
        for (int n : q) if (n < 8) return 0;
        return score;
    }

    static boolean connects(
            byte[] labels, byte[] g, int w, int h, int[] own, int[] a, int[] b, float gap) {
        if (labels == null
                || g == null
                || w < 3
                || h < 3
                || g.length != (long) w * h
                || labels.length != g.length
                || own == null
                || a == null
                || b == null
                || own.length != 3
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 8
                || gap > Math.min(w, h) / 8f
                || Math.abs(own[2]) != 1
                || own[2] != a[2]
                || a[2] != b[2]) return false;
        for (int[] p : new int[][] {own, a, b})
            if (p[0] < 1 || p[0] >= w - 1 || p[1] < 1 || p[1] >= h - 1) return false;
        if (!PrintedSingleBeamPath.connects(g, w, h, a, b, gap)
                || !FilledSparseBeamHead.rootedEnds(labels, g, w, h, a, b, gap)) return false;
        if (a[0] > b[0]) {
            int[] t = a;
            a = b;
            b = t;
        }
        if (own[0] >= a[0] && own[0] <= b[0]) return false;
        int[] near = own[0] < a[0] ? a : b;
        int span = Math.abs(near[0] - own[0]);
        if (span < gap * 2 || span > gap * 5) return false;
        float slope = (b[1] - a[1]) / (float) (b[0] - a[0]);
        if (Math.abs(slope) > .3f) return false;
        float projected = a[1] + slope * (own[0] - a[0]);
        if (Math.abs(own[1] - projected) > gap * .25f) return false;
        int left = Math.min(own[0], near[0]),
                right = Math.max(own[0], near[0]),
                margin = Math.max(3, Math.round(gap * .3f));
        int[] paper = new int[right - left + 1];
        for (int x = left + margin; x <= right - margin; x++) {
            int expected = Math.round(a[1] + slope * (x - a[0])), r = Math.round(gap);
            if (expected - r - 1 < 0 || expected + r + 1 >= h || x < 1 || x >= w - 1) return false;
            int[] up = new int[Math.max(3, Math.round(gap * .4f))], down = new int[up.length];
            for (int j = 0; j < up.length; j++) {
                up[j] = g[(expected - r + j) * w + x] & 255;
                down[j] = g[(expected + r - j) * w + x] & 255;
            }
            Arrays.sort(up);
            Arrays.sort(down);
            paper[x - left] = Math.min(up[up.length * 4 / 5], down[down.length * 4 / 5]);
            if (paper[x - left] < 225) return false;
        }
        for (int x = left; x < left + margin; x++) paper[x - left] = paper[margin];
        for (int x = right - margin + 1; x <= right; x++)
            paper[x - left] = paper[right - left - margin];
        int reach = Math.round(gap * .5f),
                radius = Math.round(gap),
                minimum = Math.max(4, (int) Math.ceil(gap * .25f));
        int
                low =
                        (int)
                                Math.floor(
                                        Math.min(
                                                        a[1] + slope * (left - a[0]),
                                                        a[1] + slope * (right - a[0]))
                                                - gap * 1.7f),
                high =
                        (int)
                                Math.ceil(
                                        Math.max(
                                                        a[1] + slope * (left - a[0]),
                                                        a[1] + slope * (right - a[0]))
                                                + gap * 1.7f);
        if (low < 1 || high >= h - 1) return false;
        int total = right - left - 2 * margin + 1;
        if (total < Math.round(gap * 1.5f)) return false;
        int[] prev = new int[high - low + 1];
        Arrays.fill(prev, -1);
        boolean first = true;
        int[] leftEnd = left == own[0] ? own : near, rightEnd = right == own[0] ? own : near;
        for (int x = left; x <= right; x++) {
            int expected = Math.round(a[1] + slope * (x - a[0])), background = paper[x - left];
            boolean body = x >= left + margin && x <= right - margin, any = false;
            int[] cur = new int[prev.length];
            Arrays.fill(cur, -1);
            for (int y = expected - reach; y <= expected + reach; y++) {
                if ((x == left && !insideEnd(y, leftEnd, gap))
                        || (x == right && !insideEnd(y, rightEnd, gap))) continue;
                if (y - radius < 0 || y + radius >= h || y - 1 < low || y + 1 > high) return false;
                if ((g[(y - 1) * w + x] & 255) >= background - 8
                        || (g[y * w + x] & 255) >= background - 8
                        || (g[(y + 1) * w + x] & 255) >= background - 8) continue;
                int top = y, bottom = y;
                while (top > y - radius && (g[(top - 1) * w + x] & 255) < background - 8) top--;
                while (bottom < y + radius && (g[(bottom + 1) * w + x] & 255) < background - 8)
                    bottom++;
                int thickness = bottom - top + 1;
                if (body && (thickness < minimum || thickness > gap * .8f)) continue;
                int j = y - low,
                        best = first ? 0 : Math.max(prev[j - 1], Math.max(prev[j], prev[j + 1]));
                if (best < 0) continue;
                int strongTop = y, strongBottom = y;
                if ((g[y * w + x] & 255) < background - 30) {
                    while (strongTop > top && (g[(strongTop - 1) * w + x] & 255) < background - 30)
                        strongTop--;
                    while (strongBottom < bottom
                            && (g[(strongBottom + 1) * w + x] & 255) < background - 30)
                        strongBottom++;
                }
                boolean strongHere =
                        (g[y * w + x] & 255) < background - 30
                                && strongBottom - strongTop + 1 >= minimum;
                cur[j] = best + (body && strongHere ? 1 : 0);
                any = true;
            }
            if (!any) return false;
            prev = cur;
            first = false;
        }
        return Arrays.stream(prev).max().orElse(-1) >= total * .75f;
    }

    private static boolean insideEnd(int y, int[] stem, float gap) {
        float distance = (y - stem[1]) * stem[2];
        return distance <= gap * .15f && distance >= -gap * .8f;
    }

    record Proof(
            int ownX,
            int ownY,
            int direction,
            int nearX,
            int nearY,
            int aX,
            int aY,
            int bX,
            int bY,
            float gap) {}

    static Proof prove(
            byte[] labels, byte[] gray, int w, int h, int[] own, int[] a, int[] b, float gap) {
        if (!connects(labels, gray, w, h, own, a, b, gap)) return null;
        int[] near = Math.abs(own[0] - a[0]) < Math.abs(own[0] - b[0]) ? a : b;
        return new Proof(own[0], own[1], own[2], near[0], near[1], a[0], a[1], b[0], b[1], gap);
    }

    /** A caller may exclude a small component only after separately ruling out its own shaft. */
    static boolean contains(
            Proof proof, byte[] g, int w, int h, int left, int top, int right, int bottom) {
        if (proof == null
                || g == null
                || w < 3
                || h < 3
                || g.length != (long) w * h
                || left < 1
                || right >= w - 1
                || top < 1
                || bottom >= h - 1
                || left > right
                || top > bottom) return false;
        float gap = proof.gap(),
                slope = (proof.bY() - proof.aY()) / (float) (proof.bX() - proof.aX());
        int margin = Math.max(3, Math.round(gap * .3f)), radius = Math.round(gap);
        if (left < Math.min(proof.ownX(), proof.nearX()) + margin
                || right > Math.max(proof.ownX(), proof.nearX()) - margin) return false;
        for (int x = left; x <= right; x++) {
            int expected = Math.round(proof.aY() + slope * (x - proof.aX()));
            if (expected - radius < 0
                    || expected + radius >= h
                    || (proof.direction() > 0
                            ? (top < expected - gap * .9f || bottom > expected + gap * .15f)
                            : (top < expected - gap * .15f || bottom > expected + gap * .9f)))
                return false;
            int[] up = new int[Math.max(3, Math.round(gap * .4f))], down = new int[up.length];
            for (int j = 0; j < up.length; j++) {
                up[j] = g[(expected - radius + j) * w + x] & 255;
                down[j] = g[(expected + radius - j) * w + x] & 255;
            }
            Arrays.sort(up);
            Arrays.sort(down);
            int paper = Math.min(up[up.length * 4 / 5], down[down.length * 4 / 5]);
            if (paper < 225) return false;
            int limit = paper - 8;
            for (int y = top; y <= bottom; y++) if ((g[y * w + x] & 255) >= limit) return false;
            int t = top, b = bottom;
            while (t > expected - radius && (g[(t - 1) * w + x] & 255) < limit) t--;
            while (b < expected + radius && (g[(b + 1) * w + x] & 255) < limit) b++;
            if (b - t + 1 < Math.max(4, Math.ceil(gap * .25f)) || b - t + 1 > gap * .8f)
                return false;
        }
        return true;
    }
}
