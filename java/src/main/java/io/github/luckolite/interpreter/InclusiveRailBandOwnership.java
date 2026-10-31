// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Proves separate printed beam paths reaching the original owned shaft columns. */
final class InclusiveRailBandOwnership {
    static boolean proves(
            PairedGraceBeamInk.ObservedPair p,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int ownerCount,
            int[] a,
            int[] b,
            int headA,
            int headB,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            float staffTop,
            StaffPitchTrack track) {
        if (p == null
                || gray == null
                || labels == null
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || width < 3
                || height < 3
                || ownerCount < 3
                || ownerCount > 32
                || !Float.isFinite(gap)
                || gap < 4
                || !Float.isFinite(staffTop)
                || left < 0
                || right >= width
                || left > right
                || top < 0
                || bottom >= height
                || top > bottom
                || a[0] < left
                || b[0] > right
                || a[0] >= b[0]
                || Math.abs(a[2]) != 1
                || a[2] != b[2]
                || (long) b[0] - a[0] + 1 > gap * 40
                || a[1] < top
                || a[1] > bottom
                || b[1] < top
                || b[1] > bottom
                || headA < top
                || headA > bottom
                || headB < top
                || headB > bottom
                || p.gray() != gray
                || p.width() != width
                || p.height() != height
                || labels.length != gray.length
                || gray.length != (long) width * height
                || !Arrays.equals(p.first(), a)
                || !Arrays.equals(p.last(), b)
                || p.track() != track
                || p.bands() == null
                || p.bands().size() != 10) return false;
        var bands = p.bands();
        for (int k = 0; k < 5; k++) {
            var one = bands.get(k * 2);
            var two = bands.get(k * 2 + 1);
            if (one.x() != two.x()
                    || one.top() > one.bottom()
                    || two.top() > two.bottom()
                    || one.bottom() + 1 >= two.top()
                    || k > 0 && one.x() <= bands.get((k - 1) * 2).x()) return false;
        }
        int columns = b[0] - a[0] + 1;
        int[][][] bounds = new int[2][columns][2];
        for (int x = a[0]; x <= b[0]; x++) {
            if (x < left || x > right || x < 1 || x >= width - 1) return false;
            int segment = 0;
            while (segment < 3 && x > bands.get((segment + 1) * 2).x()) segment++;
            for (int rail = 0; rail < 2; rail++) {
                var first = bands.get(segment * 2 + rail);
                var last = bands.get((segment + 1) * 2 + rail);
                float t = (x - first.x()) / (float) (last.x() - first.x());
                int lo = Math.round(first.top() + (last.top() - first.top()) * t),
                        hi = Math.round(first.bottom() + (last.bottom() - first.bottom()) * t);
                bounds[rail][x - a[0]][0] = lo;
                bounds[rail][x - a[0]][1] = hi;
                if (lo > hi || lo < top || hi > bottom || lo < 1 || hi >= height - 1) return false;
                if (x == a[0] && (lo < Math.min(a[1], headA) || hi > Math.max(a[1], headA))
                        || x == b[0] && (lo < Math.min(b[1], headB) || hi > Math.max(b[1], headB)))
                    return false;
                if (LocalPrintedStaffBand.matches(
                        gray, width, height, x, staffTop, gap, 165, lo, hi)) return false;
            }
            if (bounds[0][x - a[0]][1] + 1 >= bounds[1][x - a[0]][0]) return false;
        }
        for (int rail = 0; rail < 2; rail++) {
            int lo = height, hi = 0;
            for (int[] band : bounds[rail]) {
                lo = Math.min(lo, band[0]);
                hi = Math.max(hi, band[1]);
            }
            int rows = hi - lo + 1;
            long cells = (long) columns * rows;
            if (cells <= 0 || cells > Integer.MAX_VALUE) return false;
            boolean[] seen = new boolean[(int) cells];
            int[] queue = new int[(int) cells];
            int size = 0, take = 0;
            for (int y = bounds[rail][0][0]; y <= bounds[rail][0][1]; y++)
                if (ownedInk(labels, gray, width, a[0], y)) {
                    int at = (y - lo) * columns;
                    seen[at] = true;
                    queue[size++] = at;
                }
            boolean connected = false;
            while (take < size) {
                int at = queue[take++], cx = at % columns, cy = lo + at / columns;
                if (cx == columns - 1) {
                    connected = true;
                    break;
                }
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx, ny = cy + dy;
                        if (nx < 0
                                || nx >= columns
                                || ny < bounds[rail][nx][0]
                                || ny > bounds[rail][nx][1]) continue;
                        int next = (ny - lo) * columns + nx;
                        if (seen[next] || !ownedInk(labels, gray, width, a[0] + nx, ny)) continue;
                        seen[next] = true;
                        queue[size++] = next;
                    }
            }
            if (!connected) return false;
        }
        return true;
    }

    private static boolean ownedInk(byte[] labels, byte[] gray, int width, int x, int y) {
        int i = y * width + x;
        return (gray[i] & 255) < 165;
    }
}
