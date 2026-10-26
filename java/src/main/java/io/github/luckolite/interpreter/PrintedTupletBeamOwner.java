// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** A numeral beside a continuous beam belongs to its attached shafts, even between staves. */
final class PrintedTupletBeamOwner {
    /** New fallback's invocation-owned logical work. Legacy queries do not use this pool. */
    static final class LiteralWork {
        final long limit;
        long used;
        boolean exhausted;

        LiteralWork(long limit) {
            if (limit < 0 || limit > 8_000_000)
                throw new IllegalArgumentException("Tuplet work limit");
            this.limit = limit;
        }

        void take(long units) {
            if (units < 0) throw new IllegalArgumentException("Negative tuplet work");
            if (exhausted || units > limit - used) {
                exhausted = true;
                throw new LiteralLimit();
            }
            used += units;
        }
    }

    private static final class LiteralLimit extends RuntimeException {}

    /** Same-call occurrence-bound evidence. No Source frame or external ink assertion. */
    static final class LiteralProof {
        record NumeralWindow(byte[] pixels, int width, int height, int left, int top) {}

        final byte[] raw;
        final int width, height, direction;
        final float gap;
        final LiteralWork work;
        final float[] xs = new float[3], ys = new float[3];
        final java.util.List<Tip>[] ends;
        int cacheLeft, cacheTop, cacheWidth, cacheHeight;
        byte[][] inkCache;
        byte[] firstMiddle, middleLast;

        @SuppressWarnings("unchecked")
        private LiteralProof(
                byte[] raw, int width, int height, float gap, int direction, LiteralWork work) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.gap = gap;
            this.direction = direction;
            this.work = work;
            ends = (java.util.List<Tip>[]) new java.util.List<?>[3];
        }

        static LiteralProof create(
                byte[] raw,
                int width,
                int height,
                java.util.List<ScoreNoteEvent> heads,
                int realCount,
                java.util.List<MeasureRegion> measures,
                int a,
                int b,
                int c,
                float gap,
                LiteralWork work) {
            return create(
                    raw, width, height, heads, realCount, measures, a, b, c, gap, work, List.of());
        }

        static LiteralProof create(
                byte[] raw,
                int width,
                int height,
                List<ScoreNoteEvent> heads,
                int realCount,
                List<MeasureRegion> measures,
                int a,
                int b,
                int c,
                float gap,
                LiteralWork work,
                List<ScoreRestEvent> rests) {
            if (work == null
                    || work.exhausted
                    || raw == null
                    || width < 3
                    || height < 3
                    || (long) width * height != raw.length
                    || raw.length > 32_000_000
                    || !Float.isFinite(gap)
                    || gap < 4
                    || gap > 64
                    || heads == null
                    || measures == null
                    || rests == null
                    || rests.size() > 4096
                    || realCount < 3
                    || realCount > 4096
                    || realCount > heads.size()
                    || a < 0
                    || a >= b
                    || b >= c
                    || c >= realCount) return null;
            try {
                work.take(64 + realCount * 16L);
                var first = heads.get(a);
                if (first == null
                        || first.measureIndex() < 0
                        || first.measureIndex() >= measures.size()) return null;
                var bar = measures.get(first.measureIndex());
                if (bar == null) return null;
                var proof = new LiteralProof(raw, width, height, gap, first.stemDirection(), work);
                int[] owners = {a, b, c};
                for (int k = 0; k < 3; k++) {
                    var head = heads.get(owners[k]);
                    if (head == null
                            || head.staffCount() < 1
                            || head.staffIndex() < 0
                            || head.staffIndex() >= head.staffCount()
                            || head.kind() != ScoreNoteEvent.Kind.PITCHED
                            || head.crossStaffBeam()
                            || (head.articulations() & NoteOrnament.GRACE) != 0
                            || head.tupletDivisor() != 1
                            || head.augmentationDots() != 0
                            || (head.beamCount() != 1 && head.beamCount() != 2)
                            || head.beamCount() != first.beamCount()
                            || Math.abs(head.stemDirection()) != 1
                            || head.stemDirection() != first.stemDirection()
                            || head.measureIndex() != first.measureIndex()
                            || head.staffIndex() != first.staffIndex()
                            || head.staffCount() != first.staffCount()) return null;
                    proof.xs[k] =
                            (bar.left() + head.positionInMeasure() * (bar.right() - bar.left()))
                                    * width;
                    proof.ys[k] = head.pageY() * height;
                    if (!Float.isFinite(proof.xs[k])
                            || !Float.isFinite(proof.ys[k])
                            || proof.xs[k] < gap
                            || proof.xs[k] >= width - gap
                            || proof.ys[k] < gap
                            || proof.ys[k] >= height - gap
                            || k > 0 && proof.xs[k] <= proof.xs[k - 1]) return null;
                }
                if (proof.xs[2] - proof.xs[0] < 2 * gap || proof.xs[2] - proof.xs[0] > 18 * gap)
                    return null;
                // This is an occurrence census, never a value lookup or selected-list index.
                for (int i = 0; i < realCount; i++) {
                    if (i == a || i == b || i == c) continue;
                    var rival = heads.get(i);
                    if (rival == null
                            || rival.staffCount() < 1
                            || rival.staffIndex() < 0
                            || rival.staffIndex() >= rival.staffCount()
                            || rival.measureIndex() < 0
                            || rival.measureIndex() >= measures.size()) return null;
                    var rivalBar = measures.get(rival.measureIndex());
                    if (rivalBar == null) return null;
                    float x =
                            (rivalBar.left()
                                            + rival.positionInMeasure()
                                                    * (rivalBar.right() - rivalBar.left()))
                                    * width;
                    float y = rival.pageY() * height;
                    if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
                    // Include full head bodies and the possible numeral-window footprint.
                    if (x >= proof.xs[0] - gap * 2
                            && x <= proof.xs[2] + gap * 2
                            && y
                                    >= Math.min(proof.ys[0], Math.min(proof.ys[1], proof.ys[2]))
                                            - gap * 12
                            && y
                                    <= Math.max(proof.ys[0], Math.max(proof.ys[1], proof.ys[2]))
                                            + gap * 12) return null;
                }
                work.take(32L + 16L * rests.size());
                for (var rest : rests) {
                    if (rest == null
                            || rest.measureIndex() < 0
                            || rest.measureIndex() >= measures.size()
                            || rest.staffCount() < 1
                            || rest.staffIndex() < 0
                            || rest.staffIndex() >= rest.staffCount()
                            || !Float.isFinite(rest.positionInMeasure())
                            || !Float.isFinite(rest.pageY())
                            || !Double.isFinite(rest.durationBeats())
                            || rest.durationBeats() <= 0) return null;
                    if (rest.isFullMeasure()
                            && rest.measureIndex() == first.measureIndex()
                            && rest.staffIndex() == first.staffIndex()
                            && rest.staffCount() == first.staffCount()) return null;
                    var restBar = measures.get(rest.measureIndex());
                    if (restBar == null) return null;
                    float x =
                            (restBar.left()
                                            + rest.positionInMeasure()
                                                    * (restBar.right() - restBar.left()))
                                    * width;
                    float y = rest.pageY() * height;
                    if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
                    if (x >= proof.xs[0] - gap * 2
                            && x <= proof.xs[2] + gap * 2
                            && y
                                    >= Math.min(proof.ys[0], Math.min(proof.ys[1], proof.ys[2]))
                                            - gap * 12
                            && y
                                    <= Math.max(proof.ys[0], Math.max(proof.ys[1], proof.ys[2]))
                                            + gap * 12) return null;
                }
                return proof;
            } catch (LiteralLimit unavailable) {
                return null;
            }
        }

        boolean prepared, usable;

        boolean reserve(long units) {
            if (work.exhausted) return false;
            try {
                work.take(units);
                return true;
            } catch (LiteralLimit unavailable) {
                return false;
            }
        }

        boolean preparePixels() {
            if (prepared) return usable;
            prepared = true;
            cacheLeft = Math.max(0, (int) Math.floor(xs[0] - gap));
            cacheTop =
                    Math.max(
                            0,
                            (int) Math.floor(Math.min(ys[0], Math.min(ys[1], ys[2])) - gap * 6.5));
            int cacheRight = Math.min(width - 1, (int) Math.ceil(xs[2] + gap));
            int cacheBottom =
                    Math.min(
                            height - 1,
                            (int) Math.ceil(Math.max(ys[0], Math.max(ys[1], ys[2])) + gap * 6.5));
            cacheWidth = cacheRight - cacheLeft + 1;
            cacheHeight = cacheBottom - cacheTop + 1;
            work.take(32 + 3L * cacheWidth * cacheHeight);
            inkCache = new byte[3][cacheWidth * cacheHeight];
            for (int k = 0; k < 3; k++) {
                ends[k] = literalTips(xs[k], ys[k], k == 2 ? -1 : 1);
                if (ends[k].isEmpty()) return false;
            }
            int ab = ends[0].size() * ends[1].size();
            int bc = ends[1].size() * ends[2].size();
            work.take(16L + ab + bc);
            firstMiddle = new byte[ab];
            middleLast = new byte[bc];
            usable = true;
            return true;
        }

        int pixel(int x, int y) {
            work.take(1);
            return x < 0 || x >= width || y < 0 || y >= height ? 255 : raw[y * width + x] & 255;
        }

        boolean ink(int x, int y, boolean shaft, boolean neck) {
            work.take(2);
            int mode = !shaft ? 0 : neck ? 2 : 1;
            int slot =
                    x >= cacheLeft
                                    && x < cacheLeft + cacheWidth
                                    && y >= cacheTop
                                    && y < cacheTop + cacheHeight
                            ? (y - cacheTop) * cacheWidth + x - cacheLeft
                            : -1;
            if (slot >= 0 && inkCache[mode][slot] != 0) return inkCache[mode][slot] == 2;
            boolean value = uncachedInk(x, y, shaft, neck);
            if (slot >= 0) inkCache[mode][slot] = (byte) (value ? 2 : 1);
            return value;
        }

        boolean uncachedInk(int x, int y, boolean shaft, boolean neck) {
            int v = pixel(x, y);
            if (v < 165) return true;
            if (v > 229 || x < 0 || x >= width || y < 0 || y >= height) return false;
            return PrintedBeamRawContrast.faint(raw, width, height, x, y, gap, shaft, work::take)
                    || neck
                            && PrintedBeamRawContrast.neck(
                                    raw, width, height, x, y, gap, work::take)
                    || shaft
                            && PrintedBeamRawContrast.crossing(
                                    raw, width, height, x, y, gap, work::take);
        }

        java.util.List<Tip> literalTips(float hx, float hy, int side) {
            work.take(16);
            var found = new java.util.ArrayList<Tip>();
            int center = Math.round(hx + direction * gap * .3f),
                    radius = Math.max(1, Math.round(gap * .5f));
            int travel = -direction, firstDistance = Math.round(gap * .6f);
            int start = Math.round(hy + travel * firstDistance);
            int l = Math.round(hx - gap * .8f), r = Math.round(hx + gap * .8f);
            int t = Math.round(hy - gap * .65f), b = Math.round(hy + gap * .65f);
            int w = r - l + 1, h = b - t + 1;
            work.take(16L + w * h * 4L);
            boolean[] seen = new boolean[w * h];
            int[] queue = new int[w * h];
            int from = 0, to = 0, bodyRows = 0, minimum = Math.max(3, (int) Math.ceil(gap * .25));
            for (int y = t; y <= b; y++) {
                int first = r + 1, last = l - 1, hits = 0;
                if (pixel(l, y) < 165 || pixel(r, y) < 165) continue;
                for (int x = l; x <= r; x++)
                    if (pixel(x, y) < 165) {
                        first = Math.min(first, x);
                        last = x;
                        hits++;
                    }
                if (last - first + 1 < gap * .5 || last - first + 1 > gap * 1.8 || hits < minimum)
                    continue;
                bodyRows++;
                if (Math.abs(y - hy) > gap * .35) continue;
                for (int x = first; x <= last; x++)
                    if (Math.abs(x - hx) <= gap * .35 && pixel(x, y) < 165) {
                        int q = (y - t) * w + x - l;
                        if (!seen[q]) {
                            seen[q] = true;
                            queue[to++] = q;
                        }
                    }
            }
            if (bodyRows < minimum || to == 0) return found;
            while (from < to) {
                work.take(16);
                int q = queue[from++], x = l + q % w, y = t + q / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < l || nx > r || ny < t || ny > b) continue;
                        int n = (ny - t) * w + nx - l;
                        if (!seen[n] && ink(nx, ny, true, true)) {
                            seen[n] = true;
                            queue[to++] = n;
                        }
                    }
            }
            for (int x = center - radius; x <= center + radius; x++) {
                if (x < l || x > r || start < t || start > b || !seen[(start - t) * w + x - l])
                    continue;
                for (int d = firstDistance; d <= gap * 6; d++) {
                    work.take(4);
                    int y = Math.round(hy + travel * d);
                    if (!ink(x, y, true, false)) break; // no white gaps, no aggregate fill
                    if (d >= gap * 1.4f && literalOutward(x, y, side)) {
                        work.take(8);
                        found.add(new Tip(x, y));
                    }
                }
            }
            return found;
        }

        boolean literalCore(int x, int y) {
            int radius = Math.max(1, Math.round(gap * .08f));
            for (int dy = -radius; dy <= radius; dy++)
                if (!ink(x, y + dy, false, false)) return false;
            return true;
        }

        boolean literalOutward(int x, int y, int side) {
            for (int angle = -10; angle <= 10; angle++) {
                work.take(4);
                boolean all = true;
                for (int d = Math.round(gap * .25f); d <= gap * 1.35f; d++)
                    if (!literalCore(x + side * d, Math.round(y + angle * .06f * d))) {
                        all = false;
                        break;
                    }
                if (all) return true;
            }
            return false;
        }

        boolean span(Tip a, Tip b) {
            work.take(8);
            if (b.x() - a.x() < gap * .5f || Math.abs(b.y() - a.y()) > gap * 3) return false;
            for (int x = a.x(); x <= b.x(); x++) {
                int y = Math.round(a.y() + (b.y() - a.y()) * (x - a.x()) / (float) (b.x() - a.x()));
                if (!literalCore(x, y)) return false;
            }
            // Bridge each centerline step with actual vertical pixels at that column.
            // A steep interpolated step must not jump over an unvisited white gap.
            int previous = a.y();
            for (int x = a.x() + 1; x <= b.x(); x++) {
                int y = Math.round(a.y() + (b.y() - a.y()) * (x - a.x()) / (float) (b.x() - a.x()));
                for (int row = Math.min(previous, y); row <= Math.max(previous, y); row++)
                    if (!ink(x, row, false, false)) return false;
                previous = y;
            }
            return true;
        }

        boolean pair(int side, int left, int right) {
            work.take(2);
            byte[] cache = side == 0 ? firstMiddle : middleLast;
            int slot = left * ends[side + 1].size() + right;
            if (cache[slot] == 0)
                cache[slot] =
                        (byte) (span(ends[side].get(left), ends[side + 1].get(right)) ? 2 : 1);
            return cache[slot] == 2;
        }

        boolean closed(Tip a, Tip c) {
            int radius = Math.max(1, Math.round(gap * .08f));
            for (int side = -1; side <= 1; side += 2) {
                int edge = side < 0 ? a.x() : c.x();
                for (int d = Math.round(gap * .35f); d <= gap; d++) {
                    int x = edge + side * d;
                    int y =
                            Math.round(
                                    a.y()
                                            + (c.y() - a.y())
                                                    * (x - a.x())
                                                    / (float) (c.x() - a.x()));
                    for (int dy = -radius; dy <= radius; dy++)
                        if (x < 0
                                || x >= width
                                || y + dy < 0
                                || y + dy >= height
                                || pixel(x, y + dy) < 230) return false;
                }
            }
            return true;
        }

        NumeralWindow numeralWindow(int left, int top, int right, int bottom) {
            if (work.exhausted
                    || left < 0
                    || top < 0
                    || right >= width
                    || bottom >= height
                    || right < left
                    || bottom < top
                    || right - left > gap * 2.3f
                    || bottom - top > gap * 2.3f) return null;
            try {
                int margin = Math.max(3, (int) Math.ceil(gap * .5));
                int l = left - margin, t = top - margin, r = right + margin, b = bottom + margin;
                if (l < 0 || t < 0 || r >= width || b >= height) return null;
                int w = r - l + 1, h = b - t + 1;
                work.take(64L + (long) w * h * 3);
                byte[] pixels = new byte[w * h];
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        work.take(2);
                        pixels[y * w + x] = ink(x + l, y + t, false, false) ? 0 : (byte) 255;
                    }
                work.take(32L + 5L * w * h);
                boolean[] seen = new boolean[w * h];
                int[] queue = new int[w * h];
                int closedComponents = 0;
                for (int seed = 0; seed < pixels.length; seed++) {
                    work.take(1);
                    if (seen[seed] || pixels[seed] != 0) continue;
                    boolean clipped = false;
                    int from = 0, to = 1;
                    queue[0] = seed;
                    seen[seed] = true;
                    while (from < to) {
                        work.take(2);
                        int at = queue[from++], x = at % w, y = at / w;
                        if (x == 0 || x == w - 1 || y == 0 || y == h - 1) clipped = true;
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                work.take(1);
                                int nx = x + dx, ny = y + dy;
                                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                                int n = ny * w + nx;
                                if (!seen[n] && pixels[n] == 0) {
                                    seen[n] = true;
                                    queue[to++] = n;
                                }
                            }
                    }
                    if (!clipped && ++closedComponents > 1) return null;
                }
                if (closedComponents != 1) return null;
                // A distinct local glyph-reader pass has a bounded prepaid contract.
                work.take(4096L + (long) w * h * 1024);
                return new NumeralWindow(pixels, w, h, l, t);
            } catch (LiteralLimit unavailable) {
                return null;
            }
        }

        float distance(int left, int top, int right, int bottom) {
            if (work.exhausted) return Float.POSITIVE_INFINITY;
            try {
                if (!preparePixels()) return Float.POSITIVE_INFINITY;
                float nearest = Float.POSITIVE_INFINITY;
                for (int ia = 0; ia < ends[0].size(); ia++)
                    for (int ic = 0; ic < ends[2].size(); ic++) {
                        Tip a = ends[0].get(ia), c = ends[2].get(ic);
                        work.take(16);
                        float fraction = ((left + right) * .5f - a.x()) / (c.x() - a.x());
                        if (fraction < .15f || fraction > .85f) continue;
                        float y = a.y() + (c.y() - a.y()) * fraction;
                        float d = direction == -1 ? top - y : y - bottom;
                        if (d < Math.max(2, gap * .2f) || d > gap * 1.8f) continue;
                        if (!closed(a, c)) continue;
                        for (int ib = 0; ib < ends[1].size(); ib++)
                            if (pair(0, ia, ib) && pair(1, ib, ic)) {
                                nearest = Math.min(nearest, d);
                                break;
                            }
                    }
                return nearest;
            } catch (LiteralLimit unavailable) {
                return Float.POSITIVE_INFINITY;
            }
        }
    }

    private PrintedTupletBeamOwner() {}

    private record Tip(int x, int y) {}

    static boolean owns(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return Float.isFinite(
                distance(
                        gray,
                        width,
                        height,
                        firstX,
                        firstY,
                        lastX,
                        lastY,
                        gap,
                        stemDirection,
                        numeralLeft,
                        numeralTop,
                        numeralRight,
                        numeralBottom));
    }

    static float distance(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return distanceWithBounds(
                gray,
                width,
                height,
                firstX,
                firstY,
                lastX,
                lastY,
                gap,
                stemDirection,
                numeralLeft,
                numeralTop,
                numeralRight,
                numeralBottom,
                false,
                false);
    }

    /** The caller must already prove a complete rest-containing bracket. */
    static float bracketedDistance(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return distanceWithBounds(
                gray,
                width,
                height,
                firstX,
                firstY,
                lastX,
                lastY,
                gap,
                stemDirection,
                numeralLeft,
                numeralTop,
                numeralRight,
                numeralBottom,
                true,
                false);
    }

    /** Caller proves a same-page repeated numeral with its visible original outline. */
    static float joinedDistance(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return distanceWithBounds(
                gray,
                width,
                height,
                firstX,
                firstY,
                lastX,
                lastY,
                gap,
                stemDirection,
                numeralLeft,
                numeralTop,
                numeralRight,
                numeralBottom,
                false,
                true);
    }

    private static float distanceWithBounds(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom,
            boolean bracketed,
            boolean joined) {
        float nearest = Float.POSITIVE_INFINITY;
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != width * height
                || !Float.isFinite(gap)
                || gap < 4
                || Math.abs(stemDirection) != 1
                || lastX - firstX < gap * 2
                || lastX - firstX > gap * 26) return Float.POSITIVE_INFINITY;
        int travel = -stemDirection;
        List<Tip> first = tips(gray, width, height, firstX, firstY, gap, stemDirection, 1);
        List<Tip> last = tips(gray, width, height, lastX, lastY, gap, stemDirection, -1);
        float numeralX = (numeralLeft + numeralRight) * .5f;
        for (Tip a : first)
            for (Tip b : last) {
                if (b.x - a.x < gap || Math.abs(b.y - a.y) > gap * 3) continue;
                float fraction = (numeralX - a.x) / (b.x - a.x);
                if (fraction < (bracketed ? -.15f : .15f) || fraction > (bracketed ? 1.15f : .85f))
                    continue;
                float beamY = a.y + fraction * (b.y - a.y);
                float distance = travel > 0 ? numeralTop - beamY : beamY - numeralBottom;
                float minimum =
                        joined ? -(numeralBottom - numeralTop + 1) * .45f : Math.max(2, gap * .2f);
                if (distance < minimum || distance > gap * 1.8f) continue;
                int covered = 0, total = 0;
                for (int x = a.x + 2; x < b.x - 1; x++) {
                    int y = Math.round(a.y + (b.y - a.y) * (x - a.x) / (float) (b.x - a.x));
                    total++;
                    if (core(gray, width, height, x, y, gap)) covered++;
                }
                if (total > 0
                        && covered >= total * .94f
                        && BeamInkConnectivity.connected(
                                gray, width, height, a.x, a.y, b.x, b.y, gap))
                    nearest = Math.min(nearest, joined ? Math.max(0, distance) : distance);
            }
        return nearest;
    }

    /** Both shafts must join the same uninterrupted thick beam. */
    static boolean connectedHeads(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int direction) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != width * height
                || !Float.isFinite(gap)
                || gap < 4
                || Math.abs(direction) != 1
                || lastX - firstX < gap * .8f
                || lastX - firstX > gap * 26) return false;
        var first = tips(gray, width, height, firstX, firstY, gap, direction, 1);
        if (first.isEmpty()) return false;
        var last = tips(gray, width, height, lastX, lastY, gap, direction, -1);
        for (var a : first)
            for (var b : last) {
                if (b.x - a.x < gap * .5f || Math.abs(b.y - a.y) > gap * 3) continue;
                int covered = 0, total = 0;
                for (int x = a.x + 2; x < b.x - 1; x++) {
                    int y = Math.round(a.y + (b.y - a.y) * (x - a.x) / (float) (b.x - a.x));
                    total++;
                    if (core(gray, width, height, x, y, gap)) covered++;
                }
                if (total > 0
                        && covered >= total * .94f
                        && BeamInkConnectivity.connected(
                                gray, width, height, a.x, a.y, b.x, b.y, gap)) return true;
            }
        return false;
    }

    private static List<Tip> tips(
            byte[] gray,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            int stemDirection,
            int outward) {
        var result = new ArrayList<Tip>();
        // A chord's shared attack x can be the shaft edge of its displaced second.
        // Require the same continuous shaft/beam evidence over the whole oval-side range.
        int center = Math.round(headX + stemDirection * gap * .3f), travel = -stemDirection;
        int radius = Math.max(1, Math.round(gap * .5f));
        for (int x = center - radius; x <= center + radius; x++) {
            if (x < 0 || x >= width) continue;
            int hits = 0, total = 0, blank = 0;
            for (int d = Math.round(gap * .6f); d <= gap * 6; d++) {
                int y = Math.round(headY) + travel * d;
                if (y < 0 || y >= height) break;
                total++;
                if ((gray[y * width + x] & 255) < 165) {
                    hits++;
                    blank = 0;
                } else if (++blank > Math.max(1, Math.round(gap * .16f))) break;
                if (total >= gap * 1.4f
                        && hits >= total * .92f
                        && outwardBeam(gray, width, height, x, y, gap, outward))
                    result.add(new Tip(x, y));
            }
        }
        return result;
    }

    private static boolean outwardBeam(
            byte[] gray, int width, int height, int x, int y, float gap, int side) {
        for (int angle = -10; angle <= 10; angle++) {
            int hits = 0, total = 0;
            for (int d = Math.round(gap * .25f); d <= gap * 1.35f; d++) {
                total++;
                if (core(gray, width, height, x + side * d, Math.round(y + angle * .06f * d), gap))
                    hits++;
            }
            if (total > 0 && hits >= total * .94f) return true;
        }
        return false;
    }

    private static boolean core(byte[] gray, int width, int height, int x, int y, float gap) {
        int radius = Math.max(1, Math.round(gap * .08f));
        if (x < 0 || x >= width || y - radius < 0 || y + radius >= height) return false;
        for (int dy = -radius; dy <= radius; dy++)
            if ((gray[(y + dy) * width + x] & 255) >= 165) return false;
        return true;
    }
}
