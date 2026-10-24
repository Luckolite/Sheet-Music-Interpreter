// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A positively fitted complete crest with two observed ports; never standalone glyph authority. */
final class FilledTurnRibbon {
    record Proof(
            int[] coveredRuns,
            int leftArm,
            int rightArm,
            int cells,
            long areaTwice,
            ObservedCentreTurnFamily.Fit measuredFit) {}

    private static void pay(ObservedFringeContinuation.Charge w, int n) {
        for (int i = 0; i < n; i++) w.tick();
    }

    static Proof inspect(
            byte[] raw,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            int[][] runs,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (raw == null
                || work == null
                || capacity == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || left < 1
                || top < 1
                || right >= width - 1
                || bottom >= height - 1
                || left > right
                || top > bottom
                || runs == null
                || runs.length < 3
                || runs.length > 4096
                || (long) (right - left + 1) * (bottom - top + 1) > 16384) return null;
        int h = bottom - top + 1;
        capacity.run();
        pay(work, h * 3 + 12);
        int[] first = new int[h], second = new int[h], count = new int[h];
        Arrays.fill(first, -1);
        Arrays.fill(second, -1);
        int previousRow = top - 1, previousRight = left - 2;
        for (int id = 0; id < runs.length; id++) {
            work.tick();
            int[] q = runs[id];
            if (q == null
                    || q.length != 4
                    || q[0] < top
                    || q[0] > bottom
                    || q[1] < left
                    || q[2] > right
                    || q[1] > q[2]
                    || q[3] < 0
                    || q[3] > 1
                    || q[0] < previousRow
                    || q[0] == previousRow && q[1] <= previousRight) return null;
            previousRow = q[0];
            previousRight = q[2];
            int row = q[0] - top;
            if (count[row] == 0) first[row] = id;
            else if (count[row] == 1) second[row] = id;
            count[row]++;
        }
        int split = -1;
        for (int y = 0; y < h; y++) {
            work.tick();
            if (count[y] != 1) {
                if (count[y] == 2) split = y;
                break;
            }
        }
        // A fixed first two-arm domain plus its literal continuation. No chosen fitting depth.
        if (split < 1 || split + 2 >= h || count[split + 1] != 2 || count[split + 2] != 2)
            return null;
        int end = split + 1, dl = right, dr = left, cells = 0;
        for (int y = 0; y <= end + 1; y++) {
            work.tick();
            int[] a = runs[first[y]];
            if (a[3] != 0) return null;
            if (y < split) {
                cells += a[2] - a[1] + 1;
                dl = Math.min(dl, a[1]);
                dr = Math.max(dr, a[2]);
            } else {
                int[] b = runs[second[y]];
                if (b[3] != 0 || a[2] + 1 >= b[1]) return null;
                if (y <= end) {
                    dl = Math.min(dl, a[1]);
                    dr = Math.max(dr, b[2]);
                }
                int[] pa = runs[first[y - 1]];
                if (a[1] > pa[2] + 1 || pa[1] > a[2] + 1) return null;
                int[] pb = y == split ? pa : runs[second[y - 1]];
                if (b[1] > pb[2] + 1 || pb[1] > b[2] + 1) return null;
            }
        }
        if (dl < 1 || dr >= width - 1) return null;
        // Every original dark/weak/paper sample is accounted for. The only exits are actual
        // next-row arm continuations; their pixels are not called white or assigned to the crest.
        for (int y = top - 1; y <= top + end + 1; y++)
            for (int x = dl - 1; x <= dr + 1; x++) {
                work.tick();
                boolean owned = false;
                if (y >= top) {
                    int row = y - top;
                    int[] a = runs[first[row]];
                    owned = x >= a[1] && x <= a[2];
                    if (row >= split) {
                        int[] b = runs[second[row]];
                        owned |= x >= b[1] && x <= b[2];
                    }
                }
                int v = raw[y * width + x] & 255;
                if (owned ? v > 165 : v != 255) return null;
            }
        ObservedCentreTurnFamily.Fit fit =
                ObservedCentreTurnFamily.inspect(
                        raw, width, height, dl, top, dr, top + end, null, work, capacity);
        if (fit == null || !fit.supported()) return null;
        capacity.run();
        pay(work, split + 12);
        int[] covered = new int[split];
        for (int y = 0; y < split; y++) {
            work.tick();
            covered[y] = first[y];
        }
        return new Proof(covered, first[split], second[split], cells, 2L * cells, fit);
    }
}
