// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Additive recognition of isolated grace-to-principal bows in an exact staff contrast field.
 * This derived feature view never erases source ink, changes a head, or grants background ownership. */
final class CalibratedGraceSlurs {
    record Observed(
            PrintedSlurPageCapture.Frame<?> frame,
            PrintedSlurContours.Bow bow,
            int graceOwner,
            int principalOwner) {}

    private record Band(int x, int top, int bottom) {}

    private final List<ScoreNoteEvent> notes;
    private final byte[] gray;
    private final int width, height;
    private final LiteralStaffContrast.Budget budget;

    CalibratedGraceSlurs(List<ScoreNoteEvent> notes, byte[] gray, int width, int height) {
        this(notes, gray, width, height, 8000000);
    }

    CalibratedGraceSlurs(
            List<ScoreNoteEvent> notes, byte[] gray, int width, int height, long allowance) {
        if (notes == null
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height)
            throw new IllegalArgumentException("Invalid grace source");
        this.notes = notes;
        this.gray = gray;
        this.width = width;
        this.height = height;
        budget = new LiteralStaffContrast.Budget(allowance);
    }

    List<Observed> discover(List<? extends PrintedSlurPageCapture.Frame<?>> frames) {
        var out = new ArrayList<Observed>();
        for (var frame : frames) {
            if (!budget.take(frame.heads().size() * 64L)) return List.of();
            for (var grace : frame.heads()) {
                if (!validNote(grace) || !grace.grace()) continue;
                float gap = frame.track().at(grace.x())[1];
                var gn = notes.get(grace.owner());
                int graceStem = gn.stemDirection();
                if ((gn.articulations() & NoteOrnament.GRACE) == 0 || Math.abs(graceStem) != 1)
                    continue;
                boolean clustered = false;
                for (var other : frame.heads())
                    if (other.owner() != grace.owner()
                            && other.grace()
                            && Math.abs(other.x() - grace.x()) < 3 * gap) clustered = true;
                if (clustered) continue;
                var possible = new ArrayList<PrintedSlurOwners.Location>();
                for (var other : frame.heads())
                    if (validNote(other)
                            && !other.grace()
                            && other.staffIndex() == grace.staffIndex()
                            && other.staffCount() == grace.staffCount()
                            && other.x() > grace.x()
                            && other.x() - grace.x() <= 3 * gap
                            && notes.get(other.owner()).measureIndex() == gn.measureIndex())
                        possible.add(other);
                possible.sort(Comparator.comparingDouble(PrintedSlurOwners.Location::x));
                if (possible.isEmpty()
                        || possible.size() > 1
                                && possible.get(1).x() - possible.get(0).x() <= gap * .3f) continue;
                var principal = possible.get(0);
                var pn = notes.get(principal.owner());
                if (pn.stemDirection() != -graceStem) continue;
                if (!budget.take((long) Math.ceil(gap * gap * 64 + 1024))) return List.of();
                if (!PrintedSlurOwners.headBody(gray, width, height, grace, gap)
                        || !PrintedSlurOwners.headBody(gray, width, height, principal, gap))
                    continue;
                if (PrintedStemDirection.detect(
                                gray, width, height, principal.x(), principal.y(), gap)
                        != -graceStem) continue;
                int left = Math.max(frame.left(), Math.round(grace.x() - 2 * gap)),
                        right = Math.min(frame.right(), Math.round(grace.x() + 3 * gap));
                float top = frame.track().at(grace.x())[0] - 4 * gap;
                var reference =
                        LiteralStaffContrast.observe(
                                gray, width, height, left, right, top, gap, budget);
                if (budget.exhausted()) return List.of();
                if (reference.field().isEmpty()) continue;
                var bows =
                        recognize(
                                reference.field().orElseThrow(),
                                frame,
                                grace,
                                principal,
                                graceStem,
                                gap);
                if (budget.exhausted()) return List.of();
                if (bows.size() == 1)
                    out.add(new Observed(frame, bows.get(0), grace.owner(), principal.owner()));
            }
        }
        return budget.exhausted() ? List.of() : List.copyOf(out);
    }

    long workRemaining() {
        return budget.remaining();
    }

    private boolean validNote(PrintedSlurOwners.Location h) {
        return h.owner() >= 0
                && h.owner() < notes.size()
                && notes.get(h.owner()).staffIndex() == h.staffIndex()
                && notes.get(h.owner()).staffCount() == h.staffCount();
    }

    private List<PrintedSlurContours.Bow> recognize(
            LiteralStaffContrast.Field field,
            PrintedSlurPageCapture.Frame<?> frame,
            PrintedSlurOwners.Location grace,
            PrintedSlurOwners.Location principal,
            int graceStem,
            float gap) {
        int left = field.left(),
                right = field.right(),
                top = field.firstY(),
                bottom = field.lastY(),
                w = right - left + 1,
                h = bottom - top + 1;
        long headWork = 0;
        for (var head : frame.heads()) {
            float g = frame.track().at(head.x())[1];
            headWork += (long) Math.ceil(g * g * 32 + 128);
        }
        if (!budget.take((long) w * h * 64 + headWork)) return List.of();
        boolean[] core = new boolean[w * h], extra = new boolean[w * h];
        var visible = new ArrayList<PrintedSlurOwners.Location>();
        for (var head : frame.heads())
            if (PrintedSlurOwners.headBody(
                    gray, width, height, head, frame.track().at(head.x())[1])) visible.add(head);
        var hints =
                PrintedSlurOwners.headEvidence(frame.heads(), gray, width, height, frame.track());
        boolean[] headFringe = new boolean[w * h];
        for (var head : visible) {
            float g = frame.track().at(head.x())[1];
            for (int yy = Math.max(top, Math.round(head.y() - .55f * g));
                    yy <= bottom && yy <= head.y() + .55f * g;
                    yy++)
                for (int xx = Math.max(left, Math.round(head.x() - .72f * g));
                        xx <= right && xx <= head.x() + .72f * g;
                        xx++) {
                    double dx = (xx - head.x()) / (.72 * g), dy = (yy - head.y()) / (.55 * g);
                    if (dx * dx + dy * dy <= 1) headFringe[(yy - top) * w + xx - left] = true;
                }
        }
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                var cell = field.cell(x, y);
                boolean ink = cell.kind() == LiteralStaffContrast.Kind.EXTRA_CORE,
                        fringe = cell.kind() == LiteralStaffContrast.Kind.EXTRA_FRINGE;
                if (!ink && !fringe || hints.contains(x, y)) continue;
                // The old independently visible head ellipse also bounds weak head-proximal
                // proposal fringe. It is excluded from this feature view, never called paper.
                boolean nearHead =
                        fringe && cell.observed() > 205 && headFringe[(y - top) * w + x - left];
                if (nearHead) continue;
                int p = (y - top) * w + x - left;
                core[p] = ink;
                extra[p] = true;
            }
        int side = graceStem > 0 ? 1 : -1;
        int first = Math.max(top, Math.round(grace.y() + Math.min(side * .4f, side * 1.8f) * gap));
        int last =
                Math.min(bottom, Math.round(grace.y() + Math.max(side * .4f, side * 1.8f) * gap));
        int maximum = Math.max(2, Math.round(gap * .42f));
        // Weak complete bands can support the taper or a short carrier crossing.
        // They cannot turn a tall unknown shaft/body into a thin curve.
        for (int x = left; x <= right; x++)
            for (int y = first; y <= last; ) {
                int p = (y - top) * w + x - left;
                if (!extra[p]) {
                    y++;
                    continue;
                }
                int begin = y;
                boolean strong = false;
                while (y <= last && extra[(y - top) * w + x - left]) {
                    strong |= core[(y - top) * w + x - left];
                    y++;
                }
                if (!strong && y - begin <= maximum)
                    for (int yy = begin; yy < y; yy++) core[(yy - top) * w + x - left] = true;
            }
        var bands = new ArrayList<Band>();
        var unowned = new ArrayList<Band>();
        var columns = new ArrayList<List<Integer>>(Collections.nCopies(w, null));
        for (int x = left; x <= right; x++)
            for (int y = top; y <= bottom; ) {
                if (!core[(y - top) * w + x - left]) {
                    y++;
                    continue;
                }
                int begin = y;
                while (y <= bottom && core[(y - top) * w + x - left]) y++;
                var band = new Band(x, begin, y - 1);
                if (y - begin > maximum) {
                    unowned.add(band);
                    continue;
                }
                int id = bands.size();
                bands.add(band);
                if (columns.get(x - left) == null) columns.set(x - left, new ArrayList<>());
                columns.get(x - left).add(id);
            }
        if (bands.size() > 4096 || unowned.size() > 4096) return List.of();
        int[] parents = new int[bands.size()];
        for (int i = 0; i < parents.length; i++) parents[i] = i;
        for (int x = 1; x < w; x++) {
            var a = columns.get(x - 1);
            var b = columns.get(x);
            if (a == null || b == null) continue;
            for (int i : a)
                for (int j : b) {
                    var aa = bands.get(i);
                    var bb = bands.get(j);
                    if (aa.top() <= bb.bottom() + 1 && bb.top() <= aa.bottom() + 1)
                        parents[root(j, parents)] = root(i, parents);
                }
        }
        var groups = new LinkedHashMap<Integer, List<Band>>();
        for (int i = 0; i < bands.size(); i++)
            groups.computeIfAbsent(root(i, parents), k -> new ArrayList<>()).add(bands.get(i));
        boolean rawStem =
                PrintedStemDirection.detect(gray, width, height, principal.x(), principal.y(), gap)
                        == -graceStem;
        var out = new ArrayList<PrintedSlurContours.Bow>();
        for (var component : groups.values()) {
            if (component.size() < 12) continue;
            if (!budget.take(component.size() * (unowned.size() * 16L + 512) + headWork))
                return List.of();
            var runs = new ArrayList<PrintedSlurContours.ForeignRun>();
            boolean valid = true;
            int previous = -1;
            for (var b : component) {
                if (b.x() <= previous
                        || b.x() == left
                        || b.x() == right
                        || b.top() == top
                        || b.bottom() == bottom) {
                    valid = false;
                    break;
                }
                previous = b.x();
                for (var u : unowned)
                    if (Math.abs(u.x() - b.x()) <= 1
                            && u.top() <= b.bottom() + 1
                            && b.top() <= u.bottom() + 1) {
                        float edge = principal.x() + (graceStem > 0 ? -.6f : .6f) * gap;
                        if (!rawStem || Math.abs(u.x() - edge) > .3f * gap) {
                            valid = false;
                            break;
                        }
                    }
                if (!valid) break;
                runs.add(
                        new PrintedSlurContours.ForeignRun(
                                b.x(),
                                List.of(new PrintedSlurContours.RawBand(b.top(), b.bottom()))));
            }
            if (!valid) continue;
            var bow = PrintedSlurContours.fitPixelCells(runs, frame.track());
            if (bow == null || bow.above() != (graceStem < 0)) continue;
            double startClear = side * (bow.start().y() - grace.y()) / gap,
                    endClear = side * (bow.end().y() - principal.y()) / gap;
            if (startClear < .2 || startClear > 2 || endClear < .2 || endClear > 2) continue;
            var bound =
                    PrintedSlurOwners.bindPixelCells(
                            "calibrated-grace",
                            0,
                            bow,
                            frame.heads(),
                            gray,
                            width,
                            height,
                            frame.track());
            if (bound.span().isEmpty()) continue;
            var span = bound.span().orElseThrow();
            if (span.start().noteIndex() == grace.owner()
                    && span.end().noteIndex() == principal.owner()) out.add(bow);
        }
        return out;
    }

    private static int root(int n, int[] parents) {
        while (parents[n] != n) {
            parents[n] = parents[parents[n]];
            n = parents[n];
        }
        return n;
    }
}
