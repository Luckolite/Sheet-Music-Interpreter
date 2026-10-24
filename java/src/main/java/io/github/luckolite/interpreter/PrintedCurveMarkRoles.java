// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A complete raw, explicitly attack-owned accent is distinct from a neighbouring curve. */
final class PrintedCurveMarkRoles {
    private record Typed(PrintedSlurOwners.Location head, float gap) {}

    private record Glyph(Set<Integer> pixels, int left, int top, int right, int bottom) {}

    private record Owned(Glyph glyph, int owner) {}

    private final List<ScoreNoteEvent> notes;
    private final byte[] gray;
    private final int width, height;
    private long remaining;
    private boolean exhausted;
    private final Set<PrintedSlurContours.Bow> certified =
            Collections.newSetFromMap(new IdentityHashMap<>());

    PrintedCurveMarkRoles(List<ScoreNoteEvent> notes, byte[] gray, int width, int height) {
        this(notes, gray, width, height, 8_000_000L);
    }

    PrintedCurveMarkRoles(
            List<ScoreNoteEvent> notes, byte[] gray, int width, int height, long allowance) {
        Objects.requireNonNull(notes);
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || allowance < 0
                || allowance > 8_000_000L)
            throw new IllegalArgumentException("Invalid raw mark source");
        this.notes = notes;
        this.gray = gray;
        this.width = width;
        this.height = height;
        remaining = allowance;
    }

    private boolean take(long work) {
        if (exhausted || work < 0 || work > remaining) {
            exhausted = true;
            return false;
        }
        remaining -= work;
        return true;
    }

    boolean ownsRivalCurve(PrintedSlurContours.Bow bow) {
        return certified.contains(bow);
    }

    PrintedSlurContours.HeadEvidence wrap(
            PrintedSlurContours.HeadEvidence original,
            List<? extends PrintedSlurOwners.Location> heads,
            StaffPitchTrack track) {
        if (!take(heads.size() * 64L) || track == null || !track.verified()) return original;
        var typed = new ArrayList<Typed>();
        for (var head : heads) {
            if (head.owner() < 0 || head.owner() >= notes.size()) return original;
            var note = notes.get(head.owner());
            if (note.staffIndex() != head.staffIndex() || note.staffCount() != head.staffCount())
                return original;
            if ((note.articulations() & NoteArticulation.ACCENT) == 0) continue;
            float gap = track.at(head.x())[1];
            if (!Float.isFinite(gap) || gap < 6 || gap > 64) return original;
            typed.add(new Typed(head, gap));
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
                return take(work);
            }

            public boolean ownsCentralRivals(
                    PrintedSlurContours.Bow bow, List<PrintedSlurContours.ForeignRun> rivals) {
                boolean owned = prove(bow, rivals, typed);
                if (owned) certified.add(bow);
                return owned;
            }
        };
    }

    private boolean prove(
            PrintedSlurContours.Bow bow,
            List<PrintedSlurContours.ForeignRun> rivals,
            List<Typed> typed) {
        if (exhausted || rivals.isEmpty() || rivals.size() > 4096) return false;
        long pixels = 0;
        for (var run : rivals) {
            if (run.x() < 0 || run.x() >= width || run.members().isEmpty()) return false;
            for (var band : run.members()) {
                if (band.first() < 0 || band.last() >= height || band.last() < band.first())
                    return false;
                pixels += band.last() - band.first() + 1L;
            }
        }
        if (pixels == 0 || !take(pixels * 16 + typed.size() * 64L)) return false;
        var claimed = new HashMap<Integer, Owned>();
        var ownerGlyph = new HashMap<Integer, Glyph>();
        int glyphs = 0;
        for (var run : rivals)
            for (var band : run.members())
                for (int y = band.first(); y <= band.last(); y++) {
                    int pixel = y * width + run.x();
                    if (claimed.containsKey(pixel)) continue;
                    if ((gray[pixel] & 255) > 205 || ++glyphs > 16) return false;
                    Owned owned = owned(pixel, bow, typed);
                    if (owned == null) return false;
                    Glyph prior = ownerGlyph.putIfAbsent(owned.owner(), owned.glyph());
                    if (prior != null && !prior.pixels().contains(pixel)) return false;
                    for (int p : owned.glyph().pixels()) claimed.put(p, owned);
                }
        for (var point : bow.points()) {
            int x = point.x(), y = Math.round(point.y());
            if (!Float.isFinite(point.y())
                    || x < 0
                    || x >= width
                    || y < 0
                    || y >= height
                    || claimed.containsKey(y * width + x)) return false;
        }
        return !exhausted;
    }

    private Owned owned(int seed, PrintedSlurContours.Bow bow, List<Typed> typed) {
        float gap = 0;
        for (var t : typed) gap = Math.max(gap, t.gap());
        int rx = (int) Math.ceil(gap * 2.5),
                ry = (int) Math.ceil(gap * 2),
                sx = seed % width,
                sy = seed / width;
        long area = (rx * 2L + 1) * (ry * 2L + 1);
        long ownership = 0;
        for (var t : typed) ownership += (long) Math.ceil(t.gap() * t.gap() * 16 + 64);
        if (!take(area * 64 + ownership)) return null;
        int left = sx - rx, right = sx + rx, top = sy - ry, bottom = sy + ry;
        if (left < 0 || right >= width || top < 0 || bottom >= height) return null;
        var pixels = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        pixels.add(seed);
        queue.add(seed);
        int l = sx, r = sx, t = sy, b = sy;
        while (!queue.isEmpty()) {
            int p = queue.removeFirst(), x = p % width, y = p / width;
            l = Math.min(l, x);
            r = Math.max(r, x);
            t = Math.min(t, y);
            b = Math.max(b, y);
            if (x == left || x == right || y == top || y == bottom) return null;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy, next = yy * width + xx;
                    if ((gray[next] & 255) <= 205 && !pixels.contains(next)) {
                        if (pixels.size() == 4096) return null;
                        pixels.add(next);
                        queue.add(next);
                    }
                }
        }
        int[] body = pixels.stream().mapToInt(Integer::intValue).toArray();
        if (!openTopology(pixels, l, t, r, b) || !OpenChevron.matches(body, width, l, t, r, b))
            return null;
        float cx = (l + r) * .5f, cy = (t + b) * .5f;
        int owner = -1;
        for (var candidate : typed) {
            var head = candidate.head();
            float g = candidate.gap(), clearance = bow.above() ? head.y() - cy : cy - head.y();
            if (r - l + 1 < g * .75
                    || r - l + 1 > g * 2.2 + 1
                    || b - t + 1 < g * .35
                    || b - t + 1 > g * 1.35
                    || Math.abs(cx - head.x()) > g * .75
                    || clearance < g * .7
                    || clearance > g * 8
                    || head.x() < bow.start().x() - g * .85
                    || head.x() > bow.end().x() + g * .85) continue;
            if (!PrintedSlurOwners.headBody(gray, width, height, head, g)) continue;
            if (owner >= 0 && owner != head.owner()) return null;
            owner = head.owner();
        }
        return owner < 0 ? null : new Owned(new Glyph(Set.copyOf(pixels), l, t, r, b), owner);
    }

    /** A closed counter cannot borrow the two visible arms of an otherwise open chevron. */
    private boolean openTopology(Set<Integer> pixels, int left, int top, int right, int bottom) {
        int w = right - left + 3, h = bottom - top + 3;
        boolean[] ink = new boolean[w * h], seen = new boolean[w * h];
        for (int pixel : pixels)
            ink[(pixel / width - top + 1) * w + pixel % width - left + 1] = true;
        int[] queue = new int[ink.length];
        int next = 0, size = 1;
        queue[0] = 0;
        seen[0] = true;
        while (next < size) {
            int p = queue[next++], x = p % w, y = p / w;
            for (int direction = 0; direction < 4; direction++) {
                int xx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0),
                        yy = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                int id = yy * w + xx;
                if (!ink[id] && !seen[id]) {
                    seen[id] = true;
                    queue[size++] = id;
                }
            }
        }
        for (int i = 0; i < ink.length; i++) if (!ink[i] && !seen[i]) return false;
        return true;
    }
}
