// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Preservation-only compact filled-body evidence; does not create notes or prove beams. */
final class CompactFilledHeadCore {
    private CompactFilledHeadCore() {}

    static boolean isFilled(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float cx,
            float cy,
            float gap) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != labels.length
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 64
                || !Float.isFinite(cx)
                || !Float.isFinite(cy)
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || left > right
                || top > bottom
                || cx < left
                || cx > right
                || cy < top
                || cy > bottom) return false;
        int bw = right - left + 1, bh = bottom - top + 1;
        if (bw < gap * .7f
                || bw > gap * 1.15f
                || bh < gap * .5f
                || bh > gap * .9f
                || bw < bh * 1.1f
                || bw > bh * 2f) return false;
        int margin = Math.round(gap), inner = Math.max(2, Math.round(gap * .5f));
        if (left - margin < 0 || (long) right + margin >= width || top < 2 || bottom + 2 >= height)
            return false;
        int[] rowLeft = new int[bh], rowRight = new int[bh], histogram = new int[256];
        // Reuse the full-size law's finite local-paper estimate; isolated staff rows
        // cannot turn the exterior paper into ink. The separate closed-counter veto
        // below preserves locally shadowed hollow interiors.
        int paperCount = 0;
        for (int y = Math.max(0, top - margin); y <= Math.min(height - 1, bottom + margin); y++)
            for (int x = left - margin; x <= right + margin; x++) {
                histogram[gray[y * width + x] & 255]++;
                paperCount++;
            }
        int exteriorPaper = 255, paperSeen = 0;
        for (int value = 0; value < 256; value++)
            if ((paperSeen += histogram[value]) >= paperCount * .8f) {
                exteriorPaper = value;
                break;
            }
        if (exteriorPaper < 210) return false;
        java.util.Arrays.fill(rowLeft, exteriorPaper);
        java.util.Arrays.fill(rowRight, exteriorPaper);
        boolean[] seen = new boolean[bw * bh];
        int[] queue = new int[bw * bh];
        int total = 0, seed = -1;
        int[] quadrants = new int[4];
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                if (labels[y * width + x] != 2) continue;
                total++;
                seed = (y - top) * bw + x - left;
                float paper =
                        localPaper(
                                rowLeft[y - top], rowRight[y - top], x, left, right, margin, inner);
                if ((gray[y * width + x] & 255) <= paper - 25)
                    quadrants[(x >= cx ? 1 : 0) + (y >= cy ? 2 : 0)]++;
            }
        if (total < bw * bh * .4f || seed < 0) return false;
        for (int q : quadrants) if (q < 2) return false;
        int first = 0, last = 1;
        queue[0] = seed;
        seen[seed] = true;
        while (first < last) {
            int at = queue[first++], x = at % bw, y = at / bw;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= bw || yy < 0 || yy >= bh) continue;
                    int next = yy * bw + xx;
                    if (!seen[next] && labels[(top + yy) * width + left + xx] == 2) {
                        seen[next] = true;
                        queue[last++] = next;
                    }
                }
        }
        if (last != total) return false;
        // A local paper shadow can be darker than the exterior. Preserve an enclosed
        // relative-light counter even if the ordinary pale-core density would pass.
        java.util.Arrays.fill(histogram, 0);
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) histogram[gray[y * width + x] & 255]++;
        int dark = 0, accumulated = 0;
        for (; dark < 255; dark++) if ((accumulated += histogram[dark]) >= bw * bh * .1f) break;
        int paperSum = 0;
        for (int y = 0; y < bh; y++) paperSum += rowLeft[y] + rowRight[y];
        int counterThreshold = Math.round((dark + paperSum / (2f * bh)) * .5f);
        java.util.Arrays.fill(seen, false);
        int enclosed = 0, central = 0;
        boolean[] counterRows = new boolean[bh];
        for (int seedIndex = 0; seedIndex < bw * bh; seedIndex++) {
            if (seen[seedIndex]
                    || (gray[(top + seedIndex / bw) * width + left + seedIndex % bw] & 255)
                            < counterThreshold) continue;
            first = 0;
            last = 1;
            queue[0] = seedIndex;
            seen[seedIndex] = true;
            boolean edge = false;
            while (first < last) {
                int at = queue[first++], x = at % bw, y = at / bw;
                edge |= x == 0 || y == 0 || x == bw - 1 || y == bh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= bw || yy < 0 || yy >= bh) continue;
                        int next = yy * bw + xx;
                        if (!seen[next]
                                && (gray[(top + yy) * width + left + xx] & 255)
                                        >= counterThreshold) {
                            seen[next] = true;
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
                    counterRows[y] = true;
                }
            }
        }
        int rows = 0;
        for (boolean row : counterRows) if (row) rows++;
        if (enclosed >= Math.max(4, Math.round(bw * bh * .03f)) && central >= 3 && rows >= 2)
            return false;
        int count = 0, filled = 0, pale = 0;
        for (int y = Math.round(cy - bh * .25f); y <= Math.round(cy + bh * .25f); y++)
            for (int x = Math.round(cx - bw * .2f); x <= Math.round(cx + bw * .2f); x++) {
                if (x < left || x > right || y < top || y > bottom) return false;
                int value = gray[y * width + x] & 255;
                float paper =
                        localPaper(
                                rowLeft[y - top], rowRight[y - top], x, left, right, margin, inner);
                count++;
                if (value <= paper - 25) filled++;
                if (value >= 145 && value <= paper - 25) pale++;
            }
        // Identical contrast, coverage and pale fraction to the existing full-size law.
        return count >= 12 && filled >= count * .95f && pale >= count * .5f;
    }

    private static float localPaper(
            int a, int b, int x, int left, int right, int margin, int inner) {
        float lx = left - (margin + inner) * .5f, rx = right + (margin + inner) * .5f;
        return a + (b - a) * ((x - lx) / (rx - lx));
    }

    private static int paper(byte[] gray, int width, int left, int right, int y, int[] histogram) {
        java.util.Arrays.fill(histogram, 0);
        int count = 0;
        for (int yy = y - 2; yy <= y + 2; yy++)
            for (int x = left; x <= right; x++) {
                histogram[gray[yy * width + x] & 255]++;
                count++;
            }
        int seen = 0;
        for (int value = 0; value < 256; value++)
            if ((seen += histogram[value]) >= count * .8f) return value;
        return 255;
    }
}
