// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Types a tiny end spur only through two raw shafts and the complete connecting beam strip. */
final class BeamEndBranchOwnership {
    private BeamEndBranchOwnership() {}

    static boolean owns(
            byte[] raw,
            int w,
            int h,
            int[] path,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Set<Integer> downShafts,
            AttachedApproachBranches.Budget budget) {
        if (path.length < 2 || path.length > 48) return false;
        int left = w, right = -1, top = h, bottom = -1;
        for (int p : path) {
            if (p < 0 || p >= raw.length || (raw[p] & 255) >= 150) return false;
            left = Math.min(left, p % w);
            right = Math.max(right, p % w);
            top = Math.min(top, p / w);
            bottom = Math.max(bottom, p / w);
        }
        float cx = (left + right) * .5f, cy = (top + bottom) * .5f;
        var shafts = new HashMap<Integer, int[]>();
        for (int i : localHeads) {
            var head = heads.get(i);
            float gap = head.gap();
            if (!downShafts.contains(i)
                    || path.length > gap * .75f
                    || right - left > gap * .5f
                    || bottom - top > gap * .5f) continue;
            if (!budget.take((long) Math.ceil(gap * gap * 5))) return false;
            var shaft = shaftEnd(raw, w, h, head);
            if (shaft != null) shafts.put(i, shaft);
        }
        for (var endpoint : shafts.entrySet()) {
            var head = heads.get(endpoint.getKey());
            float gap = head.gap();
            int[] a = endpoint.getValue();
            if (Math.abs(cx - a[0]) > gap * .5f || Math.abs(cy - a[1]) > gap * .6f) continue;
            for (var other : shafts.entrySet()) {
                if (other.getKey().equals(endpoint.getKey())) continue;
                var second = heads.get(other.getKey());
                int[] b = other.getValue();
                float span = Math.abs(b[0] - a[0]);
                if (second.staff() != head.staff()
                        || second.measure() != head.measure()
                        || span < gap * 2
                        || span > gap * 6) continue;
                double slope = (b[1] - a[1]) / (double) (b[0] - a[0]);
                if (Math.abs(slope) > .6) continue;
                boolean bounded = true;
                for (int p : path) {
                    double bottomEdge = a[1] + slope * (p % w - a[0]);
                    if (p / w < bottomEdge - gap * .9 || p / w > bottomEdge + gap * .15) {
                        bounded = false;
                        break;
                    }
                }
                if (!bounded) continue;
                // The strip verifier searches shifts, columns and vertical runs; charge its
                // cubic worst-case envelope before invoking it, including threshold sampling.
                if (!budget.take((long) Math.ceil(gap * gap * (gap * 32 + 32)))) return false;
                if (StemOwnedBeamTip.matches(
                        raw,
                        w,
                        h,
                        a,
                        b,
                        cx,
                        cy,
                        right - left + 1,
                        bottom - top + 1,
                        path.length,
                        gap)) return true;
            }
        }
        return false;
    }

    private static int[] shaftEnd(byte[] raw, int w, int h, NoteSlideDetector.Head head) {
        float gap = head.gap();
        int first = Math.round(head.y() + gap * .2f),
                limit = Math.min(h - 2, Math.round(head.y() + gap * 7));
        int bestX = -1, bestEnd = -1, minEnd = h, maxEnd = -1;
        for (int x = Math.round(head.x() - gap * .8f); x <= head.x() - gap * .4f; x++) {
            if (x < 1 || x >= w - 1) continue;
            int end = first - 1, blank = 0, hits = 0;
            for (int y = first; y <= limit; y++) {
                if ((raw[y * w + x] & 255) < 150) {
                    end = y;
                    blank = 0;
                    hits++;
                } else if (++blank > 1) break;
            }
            int span = end - first + 1;
            if (span < gap * 2 || end >= limit - 1 || hits < span * .95f) continue;
            minEnd = Math.min(minEnd, end);
            maxEnd = Math.max(maxEnd, end);
            if (end > bestEnd) {
                bestEnd = end;
                bestX = x;
            }
        }
        if (bestX < 0 || maxEnd - minEnd > gap * .3f) return null;
        return new int[] {bestX, bestEnd, 1};
    }
}
