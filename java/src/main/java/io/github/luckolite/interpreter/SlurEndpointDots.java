// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete raw-component partition; no input pixel is erased or reassigned by a mask. */
final class SlurEndpointDots {
    static final int MAX_PIXELS = 4096, MAX_COLUMNS = 1024, MAX_WORK = 250000;

    record Dot(int owner, int left, int top, int right, int bottom, int[] pixels) {}

    record Result(List<Dot> dots, boolean limited) {}

    private record Body(int cut, int low, int high, int count) {}

    private static final class Work {
        int left = MAX_WORK;

        boolean use(int n) {
            left -= n;
            return left >= 0;
        }
    }

    static Result partition(
            byte[] gray,
            int width,
            int height,
            int[] pixels,
            List<NoteArticulationDetector.Anchor> notes) {
        Work work = new Work();
        Result old = columnPartition(gray, width, height, pixels, notes, work);
        if (!old.dots().isEmpty() || old.limited()) return old;
        return contactPartition(gray, width, height, pixels, notes, work);
    }

    private static Result columnPartition(
            byte[] gray,
            int width,
            int height,
            int[] pixels,
            List<NoteArticulationDetector.Anchor> notes,
            Work work) {
        Result unknown = new Result(List.of(), false);
        if (gray == null
                || gray.length != (long) width * height
                || pixels == null
                || pixels.length < 3
                || notes == null) return unknown;
        if (pixels.length > MAX_PIXELS) return new Result(List.of(), true);
        int l = width, r = -1, t = height, b = -1;
        Set<Integer> member = new HashSet<>();
        for (int p : pixels) {
            if (p < 0 || p >= gray.length || (gray[p] & 255) >= 155 || !member.add(p))
                return unknown;
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        int columns = r - l + 1;
        if (columns > MAX_COLUMNS) return new Result(List.of(), true);
        // Caller supplies the complete accepted raw component. Verify that boundary rather than
        // trusting a favorable cropped list which could conceal a mark or a foreign branch.
        for (int p : pixels) {
            int x = p % width, y = p / width;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0 && yy >= 0 && xx < width && yy < height) {
                        int q = yy * width + xx;
                        if ((gray[q] & 255) < 155 && !member.contains(q)) return unknown;
                    }
                }
        }
        int[] low = new int[columns], high = new int[columns], count = new int[columns];
        Arrays.fill(low, height);
        for (int p : pixels) {
            int x = p % width - l, y = p / width;
            low[x] = Math.min(low[x], y);
            high[x] = Math.max(high[x], y);
            count[x]++;
        }
        // Terminal bodies retain their exact two-dimensional samples; only the exposed
        // connector must be a single complete vertical run in each column.
        for (int x = 0; x < columns; x++) if (count[x] == 0) return unknown;
        // An already accepted head anywhere in this component cannot be donated to articulation.
        for (var a : notes)
            if (a.x() >= l && a.x() <= r && a.y() >= t && a.y() <= b) return unknown;
        int leftOwner = -1, rightOwner = -1, side = 0;
        for (int i = 0; i < notes.size(); i++) {
            var a = notes.get(i);
            if (a.staff() < 0 || !(a.gap() > 0)) continue;
            int away = b < a.y() - .7f * a.gap() ? -1 : t > a.y() + .7f * a.gap() ? 1 : 0;
            if (away == 0 || Math.max(Math.abs(t - a.y()), Math.abs(b - a.y())) > 8 * a.gap())
                continue;
            if (Math.abs(a.x() - l) <= .7f * a.gap()) {
                if (leftOwner >= 0) return unknown;
                leftOwner = i;
                side = away;
            }
            if (Math.abs(a.x() - r) <= .7f * a.gap()) {
                if (rightOwner >= 0) return unknown;
                rightOwner = i;
                if (side != 0 && side != away) return unknown;
                side = away;
            }
        }
        if (leftOwner < 0 || rightOwner < 0 || leftOwner == rightOwner) return unknown;
        var first = notes.get(leftOwner);
        var last = notes.get(rightOwner);
        if (first.staff() != last.staff() || first.x() >= last.x()) return unknown;
        float gap = Math.min(first.gap(), last.gap());
        if (Math.abs(first.y() - last.y()) > .35f * gap) return unknown;
        // A complete exposed middle lies outside every body allowed by the existing dot bounds.
        int cap = (int) Math.floor(.62f * gap);
        if (cap < 1 || columns <= 2 * cap + 2) return unknown;
        int min = Integer.MAX_VALUE, max = 0;
        for (int x = cap; x < columns - cap; x++) {
            min = Math.min(min, count[x]);
            max = Math.max(max, count[x]);
        }
        // All exposed columns, not a preferred threshold/angle/sample, constrain the connector.
        // Curvature changes a finite stroke's vertical projection. Preserve every observed
        // exposed column under the existing thin-stroke bound; never fill gaps in it.
        if (max > Math.max(2, Math.round(gap * .42f))) return unknown;
        int wholeMax = 0;
        for (int n : count) wholeMax = Math.max(wholeMax, n);
        if (wholeMax <= max) return unknown; // A constant-width rounded tie tip is not a bulb.
        List<Body> left = new ArrayList<>(), right = new ArrayList<>();
        left.add(null);
        right.add(null);
        for (int n = 1; n <= cap; n++) {
            Body a = body(member, width, l, low, high, count, 0, n - 1, first, gap, max, work);
            if (a != null) left.add(a);
            Body z =
                    body(
                            member,
                            width,
                            l,
                            low,
                            high,
                            count,
                            columns - n,
                            columns - 1,
                            last,
                            gap,
                            max,
                            work);
            if (z != null) right.add(z);
            if (work.left < 0) return new Result(List.of(), true);
        }
        int agreed = -1, leftCut = -1, rightCut = columns;
        for (Body a : left)
            for (Body z : right) {
                int start = a == null ? 0 : a.cut() + 1,
                        end = z == null ? columns - 1 : z.cut() - 1;
                if (!connector(low, high, count, start, end, side, gap, max, work)) {
                    if (work.left < 0) return new Result(List.of(), true);
                    continue;
                }
                int roles = (a == null ? 0 : 1) | (z == null ? 0 : 2);
                if (agreed >= 0 && agreed != roles) return unknown;
                agreed = roles;
                if (a != null) leftCut = Math.max(leftCut, a.cut());
                if (z != null) rightCut = Math.min(rightCut, z.cut());
            }
        if (agreed <= 0) return unknown;
        // Return the union of all feasible body extents, never a favorable smaller crop.
        Body a =
                (agreed & 1) == 0
                        ? null
                        : body(
                                member, width, l, low, high, count, 0, leftCut, first, gap, max,
                                work);
        Body z =
                (agreed & 2) == 0
                        ? null
                        : body(
                                member,
                                width,
                                l,
                                low,
                                high,
                                count,
                                rightCut,
                                columns - 1,
                                last,
                                gap,
                                max,
                                work);
        if (((agreed & 1) != 0 && a == null)
                || ((agreed & 2) != 0 && z == null)
                || !connector(
                        low,
                        high,
                        count,
                        a == null ? 0 : a.cut() + 1,
                        z == null ? columns - 1 : z.cut() - 1,
                        side,
                        gap,
                        max,
                        work)) return new Result(List.of(), work.left < 0);
        List<Dot> dots = new ArrayList<>();
        if (a != null) dots.add(dot(leftOwner, l, low, high, pixels, width, 0, a.cut(), a));
        if (z != null)
            dots.add(dot(rightOwner, l, low, high, pixels, width, z.cut(), columns - 1, z));
        return new Result(List.copyOf(dots), false);
    }

    private static Body body(
            Set<Integer> member,
            int width,
            int origin,
            int[] low,
            int[] high,
            int[] count,
            int start,
            int end,
            NoteArticulationDetector.Anchor owner,
            float gap,
            int connectorMax,
            Work work) {
        if (start > end || !work.use(end - start + 1)) return null;
        int t = Integer.MAX_VALUE, b = -1, n = 0, bulge = 0;
        for (int x = start; x <= end; x++) {
            t = Math.min(t, low[x]);
            b = Math.max(b, high[x]);
            n += count[x];
            bulge = Math.max(bulge, count[x]);
        }
        int w = end - start + 1, h = b - t + 1;
        if (bulge <= connectorMax
                || w < owner.gap() * .26f
                || w > owner.gap() * .62f
                || h < owner.gap() * .26f
                || h > owner.gap() * .62f
                || n < owner.gap() * owner.gap() * .065f
                || w / (float) h <= .65f
                || w / (float) h >= 1.55f
                || n / (float) (w * h) <= .6f) return null;
        // Test actual samples, including separated column runs at a bulb/curve junction.
        // Every body row remains one filled interval; a hollow/branchy fragment is not a dot.
        for (int y = t; y <= b; y++) {
            boolean ink = false, ended = false;
            for (int x = start; x <= end; x++) {
                if (!work.use(1)) return null;
                boolean hit = member.contains(y * width + origin + x);
                if (hit) {
                    if (ended) return null;
                    ink = true;
                } else if (ink) ended = true;
            }
        }
        // cut is the interior-most column for the corresponding terminal body.
        return new Body(start == 0 ? end : start, t, b, n);
    }

    private static boolean connector(
            int[] low,
            int[] high,
            int[] count,
            int start,
            int end,
            int side,
            float gap,
            int maximum,
            Work work) {
        if (end - start < 3 || !work.use(end - start + 1)) return false;
        double first = low[start] + high[start], last = low[end] + high[end], peak = 0;
        for (int x = start; x <= end; x++) {
            if (count[x] > maximum || count[x] != high[x] - low[x] + 1) return false;
            if (x > start && Math.abs(low[x] + high[x] - low[x - 1] - high[x - 1]) > 2)
                return false;
            double chord = first + (last - first) * (x - start) / (end - start);
            double departure = side * (low[x] + high[x] - chord) * .5;
            if (departure < -1) return false;
            peak = Math.max(peak, departure);
        }
        return peak > 1 && peak <= gap * 1.4f;
    }

    private record Contact(int first, int last, int top, int bottom, int count) {}

    /** Additional complete two-dimensional contact models; the released column partition runs first. */
    private static Result contactPartition(
            byte[] gray,
            int width,
            int height,
            int[] pixels,
            List<NoteArticulationDetector.Anchor> notes,
            Work work) {
        Result unknown = new Result(List.of(), false);
        if (gray == null
                || gray.length != (long) width * height
                || pixels == null
                || pixels.length < 3
                || pixels.length > MAX_PIXELS
                || notes == null) return unknown;
        int l = width, r = -1, t = height, b = -1;
        Set<Integer> member = new HashSet<>();
        if (!work.use(pixels.length * 12)) return new Result(List.of(), true);
        for (int p : pixels) {
            if (p < 0 || p >= gray.length || (gray[p] & 255) >= 155 || !member.add(p))
                return unknown;
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        int columns = r - l + 1, rows = b - t + 1;
        // A long stores the complete observed vertical profile, not a sampled or quantized path.
        if (columns > MAX_COLUMNS || rows > 64) return unknown;
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
                            && !member.contains(yy * width + xx)) return unknown;
                }
        }
        int leftOwner = -1, rightOwner = -1, side = 0;
        for (int i = 0; i < notes.size(); i++) {
            var a = notes.get(i);
            if (a.x() >= l && a.x() <= r && a.y() >= t && a.y() <= b) return unknown;
            if (a.staff() < 0 || !(a.gap() > 0)) continue;
            int away = b < a.y() - .7f * a.gap() ? -1 : t > a.y() + .7f * a.gap() ? 1 : 0;
            if (away == 0 || Math.max(Math.abs(t - a.y()), Math.abs(b - a.y())) > 8 * a.gap())
                continue;
            if (Math.abs(a.x() - l) <= .7f * a.gap()) {
                if (leftOwner >= 0) return unknown;
                leftOwner = i;
                side = away;
            }
            if (Math.abs(a.x() - r) <= .7f * a.gap()) {
                if (rightOwner >= 0) return unknown;
                rightOwner = i;
                if (side != 0 && side != away) return unknown;
                side = away;
            }
        }
        if (leftOwner < 0 || rightOwner < 0 || leftOwner == rightOwner) return unknown;
        var first = notes.get(leftOwner);
        var last = notes.get(rightOwner);
        float gap = Math.min(first.gap(), last.gap());
        if (first.staff() != last.staff()
                || first.x() >= last.x()
                || !Float.isFinite(gap)
                || Math.abs(first.y() - last.y()) > .35f * gap) return unknown;
        int cap = (int) Math.floor(.62f * gap);
        if (cap < 1 || columns <= 2L * cap + 2) return unknown;
        if (!work.use(columns * 8)) return new Result(List.of(), true);
        long[] bits = new long[columns];
        int[] low = new int[columns], high = new int[columns], count = new int[columns];
        for (int p : pixels) bits[p % width - l] |= 1L << (p / width - t);
        int maximum = 0, whole = 0;
        for (int x = 0; x < columns; x++) {
            int n = Long.bitCount(bits[x]);
            if (n == 0) return unknown;
            low[x] = t + Long.numberOfTrailingZeros(bits[x]);
            high[x] = t + 63 - Long.numberOfLeadingZeros(bits[x]);
            count[x] = n;
            whole = Math.max(whole, n);
            if (x >= cap && x < columns - cap) maximum = Math.max(maximum, n);
        }
        if (maximum > Math.max(2, Math.round(gap * .42f)) || whole <= maximum) return unknown;
        // Dot pixels cannot supply the endpoints that make a beam look curved.
        // Prove the same finite curve on the independently exposed middle first.
        if (!connector(low, high, count, cap, columns - cap - 1, side, gap, maximum, work))
            return new Result(List.of(), work.left < 0);
        List<Contact> left = new ArrayList<>(), right = new ArrayList<>();
        left.add(null);
        right.add(null);
        for (int n = 1; n <= cap; n++)
            for (int h = 1; h <= Math.min(cap, rows); h++) {
                if (!work.use(2 * (n * h + n + h))) return new Result(List.of(), true);
                int top = side < 0 ? rows - h : 0, bottom = side < 0 ? rows - 1 : h - 1;
                Contact a = contactBody(bits, 0, n - 1, top, bottom, gap, maximum);
                Contact z = contactBody(bits, columns - n, columns - 1, top, bottom, gap, maximum);
                if (a != null) left.add(a);
                if (z != null) right.add(z);
                if (left.size() > 129 || right.size() > 129) return new Result(List.of(), true);
            }
        long pairs = (long) left.size() * right.size();
        long charge =
                pairs * columns * 8
                        + columns * 8L
                        + 2L * cap * Math.min(cap, rows)
                        + 4L * cap
                        + 2L * pixels.length;
        if (charge > Integer.MAX_VALUE || !work.use((int) charge))
            return new Result(List.of(), true);
        int agreed = -1;
        Contact unionLeft = null, unionRight = null;
        long[] residue = new long[columns];
        for (Contact a : left)
            for (Contact z : right) {
                if (!contactCurve(bits, a, z, t, side, gap, maximum, low, high, count, residue))
                    continue;
                int roles = (a == null ? 0 : 1) | (z == null ? 0 : 2);
                if (agreed >= 0 && agreed != roles) return unknown;
                agreed = roles;
                unionLeft = contactUnion(unionLeft, a);
                unionRight = contactUnion(unionRight, z);
            }
        if (agreed <= 0) return unknown;
        Contact a =
                unionLeft == null
                        ? null
                        : contactBody(
                                bits,
                                unionLeft.first,
                                unionLeft.last,
                                unionLeft.top,
                                unionLeft.bottom,
                                gap,
                                maximum);
        Contact z =
                unionRight == null
                        ? null
                        : contactBody(
                                bits,
                                unionRight.first,
                                unionRight.last,
                                unionRight.top,
                                unionRight.bottom,
                                gap,
                                maximum);
        if (unionLeft != null && a == null
                || unionRight != null && z == null
                || !contactCurve(bits, a, z, t, side, gap, maximum, low, high, count, residue))
            return unknown;
        List<Dot> dots = new ArrayList<>();
        if (a != null) dots.add(contactDot(a, leftOwner, l, t, width, pixels));
        if (z != null) dots.add(contactDot(z, rightOwner, l, t, width, pixels));
        return new Result(List.copyOf(dots), false);
    }

    private static long contactMask(int top, int bottom) {
        long high = bottom == 63 ? -1L : (1L << (bottom + 1)) - 1;
        return high & (-1L << top);
    }

    private static Contact contactBody(
            long[] bits, int first, int last, int top, int bottom, float gap, int maximum) {
        int w = last - first + 1, h = bottom - top + 1, n = 0, bulge = 0;
        if (w < gap * .26f
                || w > gap * .62f
                || h < gap * .26f
                || h > gap * .62f
                || w / (float) h <= .65f
                || w / (float) h >= 1.55f) return null;
        long mask = contactMask(top, bottom);
        boolean left = false, right = false, upper = false, lower = false;
        for (int x = first; x <= last; x++) {
            long body = bits[x] & mask;
            int k = Long.bitCount(body);
            n += k;
            bulge = Math.max(bulge, k);
            if (x == first) left = body != 0;
            if (x == last) right = body != 0;
            upper |= (body & (1L << top)) != 0;
            lower |= (body & (1L << bottom)) != 0;
        }
        if (!left
                || !right
                || !upper
                || !lower
                || bulge <= maximum
                || n < gap * gap * .065f
                || n / (float) (w * h) <= .6f) return null;
        for (int y = top; y <= bottom; y++) {
            boolean ink = false, ended = false;
            for (int x = first; x <= last; x++) {
                boolean hit = (bits[x] & (1L << y)) != 0;
                if (hit) {
                    if (ended) return null;
                    ink = true;
                } else if (ink) ended = true;
            }
        }
        return new Contact(first, last, top, bottom, n);
    }

    private static Contact contactUnion(Contact a, Contact b) {
        if (a == null) return b;
        if (b == null) return a;
        return new Contact(
                Math.min(a.first, b.first),
                Math.max(a.last, b.last),
                Math.min(a.top, b.top),
                Math.max(a.bottom, b.bottom),
                0);
    }

    private static long contactPixels(Contact body, int x, long bits) {
        return body != null && x >= body.first && x <= body.last
                ? bits & contactMask(body.top, body.bottom)
                : 0;
    }

    private static boolean contactCurve(
            long[] bits,
            Contact a,
            Contact z,
            int origin,
            int side,
            float gap,
            int maximum,
            int[] low,
            int[] high,
            int[] count,
            long[] residue) {
        int first = -1, last = -1;
        for (int x = 0; x < bits.length; x++) {
            long b = bits[x] & ~(contactPixels(a, x, bits[x]) | contactPixels(z, x, bits[x]));
            residue[x] = b;
            if (b == 0) {
                count[x] = 0;
                continue;
            }
            if (first < 0) first = x;
            last = x;
            count[x] = Long.bitCount(b);
            low[x] = origin + Long.numberOfTrailingZeros(b);
            high[x] = origin + 63 - Long.numberOfLeadingZeros(b);
            if (count[x] > maximum || count[x] != high[x] - low[x] + 1) return false;
        }
        if (first < 0 || last - first < 3) return false;
        double start = low[first] + high[first], end = low[last] + high[last], peak = 0;
        for (int x = first; x <= last; x++) {
            if (count[x] == 0
                    || x > first && Math.abs(low[x] + high[x] - low[x - 1] - high[x - 1]) > 2)
                return false;
            double chord = start + (end - start) * (x - first) / (last - first);
            double departure = side * (low[x] + high[x] - chord) * .5;
            if (departure < -1) return false;
            peak = Math.max(peak, departure);
        }
        if (!(peak > 1 && peak <= gap * 1.4f)) return false;
        return contactTouches(a, bits, residue) && contactTouches(z, bits, residue);
    }

    private static boolean contactTouches(Contact body, long[] bits, long[] residue) {
        if (body == null) return true;
        for (int x = body.first; x <= body.last; x++) {
            long b = contactPixels(body, x, bits[x]);
            long near = residue[x];
            if (x > 0) near |= residue[x - 1];
            if (x + 1 < bits.length) near |= residue[x + 1];
            if ((b & (near | (near << 1) | (near >>> 1))) != 0) return true;
        }
        return false;
    }

    private static Dot contactDot(
            Contact body, int owner, int left, int top, int width, int[] pixels) {
        int[] selected = new int[body.count];
        int n = 0;
        for (int p : pixels) {
            int x = p % width - left, y = p / width - top;
            if (x >= body.first && x <= body.last && y >= body.top && y <= body.bottom)
                selected[n++] = p;
        }
        return new Dot(
                owner,
                left + body.first,
                top + body.top,
                left + body.last,
                top + body.bottom,
                selected);
    }

    private static Dot dot(
            int owner,
            int l,
            int[] low,
            int[] high,
            int[] all,
            int width,
            int first,
            int last,
            Body body) {
        int[] pixels = new int[body.count()];
        int n = 0;
        for (int p : all) {
            int x = p % width - l;
            if (x >= first && x <= last) pixels[n++] = p;
        }
        return new Dot(owner, l + first, body.low(), l + last, body.high(), pixels);
    }
}
