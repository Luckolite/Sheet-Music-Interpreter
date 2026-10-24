// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** PRIVATE observed tone-envelope evidence. Never an eraser or note-removal API. */
final class LiteralStaffCompleteBeamJoint {
    static final class Budget {
        private long remaining;
        private boolean exhausted;

        Budget(long remaining) {
            if (remaining < 0 || remaining > 32_000_000) throw new IllegalArgumentException();
            this.remaining = remaining;
        }

        boolean take(long work) {
            if (exhausted || work < 0 || work > remaining) {
                exhausted = true;
                return false;
            }
            remaining -= work;
            return true;
        }
    }

    private record Frame(int[] values, int firstY, double measuredGap) {}

    record Result(boolean complete, String refusal, int median, int maximum, long workRemaining) {}

    static Result observe(
            byte[] gray,
            int width,
            int height,
            int leftShaft,
            int rightShaft,
            float top,
            float gap,
            float cx,
            float cy,
            int minX,
            int minY,
            int maxX,
            int maxY,
            float slope,
            Budget budget) {
        return observe(
                gray,
                width,
                height,
                leftShaft,
                rightShaft,
                top,
                gap,
                cx,
                cy,
                minX,
                minY,
                maxX,
                maxY,
                slope,
                budget,
                null);
    }

    record Ownership(byte[] labels, int[] members, int leftEnd, int rightEnd, int direction) {}

    static Result observe(
            byte[] gray,
            int width,
            int height,
            int leftShaft,
            int rightShaft,
            float top,
            float gap,
            float cx,
            float cy,
            int minX,
            int minY,
            int maxX,
            int maxY,
            float slope,
            Budget budget,
            Ownership ownership) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || !Float.isFinite(top)
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64
                || !Float.isFinite(cx)
                || !Float.isFinite(cy)
                || !Float.isFinite(slope)
                || Math.abs(slope) > .6
                || leftShaft >= rightShaft
                || rightShaft - leftShaft < 2 * gap
                || rightShaft - leftShaft > 6 * gap
                || minX > maxX
                || minY > maxY
                || cx < minX
                || cx > maxX
                || cy < minY
                || cy > maxY) return no("DOMAIN", budget);
        int left = leftShaft - (int) Math.ceil(6 * gap),
                right = rightShaft + (int) Math.ceil(6 * gap);
        int firstY = Math.round(top - gap * .48f), lastY = Math.round(top + gap * 4.48f);
        if (left < 1 || right >= width - 1 || firstY < 1 || lastY >= height - 1)
            return no("FRAME_SCOPE", budget);
        long area = (long) (right - left + 1) * (lastY - firstY + 1);
        if (area > 250_000 || !budget.take(area * 20 + 4096)) return no("FRAME_BUDGET", budget);
        // The original gray remains intact. A reference column accounts for exact
        // literal tones only, and never confers exclusive ownership of any pixel.
        List<Frame> leftFrames = new ArrayList<>(), rightFrames = new ArrayList<>();
        Frame previous = null;
        int run = 0, previousSide = -1;
        for (int x = left; x <= right; x++) {
            int side = x < leftShaft - 1 ? 0 : x > rightShaft + 1 ? 1 : -1;
            Frame f = side < 0 ? null : frame(gray, width, x, firstY, lastY, top, gap);
            if (f == null) {
                previous = null;
                run = 0;
                previousSide = -1;
                continue;
            }
            if (side == previousSide
                    && previous != null
                    && Arrays.equals(previous.values, f.values)) run++;
            else {
                previous = f;
                run = 1;
                previousSide = side;
            }
            if (run >= Math.ceil(2 * f.measuredGap)) {
                List<Frame> frames = side == 0 ? leftFrames : rightFrames;
                if (frames.stream().noneMatch(a -> Arrays.equals(a.values, f.values))) {
                    if (frames.size() == 16) return no("FRAME_AMBIGUITY_LIMIT", budget);
                    frames.add(f);
                }
            }
        }
        Frame chosen = null;
        for (Frame a : leftFrames)
            for (Frame b : rightFrames)
                if (Arrays.equals(a.values, b.values)) {
                    if (chosen != null && !Arrays.equals(chosen.values, a.values))
                        return no("MULTIPLE_LITERAL_FRAMES", budget);
                    chosen = a;
                }
        if (chosen == null) return no("NO_TWO_SIDED_LITERAL_FRAME", budget);
        int margin = Math.max(3, Math.round(gap * .22f)),
                lo = leftShaft + margin,
                hi = rightShaft - margin;
        CompleteBeamCapRoles.Shafts shafts = null;
        if (ownership != null) {
            shafts =
                    CompleteBeamCapRoles.shafts(
                            gray,
                            width,
                            height,
                            chosen.values,
                            firstY,
                            leftShaft,
                            rightShaft,
                            ownership.leftEnd(),
                            ownership.rightEnd(),
                            ownership.direction(),
                            gap,
                            budget);
            if (shafts == null) return no("INCOMPLETE_LITERAL_SHAFT_CAPS", budget);
            lo = shafts.left().right() + 1;
            hi = shafts.right().left() - 1;
        }
        int radius = (int) Math.ceil(gap), columns = hi - lo + 1;
        if (columns < 1) return no("EMPTY_LANE", budget);
        long laneArea = (long) columns * (2 * radius + 1);
        int sortDepth = 32 - Integer.numberOfLeadingZeros(Math.max(1, columns) - 1);
        long levelSortAndCopy = 255L * columns * (sortDepth + 3L);
        if (!budget.take(laneArea * 260 + levelSortAndCopy + 4096))
            return no("LEVEL_BUDGET", budget);
        int[][] contrasts = new int[columns][2 * radius + 1];
        int[] centers = new int[columns];
        for (int x = lo; x <= hi; x++) {
            int center = Math.round(cy + slope * (x - cx));
            centers[x - lo] = center;
            if (center - radius < 0 || center + radius >= height) return no("LANE_SCOPE", budget);
            for (int dy = -radius; dy <= radius; dy++) {
                int y = center + dy;
                int reference = y >= firstY && y <= lastY ? chosen.values[y - firstY] : 255;
                int observed = gray[y * width + x] & 255;
                if (observed > reference) return no("LITERAL_REFERENCE_CONTRADICTION", budget);
                contrasts[x - lo][dy + radius] = reference - observed;
            }
        }
        int baseMedian = 0, baseMaximum = 0;
        for (int level = 1; level <= 255; level++) {
            int[] spans = new int[columns];
            for (int x = 0; x < columns; x++) {
                int first = -1, last = -1;
                for (int y = 0; y < 2 * radius + 1; y++)
                    if (contrasts[x][y] >= level) {
                        if (first < 0) first = y;
                        last = y;
                    }
                spans[x] = first < 0 ? 0 : last - first + 1;
            }
            int[] sorted = spans.clone();
            Arrays.sort(sorted);
            int median = sorted[columns / 2], limit = median + Math.max(1, Math.round(gap * .12f));
            int count = 0, runLength = 0, longest = 0;
            for (int span : spans)
                if (span > limit) {
                    count++;
                    longest = Math.max(longest, ++runLength);
                } else runLength = 0;
            if (level == 1) {
                baseMedian = median;
                baseMaximum = sorted[columns - 1];
            }
            if (count > Math.max(1, Math.round(gap * .3f))
                    || longest > Math.max(1, Math.round(gap * .25f)))
                return new Result(
                        false,
                        "EXTRA_CONTRAST_GROWTH_" + level,
                        baseMedian,
                        baseMaximum,
                        budget.remaining);
        }
        if (baseMedian < gap * .25 || baseMaximum > gap * .9) return no("FULL_TONE_EXTENT", budget);
        int[] bodyTop = new int[columns], bodyBottom = new int[columns];
        int[] possibleTop = new int[columns], possibleBottom = new int[columns];
        int[] reverseTop = new int[columns], reverseBottom = new int[columns];
        int[] reversePossibleTop = new int[columns], reversePossibleBottom = new int[columns];
        for (int x = 0; x < columns; x++) {
            int first = -1, last = -1;
            for (int y = 0; y < 2 * radius + 1; y++)
                if (contrasts[x][y] > 0) {
                    if (first < 0) first = y;
                    last = y;
                }
            if (first < 1 || last >= 2 * radius)
                return no("MISSING_OR_CLIPPED_BODY_COLUMN", budget);
            bodyTop[x] = centers[x] - radius + first;
            bodyBottom[x] = centers[x] - radius + last;
            for (int y = first; y <= last; y++)
                if (contrasts[x][y] == 0) {
                    int absoluteY = centers[x] - radius + y;
                    // Only observed raw staff foreground may co-own an interior cell.
                    // White paper is never filled and no pixel is changed.
                    int reference =
                            absoluteY >= firstY && absoluteY <= lastY
                                    ? chosen.values[absoluteY - firstY]
                                    : 255;
                    if (reference == 255 || (gray[absoluteY * width + lo + x] & 255) != reference)
                        return no("INTERNAL_UNOWNED_PAPER", budget);
                }
            int potentialTop = first, potentialBottom = last;
            while (potentialTop > 0) {
                int yy = centers[x] - radius + potentialTop - 1;
                int ref = yy >= firstY && yy <= lastY ? chosen.values[yy - firstY] : 255;
                if (ref == 255 || (gray[yy * width + lo + x] & 255) != ref) break;
                potentialTop--;
            }
            while (potentialBottom < 2 * radius) {
                int yy = centers[x] - radius + potentialBottom + 1;
                int ref = yy >= firstY && yy <= lastY ? chosen.values[yy - firstY] : 255;
                if (ref == 255 || (gray[yy * width + lo + x] & 255) != ref) break;
                potentialBottom++;
            }
            if (potentialTop == 0 || potentialBottom == 2 * radius)
                return no("CLIPPED_COOWNED_EDGE", budget);
            possibleTop[x] = centers[x] - radius + potentialTop;
            possibleBottom[x] = centers[x] - radius + potentialBottom;
            reverseTop[x] = height - 1 - bodyBottom[x];
            reverseBottom[x] = height - 1 - bodyTop[x];
            reversePossibleTop[x] = height - 1 - possibleBottom[x];
            reversePossibleBottom[x] = height - 1 - possibleTop[x];
        }
        if (!budget.take(16L * columns * columns + 4096)) return no("PARALLEL_BUDGET", budget);
        if (!CoownedParallelBodyIntervals.fits(
                        bodyTop, bodyBottom, possibleTop, possibleBottom, baseMedian)
                || !CoownedParallelBodyIntervals.fits(
                        reverseTop,
                        reverseBottom,
                        reversePossibleTop,
                        reversePossibleBottom,
                        baseMedian)) return no("NONPARALLEL_FULL_TONE_BODY", budget);
        int l = Math.round(cx - gap * 1.2f),
                r = Math.round(cx + gap * 1.2f),
                t = Math.round(cy - gap * 1.2f),
                b = Math.round(cy + gap * 1.2f);
        if (l < 0 || r >= width || t < 0 || b >= height) return no("COUNTER_SCOPE", budget);
        int rw = r - l + 1, rh = b - t + 1, n = rw * rh;
        if (!budget.take(255L * n * 12 + 4096)) return no("COUNTER_BUDGET", budget);
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        for (int level = 1; level <= 255; level++) {
            Arrays.fill(seen, false);
            for (int seed = 0; seed < n; seed++) {
                if (seen[seed] || (gray[(t + seed / rw) * width + l + seed % rw] & 255) < level)
                    continue;
                seen[seed] = true;
                queue[0] = seed;
                int read = 0, count = 1;
                long sx = 0, sy = 0;
                boolean edge = false;
                while (read < count) {
                    int p = queue[read++], x = p % rw, y = p / rw;
                    sx += l + x;
                    sy += t + y;
                    edge |= x == 0 || x == rw - 1 || y == 0 || y == rh - 1;
                    for (int dir = 0; dir < 4; dir++) {
                        int xx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0),
                                yy = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                        if (xx < 0 || xx >= rw || yy < 0 || yy >= rh) continue;
                        int at = yy * rw + xx;
                        if (seen[at] || (gray[(t + yy) * width + l + xx] & 255) < level) continue;
                        seen[at] = true;
                        queue[count++] = at;
                    }
                }
                float px = sx / (float) count, py = sy / (float) count;
                if (!edge && px >= minX && px <= maxX && py >= minY - 2 && py <= maxY + 2)
                    return new Result(
                            false,
                            "RAW_COUNTER_" + level,
                            baseMedian,
                            baseMaximum,
                            budget.remaining);
            }
        }
        if (ownership != null) {
            String refusal =
                    CompleteBeamCapRoles.prove(
                            gray,
                            width,
                            height,
                            ownership.labels(),
                            ownership.members(),
                            chosen.values,
                            firstY,
                            lo,
                            hi,
                            bodyTop,
                            bodyBottom,
                            possibleTop,
                            possibleBottom,
                            baseMedian,
                            minX,
                            minY,
                            maxX,
                            maxY,
                            shafts,
                            budget);
            if (refusal != null)
                return new Result(false, refusal, baseMedian, baseMaximum, budget.remaining);
        }
        return new Result(true, null, baseMedian, baseMaximum, budget.remaining);
    }

    private static Frame frame(
            byte[] g, int w, int x, int firstY, int lastY, float top, float gap) {
        int[] values = new int[lastY - firstY + 1];
        for (int y = firstY; y <= lastY; y++) values[y - firstY] = g[y * w + x] & 255;
        double[] minima = new double[5];
        for (int k = 0; k < 5; k++) {
            int lo = Math.round(top + k * gap - gap * .48f),
                    hi = Math.round(top + k * gap + gap * .48f);
            if (lo < firstY
                    || hi > lastY
                    || (g[lo * w + x] & 255) != 255
                    || (g[hi * w + x] & 255) != 255) return null;
            int min = 255, start = -1, end = -1, runs = 0;
            long sy = 0;
            int n = 0;
            for (int y = lo; y <= hi; y++) {
                int v = g[y * w + x] & 255;
                if (v < 245) {
                    if (y == lo || (g[(y - 1) * w + x] & 255) >= 245) runs++;
                    if (start < 0) start = y;
                    end = y;
                }
                if (v < min) {
                    min = v;
                    sy = y;
                    n = 1;
                } else if (v == min) {
                    sy += y;
                    n++;
                }
            }
            if (min >= 165 || runs != 1 || end - start + 1 > Math.ceil(.35 * gap)) return null;
            minima[k] = sy / (double) n;
        }
        double measured = (minima[4] - minima[0]) / 4;
        if (measured < gap * .9 || measured > gap * 1.1) return null;
        for (int k = 0; k < 5; k++)
            if (Math.abs(minima[k] - minima[0] - k * measured) > .1 * gap) return null;
        return new Frame(values, firstY, measured);
    }

    private static Result no(String reason, Budget budget) {
        return new Result(false, reason, 0, 0, budget.remaining);
    }
}
