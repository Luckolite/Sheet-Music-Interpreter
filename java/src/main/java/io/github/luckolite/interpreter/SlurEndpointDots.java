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
        Work work = new Work();
        List<Body> left = new ArrayList<>(), right = new ArrayList<>();
        left.add(null);
        right.add(null);
        for (int n = 1; n <= cap; n++) {
            Body a = body(member, width, l, low, high, count, 0, n - 1, first, gap, max, work);
            if (a != null) left.add(a);
            Body z =
                    body(
                            member, width, l, low, high, count, columns - n, columns - 1, last, gap,
                            max, work);
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
                                member, width, l, low, high, count, rightCut, columns - 1, last,
                                gap, max, work);
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
