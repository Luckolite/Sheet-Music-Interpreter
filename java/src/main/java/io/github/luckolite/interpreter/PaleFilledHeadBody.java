// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Existing pale-filled photometry, contradicted by any complete relative-light counter.
 * The caller must independently own the observed note body and finite shaft.
 */
final class PaleFilledHeadBody {
    private PaleFilledHeadBody() {}

    static boolean isFilled(
            byte[] g, int w, int h, int l, int t, int r, int b, float cx, float cy, float gap) {
        return isFilled(g, w, h, l, t, r, b, cx, cy, gap, new FiniteHollowShaftCap.Budget(131072));
    }

    static boolean isFilled(
            byte[] g,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            float cx,
            float cy,
            float gap,
            FiniteHollowShaftCap.Budget budget) {
        if (g == null
                || w <= 0
                || h <= 0
                || g.length != (long) w * h
                || budget == null
                || !Float.isFinite(cx + cy + gap)
                || gap < 6
                || gap > 64
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || l > r
                || t > b
                || cx < l
                || cx > r
                || cy < t
                || cy > b) return false;
        int bw = r - l + 1, bh = b - t + 1, margin = Math.round(gap);
        if (bw < gap * 1.05f
                || bh < gap * .75f
                || bw > gap * 1.9f
                || bh > gap * 1.4f
                || (long) bw * bh > 4096
                || l < margin
                || r + margin >= w
                || t < margin
                || b + margin >= h) return false;
        long window = (long) (bw + 2 * margin) * (bh + 2 * margin), area = (long) bw * bh;
        // Includes the unchanged inherited Pale call, its core and all relative-counter reads.
        long charge = 2 * window + 12 * area;
        if (charge > 131072) {
            budget.refuse();
            return false;
        }
        if (!budget.debit(charge)) return false;
        if (!PaleFilledHeadCore.isFilled(g, w, h, l, t, r, b, cx, cy, gap)) return false;
        int[] exterior = new int[256], inside = new int[256];
        int all = 0;
        for (int y = t - margin; y <= b + margin; y++)
            for (int x = l - margin; x <= r + margin; x++) {
                int value = g[y * w + x] & 255;
                exterior[value]++;
                all++;
                if (x >= l && x <= r && y >= t && y <= b) inside[value]++;
            }
        int paper = 255, seen = 0;
        for (int value = 0; value < 256; value++)
            if ((seen += exterior[value]) >= all * .8f) {
                paper = value;
                break;
            }
        int dark = 0;
        seen = 0;
        for (; dark < 255; dark++) if ((seen += inside[dark]) >= area * .1f) break;
        int threshold = Math.round((dark + paper) * .5f);
        boolean[] visited = new boolean[(int) area], rows = new boolean[bh];
        int[] queue = new int[(int) area];
        int enclosed = 0, central = 0;
        for (int seed = 0; seed < area; seed++) {
            if (visited[seed] || (g[(t + seed / bw) * w + l + seed % bw] & 255) < threshold)
                continue;
            int first = 0, last = 1;
            queue[0] = seed;
            visited[seed] = true;
            boolean edge = false;
            while (first < last) {
                int at = queue[first++], x = at % bw, y = at / bw;
                edge |= x == 0 || x == bw - 1 || y == 0 || y == bh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= bw || yy < 0 || yy >= bh) continue;
                        int next = yy * bw + xx;
                        if (!visited[next] && (g[(t + yy) * w + l + xx] & 255) >= threshold) {
                            visited[next] = true;
                            queue[last++] = next;
                        }
                    }
            }
            if (edge || last < 2) continue;
            enclosed += last;
            for (int i = 0; i < last; i++) {
                int x = queue[i] % bw, y = queue[i] / bw;
                if (Math.abs(x - (bw - 1) * .5f) <= bw * .3f
                        && Math.abs(y - (bh - 1) * .5f) <= bh * .35f) {
                    central++;
                    rows[y] = true;
                }
            }
        }
        int rowCount = 0;
        for (boolean row : rows) if (row) rowCount++;
        return !(enclosed >= Math.max(4, Math.round(area * .03f)) && central >= 3 && rowCount >= 2);
    }
}
