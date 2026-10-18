// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Actual finite terminal pixels jointly connected to a continuous shaft and observed rail. */
final class FiniteShaftFoot {
    private final int left, top, width;
    private final boolean[] pixels;

    private FiniteShaftFoot(int left, int top, int width, boolean[] pixels) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.pixels = pixels;
    }

    boolean contains(int x, int y) {
        int xx = x - left, yy = y - top;
        return xx >= 0
                && xx < width
                && yy >= 0
                && yy < pixels.length / width
                && pixels[yy * width + xx];
    }

    static FiniteShaftFoot prove(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int start,
            int[] ends,
            int railX,
            int railTop,
            int railBottom,
            int[] terminalCeilings) {
        if (gray == null
                || gray.length != (long) w * h
                || left < 1
                || right >= w - 1
                || left > right
                || ends == null
                || ends.length != right - left + 1
                || terminalCeilings == null
                || terminalCeilings.length != ends.length
                || railTop < 1
                || railBottom >= h - 1
                || railTop > railBottom
                || railX != left - 1 && railX != right + 1) return null;
        int top = railTop, bottom = railBottom;
        for (int end : ends) {
            if (end < start || end >= h - 1) return null;
            top = Math.min(top, end);
            bottom = Math.max(bottom, end);
        }
        if (top < 1 || bottom >= h - 1 || top < start) return null;
        int width = ends.length;
        long size = (long) width * (bottom - top + 1);
        if (size > Integer.MAX_VALUE) return null;
        boolean[] seen = new boolean[(int) size], owned = new boolean[(int) size];
        int[] queue = new int[(int) size];
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                int p = (y - top) * width + x - left;
                if (seen[p] || (gray[y * w + x] & 255) >= 170) continue;
                int head = 0, tail = 0;
                queue[tail++] = p;
                seen[p] = true;
                boolean shaft = false, rail = false;
                while (head < tail) {
                    int at = queue[head++], xx = left + at % width, yy = top + at / width;
                    boolean continuous = yy >= start && yy <= ends[xx - left];
                    if (continuous) shaft = true;
                    // Added terminal ink cannot extend below ANY feasible parallel band's soft end.
                    if (!continuous && yy > terminalCeilings[xx - left]) return null;
                    if (Math.abs(xx - railX) == 1
                            && yy >= railTop
                            && yy <= railBottom
                            && (gray[yy * w + railX] & 255) < 170) rail = true;
                    for (int d = 0; d < 4; d++) {
                        int nx = xx + (d == 0 ? -1 : d == 1 ? 1 : 0),
                                ny = yy + (d == 2 ? -1 : d == 3 ? 1 : 0);
                        if (nx < left || nx > right || ny < top || ny > bottom) continue;
                        int next = (ny - top) * width + nx - left;
                        if (!seen[next] && (gray[ny * w + nx] & 255) < 170) {
                            seen[next] = true;
                            queue[tail++] = next;
                        }
                    }
                }
                boolean added = false;
                for (int k = 0; k < tail; k++) {
                    int at = queue[k], xx = at % width, yy = top + at / width;
                    added |= yy > ends[xx];
                }
                if (added && (!shaft || !rail)) return null;
                for (int k = 0; k < tail; k++) owned[queue[k]] = true;
            }
        // Even an all-continuous foot must show actual side-by-side raw rail contact.
        boolean contact = false;
        int side = railX < left ? left : right;
        for (int y = railTop; y <= railBottom; y++) {
            int at = (y - top) * width + side - left;
            contact |= owned[at] && (gray[y * w + railX] & 255) < 170;
        }
        return contact ? new FiniteShaftFoot(left, top, width, owned) : null;
    }
}
