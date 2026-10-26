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
        return find(gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, null);
    }

    static int[] find(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return find(
                gray,
                width,
                height,
                staffTop,
                gap,
                left,
                right,
                bandTop,
                maskedInk,
                false,
                quarterBudget);
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
        return hanging(gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, null);
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
            int[] maskedInk,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return find(
                gray,
                width,
                height,
                staffTop,
                gap,
                left,
                right,
                bandTop,
                maskedInk,
                true,
                quarterBudget);
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
        return find(
                gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, hanging, null);
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
            boolean hanging,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

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
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int rule = Math.max(1, (int) Math.ceil(middle - gap * .25f));
                    rule <= Math.min(height - 2, (int) Math.floor(middle + gap * .25f));
                    rule++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!supportedRule(gray, width, height, left, right, rule, gap, quarterBudget)
                        && !(hanging
                                && supportedRule(
                                        gray,
                                        width,
                                        height,
                                        left,
                                        right,
                                        rule - 1,
                                        gap,
                                        quarterBudget)
                                && supportedRuleEdge(
                                        gray, width, left, right, rule, gap, quarterBudget)))
                    continue;
                int first = rule;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    while (first + direction >= 0
                            && first + direction < height
                            && Math.abs(rule - first) < gap * .7f
                            && rowInk(gray, width, left, right, first + direction, quarterBudget)
                                    >= w * .80f) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        first += direction;
                    }
                }

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
                int fringe = rowInk(gray, width, left, right, fringeY, quarterBudget);
                if (fringe > w * .45f) continue;
                int margin = Math.max(2, Math.round(gap * .25f));
                boolean clear = true;
                if (left - margin < 0 || right + margin >= width) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int y = minY; y <= maxY && clear; y++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        float fringeLimit =
                                y == rule + direction
                                        ? Math.max(
                                                margin * .2f, Math.max(1, Math.round(gap * .10f)))
                                        : margin * .2f;
                        boolean firstHangingRow = hanging && y == rule + 1;
                        if (sideInk(
                                                gray,
                                                width,
                                                left - margin,
                                                left - 1,
                                                y,
                                                firstHangingRow,
                                                quarterBudget)
                                        > fringeLimit
                                || sideInk(
                                                gray,
                                                width,
                                                right + 1,
                                                right + margin,
                                                y,
                                                firstHangingRow,
                                                quarterBudget)
                                        > fringeLimit) clear = false;
                    }
                }

                if (!clear) continue;
                int outside = 0;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int i = 0; i < maskedInk.length; i++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int y = bandTop + i;
                        if (y >= minY && y <= maxY || y == fringeY && maskedInk[i] <= w * .45f)
                            continue;
                        if (maskedInk[i] > 0
                                && !thinRuleFragment(
                                        gray,
                                        width,
                                        height,
                                        left,
                                        right,
                                        y,
                                        staffTop,
                                        gap,
                                        quarterBudget)) outside += maskedInk[i];
                    }
                }

                if (outside <= Math.max(1, Math.round(gap * .12f)))
                    return (quarterBudget == null
                                    || quarterBudget.allocate(
                                            (Math.max(0L, (long) (2))) + 1,
                                            32L * 1 + 8L * (Math.max(0L, (long) (2))))
                            ? new int[] {minY, maxY}
                            : null);
            }
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
        return findSupportedCore(
                gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, null);
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
            int[] maskedInk,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return standingSupportedCore(
                gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, quarterBudget);
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
        return standingSupportedCore(
                gray, width, height, staffTop, gap, left, right, bandTop, maskedInk, null);
    }

    private static int[] standingSupportedCore(
            byte[] gray,
            int width,
            int height,
            float staffTop,
            float gap,
            int left,
            int right,
            int bandTop,
            int[] maskedInk,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

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
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int seed = Math.max(1, (int) Math.ceil(middle - gap * .25f));
                    seed <= Math.min(height - 2, (int) Math.floor(middle + gap * .25f));
                    seed++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!supportedRule(gray, width, height, left, right, seed, gap, quarterBudget))
                    continue;
                int[] core =
                        completeSupportedCore(
                                gray, width, height, left, right, seed, gap, quarterBudget);
                if (core == null) continue;
                int rule = core[0], first = rule;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    while (first > 0
                            && rule - first < gap * .7f
                            && rowInk(gray, width, left, right, first - 1, quarterBudget)
                                    >= w * .80f) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        first--;
                    }
                }

                int rows = rule - first, minY = first, maxY = rule - 1, fringeY = first - 1;
                if (rows < Math.max(3, Math.round(gap * .25f))
                        || rows > gap * .65f
                        || minY <= bandTop
                        || fringeY < 0) continue;
                if (rowInk(gray, width, left, right, fringeY, quarterBudget) > w * .45f) continue;
                int margin = Math.max(2, Math.round(gap * .25f));
                if (left - margin < 0 || right + margin >= width) continue;
                boolean clear = true;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int y = minY; y <= maxY && clear; y++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        boolean attached = y == rule - 1;
                        float limit =
                                attached
                                        ? Math.max(
                                                margin * .2f, Math.max(1, Math.round(gap * .10f)))
                                        : margin * .2f;
                        if (standingSideInk(
                                                gray,
                                                width,
                                                left - margin,
                                                left - 1,
                                                y,
                                                attached,
                                                quarterBudget)
                                        > limit
                                || standingSideInk(
                                                gray,
                                                width,
                                                right + 1,
                                                right + margin,
                                                y,
                                                attached,
                                                quarterBudget)
                                        > limit) clear = false;
                    }
                }

                if (!clear) continue;
                int supportFringe = core[1] + 1;
                boolean faded =
                        fadingSupportEdge(
                                gray,
                                width,
                                height,
                                left - margin,
                                right + margin,
                                supportFringe,
                                quarterBudget);
                int outside = 0;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int i = 0; i < maskedInk.length; i++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int y = bandTop + i;
                        if (y >= minY && y <= maxY
                                || y >= core[0] && y <= core[1]
                                || y == fringeY && maskedInk[i] <= w * .45f
                                || y == supportFringe && faded) continue;
                        if (maskedInk[i] > 0
                                && !thinRuleFragment(
                                        gray,
                                        width,
                                        height,
                                        left,
                                        right,
                                        y,
                                        staffTop,
                                        gap,
                                        quarterBudget)) outside += maskedInk[i];
                    }
                }

                if (outside <= Math.max(1, Math.round(gap * .12f)))
                    return (quarterBudget == null
                                    || quarterBudget.allocate(
                                            (Math.max(0L, (long) (2))) + 1,
                                            32L * 1 + 8L * (Math.max(0L, (long) (2))))
                            ? new int[] {minY, maxY}
                            : null);
            }
        }

        return null;
    }

    /** Refuse an overlong core rather than retaining a favourable truncated interval. */
    private static int[] completeSupportedCore(
            byte[] g, int w, int h, int left, int right, int seed, float gap) {
        return completeSupportedCore(g, w, h, left, right, seed, gap, null);
    }

    private static int[] completeSupportedCore(
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            int seed,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int first = seed, last = seed, max = Math.max(2, Math.round(gap * .30f));
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (first > 0
                    && supportedRule(g, w, h, left, right, first - 1, gap, quarterBudget)) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (last - first + 1 >= max) return null;
                first--;
            }
        }

        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (last < h - 1
                    && supportedRule(g, w, h, left, right, last + 1, gap, quarterBudget)) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (last - first + 1 >= max) return null;
                last++;
            }
        }

        return (quarterBudget == null
                        || quarterBudget.allocate(
                                (Math.max(0L, (long) (2))) + 1,
                                32L * 1 + 8L * (Math.max(0L, (long) (2))))
                ? new int[] {first, last}
                : null);
    }

    /** Exact mirror of the released hanging-rest first-body-row fade grammar. */
    private static int standingSideInk(
            byte[] g, int w, int left, int right, int y, boolean attached) {
        return standingSideInk(g, w, left, right, y, attached, null);
    }

    private static int standingSideInk(
            byte[] g,
            int w,
            int left,
            int right,
            int y,
            boolean attached,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int unexplained = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = left; x <= right; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (dark(g, w, x, y, quarterBudget)) {
                    int tone = g[y * w + x] & 255;
                    if (attached
                            && tone >= (g[(y + 1) * w + x] & 255) + 24
                            && (g[(y - 1) * w + x] & 255) >= tone + 24) continue;
                    unexplained++;
                }
            }
        }

        return unexplained;
    }

    /** Only one literal row beyond the complete support core; no farther foreground is licensed. */
    private static boolean fadingSupportEdge(byte[] g, int w, int h, int left, int right, int y) {
        return fadingSupportEdge(g, w, h, left, right, y, null);
    }

    private static boolean fadingSupportEdge(
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            int y,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (y <= 0 || y >= h - 1) return false;
        boolean observed = false;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = left; x <= right; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (dark(g, w, x, y, quarterBudget)) {
                    observed = true;
                    int tone = g[y * w + x] & 255;
                    if (tone < (g[(y - 1) * w + x] & 255) + 24
                            || (g[(y + 1) * w + x] & 255) < tone + 24) return false;
                }
            }
        }

        return observed;
    }

    private static boolean thinRuleFragment(
            byte[] g, int w, int h, int left, int right, int y, float top, float gap) {
        return thinRuleFragment(g, w, h, left, right, y, top, gap, null);
    }

    private static boolean thinRuleFragment(
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            int y,
            float top,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        float expected = top + Math.round((y - top) / gap) * gap;
        if (Math.abs(y - expected) > gap * .35f) return false;
        boolean supported = false;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int at = Math.max(0, y - 1); at <= Math.min(h - 1, y + 1); at++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                supported |= supportedRule(g, w, h, left, right, at, gap, quarterBudget);
            }
        }

        if (!supported) return false;
        int max = Math.max(2, Math.round(gap * .30f));
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = left; x <= right; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (dark(g, w, x, y, quarterBudget)) {
                    int first = y, last = y;
                    {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        while (first > 0
                                && y - first < max
                                && dark(g, w, x, first - 1, quarterBudget)) {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            first--;
                        }
                    }

                    {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        while (last < h - 1
                                && last - y < max
                                && dark(g, w, x, last + 1, quarterBudget)) {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            last++;
                        }
                    }

                    if (last - first + 1 > max) return false;
                }
            }
        }

        return true;
    }

    private static boolean supportedRule(
            byte[] g, int w, int h, int left, int right, int y, float gap) {
        return supportedRule(g, w, h, left, right, y, gap, null);
    }

    private static boolean supportedRule(
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            int y,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int span = Math.max(4, Math.round(gap * 1.6f));
        if (y < 0 || y >= h || left - span < 0 || right + span >= w) return false;
        return rowInk(g, w, left - span, left - 1, y, quarterBudget) >= span * .90f
                && rowInk(g, w, right + 1, right + span, y, quarterBudget) >= span * .90f
                && rowInk(g, w, left, right, y, quarterBudget) >= (right - left + 1) * .90f;
    }

    private static boolean supportedRuleEdge(
            byte[] g, int w, int left, int right, int y, float gap) {
        return supportedRuleEdge(g, w, left, right, y, gap, null);
    }

    private static boolean supportedRuleEdge(
            byte[] g,
            int w,
            int left,
            int right,
            int y,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int span = Math.max(4, Math.round(gap * 1.6f));
        return left >= span
                && right + span < w
                && rowInk(g, w, left - span, left - 1, y, quarterBudget) >= span * .50f
                && rowInk(g, w, right + 1, right + span, y, quarterBudget) >= span * .50f
                && rowInk(g, w, left, right, y, quarterBudget) >= (right - left + 1) * .90f;
    }

    /** A rule's blurred edge fades toward paper on the first attached body row only. */
    private static int sideInk(
            byte[] g, int w, int left, int right, int y, boolean firstHangingRow) {
        return sideInk(g, w, left, right, y, firstHangingRow, null);
    }

    private static int sideInk(
            byte[] g,
            int w,
            int left,
            int right,
            int y,
            boolean firstHangingRow,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int unexplained = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = left; x <= right; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (dark(g, w, x, y, quarterBudget)) {
                    int tone = g[y * w + x] & 255;
                    if (firstHangingRow
                            && tone >= (g[(y - 1) * w + x] & 255) + 24
                            && (g[(y + 1) * w + x] & 255) >= tone + 24) continue;
                    unexplained++;
                }
            }
        }

        return unexplained;
    }

    private static int rowInk(byte[] g, int w, int left, int right, int y) {
        return rowInk(g, w, left, right, y, null);
    }

    private static int rowInk(
            byte[] g, int w, int left, int right, int y, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int n = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = left; x <= right; x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (dark(g, w, x, y, quarterBudget)) n++;
            }
        }

        return n;
    }

    private static boolean dark(byte[] g, int w, int x, int y) {
        return dark(g, w, x, y, null);
    }

    private static boolean dark(
            byte[] g, int w, int x, int y, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return (g[y * w + x] & 255) < 170;
    }
}
