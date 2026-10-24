// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Positive attached returning-flag ownership; failed beam proofs are not input
 * evidence. The caller authenticates heads, physical shafts and member origins.
 * No duration, scalar beam count or output order creates a positive flag. */
final class PrintedIsolatedFlags {
    record Head(int owner, double x, double y, double glyphGap) {
        Head {
            if (owner < 0
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(glyphGap)
                    || glyphGap < 3
                    || glyphGap > 64) throw new IllegalArgumentException();
        }
    }

    record Pixel(int x, int y) {}

    record Proof(
            int columnId,
            List<Integer> owners,
            int observedRootBands,
            boolean countComplete,
            int corePixels,
            List<Pixel> roots,
            int minX,
            int minY,
            int maxX,
            int maxY,
            List<RawPrimaryBeamAbsence.Certificate> primaryAbsence) {
        Proof {
            owners = List.copyOf(owners);
            roots = List.copyOf(roots);
            primaryAbsence = List.copyOf(primaryAbsence);
        }
    }

    static final class Budget {
        private final long limit;
        private long used;
        private boolean exhausted;

        Budget(long limit) {
            if (limit < 1) throw new IllegalArgumentException();
            this.limit = limit;
        }

        void take(long n) {
            if (exhausted || n < 0 || n > limit - used) {
                exhausted = true;
                throw new Stop();
            }
            used += n;
        }

        boolean exhausted() {
            return exhausted;
        }

        long used() {
            return used;
        }
    }

    private static final class Stop extends RuntimeException {}

    static Optional<Proof> prove(
            byte[] raw,
            int width,
            int height,
            PrintedBeamGeometry.Column shaft,
            List<Integer> owners,
            List<Head> heads,
            List<PrintedBeamGeometry.Column> allColumns,
            FiniteRawBeamStrip.Carrier carrier,
            Budget budget) {
        if (raw == null
                || width < 1
                || height < 1
                || (long) width * height != raw.length
                || shaft == null
                || owners == null
                || owners.isEmpty()
                || owners.size() > 64
                || heads == null
                || heads.size() > 8192
                || allColumns == null
                || allColumns.size() > 4096
                || carrier == null
                || budget == null) throw new IllegalArgumentException();
        if (new HashSet<>(owners).size() != owners.size()
                || owners.stream().anyMatch(x -> x == null || x < 0)
                || shaft.left() < 0
                || shaft.right() >= width
                || shaft.cap() >= height
                || shaft.shaftStart() >= height) throw new IllegalArgumentException();
        var headIds = new HashSet<Integer>();
        for (var head : heads)
            if (head == null || !headIds.add(head.owner())) return Optional.empty();
        if (!headIds.containsAll(owners)) return Optional.empty();
        var nodeIds = new HashSet<Integer>();
        boolean ownColumn = false;
        for (var node : allColumns) {
            if (node == null
                    || !nodeIds.add(node.id())
                    || node.right() >= width
                    || node.cap() >= height
                    || node.shaftStart() >= height
                    || node.shaftColumns().stream().anyMatch(e -> e.endY() >= height))
                return Optional.empty();
            if (node.id() == shaft.id()) {
                if (!node.equals(shaft)) return Optional.empty();
                ownColumn = true;
            }
        }
        if (!ownColumn) return Optional.empty();
        if (budget.exhausted()) return Optional.empty();
        try {
            return new Context(
                            raw, width, height, shaft, owners, heads, allColumns, carrier, budget)
                    .prove();
        } catch (Stop exhausted) {
            return Optional.empty();
        }
    }

    private static final class Context {
        final byte[] raw;
        final int w, h;
        final PrintedBeamGeometry.Column shaft;
        final List<Integer> owners;
        final Set<Integer> own;
        final List<Head> heads, ownHeads;
        final List<PrintedBeamGeometry.Column> allColumns;
        final FiniteRawBeamStrip.Carrier carrier;
        final Budget budget;
        final double gap;
        final int sx, cap, headward, left, right, low, high, cross;

        Context(
                byte[] r,
                int width,
                int height,
                PrintedBeamGeometry.Column s,
                List<Integer> o,
                List<Head> hs,
                List<PrintedBeamGeometry.Column> cs,
                FiniteRawBeamStrip.Carrier c,
                Budget b) {
            raw = r;
            w = width;
            h = height;
            shaft = s;
            owners = List.copyOf(o);
            own = Set.copyOf(o);
            heads = List.copyOf(hs);
            ownHeads = hs.stream().filter(head -> own.contains(head.owner())).toList();
            allColumns = List.copyOf(cs);
            carrier = c;
            budget = b;
            gap = s.gap();
            sx = s.right();
            cap = s.cap();
            headward = -s.direction();
            left = sx + Math.max(2, (int) Math.ceil(gap * .25));
            right = Math.min(w - 2, sx + (int) Math.ceil(gap * 1.7));
            int outward = (int) Math.ceil(gap * .35),
                    inward =
                            (int)
                                    Math.ceil(
                                            Math.min(
                                                    12 * gap,
                                                    Math.abs(s.shaftStart() - cap) + gap));
            low = Math.max(1, cap - (s.direction() > 0 ? inward : outward));
            high = Math.min(h - 2, cap + (s.direction() > 0 ? outward : inward));
            cross = (int) Math.ceil(gap * .3) + 2;
        }

        boolean ink(int x, int y) {
            budget.take(1);
            return x >= 0 && x < w && y >= 0 && y < h && (raw[y * w + x] & 255) < 165;
        }

        boolean ownHead(int x, int y) {
            budget.take(ownHeads.size());
            for (var head : ownHeads)
                if (Math.abs(x - head.x()) <= head.glyphGap() * .85
                        && Math.abs(y - head.y()) <= head.glyphGap() * .65) return true;
            return false;
        }

        boolean core(int x, int y) {
            return ink(x, y) && !carrier.at(x, y) && !carrier.unknown(x, y) && !ownHead(x, y);
        }

        boolean inside(int x, int y) {
            return x >= left && x <= right && y >= low && y <= high;
        }

        long key(int x, int y) {
            return ((long) y << 32) | (x & 0xffffffffL);
        }

        Pixel pixel(long k) {
            return new Pixel((int) k, (int) (k >>> 32));
        }

        List<Pixel> attached(int y) {
            int spread = (int) Math.ceil(.8 * (left - shaft.left())) + 1;
            var seen = new HashSet<Long>();
            var pending = new ArrayDeque<Pixel>();
            var targets = new ArrayList<Pixel>();
            pending.add(new Pixel(left, y));
            seen.add(key(left, y));
            while (!pending.isEmpty()) {
                var p = pending.remove();
                if (shaft.owns(p.x(), p.y())) {
                    targets.add(p);
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = p.x() + dx, ny = p.y() + dy;
                        if (dx == 0 && dy == 0
                                || nx < shaft.left()
                                || nx > left
                                || ny < y - spread
                                || ny > y + spread
                                || !seen.add(key(nx, ny))) continue;
                        if (core(nx, ny)) pending.add(new Pixel(nx, ny));
                    }
            }
            return targets;
        }

        // Union of ALL paths in the declared direction; no favorable path is selected.
        Set<Long> flood(Collection<Long> starts, Set<Long> allowed, boolean monotone) {
            var pixels = new HashSet<Long>(starts);
            var queue = new ArrayDeque<Long>(starts);
            while (!queue.isEmpty()) {
                var p = pixel(queue.remove());
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 || monotone && dy != 0 && dy != headward) continue;
                        int x = p.x() + dx, y = p.y() + dy;
                        if (!inside(x, y)) continue;
                        if (core(x, y) && (allowed == null || allowed.contains(key(x, y)))) {
                            if (pixels.add(key(x, y))) queue.add(key(x, y));
                        } else if (dy != 0
                                && ink(x, y)
                                && carrier.at(x, y)
                                && !carrier.unknown(x, y)) {
                            var frontier = new HashSet<Long>();
                            frontier.add(key(x, y));
                            for (int depth = 0; depth < cross && !frontier.isEmpty(); depth++) {
                                var next = new HashSet<Long>();
                                for (long k : frontier) {
                                    var c = pixel(k);
                                    for (int ddx = -1; ddx <= 1; ddx++) {
                                        int nx = c.x() + ddx, ny = c.y() + dy;
                                        if (!inside(nx, ny)) continue;
                                        if (core(nx, ny)
                                                && (allowed == null
                                                        || allowed.contains(key(nx, ny)))) {
                                            if (pixels.add(key(nx, ny))) queue.add(key(nx, ny));
                                        } else if (ink(nx, ny)
                                                && carrier.at(nx, ny)
                                                && !carrier.unknown(nx, ny)) next.add(key(nx, ny));
                                    }
                                }
                                frontier = next;
                            }
                        }
                        if (pixels.size() > 4096) return Set.of();
                    }
            }
            return pixels;
        }

        boolean rival(int x, int y) {
            budget.take(heads.size() + allColumns.size());
            for (var head : heads)
                if (!own.contains(head.owner())
                        && Math.abs(x - head.x()) <= head.glyphGap() * .7
                        && Math.abs(y - head.y()) <= head.glyphGap() * .5) return true;
            for (var column : allColumns)
                if (column.id() != shaft.id() && column.owns(x, y)) return true;
            return false;
        }

        boolean nonlinear(TreeMap<Integer, TreeSet<Integer>> rows) {
            var keys = new ArrayList<>(rows.keySet());
            long ln = -10000000, ld = 1, un = 10000000, ud = 1;
            // Doubled row centers, half-pixel quantization on each endpoint.
            for (int i = 0; i < keys.size(); i++)
                for (int j = i + 1; j < keys.size(); j++) {
                    budget.take(1);
                    var a = rows.get(keys.get(i));
                    var b = rows.get(keys.get(j));
                    long d = keys.get(j) - keys.get(i),
                            delta = b.first() + b.last() - a.first() - a.last(),
                            low = delta - 2,
                            high = delta + 2;
                    if (low * ld > ln * d) {
                        ln = low;
                        ld = d;
                    }
                    if (high * ud < un * d) {
                        un = high;
                        ud = d;
                    }
                }
            return ln * ud > un * ld;
        }

        boolean qualified(Set<Long> pixels, Set<Long> roots, Set<Long> near) {
            if (Collections.disjoint(pixels, near)) return false;
            var componentRoots = new HashSet<>(roots);
            componentRoots.retainAll(pixels);
            int firstRoot = Integer.MAX_VALUE, firstGlyph = Integer.MAX_VALUE;
            for (long k : componentRoots)
                firstRoot = Math.min(firstRoot, (pixel(k).y() - cap) * headward);
            for (long k : pixels)
                firstGlyph = Math.min(firstGlyph, (pixel(k).y() - cap) * headward);
            int longest = 0, run = 0;
            for (int y = Math.max(low, cap - (int) Math.ceil(gap * 1.3));
                    y <= Math.min(high, cap + (int) Math.ceil(gap * 1.3));
                    y++) {
                if (ink(left, y) && carrier.at(left, y) && !carrier.unknown(left, y)) {
                    run++;
                    longest = Math.max(longest, run);
                } else run = 0;
            }
            if (firstGlyph < firstRoot - longest - 1) return false;
            for (long k : pixels) {
                var p = pixel(k);
                if (p.y() == low || p.y() == high) return false;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = p.x() + dx, yy = p.y() + dy;
                        if (inside(xx, yy)
                                && ink(xx, yy)
                                && carrier.unknown(xx, yy)
                                && !ownHead(xx, yy)) return false;
                    }
            }
            var body = flood(componentRoots, pixels, true);
            if (body.isEmpty()) return false;
            var rows = new TreeMap<Integer, TreeSet<Integer>>();
            for (long k : body) {
                var p = pixel(k);
                int t = (p.y() - cap) * headward;
                if (t < 0 || t > firstRoot + gap * 2.3) continue;
                if (rival(p.x(), p.y())) return false;
                rows.computeIfAbsent(t, ignored -> new TreeSet<>()).add(p.x() - sx);
            }
            if (rows.size() < Math.max(4, (int) Math.ceil(gap * .65))) return false;
            int bulge = 0, exterior = 0;
            for (var row : rows.values()) {
                if (row.last() >= gap * .55) bulge++;
                if (row.last() >= gap * 1.5 - 1) exterior++;
            }
            if (bulge < Math.max(3, (int) Math.ceil(gap * .3))
                    || exterior > Math.max(1, rows.size() * .15)) return false;
            int square = 0;
            for (int size = Math.max(2, (int) Math.ceil(gap * .18));
                    size <= (int) Math.floor(gap);
                    size++) {
                boolean found = false;
                search:
                for (int x0 = sx + 1;
                        x0 < Math.min(right - size + 2, sx + (int) Math.ceil(gap));
                        x0++) {
                    if (x0 + size - 1 < sx + (int) Math.ceil(gap * .25)) continue;
                    for (int tt = 0; tt < (int) Math.ceil(gap * 1.8) - size + 2; tt++) {
                        boolean solid = true;
                        for (int xx = x0; xx < x0 + size && solid; xx++)
                            for (int dy = 0; dy < size; dy++) {
                                int yy = cap + headward * (tt + dy);
                                if (!core(xx, yy) || xx >= left && !body.contains(key(xx, yy))) {
                                    solid = false;
                                    break;
                                }
                            }
                        if (solid) {
                            found = true;
                            break search;
                        }
                    }
                }
                if (found) square = size;
            }
            if (square == 0) return false;
            int thin = 0;
            for (var e : rows.entrySet()) {
                if (e.getKey() < firstRoot + gap * .75) continue;
                int first = Integer.MIN_VALUE, last = Integer.MIN_VALUE;
                boolean good = false;
                for (int x : e.getValue()) {
                    if (first == Integer.MIN_VALUE) {
                        first = last = x;
                        continue;
                    }
                    if (x == last + 1) last = x;
                    else {
                        if (last - first + 1 < square && first >= gap * .35) good = true;
                        first = last = x;
                    }
                }
                if (last - first + 1 < square && first >= gap * .35) good = true;
                if (good) thin++;
            }
            return thin >= 3 && nonlinear(rows);
        }

        Optional<Proof> prove() {
            if (gap < 4 || left > right || low >= high) return Optional.empty();
            budget.take(
                    (long) (right - left + 1) * (high - low + 1)
                            + heads.size()
                            + allColumns.size());
            var roots = new HashSet<Long>();
            var near = new HashSet<Long>();
            for (int y = low; y <= high; y++) {
                int t = (y - cap) * headward;
                if (t < 0
                        || t > gap * (1.2 + .8 * Math.max(1, shaft.maximumRails()))
                        || !core(left, y)) continue;
                var targets = attached(y);
                if (targets.isEmpty()) continue;
                roots.add(key(left, y));
                for (var target : targets)
                    if ((target.y() - cap) * headward <= gap * 1.15) {
                        near.add(key(left, y));
                        break;
                    }
            }
            if (near.isEmpty()) return Optional.empty();
            var visited = new HashSet<Long>();
            var all = new HashSet<Long>();
            boolean positive = false;
            for (long root : roots) {
                if (visited.contains(root)) continue;
                var pixels = flood(List.of(root), null, false);
                if (pixels.isEmpty()) return Optional.empty();
                visited.addAll(pixels);
                all.addAll(pixels);
                if (qualified(pixels, roots, near)) positive = true;
            }
            if (!positive) return Optional.empty();
            var absence =
                    new RawPrimaryBeamAbsence(raw, w, h, carrier, budget).prove(shaft, allColumns);
            if (absence.isEmpty()) return Optional.empty();
            int minX = w, minY = h, maxX = 0, maxY = 0;
            for (long k : all) {
                var p = pixel(k);
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
            var rootPixels = new ArrayList<Pixel>();
            for (long k : roots) rootPixels.add(pixel(k));
            rootPixels.sort(Comparator.comparingInt(Pixel::y));
            int bands = 0, previous = Integer.MIN_VALUE;
            for (var p : rootPixels) {
                if (previous == Integer.MIN_VALUE || p.y() > previous + 1) bands++;
                previous = p.y();
            }
            return Optional.of(
                    new Proof(
                            shaft.id(),
                            owners,
                            bands,
                            false,
                            all.size(),
                            rootPixels,
                            minX,
                            minY,
                            maxX,
                            maxY,
                            absence.get()));
        }
    }
}
