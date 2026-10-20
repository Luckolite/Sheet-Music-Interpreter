// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Additive raw rectangle proof when a global staff mask erased the rest body. */
final class HalfRestRuleBody {
    private HalfRestRuleBody() {}

    static int[] find(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk) {
        return find(gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, false);
    }

    static int[] hanging(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk) {
        return find(gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, true);
    }

    private static int[] find(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk,
            boolean hanging) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || !Float.isFinite(staffTop)
                || gap < 4
                || left < 0
                || right >= width
                || left > right
                || maskedInk == null
                || bandTop < 0
                || bandTop + maskedInk.length > height) return null;
        int w = right - left + 1;
        if (w < gap * .7f || w > gap * 1.6f) return null;
        float middle = staffTop + (hanging ? gap : 2 * gap);
        int direction = hanging ? 1 : -1;
        for (int rule = Math.max(1, (int) Math.ceil(middle - gap * .25f));
                rule <= Math.min(height - 2, (int) Math.floor(middle + gap * .25f));
                rule++) {
            if (!supportedRule(gray, width, height, left, right, rule, gap)
                    && !(hanging
                            && supportedRule(gray, width, height, left, right, rule - 1, gap)
                            && supportedRuleEdge(gray, width, left, right, rule, gap))) continue;
            int first = rule;
            while (first + direction >= 0
                    && first + direction < height
                    && Math.abs(rule - first) < gap * .7f
                    && rowInk(gray, width, left, right, first + direction) >= w * .80f)
                first += direction;
            int rows = Math.abs(rule - first),
                    minY = hanging ? rule + 1 : first,
                    maxY = hanging ? first : rule - 1;
            int fringeY = first + direction;
            if (rows < Math.max(3, Math.round(gap * .25f))
                    || rows > gap * .65f
                    || minY <= bandTop
                    || hanging && maxY >= bandTop + maskedInk.length - 1
                    || fringeY < 0
                    || fringeY >= height) continue;
            int fringe = rowInk(gray, width, left, right, fringeY);
            if (fringe > w * .45f) continue;
            int margin = Math.max(2, Math.round(gap * .25f));
            boolean clear = true;
            if (left - margin < 0 || right + margin >= width) continue;
            for (int y = minY; y <= maxY && clear; y++) {
                float fringeLimit =
                        y == rule + direction
                                ? Math.max(margin * .2f, Math.max(1, Math.round(gap * .10f)))
                                : margin * .2f;
                boolean firstHangingRow = hanging && y == rule + 1;
                if (sideInk(gray, width, left - margin, left - 1, y, firstHangingRow) > fringeLimit
                        || sideInk(gray, width, right + 1, right + margin, y, firstHangingRow)
                                > fringeLimit) clear = false;
            }
            if (!clear) continue;
            int outside = 0;
            for (int i = 0; i < maskedInk.length; i++) {
                int y = bandTop + i;
                if (y >= minY && y <= maxY || y == fringeY && maskedInk[i] <= w * .45f) continue;
                if (maskedInk[i] > 0
                        && !thinRuleFragment(gray, width, height, left, right, y, staffTop, gap))
                    outside += maskedInk[i];
            }
            if (outside <= Math.max(1, Math.round(gap * .12f))) return new int[] {minY, maxY};
        }
        return null;
    }

    static int[] findSupportedCore(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk) {
        return standingSupportedCore(
                gray, width, height, staffTop, gap, left, right, bandTop, maskedInk);
    }

    /** A standing plate attaches to the body-facing edge of the complete observed rule core. */
    private static int[] standingSupportedCore(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk) {
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || !Float.isFinite(gap)
                || !Float.isFinite(staffTop)
                || gap < 4
                || gap > 64
                || left < 0
                || right >= width
                || left > right
                || maskedInk == null
                || bandTop < 0
                || (long) bandTop + maskedInk.length > height
                || maskedInk.length > Math.ceil(4.5d * gap) + 4) return null;
        int w = right - left + 1;
        if (w < gap * .7f || w > gap * 1.6f) return null;
        float middle = staffTop + 2 * gap;
        for (int seed = Math.max(1, (int) Math.ceil(middle - gap * .25f));
                seed <= Math.min(height - 2, (int) Math.floor(middle + gap * .25f));
                seed++) {
            if (!supportedRule(gray, width, height, left, right, seed, gap)) continue;
            int[] core = completeSupportedCore(gray, width, height, left, right, seed, gap);
            if (core == null) continue;
            int rule = core[0], first = rule;
            while (first > 0
                    && rule - first < gap * .7f
                    && rowInk(gray, width, left, right, first - 1) >= w * .80f) first--;
            int rows = rule - first, minY = first, maxY = rule - 1, fringeY = first - 1;
            if (rows < Math.max(3, Math.round(gap * .25f))
                    || rows > gap * .65f
                    || minY <= bandTop
                    || fringeY < 0) continue;
            if (rowInk(gray, width, left, right, fringeY) > w * .45f) continue;
            int margin = Math.max(2, Math.round(gap * .25f));
            if (left - margin < 0 || right + margin >= width) continue;
            boolean clear = true;
            for (int y = minY; y <= maxY && clear; y++) {
                boolean attached = y == rule - 1;
                float limit =
                        attached
                                ? Math.max(margin * .2f, Math.max(1, Math.round(gap * .10f)))
                                : margin * .2f;
                if (standingSideInk(gray, width, left - margin, left - 1, y, attached) > limit
                        || standingSideInk(gray, width, right + 1, right + margin, y, attached)
                                > limit) clear = false;
            }
            if (!clear) continue;
            int supportFringe = core[1] + 1;
            boolean faded =
                    fadingSupportEdge(
                            gray, width, height, left - margin, right + margin, supportFringe);
            int outside = 0;
            for (int i = 0; i < maskedInk.length; i++) {
                int y = bandTop + i;
                if (y >= minY && y <= maxY
                        || y >= core[0] && y <= core[1]
                        || y == fringeY && maskedInk[i] <= w * .45f
                        || y == supportFringe && faded) continue;
                if (maskedInk[i] > 0
                        && !thinRuleFragment(gray, width, height, left, right, y, staffTop, gap))
                    outside += maskedInk[i];
            }
            if (outside <= Math.max(1, Math.round(gap * .12f))) return new int[] {minY, maxY};
        }
        return null;
    }

    /** Refuse an overlong core rather than retaining a favourable truncated interval. */
    private static int[] completeSupportedCore(
            byte[] g, int w, int h, int left, int right, int seed, float gap) {
        int first = seed, last = seed, max = Math.max(2, Math.round(gap * .30f));
        while (first > 0 && supportedRule(g, w, h, left, right, first - 1, gap)) {
            if (last - first + 1 >= max) return null;
            first--;
        }
        while (last < h - 1 && supportedRule(g, w, h, left, right, last + 1, gap)) {
            if (last - first + 1 >= max) return null;
            last++;
        }
        return new int[] {first, last};
    }

    /** Exact mirror of the released hanging-rest first-body-row fade grammar. */
    private static int standingSideInk(
            byte[] g, int w, int left, int right, int y, boolean attached) {
        int unexplained = 0;
        for (int x = left; x <= right; x++)
            if (dark(g, w, x, y)) {
                int tone = g[y * w + x] & 255;
                if (attached
                        && tone >= (g[(y + 1) * w + x] & 255) + 24
                        && (g[(y - 1) * w + x] & 255) >= tone + 24) continue;
                unexplained++;
            }
        return unexplained;
    }

    /** Only one literal row beyond the complete support core; no farther foreground is licensed. */
    private static boolean fadingSupportEdge(byte[] g, int w, int h, int left, int right, int y) {
        if (y <= 0 || y >= h - 1) return false;
        boolean observed = false;
        for (int x = left; x <= right; x++)
            if (dark(g, w, x, y)) {
                observed = true;
                int tone = g[y * w + x] & 255;
                if (tone < (g[(y - 1) * w + x] & 255) + 24
                        || (g[(y + 1) * w + x] & 255) < tone + 24) return false;
            }
        return observed;
    }

    private static boolean thinRuleFragment(
            byte[] g, int w, int h, int left, int right, int y, float top, float gap) {
        float expected = top + Math.round((y - top) / gap) * gap;
        if (Math.abs(y - expected) > gap * .35f) return false;
        boolean supported = false;
        for (int at = Math.max(0, y - 1); at <= Math.min(h - 1, y + 1); at++)
            supported |= supportedRule(g, w, h, left, right, at, gap);
        if (!supported) return false;
        int max = Math.max(2, Math.round(gap * .30f));
        for (int x = left; x <= right; x++)
            if (dark(g, w, x, y)) {
                int first = y, last = y;
                while (first > 0 && y - first < max && dark(g, w, x, first - 1)) first--;
                while (last < h - 1 && last - y < max && dark(g, w, x, last + 1)) last++;
                if (last - first + 1 > max) return false;
            }
        return true;
    }

    private static boolean supportedRule(
            byte[] g, int w, int h, int left, int right, int y, float gap) {
        int span = Math.max(4, Math.round(gap * 1.6f));
        if (y < 0 || y >= h || left - span < 0 || right + span >= w) return false;
        return rowInk(g, w, left - span, left - 1, y) >= span * .90f
                && rowInk(g, w, right + 1, right + span, y) >= span * .90f
                && rowInk(g, w, left, right, y) >= (right - left + 1) * .90f;
    }

    private static boolean supportedRuleEdge(
            byte[] g, int w, int left, int right, int y, float gap) {
        int span = Math.max(4, Math.round(gap * 1.6f));
        return left >= span
                && right + span < w
                && rowInk(g, w, left - span, left - 1, y) >= span * .50f
                && rowInk(g, w, right + 1, right + span, y) >= span * .50f
                && rowInk(g, w, left, right, y) >= (right - left + 1) * .90f;
    }

    /** A rule's blurred edge fades toward paper on the first attached body row only. */
    private static int sideInk(
            byte[] g, int w, int left, int right, int y, boolean firstHangingRow) {
        int unexplained = 0;
        for (int x = left; x <= right; x++)
            if (dark(g, w, x, y)) {
                int tone = g[y * w + x] & 255;
                if (firstHangingRow
                        && tone >= (g[(y - 1) * w + x] & 255) + 24
                        && (g[(y + 1) * w + x] & 255) >= tone + 24) continue;
                unexplained++;
            }
        return unexplained;
    }

    private static int rowInk(byte[] g, int w, int left, int right, int y) {
        int n = 0;
        for (int x = left; x <= right; x++) if (dark(g, w, x, y)) n++;
        return n;
    }

    private static boolean dark(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) < 170;
    }
}
