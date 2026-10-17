// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Endpoint ownership for an isolated secondary stub beside one complete primary rail. */
final class GraceBeamEndpointInk {
    private record Band(int first, int last) {
        float center() {
            return (first + last) * .5f;
        }
    }

    private GraceBeamEndpointInk() {}

    static int[] mixed(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 5
                || Math.abs(a[2]) != 1
                || a[2] != b[2]) return null;
        int span = b[0] - a[0];
        if (span < gap * .95f
                || span > gap * 3
                || Math.abs(a[1] - b[1]) > gap
                || a[0] < 1
                || b[0] >= width - 1) return null;
        int start = -Math.round(gap * .25f), end = Math.round(gap * 1.5f);
        int rows = end - start + 1, columns = span - 3;
        if (columns < 8) return null;
        int limit =
                BeamInkThreshold.at(
                        gray,
                        width,
                        height,
                        (a[0] + b[0]) / 2,
                        Math.min(a[1], b[1]) - Math.round(gap),
                        Math.max(a[1], b[1]) + Math.round(gap * 2),
                        gap);
        boolean[][] ink = new boolean[rows][columns];
        for (int row = 0; row < rows; row++)
            for (int c = 0; c < columns; c++) {
                int dx = c + 2, x = a[0] + dx;
                int y = Math.round(a[1] + (b[1] - a[1]) * dx / (float) span - a[2] * (row + start));
                if (y < 1 || y >= height - 1) return null;
                ink[row][c] = (gray[y * width + x] & 255) < limit;
            }
        boolean[] full = new boolean[rows];
        int[][] ends = new int[2][rows];
        for (int row = 0; row < rows; row++) {
            int dark = 0;
            for (boolean value : ink[row]) if (value) dark++;
            full[row] = dark >= columns * .95f;
            for (int side = 0; side < 2; side++) {
                int run = 0;
                while (run < columns && ink[row][side == 0 ? run : columns - 1 - run]) run++;
                int stray = 0;
                for (int c = run; c < columns; c++)
                    if (ink[row][side == 0 ? c : columns - 1 - c]) stray++;
                ends[side][row] =
                        run >= gap * .35f && run + 2 <= span * .55f && stray <= 1 ? run : 0;
            }
        }
        List<Band> primary = bands(full);
        int minimum = Math.max(3, Math.round(gap * .18f));
        primary.removeIf(
                band ->
                        band.last - band.first + 1 < minimum
                                || band.last - band.first + 1 > gap * .6f);
        if (primary.size() != 1) return null;
        Band rail = primary.get(0);
        int owner = -1;
        for (int side = 0; side < 2; side++) {
            boolean[] partial = new boolean[rows];
            for (int row = 0; row < rows; row++) partial[row] = ends[side][row] > 0;
            for (Band band : bands(partial)) {
                float separation = band.center() - rail.center();
                if (band.last - band.first + 1 < minimum
                        || band.last - band.first + 1 > gap * .6f
                        || separation < gap * .3f
                        || separation > gap * .9f) continue;
                int shortest = Integer.MAX_VALUE, longest = 0;
                for (int row = band.first; row <= band.last; row++) {
                    shortest = Math.min(shortest, ends[side][row]);
                    longest = Math.max(longest, ends[side][row]);
                }
                if (longest - shortest > 1) {
                    boolean core = false;
                    for (int trimFirst = 0; trimFirst <= 1; trimFirst++)
                        for (int trimLast = 0; trimLast <= 1; trimLast++) {
                            int first = band.first + trimFirst, last = band.last - trimLast;
                            if (last - first + 1 < minimum) continue;
                            int lo = Integer.MAX_VALUE, hi = 0;
                            for (int row = first; row <= last; row++) {
                                lo = Math.min(lo, ends[side][row]);
                                hi = Math.max(hi, ends[side][row]);
                            }
                            if (hi - lo <= 1) core = true;
                        }
                    if (!core) continue;
                }
                // The white channel must separate the primary from the secondary body.
                int channel = Math.round((rail.last + band.first) * .5f);
                int dark = 0;
                int shaft = Math.max(2, Math.round(gap * .2f));
                for (int c = shaft; c < columns - shaft; c++) if (ink[channel][c]) dark++;
                if (dark > 1) continue;
                if (owner >= 0) return null;
                owner = side;
            }
        }
        return owner < 0 ? null : owner == 0 ? new int[] {2, 1} : new int[] {1, 2};
    }

    private static List<Band> bands(boolean[] rows) {
        var result = new ArrayList<Band>();
        int first = -1;
        for (int row = 0; row <= rows.length; row++) {
            if (row < rows.length && rows[row]) {
                if (first < 0) first = row;
            } else if (first >= 0) {
                result.add(new Band(first, row - 1));
                first = -1;
            }
        }
        return result;
    }
}
