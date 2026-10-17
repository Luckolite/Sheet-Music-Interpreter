// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A separated, continuously bending stroke cannot extend a completed beam shaft. */
final class DetachedSlurStemCap {
    private DetachedSlurStemCap() {}

    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int x,
            int end,
            int refined,
            int side,
            float gap,
            int threshold) {
        return matches(gray, width, height, x, end, refined, side, gap, threshold, 2);
    }

    /** Used only with a terminal rail proved on two probes and a zero-gap shaft. */
    static boolean matchesSingleBlankCut(
            byte[] gray,
            int width,
            int height,
            int x,
            int end,
            int refined,
            int side,
            float gap,
            int threshold) {
        return matches(gray, width, height, x, end, refined, side, gap, threshold, 1);
    }

    private static boolean matches(
            byte[] gray,
            int width,
            int height,
            int x,
            int end,
            int refined,
            int side,
            float gap,
            int threshold,
            int blankRows) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || gap < 8
                || !Float.isFinite(gap)
                || gap > Math.min(width, height) / 6f
                || Math.abs(side) != 1
                || threshold < 1
                || threshold > 220) return false;
        float separation = (end - refined) * side;
        int span = Math.round(gap * 3), radius = Math.round(gap * .8f);
        if (separation < gap * .35f
                || separation > gap * 1.2f
                || x - span < 1
                || x + span >= width - 1
                || end - radius < 1
                || end + radius >= height - 1
                || refined < 1
                || refined >= height - 1) return false;
        int blank = 0;
        boolean separated = false;
        for (int y = refined + side; y != end; y += side) {
            boolean clear = true;
            for (int xx = x - 1; xx <= x + 1; xx++)
                if ((gray[y * width + xx] & 255) < threshold) clear = false;
            blank = clear ? blank + 1 : 0;
            if (blank >= blankRows) separated = true;
        }
        if (!separated || (gray[end * width + x] & 255) >= threshold) return false;
        int top = end, bottom = end;
        while (top > 0 && (gray[(top - 1) * width + x] & 255) < threshold) top--;
        while (bottom + 1 < height && (gray[(bottom + 1) * width + x] & 255) < threshold) bottom++;
        if (bottom - top + 1 < 2 || bottom - top + 1 > gap * .6f) return false;
        float center = (top + bottom) * .5f;
        // Shorter spans can prove the bend before the curve merges into a staff rule.
        for (int followSpan : new int[] {span, Math.round(gap * 2.5f), Math.round(gap * 2)}) {
            float left = follow(gray, width, height, x, center, gap, threshold, -1, followSpan);
            float right = follow(gray, width, height, x, center, gap, threshold, 1, followSpan);
            if (Float.isFinite(left) && Float.isFinite(right)
                    && (center - (left + right) * .5f) * side >= gap * .25f) return true;
        }
        return false;
    }

    static boolean crossesRecovery(
            byte[] gray,
            int width,
            int height,
            int[] strict,
            int[] pale,
            float gap,
            int threshold) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || strict == null
                || pale == null
                || strict.length < 3
                || pale.length < 3
                || !Float.isFinite(gap)
                || gap < 8
                || gap > Math.min(width, height) / 6f
                || threshold < 1
                || threshold > 220
                || Math.abs(strict[2]) != 1
                || pale[2] != strict[2]
                || Math.abs(pale[0] - strict[0]) > gap * .3f
                || (pale[1] - strict[1]) * strict[2] < gap * .5f
                || (pale[1] - strict[1]) * strict[2] > gap * 1.5f) return false;
        int x = pale[0];
        if (x < 1 || x >= width - 1) return false;
        // Pale traces include antialiased fringes; anchor the proof to actual dark ink.
        for (int step = 0; step <= Math.round(gap * .35f); step++) {
            int end = pale[1] - pale[2] * step;
            if (end < 1 || end >= height - 1) return false;
            if ((gray[end * width + x] & 255) < threshold)
                return matches(gray, width, height, x, end, strict[1], strict[2], gap, threshold);
        }
        return false;
    }

    private static float follow(
            byte[] gray,
            int width,
            int height,
            int x,
            float center,
            float gap,
            int threshold,
            int side,
            int span) {
        float previous = center;
        for (int dx = 1; dx <= span; dx++) {
            int xx = x + side * dx,
                    lo = Math.max(0, (int) Math.floor(previous - gap)),
                    hi = Math.min(height - 1, (int) Math.ceil(previous + gap));
            float nearest = Float.NaN, best = Float.POSITIVE_INFINITY;
            for (int y = lo; y <= hi; ) {
                if ((gray[y * width + xx] & 255) >= threshold) {
                    y++;
                    continue;
                }
                int start = y;
                while (y <= hi && (gray[y * width + xx] & 255) < threshold) y++;
                float candidate = (start + y - 1) * .5f, delta = Math.abs(candidate - previous);
                if (start > lo
                        && y <= hi
                        && y - start >= 2
                        && y - start <= gap * .6f
                        && delta <= 2
                        && delta < best) {
                    nearest = candidate;
                    best = delta;
                }
            }
            if (!Float.isFinite(nearest)) return Float.NaN;
            previous = nearest;
        }
        return previous;
    }
}
