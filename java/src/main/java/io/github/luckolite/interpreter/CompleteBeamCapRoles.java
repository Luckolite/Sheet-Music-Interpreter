// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** PRIVATE literal full-tone shaft caps and universal strip/member evidence. */
final class CompleteBeamCapRoles {
    record Shaft(int left, int right, int nativeX, int nativeEnd, int[] profile) {
        int profileAt(int x) {
            return x < left || x > right ? 255 : profile[x - left];
        }
    }

    record Shafts(Shaft left, Shaft right) {}

    static Shafts shafts(
            byte[] gray,
            int w,
            int h,
            int[] reference,
            int y0,
            int left,
            int right,
            int leftEnd,
            int rightEnd,
            int direction,
            float gap,
            LiteralStaffCompleteBeamJoint.Budget budget) {
        if (direction != -1 && direction != 1) return null;
        long footprint =
                2L * (2L * (long) Math.ceil(gap * .6) + 1) * (2L * (long) Math.ceil(2 * gap) + 1);
        if (!budget.take(64L * reference.length + 8192 + 16L * footprint)) return null;
        Shaft a = shaft(gray, w, h, reference, y0, left, leftEnd, direction, gap);
        Shaft b = shaft(gray, w, h, reference, y0, right, rightEnd, direction, gap);
        if (a == null || b == null || a.right() >= b.left() - 2) return null;
        // Complete independently calibrated five-rail height, rather than a
        // convenient crop around the proposed semantic island.
        for (int x : new int[] {a.left() - 1, b.right() + 1}) {
            if (x < 0 || x >= w) return null;
            for (int k = 0; k < reference.length; k++)
                if ((gray[(y0 + k) * w + x] & 255) != reference[k]) return null;
        }
        return new Shafts(a, b);
    }

    private static Shaft shaft(
            byte[] g, int w, int h, int[] ref, int y0, int x, int end, int direction, float gap) {
        int radius = (int) Math.ceil(gap * .6), lo = x - radius, hi = x + radius;
        int a = end - direction * Math.round(gap),
                b = end - direction * Math.round(2 * gap),
                ya = Math.min(a, b),
                yb = Math.max(a, b);
        if (lo < 0 || hi >= w || ya < y0 || yb >= y0 + ref.length || ya < 0 || yb >= h) return null;
        int[] profile = null;
        int paper = 0;
        for (int y = ya; y <= yb; y++)
            if (ref[y - y0] == 255) {
                int[] p = new int[hi - lo + 1];
                for (int xx = lo; xx <= hi; xx++) p[xx - lo] = g[y * w + xx] & 255;
                if (profile != null && !Arrays.equals(profile, p)) return null;
                profile = p;
                paper++;
            }
        if (profile == null || paper < Math.ceil(gap * .5)) return null;
        int first = -1, last = -1;
        for (int i = 0; i < profile.length; i++)
            if (profile[i] < 255) {
                if (first < 0) first = i;
                last = i;
            }
        if (first < 1
                || last >= profile.length - 1
                || last - first + 1 > Math.max(1, Math.round(gap * .3))
                || x < lo + first
                || x > lo + last) return null;
        for (int i = first; i <= last; i++) if (profile[i] == 255) return null;
        for (int y = ya; y <= yb; y++)
            for (int xx = lo; xx <= hi; xx++) {
                int value = g[y * w + xx] & 255, reference = ref[y - y0];
                // Intersections may co-own foreground. No min-compositing model or
                // exclusive staff role is asserted for the crossing pixels.
                if (value > reference) return null;
                if (xx < lo + first || xx > lo + last) {
                    if (value != reference) return null;
                } else if (value == 255) return null;
            }
        // The measured cross-section is useful only if it belongs to one
        // literal 8-connected shaft from the native endpoint.  Inspect every
        // intervening row inside the measured foreground footprint; a gap or
        // a two-column jump cannot be bridged by a later witness slice.
        int coreLo = lo + first, coreHi = lo + last;
        if (x < coreLo
                || x > coreHi
                || !withinNativePath(g, w, h, ref, y0, coreLo, coreHi, x, end, direction, ya, yb))
            return null;
        return new Shaft(
                lo + first, lo + last, x, end, Arrays.copyOfRange(profile, first, last + 1));
    }

    private static boolean withinNativePath(
            byte[] g,
            int w,
            int h,
            int[] ref,
            int y0,
            int xLo,
            int xHi,
            int nativeX,
            int end,
            int direction,
            int witnessTop,
            int witnessBottom) {
        int far = direction > 0 ? witnessTop : witnessBottom;
        int step = -direction;
        if (end < 0 || end >= h || far < 0 || far >= h || nativeX < xLo || nativeX > xHi)
            return false;
        boolean[] reachable = new boolean[xHi - xLo + 1];
        int startValue = g[end * w + nativeX] & 255;
        if (startValue >= 185) return false;
        reachable[nativeX - xLo] = true;
        int first = Math.min(end, far), last = Math.max(end, far);
        if (first < y0 || last >= y0 + ref.length) return false;
        for (int y = end + step; ; y += step) {
            boolean[] next = new boolean[reachable.length];
            boolean any = false;
            int reference = ref[y - y0];
            for (int ix = 0; ix < reachable.length; ix++)
                if (reachable[ix]) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = ix + dx;
                        if (nx < 0 || nx >= next.length) continue;
                        int gray = g[y * w + xLo + nx] & 255;
                        if (gray >= 185 || gray > reference) continue;
                        next[nx] = true;
                        any = true;
                    }
                }
            if (!any) return false;
            reachable = next;
            if (y == far) break;
        }
        // Every measured witness row must retain the complete connected core,
        // not merely one reachable pixel at its near edge.
        for (int y = Math.min(witnessTop, witnessBottom);
                y <= Math.max(witnessTop, witnessBottom);
                y++) {
            if (y < y0 || y >= y0 + ref.length) return false;
            for (int x = xLo; x <= xHi; x++) {
                int gray = g[y * w + x] & 255;
                if (gray < 185 && gray > ref[y - y0]) return false;
            }
        }
        return true;
    }

    static String prove(
            byte[] gray,
            int w,
            int h,
            byte[] labels,
            int[] members,
            int[] reference,
            int y0,
            int lo,
            int hi,
            int[] top,
            int[] bottom,
            int[] possibleTop,
            int[] possibleBottom,
            int thickness,
            int minX,
            int minY,
            int maxX,
            int maxY,
            Shafts shafts,
            LiteralStaffCompleteBeamJoint.Budget budget) {
        int n = hi - lo + 1;
        if (shafts == null
                || labels == null
                || labels.length != gray.length
                || members == null
                || members.length < 1
                || members.length > 4096
                || n < 2
                || n > 512
                || top.length != n
                || bottom.length != n
                || possibleTop.length != n
                || possibleBottom.length != n) return "JOINT_DOMAIN";
        if (!budget.take(
                4L * n * n * n + 4L * n * n * members.length + 32L * members.length + 8192))
            return "JOINT_BUDGET";
        if (!completeMembers(labels, w, h, members, minX, minY, maxX, maxY))
            return "INCOMPLETE_CURRENT_SEMANTIC_MEMBERS";
        String memberEvidence =
                validateSemanticMembersObserved(gray, w, h, members, reference, y0, lo, hi, shafts);
        if (memberEvidence != null) return memberEvidence;
        // Every visible extra-contrast pixel at the two full body/shaft seams
        // must have a literal 8-connected extra-contrast partner in that shaft.
        for (int side = 0; side < 2; side++) {
            int i = side == 0 ? 0 : n - 1,
                    x = lo + i,
                    sx = side == 0 ? shafts.left().right() : shafts.right().left();
            int contacts = 0;
            for (int y = top[i]; y <= bottom[i]; y++) {
                if (y < y0 || y >= y0 + reference.length) return "CONTACT_REFERENCE_SCOPE";
                if ((gray[y * w + x] & 255) >= reference[y - y0]) continue;
                boolean contact = false;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy >= y0
                            && yy < y0 + reference.length
                            && (gray[yy * w + sx] & 255) < reference[yy - y0]) contact = true;
                }
                if (!contact) return "INCOMPLETE_NATIVE_SHAFT_CONTACT";
                contacts++;
            }
            if (contacts < Math.max(2, thickness / 2)) return "INSUFFICIENT_NATIVE_SHAFT_CONTACT";
        }
        long[] lower = new long[n], upper = new long[n];
        for (int i = 0; i < n; i++) {
            lower[i] = Math.max((long) bottom[i] - 1, (long) possibleTop[i] + thickness - 2);
            upper[i] = Math.min((long) possibleBottom[i] + 1, (long) top[i] + thickness);
            if (lower[i] > upper[i]) return "EMPTY_JOINT_STRIP_DOMAIN";
        }
        int vertices = 0;
        // All vertices of this bounded intersection are pairs of integer
        // column constraints. Rational cross-products avoid fitted-angle bias.
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                for (int a = 0; a < 2; a++)
                    for (int b = 0; b < 2; b++) {
                        long vi = a == 0 ? lower[i] : upper[i],
                                vj = b == 0 ? lower[j] : upper[j],
                                d = j - i,
                                m = vj - vi;
                        boolean valid = true;
                        for (int k = 0; k < n; k++) {
                            long value = vi * d + m * (k - i);
                            if (value < lower[k] * d || value > upper[k] * d) {
                                valid = false;
                                break;
                            }
                        }
                        if (!valid) continue;
                        vertices++;
                        for (int p : members) {
                            int x = p % w, y = p / w;
                            if (x < shafts.left().left() || x > shafts.right().right())
                                return "MEMBER_BEYOND_FINITE_CAP";
                            long value = vi * d + m * (x - lo - i);
                            // Conservative interior for EVERY feasible strip, excluding
                            // the existing one-pixel edge quantization on both sides.
                            if ((long) y * d < value - (thickness - 2L) * d
                                    || (long) y * d > value - d)
                                return "MEMBER_NOT_UNIVERSAL_STRIP_INTERIOR";
                        }
                    }
        return vertices == 0 ? "NO_COMPLETE_JOINT_STRIP" : null;
    }

    private static boolean completeMembers(
            byte[] labels, int w, int h, int[] members, int l, int t, int r, int b) {
        if (l < 0
                || t < 0
                || r >= w
                || b >= h
                || l > r
                || t > b
                || (long) (r - l + 1) * (b - t + 1) > 4096) return false;
        Set<Integer> expected = new HashSet<>();
        int ll = w, tt = h, rr = -1, bb = -1;
        for (int p : members) {
            if (p < 0 || p >= labels.length || (labels[p] & 255) != 2 || !expected.add(p))
                return false;
            int x = p % w, y = p / w;
            if (x < l || x > r || y < t || y > b) return false;
            ll = Math.min(ll, x);
            rr = Math.max(rr, x);
            tt = Math.min(tt, y);
            bb = Math.max(bb, y);
        }
        if (ll != l || rr != r || tt != t || bb != b) return false;
        Set<Integer> found = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        found.add(members[0]);
        queue.add(members[0]);
        while (!queue.isEmpty()) {
            int p = queue.remove(), x = p % w, y = p / w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                    int q = yy * w + xx;
                    if ((labels[q] & 255) != 2) continue;
                    if (!expected.contains(q)) return false;
                    if (found.add(q)) queue.add(q);
                }
        }
        return found.equals(expected);
    }

    static String validateSemanticMembersObserved(
            byte[] gray,
            int w,
            int h,
            int[] members,
            int[] reference,
            int y0,
            int bodyLeft,
            int bodyRight,
            Shafts shafts) {
        if (gray == null
                || members == null
                || reference == null
                || shafts == null
                || gray.length != (long) w * h) return "MEMBER_EVIDENCE_DOMAIN";
        for (int p : members) {
            if (p < 0 || p >= gray.length) return "MEMBER_EVIDENCE_DOMAIN";
            int y = p / w;
            if (y < y0 || y >= y0 + reference.length) return "MEMBER_REFERENCE_SCOPE";
            int value = gray[p] & 255, ref = reference[y - y0];
            if (value > ref) return "MEMBER_REFERENCE_CONTRADICTION";
            int x = p % w;
            if (x >= bodyLeft && x <= bodyRight) {
                if (value >= 185) return "MEMBER_NOT_PROVED_BODY_FOREGROUND";
                continue;
            }
            Shaft shaft = x < bodyLeft ? shafts.left() : x > bodyRight ? shafts.right() : null;
            int witnessed = shaft == null ? 255 : shaft.profileAt(x);
            // Outside the complete inner body, semantic AA is admissible only
            // when the independently measured shaft profile proves material
            // at that exact column. Paper cannot borrow an adjacent dark core.
            if (witnessed >= 255 || value > witnessed) return "MEMBER_NOT_PROVED_SHAFT_MATERIAL";
        }
        return null;
    }
}
