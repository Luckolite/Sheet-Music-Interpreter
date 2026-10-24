// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Set;

/** PRIVATE foreground-only partitions of complete raw cross-sections.
 * The caller must independently prove the staff stripe; a search axis supplies
 * no pixel authority. Every returned pixel was already in its complete raw run.
 * All width alternatives consistent with exposed body widths are intersected.
 */
final class RawStaffBarPartition {
    private RawStaffBarPartition() {}

    private static boolean take(long[] b, long n) {
        if (b[0] < n) {
            b[0] = -1;
            return false;
        }
        b[0] -= n;
        return true;
    }

    static boolean apply(
            int[] widths,
            int[] top,
            int[] bottom,
            int firstX,
            int pageWidth,
            int gap,
            int cap,
            Set<Integer> staff,
            long[] budget) {
        return apply(widths, top, bottom, firstX, pageWidth, gap, cap, staff, budget, null);
    }

    static boolean apply(
            int[] widths,
            int[] top,
            int[] bottom,
            int firstX,
            int pageWidth,
            int gap,
            int cap,
            Set<Integer> staff,
            long[] budget,
            StringBuilder reason) {
        return apply(widths, top, bottom, firstX, pageWidth, gap, cap, staff, budget, reason, null);
    }

    static boolean apply(
            int[] widths,
            int[] top,
            int[] bottom,
            int firstX,
            int pageWidth,
            int gap,
            int cap,
            Set<Integer> staff,
            long[] budget,
            StringBuilder reason,
            boolean[] excludeStaffMembers) {
        int n = widths.length;
        boolean any = false;
        boolean[] columnContact = new boolean[n];
        // Distinct finite strokes must never constrain one another's width through
        // an intervening staff-only column. Such a column is UNKNOWN as bar ink.
        for (int i = 0; i < n; i++)
            if (widths[i] > 0) {
                boolean exposed = false;
                for (int y = top[i]; y <= bottom[i]; y++) {
                    if (!take(budget, 1)) {
                        if (reason != null) reason.append("BUDGET_FILTER");
                        return false;
                    }
                    if (staff.contains(y * pageWidth + firstX + i)) columnContact[i] = true;
                    else exposed = true;
                }
                if (!exposed) widths[i] = 0;
            }
        for (int from = 0; from < n; ) {
            while (from < n && widths[from] == 0) from++;
            if (from == n) break;
            int to = from + 1;
            while (to < n && widths[to] > 0) to++;
            int len = to - from;
            if (!take(budget, 4L * len)) {
                if (reason != null) reason.append("BUDGET_SEGMENT");
                return false;
            }
            int[] w = java.util.Arrays.copyOfRange(widths, from, to),
                    a = java.util.Arrays.copyOfRange(top, from, to),
                    b = java.util.Arrays.copyOfRange(bottom, from, to);
            boolean contact = false, withinCap = true;
            for (int i = 0; i < len; i++) {
                if (w[i] > cap) withinCap = false;
                contact |= columnContact[from + i];
            }
            StringBuilder localReason = reason == null ? null : new StringBuilder();
            boolean[] coowned = {false};
            boolean ok =
                    contact
                            ? applyOne(
                                    w,
                                    a,
                                    b,
                                    firstX + from,
                                    pageWidth,
                                    gap,
                                    cap,
                                    staff,
                                    budget,
                                    localReason,
                                    coowned)
                            : withinCap;
            if (ok && coowned[0] && excludeStaffMembers != null)
                for (int i = from; i < to; i++) excludeStaffMembers[i] = true;
            if (reason != null && !ok)
                reason.append(contact ? localReason.toString() : "WIDTH_OVER_CAP");
            if (budget[0] < 0) return false;
            for (int i = 0; i < len; i++) {
                if (!take(budget, 1)) {
                    if (reason != null) reason.append("BUDGET_WRITE");
                    return false;
                }
                widths[from + i] = ok ? w[i] : 0;
                if (ok) {
                    top[from + i] = a[i];
                    bottom[from + i] = b[i];
                }
            }
            any |= ok;
            from = to;
        }
        if (!any && reason != null && reason.length() == 0) reason.append("NO_EXPOSED_SEGMENT");
        return any;
    }

    private static boolean applyOne(
            int[] widths,
            int[] top,
            int[] bottom,
            int firstX,
            int pageWidth,
            int gap,
            int cap,
            Set<Integer> staff,
            long[] budget,
            StringBuilder reason,
            boolean[] coowned) {
        int n = widths.length, min = Math.max(3, (int) Math.ceil(.2 * gap));
        int lower = min,
                upper = cap,
                exposedRun = 0,
                longest = 0,
                joined = 0,
                joinedRun = 0,
                longestJoined = 0;
        boolean coownedPossible = true;
        // The run arrays and stripe are read-only operands until all exposed widths
        // constrain the same finite physical band; no inferred white is introduced.
        for (int i = 0; i < n; i++) {
            if (!take(budget, 1)) {
                if (reason != null) reason.append("BUDGET_WIDTH_PROFILE");
                return false;
            }
            if (widths[i] == 0) {
                exposedRun = 0;
                joinedRun = 0;
                coownedPossible = false;
                continue;
            }
            boolean contact = false;
            int residual = 0, bodyTop = Integer.MAX_VALUE, bodyBottom = -1;
            for (int y = top[i]; y <= bottom[i]; y++) {
                if (!take(budget, 1)) {
                    if (reason != null) reason.append("BUDGET_CONTACT_SCAN");
                    return false;
                }
                if (staff.contains(y * pageWidth + firstX + i)) contact = true;
                else {
                    residual++;
                    bodyTop = Math.min(bodyTop, y);
                    bodyBottom = Math.max(bodyBottom, y);
                }
            }
            if (bottom[i] < top[i]
                    || bottom[i] - top[i] + 1 > 2 * cap
                    || residual < min
                    || residual > cap
                    || bodyBottom < bodyTop
                    || bodyBottom - bodyTop + 1 > cap) coownedPossible = false;
            if (!contact && widths[i] >= min && widths[i] <= cap) {
                lower = Math.max(lower, widths[i] - 1);
                upper = Math.min(upper, widths[i] + 1);
                longest = Math.max(longest, ++exposedRun);
            } else {
                exposedRun = 0;
                if (contact) {
                    joined++;
                    joinedRun++;
                    longestJoined = Math.max(longestJoined, joinedRun);
                } else joinedRun = 0;
            }
        }
        if (longest < Math.max(3, (int) Math.ceil(.2 * gap)) || joined == 0 || lower > upper) {
            // Avoid rescanning every weak/partial candidate. A separate co-owned run
            // is considered only when one continuous witnessed staff contact covers
            // all but the same short cap allowance used by the exposed-run minimum.
            int minimumBodyLength = Math.max(2, (int) Math.rint(.45 * 1.4 * gap));
            if (coownedPossible
                    && n >= minimumBodyLength
                    && longestJoined >= Math.max(3, n - min + 1)) {
                if (reason != null) reason.append("COOWNED_COMPLETE_RUN");
                coowned[0] = true;
                return true;
            }
            if (reason != null)
                reason.append("NO_COMMON_WIDTH_OR_EXPOSED_RUN[longest=")
                        .append(longest)
                        .append(",joined=")
                        .append(joined)
                        .append(",lower=")
                        .append(lower)
                        .append(",upper=")
                        .append(upper)
                        .append("]");
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (!take(budget, 1)) {
                if (reason != null) reason.append("BUDGET_RECONSTRUCT");
                return false;
            }
            if (widths[i] == 0) continue;
            int a = top[i], b = bottom[i], lo = Integer.MAX_VALUE, hi = -1;
            for (int y = a; y <= b; y++) {
                if (!take(budget, 1)) {
                    if (reason != null) reason.append("BUDGET_RECONSTRUCT_SCAN");
                    return false;
                }
                if (staff.contains(y * pageWidth + firstX + i)) {
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                }
            }
            if (hi < 0) continue;
            // A sparse carrier center cannot be inflated into a stripe. Every residual
            // pixel must individually have independent staff membership.
            boolean above = a < lo, below = b > hi;
            if (!above && !below) {
                widths[i] = 0;
                continue;
            }
            int commonTop = Integer.MIN_VALUE, commonBottom = Integer.MAX_VALUE;
            boolean found = false;
            for (int thick = lower; thick <= upper; thick++) {
                if (!take(budget, 1)) return false;
                int aa = above ? a : b - thick + 1, bb = below ? b : a + thick - 1;
                if (above && below) {
                    aa = a;
                    bb = b;
                    if (bb - aa + 1 != thick) continue;
                }
                if (aa < a || bb > b || aa > bb) continue;
                boolean valid = true;
                for (int y = a; y <= b; y++) {
                    if (!take(budget, 1)) return false;
                    if ((y < aa || y > bb) && !staff.contains(y * pageWidth + firstX + i)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    found = true;
                    commonTop = Math.max(commonTop, aa);
                    commonBottom = Math.min(commonBottom, bb);
                }
            }
            if (!found || commonTop > commonBottom) {
                if (reason != null) reason.append("NO_COMPLETE_RESIDUAL_AT_X_").append(firstX + i);
                widths[i] = 0;
                continue;
            }
            top[i] = commonTop;
            bottom[i] = commonBottom;
            widths[i] = commonBottom - commonTop + 1;
        }
        return true;
    }
}
