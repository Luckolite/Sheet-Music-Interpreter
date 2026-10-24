// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** Owns an entire returning flag subtree, including only a raw-proved shaft-end collar. */
final class DownFlagBranchOwnership {
    private DownFlagBranchOwnership() {}

    static Set<Integer> leaves(
            byte[] raw,
            int w,
            int h,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Set<Integer> downShafts,
            Map<Integer, PrintedSlurContours.HeadEvidence> evidence,
            List<Integer> nodes,
            List<Integer> leaves,
            IntFunction<List<Integer>> adjacent,
            IntUnaryOperator original,
            AttachedApproachBranches.Budget budget) {
        var result = new HashSet<Integer>();
        var leafSet = Set.copyOf(leaves);
        for (int owner : localHeads) {
            if (!downShafts.contains(owner)) continue;
            var head = heads.get(owner);
            float gap = head.gap();
            if (!budget.take((long) Math.ceil(gap * gap * 6))) return Set.of();
            int[] shaft = shaft(raw, w, h, head);
            if (shaft == null) continue;
            int sx = shaft[0], end = shaft[1];
            var caps = new ArrayList<Integer>();
            int minX = w, maxX = 0, minY = h, maxY = 0;
            for (int leaf : leaves) {
                int at = original.applyAsInt(leaf), x = at % w, y = at / w;
                if (x <= sx + gap * .35f
                        || x > sx + gap * 1.5f
                        || y < end - gap * 2.3f
                        || y > end - gap * .4f) continue;
                caps.add(leaf);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            if (caps.isEmpty()
                    || caps.size() > 3
                    || maxX - minX > gap * .25f
                    || maxY - minY > gap * .25f) continue;
            if (!budget.take(
                    (long) nodes.size() * 32 + (long) Math.ceil(gap * gap * (gap * 16 + 32))))
                return Set.of();
            if (!profile(raw, w, h, head, sx, end, gap)) continue;
            var seeds = new HashSet<Integer>();
            for (int node : nodes) {
                int at = original.applyAsInt(node), x = at % w, y = at / w;
                if (evidence.get(owner).contains(x, y)
                        || (Math.abs(x - sx) <= Math.max(1, gap * .18f)
                                && y >= head.y() + gap * .15f
                                && y <= end)) seeds.add(node);
            }
            if (seeds.isEmpty()) continue;
            // Flood the entire off-shaft glyph, including split cap pixels and cycles.
            // Every connection must terminate on the independently raw-proved shaft;
            // an untyped branch is inspected in full and cannot disappear in a path tie.
            var glyph = new HashSet<Integer>();
            var queue = new ArrayDeque<Integer>();
            for (int cap : caps) if (glyph.add(cap)) queue.add(cap);
            var capSet = Set.copyOf(caps);
            boolean complete = true, root = false;
            while (!queue.isEmpty() && complete) {
                int node = queue.remove();
                if (seeds.contains(node)) {
                    root = true;
                    continue;
                }
                int at = original.applyAsInt(node), x = at % w, y = at / w;
                if (x < sx - gap * .15f
                        || x > sx + gap * 1.5f
                        || y < head.y() + gap * .2f
                        || y > end + gap * .2f) {
                    complete = false;
                    break;
                }
                if (leafSet.contains(node) && !capSet.contains(node)) {
                    complete = false;
                    break;
                }
                for (int next : adjacent.apply(node)) if (glyph.add(next)) queue.add(next);
            }
            if (!complete || !root) continue;
            var rooted = new HashSet<Integer>();
            queue.clear();
            for (int node : glyph)
                if (seeds.contains(node)) {
                    rooted.add(node);
                    queue.add(node);
                }
            while (!queue.isEmpty())
                for (int next : adjacent.apply(queue.remove()))
                    if (seeds.contains(next) && rooted.add(next)) queue.add(next);
            result.addAll(caps);
            for (int leaf : leaves) {
                int at = original.applyAsInt(leaf);
                if (rooted.contains(leaf)
                        && Math.abs(at % w - sx) <= gap * .2f
                        && Math.abs(at / w - end) <= gap * .35f) result.add(leaf);
            }
        }
        return Set.copyOf(result);
    }

    static int[] shaft(byte[] raw, int w, int h, NoteSlideDetector.Head head) {
        float gap = head.gap();
        int start = Math.max(0, Math.round(head.y() + gap * .2f)),
                limit = Math.min(h - 2, Math.round(head.y() + gap * 7));
        int bestX = -1, end = -1, minimum = h, maximum = -1;
        for (int x = Math.round(head.x() - gap * .8f); x <= head.x() - gap * .4f; x++) {
            if (x < 1 || x >= w - 1) continue;
            int last = start - 1, blank = 0, hits = 0;
            for (int y = start; y <= limit; y++) {
                if ((raw[y * w + x] & 255) < 150) {
                    last = y;
                    blank = 0;
                    hits++;
                } else if (++blank > 1) break;
            }
            int span = last - start + 1;
            if (span < gap * 2 || last >= limit - 1 || hits < span * .95f) continue;
            minimum = Math.min(minimum, last);
            maximum = Math.max(maximum, last);
            if (last > end) {
                end = last;
                bestX = x;
            }
        }
        return bestX < 0 || maximum - minimum > gap * .3f ? null : new int[] {bestX, end};
    }

    /** Raw counterpart of the existing narrow curved-flag profile, without invented labels. */
    private static boolean profile(
            byte[] raw, int w, int h, NoteSlideDetector.Head head, int sx, int end, float gap) {
        int left = Math.max(0, (int) Math.floor(sx + gap * .25f)),
                right = Math.min(w - 1, Math.round(sx + gap * 1.5f));
        int rows = 0, near = 0, bulge = 0, exterior = 0, roots = 0;
        for (int y = Math.max(0, Math.round(end - gap * 2.3f)); y <= end && y < h; y++) {
            if (Math.abs(y - head.y()) < gap * .65f
                    || FlagRootStaffRule.matches(raw, w, h, y, sx, gap, 150)) continue;
            int first = right + 1, last = left - 1;
            for (int x = left; x <= right; x++)
                if ((raw[y * w + x] & 255) < 150) {
                    if (last >= left && x - last > 2) break;
                    first = Math.min(first, x);
                    last = x;
                }
            if (last < first || Math.abs(y - end) < gap * .5f && first - sx > gap * .6f) continue;
            rows++;
            if (end - y <= gap * 1.45f) near++;
            if (end - y <= gap * 1.45f && first - sx <= gap * .5f) roots++;
            if (last - sx >= gap * .55f) bulge++;
            if (last >= right - 1) exterior++;
        }
        return roots >= Math.max(2, Math.round(gap * .16f))
                && rows >= Math.max(4, Math.round(gap * .65f))
                && near >= Math.max(2, Math.round(gap * .16f))
                && bulge >= Math.max(3, Math.round(gap * .3f))
                && exterior <= Math.max(1, Math.round(rows * .15f));
    }
}
