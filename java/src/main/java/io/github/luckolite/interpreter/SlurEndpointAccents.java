// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete accent/finite-curve partitions. Geometry never substitutes for attack ownership. */
final class SlurEndpointAccents {
    record Mark(int owner, int left, int top, int right, int bottom, int[] pixels) {}

    private static final int MAX_PIXELS = 4096,
            MAX_COLUMNS = 1024,
            MAX_WORK = 8_000_000,
            MAX_CERTIFICATES = 8;

    static final class Work {
        long left = MAX_WORK;
        int certificates;

        boolean use(long n) {
            if (n < 0 || n > left) return false;
            left -= n;
            return true;
        }
    }

    private record Box(int l, int t, int r, int b) {}

    static List<Mark> partition(
            byte[] gray,
            int width,
            int height,
            int[] pixels,
            List<NoteArticulationDetector.Anchor> notes) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || pixels == null
                || pixels.length < 8
                || pixels.length > MAX_PIXELS
                || notes == null) return List.of();
        return partition(gray, width, height, pixels, notes, new Work());
    }

    static List<Mark> partition(
            byte[] gray,
            int width,
            int height,
            int[] pixels,
            List<NoteArticulationDetector.Anchor> notes,
            Work work) {
        if (!work.use(pixels.length * 12L + notes.size())) return List.of();
        Set<Integer> member = new HashSet<>();
        int l = width, r = -1, t = height, b = -1;
        for (int p : pixels) {
            if (p < 0 || p >= gray.length || (gray[p] & 255) >= 155 || !member.add(p))
                return List.of();
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        if (r - l + 1 > MAX_COLUMNS || b - t + 1 > 64) return List.of();
        for (int p : pixels) {
            int x = p % width, y = p / width;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0
                            && xx < width
                            && yy >= 0
                            && yy < height
                            && (gray[yy * width + xx] & 255) < 155
                            && !member.contains(yy * width + xx)) return List.of();
                }
        }
        long[] bits = new long[r - l + 1];
        for (int p : pixels) bits[p % width - l] |= 1L << (p / width - t);
        long terminal = bits[bits.length - 1];
        int tipTop = t + Long.numberOfTrailingZeros(terminal),
                tipBottom = t + 63 - Long.numberOfLeadingZeros(terminal);
        int owner = -1;
        Box union = null;
        for (int n = 0; n < notes.size(); n++) {
            var a = notes.get(n);
            float gap = a.gap();
            if (a.staff() < 0
                    || !Float.isFinite(gap)
                    || gap < 5
                    || gap > 64
                    || Math.abs(r - a.x()) > gap * 1.5f) continue;
            boolean splitProfile = false;
            int search = Math.max(0, bits.length - (int) Math.floor(gap * 2.1f + 1));
            if (!work.use(bits.length)) return List.of();
            for (int x = search; x < bits.length; x++) {
                long col = bits[x];
                int runs = Long.bitCount(col & ~(col << 1));
                if (runs >= 2) splitProfile = true;
            }
            if (!splitProfile) continue;
            int minW = Math.max(8, (int) Math.ceil(gap * .75f)),
                    maxW = Math.min(bits.length - 1, (int) Math.floor(gap * 2.1f + 1)),
                    maxH = (int) Math.floor(gap * 1.25f);
            for (int span = minW; span <= maxW; span++)
                for (int top = Math.max(t, tipBottom - maxH + 1); top <= tipTop; top++)
                    for (int bottom = tipBottom; bottom <= Math.min(b, top + maxH - 1); bottom++) {
                        if (!work.use(1)) return List.of();
                        int hh = bottom - top + 1;
                        if (hh < Math.max(5, gap * .35f)) continue;
                        int left = r - span + 1;
                        float cx = (left + r) * .5f, cy = (top + bottom) * .5f;
                        if (Math.abs(cx - a.x()) > gap * .7f
                                || a.y() - cy < gap * .7f
                                || a.y() - cy > gap * 8) continue;
                        if (!work.use((long) span * hh * 6 + 64)) return List.of();
                        Box box = new Box(left, top, r, bottom);
                        int[] body = body(bits, l, t, box, width);
                        if (body == null || !OpenChevron.matches(body, width, left, top, r, bottom))
                            continue;
                        if (!work.use(bits.length * 8L + 2L * notes.size() + 64)) return List.of();
                        int[] curve = curve(bits, l, t, box, width, gap, notes, n, work);
                        if (curve == null) {
                            if (work.left < 0) return List.of();
                            continue;
                        }
                        if (owner >= 0 && owner != n) return List.of();
                        owner = n;
                        union =
                                union == null
                                        ? box
                                        : new Box(
                                                Math.max(union.l, left),
                                                Math.max(union.t, top),
                                                r,
                                                Math.min(union.b, bottom));
                    }
        }
        if (owner < 0) return List.of();
        long finalWork =
                (long) (union.r - union.l + 1) * (union.b - union.t + 1) * 6
                        + bits.length * 8L
                        + 2L * notes.size()
                        + 64;
        if (!work.use(finalWork + (long) notes.size() * pixels.length)) return List.of();
        // A recognized body cannot be donated to either the mark or the connector.
        for (var a : notes) {
            if (!Float.isFinite(a.gap()) || a.gap() <= 0) continue;
            double radius = a.gap() * .3;
            for (int p : pixels)
                if (Math.abs(p % width - a.x()) <= radius && Math.abs(p / width - a.y()) <= radius)
                    return List.of();
        }
        var a = notes.get(owner);
        int[] body = body(bits, l, t, union, width);
        if (body == null
                || !OpenChevron.matches(body, width, union.l, union.t, union.r, union.b)
                || curve(bits, l, t, union, width, a.gap(), notes, owner, work) == null)
            return List.of();
        return List.of(new Mark(owner, union.l, union.t, union.r, union.b, body));
    }

    private static long mask(int top, int bottom) {
        return (bottom == 63 ? -1L : (1L << (bottom + 1)) - 1) & (-1L << top);
    }

    private static int[] body(long[] bits, int originX, int originY, Box box, int stride) {
        long mask = mask(box.t - originY, box.b - originY);
        var result = new ArrayList<Integer>();
        boolean top = false, bottom = false;
        for (int x = box.l; x <= box.r; x++) {
            long col = bits[x - originX] & mask;
            if (col == 0) return null;
            top |= (col & (1L << (box.t - originY))) != 0;
            bottom |= (col & (1L << (box.b - originY))) != 0;
            while (col != 0) {
                int y = Long.numberOfTrailingZeros(col);
                result.add((originY + y) * stride + x);
                col &= col - 1;
            }
        }
        return !top || !bottom ? null : result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] curve(
            long[] bits,
            int originX,
            int originY,
            Box box,
            int stride,
            float gap,
            List<NoteArticulationDetector.Anchor> notes,
            int owner,
            Work work) {
        int first = -1, last = -1, maximum = Math.max(2, Math.round(gap * .42f));
        int[] lo = new int[bits.length], hi = new int[bits.length];
        Set<Long> cells = new HashSet<>();
        long take = mask(box.t - originY, box.b - originY);
        for (int x = 0; x < bits.length; x++) {
            long col = bits[x];
            if (x + originX >= box.l && x + originX <= box.r) col &= ~take;
            if (col == 0) continue;
            if (first < 0) first = x;
            last = x;
            int count = Long.bitCount(col);
            lo[x] = originY + Long.numberOfTrailingZeros(col);
            hi[x] = originY + 63 - Long.numberOfLeadingZeros(col);
            if (count > maximum || count != hi[x] - lo[x] + 1) return null;
            while (col != 0) {
                int y = Long.numberOfTrailingZeros(col);
                cells.add(FiniteSlurRibbon.pixel(originX + x, originY + y));
                col &= col - 1;
            }
        }
        if (first < 0 || last - first < gap * 3 || last >= bits.length - 1) return null;
        double start = lo[first] + hi[first], end = lo[last] + hi[last], peak = 0;
        for (int x = first; x <= last; x++) {
            if (lo[x] == 0) return null;
            double chord = start + (end - start) * (x - first) / (last - first),
                    departure = (chord - lo[x] - hi[x]) * .5;
            if (departure < -1) return null;
            peak = Math.max(peak, departure);
        }
        if (!(peak > 1)) return null;
        if (!work.use(bits.length * 256L)) {
            work.left = -1;
            return null;
        }
        if (!PrintedSlurContours.completeReturningProfile(lo, hi, first, last, gap)) return null;
        int leftOwner = endpoint(notes, originX + first, (float) (start * .5), gap),
                rightOwner = endpoint(notes, originX + last, (float) (end * .5), gap);
        if (leftOwner < 0
                || rightOwner != owner
                || leftOwner == rightOwner
                || notes.get(leftOwner).staff() != notes.get(rightOwner).staff()) return null;
        if (++work.certificates > MAX_CERTIFICATES) {
            work.left = -1;
            return null;
        }
        // The existing exact strip certificate replaces a selected local slope. Its internal
        // finite-state/clip limits remain unchanged; at most eight calls include final consensus.
        var proof = FiniteSlurRibbon.certify(cells, maximum);
        if (proof.limitReached()) {
            work.left = -1;
            return null;
        }
        if (!proof.complete()) return null;
        return new int[] {leftOwner, rightOwner};
    }

    private static int endpoint(
            List<NoteArticulationDetector.Anchor> notes, int x, float y, float gap) {
        int owner = -1;
        for (int i = 0; i < notes.size(); i++) {
            var a = notes.get(i);
            if (a.staff() < 0
                    || Math.abs(a.x() - x) > .7f * gap
                    || a.y() - y < .7f * gap
                    || a.y() - y > 8 * gap) continue;
            if (owner >= 0) return -1;
            owner = i;
        }
        return owner;
    }
}
