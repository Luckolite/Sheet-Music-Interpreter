// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A complete free shaft end may coexist with a separately owned approach branch. */
final class StemEndBranchOwnership {
    private StemEndBranchOwnership() {}

    static boolean owns(
            byte[] raw,
            int w,
            int h,
            int[] path,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Set<Integer> downShafts,
            AttachedApproachBranches.Budget budget) {
        if (path.length < 3 || path.length > 448) return false;
        int tip = path[0], joint = path[path.length - 1];
        if (tip < 0 || tip >= raw.length || joint < 0 || joint >= raw.length) return false;
        for (int index : localHeads) {
            if (!downShafts.contains(index)) continue;
            var head = heads.get(index);
            float gap = head.gap();
            int tipY = tip / w, jointY = joint / w;
            if (path.length < gap * .75f
                    || path.length > gap * 7
                    || tipY - head.y() < gap * 1.7f
                    || tipY - head.y() > gap * 7
                    || jointY < head.y() - gap * .2f
                    || jointY > head.y() + gap * 1.5f) continue;
            boolean vertical = true;
            int previousY = tipY;
            for (int p : path) {
                if (p < 0
                        || p >= raw.length
                        || (raw[p] & 255) >= 150
                        || p / w > previousY + 1
                        || p / w < jointY - 1) {
                    vertical = false;
                    break;
                }
                previousY = p / w;
            }
            if (!vertical) continue;
            if (!budget.take((long) Math.ceil(gap * gap * 8))) return false;
            for (int x = Math.round(head.x() - gap * .8f); x <= head.x() - gap * .4f; x++) {
                if (x < 1 || x >= w - 1) continue;
                boolean aligned = true;
                float radius = Math.max(1, gap * .12f);
                for (int p : path)
                    if (Math.abs(p % w - x) > radius) {
                        aligned = false;
                        break;
                    }
                if (!aligned) continue;
                int start = Math.max(0, Math.round(head.y() + gap * .2f)), hits = 0, blank = 0;
                boolean complete = true;
                for (int y = start; y <= tipY; y++) {
                    if ((raw[y * w + x] & 255) < 150) {
                        hits++;
                        blank = 0;
                    } else if (++blank > 1) {
                        complete = false;
                        break;
                    }
                }
                if (!complete || hits < (tipY - start + 1) * .95f) continue;
                int paper = 0;
                boolean end = false;
                for (int y = tipY + 1; y < h && y <= tipY + Math.ceil(gap * .5f); y++) {
                    if ((raw[y * w + x] & 255) >= 150) paper++;
                    else paper = 0;
                    if (paper >= Math.max(2, Math.ceil(gap * .15f))) {
                        end = true;
                        break;
                    }
                }
                if (end) return true;
            }
        }
        return false;
    }
}
