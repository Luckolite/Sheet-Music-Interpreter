// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Types a separate short run only inside an independently proved five-rule staff band. */
final class SeparatedRuleRunOwnership {
    private SeparatedRuleRunOwnership() {}

    static boolean matches(
            byte[] raw,
            int w,
            int h,
            int x,
            int first,
            int last,
            StaffPitchTrack track,
            AttachedApproachBranches.Budget budget) {
        if (first < 1
                || last >= h - 1
                || last < first
                || x < 0
                || x >= w
                || track == null
                || !track.verifiedAt(x)) return false;
        float[] frame = track.at(x);
        float gap = frame[1];
        if (!Float.isFinite(frame[0] + gap)
                || gap < 6
                || gap > 64
                || last - first + 1 > Math.max(3, Math.ceil(gap * .24f))) return false;
        boolean band = false;
        float radius = Math.max(1, gap * .13f + .5f);
        for (int line = 0; line < 5; line++) {
            float center = frame[0] - line * gap;
            if (first >= center - radius && last <= center + radius) band = true;
        }
        if (!band) return false;
        for (int y = first; y <= last; y++) if ((raw[y * w + x] & 255) >= 150) return false;
        // A separate run has real paper on both sides; a clipped piece of a head or shaft
        // may not acquire rule ownership merely because it lies near a printed line.
        if ((raw[(first - 1) * w + x] & 255) < 150 || (raw[(last + 1) * w + x] & 255) < 150)
            return false;
        if (!budget.take((long) Math.ceil(gap * gap * 2 + 32))) return false;
        return FlagRootStaffRule.matches(raw, w, h, Math.round((first + last) * .5f), x, gap, 150);
    }

    /** Remove only disjoint raw-proved staff runs from an otherwise retained curve column. */
    static int[] withoutSeparateRuleIslands(
            byte[] raw,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            StaffPitchTrack track,
            AttachedApproachBranches.Budget budget) {
        int columns = right - left + 1, rows = bottom - top + 1;
        if (track == null
                || count < 8
                || count > 4096
                || columns < 1
                || columns > 128
                || rows < 1
                || rows > 128) return null;
        if (!budget.take((long) columns * rows * 4 + count * 4L)) return null;
        boolean[] ink = new boolean[columns * rows], drop = new boolean[ink.length];
        for (int i = 0; i < count; i++)
            ink[(pixels[i] / w - top) * columns + pixels[i] % w - left] = true;
        boolean changed = false;
        for (int x = 0; x < columns; x++) {
            var runs = new java.util.ArrayList<int[]>();
            for (int y = 0; y < rows; ) {
                if (!ink[y * columns + x]) {
                    y++;
                    continue;
                }
                int first = y;
                while (y < rows && ink[y * columns + x]) y++;
                runs.add(new int[] {first, y - 1, 0});
            }
            if (runs.size() < 2) continue;
            int meaningful = 0;
            float gap = track.at(left + x)[1];
            int limit = Math.max(3, (int) Math.ceil(gap * .24f));
            if (!budget.take((long) runs.size() * (limit * 2L + 4))) return null;
            for (var run : runs) {
                int first = top + run[0], last = top + run[1];
                while (first > 0
                        && top + run[0] - first < limit
                        && (raw[(first - 1) * w + left + x] & 255) < 150) first--;
                while (last < h - 1
                        && last - top - run[1] < limit
                        && (raw[(last + 1) * w + left + x] & 255) < 150) last++;
                if (matches(raw, w, h, left + x, first, last, track, budget)) run[2] = 1;
                else meaningful++;
            }
            if (budget.refused) return null;
            if (meaningful != 1) continue;
            for (var run : runs)
                if (run[2] == 1)
                    for (int y = run[0]; y <= run[1]; y++) {
                        drop[y * columns + x] = true;
                        changed = true;
                    }
        }
        if (!changed) return null;
        int[] selected = new int[count];
        int n = 0;
        for (int i = 0; i < count; i++)
            if (!drop[(pixels[i] / w - top) * columns + pixels[i] % w - left])
                selected[n++] = pixels[i];
        return java.util.Arrays.copyOf(selected, n);
    }
}
