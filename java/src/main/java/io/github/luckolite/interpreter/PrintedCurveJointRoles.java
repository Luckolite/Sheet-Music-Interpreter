// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete raw accent/returning-ribbon partitions; never head admission or ink removal. */
final class PrintedCurveJointRoles {
    private static final int MAX_PIXELS = 4096,
            MAX_COLUMNS = 1024,
            MAX_TRIALS = 128,
            MAX_CERTIFICATES = 8;
    private final List<ScoreNoteEvent> notes;
    private final byte[] gray;
    private final int width, height;
    private long remaining;
    private boolean exhausted;
    private final Set<Integer> rejectedComponentPixels = new HashSet<>();
    private final Map<Integer, Component> completeComponents = new HashMap<>();

    private record Typed(PrintedSlurOwners.Location head, float gap) {}

    private record Component(Set<Integer> pixels, int left, int top, int right, int bottom) {}

    private record Partition(Set<Integer> mark, int[] low, int[] high, boolean above, int owner) {}

    PrintedCurveJointRoles(List<ScoreNoteEvent> notes, byte[] gray, int width, int height) {
        this(notes, gray, width, height, 8_000_000L);
    }

    PrintedCurveJointRoles(
            List<ScoreNoteEvent> notes, byte[] gray, int width, int height, long allowance) {
        if (notes == null
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || allowance < 0
                || allowance > 8_000_000L)
            throw new IllegalArgumentException("Invalid joint source");
        this.notes = notes;
        this.gray = gray;
        this.width = width;
        this.height = height;
        remaining = allowance;
    }

    private boolean take(long n) {
        if (exhausted || n < 0 || n > remaining) {
            exhausted = true;
            return false;
        }
        remaining -= n;
        return true;
    }

    PrintedSlurContours.HeadEvidence wrap(
            PrintedSlurContours.HeadEvidence original,
            List<? extends PrintedSlurOwners.Location> heads,
            StaffPitchTrack track) {
        if (!take(heads.size() * 64L) || track == null || !track.verified()) return original;
        var typed = new ArrayList<Typed>();
        for (var h : heads) {
            if (h.owner() < 0 || h.owner() >= notes.size()) return original;
            var n = notes.get(h.owner());
            if (n.staffIndex() != h.staffIndex() || n.staffCount() != h.staffCount())
                return original;
            if ((n.articulations() & NoteArticulation.ACCENT) == 0) continue;
            float g = track.at(h.x())[1];
            if (!Float.isFinite(g) || g < 6 || g > 64) return original;
            typed.add(new Typed(h, g));
        }
        if (typed.isEmpty()) return original;
        return new PrintedSlurContours.HeadEvidence() {
            public boolean contains(int x, int y) {
                return original.contains(x, y);
            }

            public boolean closes(PrintedSlurContours.Point cap, int x, int y, boolean first) {
                return original.closes(cap, x, y, first);
            }

            public long returningOwnerCost() {
                return original.returningOwnerCost();
            }

            public boolean ownsReturningBow(PrintedSlurContours.Bow bow) {
                return original.ownsReturningBow(bow);
            }

            public boolean admitRivalOwnership(long work) {
                return original.admitRivalOwnership(work);
            }

            public boolean ownsCentralRivals(
                    PrintedSlurContours.Bow bow, List<PrintedSlurContours.ForeignRun> rivals) {
                return original.ownsCentralRivals(bow, rivals);
            }

            public List<PrintedSlurContours.JointRibbon> jointRibbons(
                    Map<Integer, List<PrintedSlurContours.RawBand>> unowned) {
                return discover(unowned, typed, heads);
            }
        };
    }

    private List<PrintedSlurContours.JointRibbon> discover(
            Map<Integer, List<PrintedSlurContours.RawBand>> unowned,
            List<Typed> typed,
            List<? extends PrintedSlurOwners.Location> heads) {
        long bands = 0;
        for (var column : unowned.values()) bands += column.size();
        if (exhausted || !take(unowned.size() * 64L + bands * typed.size() * 16L)) return List.of();
        float gap = 0;
        for (var t : typed) gap = Math.max(gap, t.gap());
        var done = new HashSet<Integer>();
        var output = new ArrayList<PrintedSlurContours.JointRibbon>();
        var columns = new ArrayList<>(unowned.keySet());
        Collections.sort(columns);
        for (int x : columns)
            for (var band : unowned.get(x)) {
                int y = (band.first() + band.last()) / 2, seed = y * width + x;
                if (done.contains(seed)) continue;
                boolean close = false;
                for (var t : typed) {
                    double dx = Math.abs(x - t.head().x()) / t.gap(),
                            dy = Math.abs(y - t.head().y()) / t.gap();
                    if (dx <= 2.1 && dy >= .7 && dy <= 8) close = true;
                }
                if (!close) continue;
                Component c = flood(x, y, gap);
                if (exhausted) return List.of();
                if (c == null) continue;
                done.addAll(c.pixels());
                var partition = prove(c, typed, heads);
                if (exhausted) return List.of();
                if (partition != null) output.add(partition);
            }
        return List.copyOf(output);
    }

    private Component flood(int sx, int sy, float gap) {
        int seed = sy * width + sx;
        if (rejectedComponentPixels.contains(seed)) return null;
        if (completeComponents.containsKey(seed)) return completeComponents.get(seed);
        if (!take(128)) return null;
        if ((gray[sy * width + sx] & 255) > 205) return null;
        var pixels = new HashSet<Integer>();
        var q = new ArrayDeque<Integer>();
        pixels.add(sy * width + sx);
        q.add(sy * width + sx);
        int l = sx, r = sx, t = sy, b = sy;
        while (!q.isEmpty()) {
            if (!take(32)) return null;
            int p = q.remove(), x = p % width, y = p / width;
            l = Math.min(l, x);
            r = Math.max(r, x);
            t = Math.min(t, y);
            b = Math.max(b, y);
            // These are seed-independent failures of the actual connected component.
            // Its observed connected members cannot become valid at another seed.
            if (x == 0
                    || x == width - 1
                    || y == 0
                    || y == height - 1
                    || r - l + 1 > MAX_COLUMNS
                    || b - t + 1 > 64) {
                if (take(pixels.size() * 4L)) rejectedComponentPixels.addAll(pixels);
                return null;
            }
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int n = (y + dy) * width + x + dx;
                    if ((gray[n] & 255) <= 205 && pixels.add(n)) {
                        if (pixels.size() > MAX_PIXELS) {
                            if (take(pixels.size() * 4L)) rejectedComponentPixels.addAll(pixels);
                            return null;
                        }
                        q.add(n);
                    }
                }
        }
        if (!take(pixels.size() * 8L)) return null;
        var c = new Component(Set.copyOf(pixels), l, t, r, b);
        for (int p : pixels) completeComponents.put(p, c);
        return c;
    }

    private PrintedSlurContours.JointRibbon prove(
            Component c, List<Typed> typed, List<? extends PrintedSlurOwners.Location> heads) {
        float gap = 0, minGap = Float.MAX_VALUE;
        for (var t : typed) {
            gap = Math.max(gap, t.gap());
            minGap = Math.min(minGap, t.gap());
        }
        if (c.right() - c.left() < 3 * minGap) return null;
        if (!take(heads.size() * 32L)) return null;
        for (var h : heads) {
            float g = gap;
            if (h.x() + .3 * g < c.left()
                    || h.x() - .3 * g > c.right()
                    || h.y() + .3 * g < c.top()
                    || h.y() - .3 * g > c.bottom()) continue;
            if (!take(c.pixels().size() * 8L)) return null;
            for (int p : c.pixels())
                if (Math.abs(p % width - h.x()) < .3 * g && Math.abs(p / width - h.y()) < .3 * g)
                    return null;
        }
        long[] bits = new long[c.right() - c.left() + 1];
        for (int p : c.pixels()) bits[p % width - c.left()] |= 1L << (p / width - c.top());
        int maximum = Math.max(2, Math.round(gap * .42f));
        var profiles = new ArrayList<Partition>();
        for (boolean above : new boolean[] {true, false}) {
            long trials = 1;
            for (long col : bits) {
                if (col == 0) return null;
                int
                        start =
                                above
                                        ? Long.numberOfTrailingZeros(col)
                                        : 63 - Long.numberOfLeadingZeros(col),
                        end = start,
                        step = above ? 1 : -1;
                while (end + step >= 0 && end + step < 64 && (col & (1L << (end + step))) != 0)
                    end += step;
                if (Math.abs(start - end) + 1 > maximum) trials *= maximum;
                if (trials > MAX_TRIALS) return null;
            }
            if (!take(trials * bits.length * 64L)) return null;
            enumerate(
                    c,
                    bits,
                    typed,
                    gap,
                    maximum,
                    above,
                    0,
                    new int[bits.length],
                    new int[bits.length],
                    profiles);
            if (exhausted || profiles.size() >= MAX_CERTIFICATES) return null;
        }
        // Finish every geometric partition before any expensive certificate/consensus.
        var valid = new ArrayList<Partition>();
        for (var p : profiles) {
            if (!take(c.pixels().size() * 256L)) return null;
            var proof = FiniteSlurRibbon.certify(curve(c, p.mark()), maximum);
            if (proof.limitReached()) {
                exhausted = true;
                return null;
            }
            if (proof.complete()) {
                if (p.owner() < 0) return null;
                valid.add(p);
            }
        }
        if (valid.isEmpty()) return null;
        Partition first = valid.get(0);
        var mark = new HashSet<>(first.mark());
        for (var p : valid) {
            if (p.owner() != first.owner() || p.above() != first.above()) return null;
            mark.retainAll(p.mark());
        }
        // Only unanimously mark-owned cells stay marks; every other cell is in the
        // complete ribbon. Revalidate both complete roles after consensus.
        var consensus = partition(c, mark, typed, gap, maximum, first.above());
        if (consensus == null
                || consensus.owner() != first.owner()
                || !take(c.pixels().size() * 256L)) return null;
        var proof = FiniteSlurRibbon.certify(curve(c, mark), maximum);
        if (proof.limitReached()) {
            exhausted = true;
            return null;
        }
        if (!proof.complete()) return null;
        var columns = new ArrayList<PrintedSlurContours.ForeignRun>();
        for (int x = 0; x < bits.length; x++)
            columns.add(
                    new PrintedSlurContours.ForeignRun(
                            x + c.left(),
                            List.of(
                                    new PrintedSlurContours.RawBand(
                                            consensus.low()[x], consensus.high()[x]))));
        return new PrintedSlurContours.JointRibbon(columns);
    }

    private void enumerate(
            Component c,
            long[] bits,
            List<Typed> typed,
            float gap,
            int maximum,
            boolean above,
            int x,
            int[] low,
            int[] high,
            List<Partition> output) {
        if (exhausted || output.size() >= MAX_CERTIFICATES) return;
        if (x == bits.length) {
            var mark = new HashSet<Integer>();
            for (int p : c.pixels())
                if (p / width < low[p % width - c.left()] || p / width > high[p % width - c.left()])
                    mark.add(p);
            var p = partition(c, mark, typed, gap, maximum, above);
            if (p != null) output.add(p);
            return;
        }
        long col = bits[x];
        int start = above ? Long.numberOfTrailingZeros(col) : 63 - Long.numberOfLeadingZeros(col),
                end = start,
                step = above ? 1 : -1;
        while (end + step >= 0 && end + step < 64 && (col & (1L << (end + step))) != 0) end += step;
        int count = Math.abs(end - start) + 1;
        if (count <= maximum) {
            low[x] = c.top() + Math.min(start, end);
            high[x] = c.top() + Math.max(start, end);
            enumerate(c, bits, typed, gap, maximum, above, x + 1, low, high, output);
        } else
            for (int n = 1; n <= maximum; n++) {
                int last = start + step * (n - 1);
                low[x] = c.top() + Math.min(start, last);
                high[x] = c.top() + Math.max(start, last);
                enumerate(c, bits, typed, gap, maximum, above, x + 1, low, high, output);
            }
    }

    private Set<Long> curve(Component c, Set<Integer> mark) {
        var cells = new HashSet<Long>();
        for (int p : c.pixels())
            if (!mark.contains(p)) cells.add(FiniteSlurRibbon.pixel(p % width, p / width));
        return cells;
    }

    private Partition partition(
            Component c,
            Set<Integer> mark,
            List<Typed> typed,
            float gap,
            int maximum,
            boolean above) {
        if (mark.size() < 8 || !take(c.pixels().size() * 16L + typed.size() * 64L)) return null;
        int l = width, r = -1, t = height, b = -1;
        for (int p : mark) {
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        if (r - l + 1 < gap * .75f
                || r - l + 1 > gap * 2.1f + 1
                || b - t + 1 < gap * .35f
                || b - t + 1 > gap * 1.6f) return null;
        if (!take((r - l + 3L) * (b - t + 3) * 16L)) return null;
        if (!OpenChevron.matches(
                        mark.stream().mapToInt(Integer::intValue).toArray(), width, l, t, r, b)
                || !open(mark, l, t, r, b)) return null;
        var owners = new HashSet<Integer>();
        float cx = (l + r) * .5f, cy = (t + b) * .5f;
        for (var candidate : typed) {
            var h = candidate.head();
            float g = candidate.gap();
            float clearance = (above ? h.y() - cy : cy - h.y()) / g;
            if (Math.abs(cx - h.x()) > g * .7f || clearance < .7f || clearance > 8) continue;
            if (!take((long) Math.ceil(g * g * 16 + 64))) return null;
            if (!PrintedSlurOwners.headBody(gray, width, height, h, g)) continue;
            owners.add(h.owner());
        }
        int owner = owners.size() == 1 ? owners.iterator().next() : -1;
        int size = c.right() - c.left() + 1;
        int[] low = new int[size], high = new int[size], count = new int[size];
        Arrays.fill(low, height);
        Arrays.fill(high, -1);
        for (int p : c.pixels())
            if (!mark.contains(p)) {
                int x = p % width - c.left(), y = p / width;
                low[x] = Math.min(low[x], y);
                high[x] = Math.max(high[x], y);
                count[x]++;
            }
        int[] profileLow = new int[size], profileHigh = new int[size];
        for (int x = 0; x < size; x++) {
            if (count[x] == 0 || count[x] != high[x] - low[x] + 1 || count[x] > maximum)
                return null;
            profileLow[x] = above ? low[x] : height - 1 - high[x];
            profileHigh[x] = above ? high[x] : height - 1 - low[x];
        }
        if (size - 1 < gap * 3
                || !take(size * 256L)
                || !PrintedSlurContours.completeReturningProfile(
                        profileLow, profileHigh, 0, size - 1, gap)) return null;
        return new Partition(Set.copyOf(mark), low, high, above, owner);
    }

    private boolean open(Set<Integer> mark, int l, int t, int r, int b) {
        int w = r - l + 3, h = b - t + 3;
        boolean[] seen = new boolean[w * h];
        var q = new ArrayDeque<Integer>();
        q.add(0);
        seen[0] = true;
        while (!q.isEmpty()) {
            int p = q.remove(), x = p % w, y = p / w;
            for (int step : new int[] {-1, 1, -w, w}) {
                int n = p + step;
                if (n < 0 || n >= seen.length || step == -1 && x == 0 || step == 1 && x == w - 1)
                    continue;
                int xx = n % w + l - 1, yy = n / w + t - 1;
                if (seen[n] || mark.contains(yy * width + xx)) continue;
                seen[n] = true;
                q.add(n);
            }
        }
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                if (!mark.contains(y * width + x) && !seen[(y - t + 1) * w + x - l + 1])
                    return false;
        return true;
    }
}
