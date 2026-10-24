// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Whole non-returning quarter-bows departing one independently visible source head.
 * The rule-adjusted feature view is recognition evidence, never an erasure or note owner. */
final class PrintedFallOffContours {
    record Fall(int owner, int left, float leftY, int right, float rightY) {}

    static final class Budget {
        private long remaining;
        private boolean refused;

        Budget(long allowance) {
            if (allowance < 0 || allowance > 128_000_000L) throw new IllegalArgumentException();
            remaining = allowance;
        }

        boolean take(long work) {
            if (refused || work < 0 || work > remaining) {
                refused = true;
                return false;
            }
            remaining -= work;
            return true;
        }

        long remaining() {
            return remaining;
        }

        boolean refused() {
            return refused;
        }
    }

    private PrintedFallOffContours() {}

    /** Independent additive route for contrast that an absolute rule adjustment cannot retain.
     * Catalog and contour work have separate explicit whole-page allowances. */
    static List<Fall> discoverCalibrated(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            LiteralStaffContrast.CatalogBudget references,
            Budget work) {
        return discoverCalibrated(gray, width, height, frames, references, work, true);
    }

    static List<Fall> calibratedCandidates(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            LiteralStaffContrast.CatalogBudget references,
            Budget work) {
        return discoverCalibrated(gray, width, height, frames, references, work, false);
    }

    private static List<Fall> discoverCalibrated(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            LiteralStaffContrast.CatalogBudget references,
            Budget work,
            boolean uniqueOnly) {
        var out = new ArrayList<Fall>();
        for (var frame : frames) {
            if (frame.heads().isEmpty()) continue;
            float[] phase = frame.track().at(width * .5f);
            var catalog =
                    LiteralStaffContrast.catalog(
                            gray, width, height, phase[0] - 4 * phase[1], phase[1], references);
            if (references.refused()) return List.of();
            if (catalog.isEmpty()) continue;
            for (var source : frame.heads()) {
                float gap = frame.track().at(source.x())[1];
                if (!Float.isFinite(gap) || gap < 8 || gap > 64) continue;
                int left = Math.max(1, (int) Math.floor(source.x() + .5f * gap));
                int right = Math.min(width - 2, (int) Math.ceil(source.x() + 7 * gap));
                var observed = catalog.get().field(left, right, references);
                if (references.refused()) return List.of();
                if (observed.isEmpty()) continue;
                var field = observed.get();
                int w = right - left + 1, h = field.lastY() - field.firstY() + 1;
                if (!work.take((long) w * h * 16 + frame.heads().size() * 32L)) return List.of();
                boolean[] core = new boolean[w * h];
                int[] queue = new int[core.length];
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        core[y * w + x] =
                                field.cell(x + left, y + field.firstY()).kind()
                                        == LiteralStaffContrast.Kind.EXTRA_CORE;
                for (int pixel = 0; pixel < core.length; pixel++)
                    if (core[pixel]) {
                        int count = 1, l = w, r = -1, t = h, b = -1;
                        queue[0] = pixel;
                        core[pixel] = false;
                        for (int at = 0; at < count; at++) {
                            int x = queue[at] % w, y = queue[at] / w;
                            l = Math.min(l, x);
                            r = Math.max(r, x);
                            t = Math.min(t, y);
                            b = Math.max(b, y);
                            for (int dy = -1; dy <= 1; dy++)
                                for (int dx = -1; dx <= 1; dx++) {
                                    int xx = x + dx, yy = y + dy;
                                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                                    int p = yy * w + xx;
                                    if (core[p]) {
                                        core[p] = false;
                                        queue[count++] = p;
                                    }
                                }
                        }
                        if (l == 0
                                || r == w - 1
                                || t == 0
                                || b == h - 1
                                || count < 8
                                || count > 4096
                                || r - l < 5
                                || r - l >= 128
                                || b - t >= 128) continue;
                        if (!work.take(
                                count * 4L
                                        + frame.heads().size() * 32L
                                        + (long) (r - l + 1) * (b - t + 1) * 8
                                        + 1024)) return List.of();
                        int[] pixels = new int[count];
                        for (int i = 0; i < count; i++)
                            pixels[i] =
                                    (queue[i] / w + field.firstY()) * width + queue[i] % w + left;
                        var fall =
                                classify(
                                        pixels,
                                        width,
                                        l + left,
                                        r + left,
                                        t + field.firstY(),
                                        b + field.firstY(),
                                        frame.heads(),
                                        frame.track(),
                                        gray,
                                        height,
                                        work);
                        if (work.refused()) return List.of();
                        // A search window nominates no particular owner; complete competition above
                        // must independently resolve this exact source slot before it is retained.
                        if (fall != null && fall.owner() == source.owner()) out.add(fall);
                    }
            }
        }
        if (references.refused() || work.refused()) return List.of();
        if (!uniqueOnly) return List.copyOf(out);
        var counts = new HashMap<Integer, Integer>();
        for (var f : out) counts.merge(f.owner(), 1, Integer::sum);
        return out.stream().filter(f -> counts.get(f.owner()) == 1).toList();
    }

    static List<Fall> discover(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            Budget budget) {
        return discover(gray, width, height, frames, budget, true);
    }

    static List<Fall> candidates(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            Budget budget) {
        return discover(gray, width, height, frames, budget, false);
    }

    private static List<Fall> discover(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            Budget budget,
            boolean uniqueOnly) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || frames == null
                || frames.size() > 128
                || budget == null)
            throw new IllegalArgumentException("Invalid outgoing curve source");
        var found = new ArrayList<Fall>();
        for (var frame : frames) {
            if (frame.heads().isEmpty()) continue;
            if (!frame.track().verified() || frame.heads().size() > 4096) continue;
            int left = Math.max(1, frame.left()), right = Math.min(width - 2, frame.right());
            int top = height, bottom = -1;
            float maxGap = 0;
            for (var head : frame.heads()) {
                float g = frame.track().at(head.x())[1];
                if (!Float.isFinite(g) || g < 8 || g > 64) continue;
                maxGap = Math.max(maxGap, g);
                top = Math.min(top, (int) Math.floor(head.y() - .75f * g));
                bottom = Math.max(bottom, (int) Math.ceil(head.y() + 3 * g));
            }
            top = Math.max(1, top);
            bottom = Math.min(height - 2, bottom);
            int w = right - left + 1, h = bottom - top + 1;
            long area = (long) w * h;
            if (w < 3 || h < 3 || area > 1_000_000) continue;
            // Entire frame scan/feature components and every bounded rule-profile query precede
            // any result. Owner competition and raw-head queries are additionally charged below.
            long profileWork = (long) w * 5 * ((long) Math.ceil(.7 * maxGap) + 4) * 32;
            if (!budget.take(area * 16 + profileWork + frame.heads().size() * 64L))
                return List.of();
            var rules = new PrintedCurveRuleInk(null, gray, width, height, 205, frame.track());
            boolean[] ink = new boolean[(int) area];
            for (int yy = 0; yy < h; yy++)
                for (int xx = 0; xx < w; xx++)
                    ink[yy * w + xx] = rules.sample(left + xx, top + yy) < 205;
            int[] queue = new int[(int) area];
            for (int pixel = 0; pixel < ink.length; pixel++) {
                if (!ink[pixel]) continue;
                int count = 1, l = w, r = -1, t = h, b = -1;
                queue[0] = pixel;
                ink[pixel] = false;
                for (int at = 0; at < count; at++) {
                    int x = queue[at] % w, y = queue[at] / w;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            int n = ny * w + nx;
                            if (ink[n]) {
                                ink[n] = false;
                                queue[count++] = n;
                            }
                        }
                }
                // Cropped and large components are never reduced to favorable thin subsets.
                if (l == 0
                        || r == w - 1
                        || t == 0
                        || b == h - 1
                        || count < 8
                        || count > 4096
                        || r - l < 5
                        || r - l >= 128
                        || b - t >= 128) continue;
                if (!budget.take(
                        (long) count * 4
                                + frame.heads().size() * 32L
                                + (long) (r - l + 1) * (b - t + 1) * 8
                                + 1024)) return List.of();
                int[] original = new int[count];
                for (int i = 0; i < count; i++)
                    original[i] = (queue[i] / w + top) * width + queue[i] % w + left;
                var fall =
                        classify(
                                original,
                                width,
                                l + left,
                                r + left,
                                t + top,
                                b + top,
                                frame.heads(),
                                frame.track(),
                                gray,
                                height,
                                budget);
                if (budget.refused()) return List.of();
                if (fall != null) found.add(fall);
            }
        }
        if (budget.refused()) return List.of();
        if (!uniqueOnly) return List.copyOf(found);
        // Two candidate marks for one head are ambiguous; order cannot select one.
        var counts = new HashMap<Integer, Integer>();
        for (var fall : found) counts.merge(fall.owner(), 1, Integer::sum);
        return found.stream().filter(f -> counts.get(f.owner()) == 1).toList();
    }

    static Fall classify(
            int[] pixels,
            int width,
            int left,
            int right,
            int top,
            int bottom,
            List<? extends PrintedSlurOwners.Location> heads,
            StaffPitchTrack track,
            byte[] raw,
            int height,
            Budget budget) {
        int columns = right - left + 1, rows = bottom - top + 1;
        if (pixels.length < 8
                || pixels.length > 4096
                || columns < 6
                || columns > 128
                || rows < 6
                || rows > 128) return null;
        int[] lo = new int[columns], hi = new int[columns], size = new int[columns];
        int[] rowLo = new int[rows], rowHi = new int[rows], rowN = new int[rows];
        Arrays.fill(lo, height);
        Arrays.fill(rowLo, width);
        Arrays.fill(hi, -1);
        Arrays.fill(rowHi, -1);
        var distinct = new HashSet<Integer>();
        for (int p : pixels) {
            int x = p % width, y = p / width;
            if (x < left || x > right || y < top || y > bottom || !distinct.add(p)) return null;
            int c = x - left, k = y - top;
            lo[c] = Math.min(lo[c], y);
            hi[c] = Math.max(hi[c], y);
            size[c]++;
            rowLo[k] = Math.min(rowLo[k], x);
            rowHi[k] = Math.max(rowHi[k], x);
            rowN[k]++;
        }
        float[] centers = new float[columns];
        for (int x = 0; x < columns; x++) {
            if (size[x] == 0 || size[x] != hi[x] - lo[x] + 1) return null;
            centers[x] = (lo[x] + hi[x]) * .5f;
            if (x > 0 && centers[x] < centers[x - 1] - 1) return null;
        }
        for (int y = 0; y < rows; y++)
            if (rowN[y] == 0 || rowN[y] != rowHi[y] - rowLo[y] + 1) return null;
        int owner = -1;
        for (int i = 0; i < heads.size(); i++) {
            var head = heads.get(i);
            float gap = track.at(head.x())[1];
            if (!Float.isFinite(gap)
                    || gap < 8
                    || gap > 64
                    || !Float.isFinite(head.x() + head.y())
                    || columns < gap * .65f
                    || columns > gap * 3.5f
                    || rows < gap * .6f
                    || rows > gap * 2.5f
                    || pixels.length > columns * rows * .6f
                    || head.y() >= top
                    || top - head.y() > gap
                    || left - head.x() < gap * .5f
                    || left - head.x() > gap * 3) continue;
            if (!budget.take(columns * 16L)) return null;
            float[] bins = new float[3];
            int[] n = new int[3];
            for (int x = 0; x < columns; x++) {
                int j = Math.min(2, x * 3 / columns);
                bins[j] += centers[x];
                n[j]++;
            }
            for (int j = 0; j < 3; j++) bins[j] /= n[j];
            if (bins[1] - bins[0] < gap * .08f
                    || bins[2] - bins[1] < gap * .25f
                    || (bins[0] + bins[2]) * .5f - bins[1] < gap * .08f) continue;
            // An angular elbow cannot become a curved fall from three favorable averages.
            // The complete column centerline must fit a quarter-ellipse with physical cells.
            double squared = 0;
            int outside = 0;
            for (int x = 0; x < columns; x++) {
                double u = (x + .5) / columns, v = (centers[x] - bottom - .5) / rows;
                double error = Math.abs(Math.sqrt(u * u + v * v) - 1) * Math.min(columns, rows);
                squared += error * error;
                if (error > gap * .27) outside++;
            }
            if (Math.sqrt(squared / columns) > gap * .16 || outside > columns * .05) continue;
            int thin = 0, checked = 0;
            for (int x = 2; x < columns - 2; x++) {
                double tangent = (centers[x + 2] - centers[x - 2]) * .25;
                checked++;
                // Falloff glyphs have a visibly heavier rounded stroke than approach slashes.
                // This is the new falloff-specific limit; incoming/slur classifiers stay exact.
                if (size[x] / Math.sqrt(1 + tangent * tangent) <= Math.max(3, gap * .4f)) thin++;
            }
            if (checked < gap * .7f || thin < checked * .85f) continue;
            if (owner >= 0) return null;
            owner = i;
        }
        if (owner < 0) return null;
        var source = heads.get(owner);
        float gap = track.at(source.x())[1];
        // Every physically overlapping rival participates, including duplicate source slots.
        for (int i = 0; i < heads.size(); i++)
            if (i != owner) {
                var other = heads.get(i);
                if (Math.abs(other.x() - source.x()) <= gap * .35f
                        && Math.abs(other.y() - source.y()) <= gap * .35f) return null;
                if (other.x() < left - gap * .6f
                        || other.x() > right + gap * .6f
                        || other.y() < top - gap * .6f
                        || other.y() > bottom + gap * .6f) continue;
                if (!budget.take(pixels.length * 4L)) return null;
                for (int p : pixels)
                    if (Math.abs(other.x() - p % width) < gap * .6f
                            && Math.abs(other.y() - p / width) < gap * .6f) return null;
            }
        if (!budget.take((long) Math.ceil(gap * gap * 64 + 1024))) return null;
        if (!PrintedSlurOwners.headBody(raw, width, height, source, gap)) return null;
        return new Fall(source.owner(), left, centers[0], right, centers[columns - 1]);
    }
}
