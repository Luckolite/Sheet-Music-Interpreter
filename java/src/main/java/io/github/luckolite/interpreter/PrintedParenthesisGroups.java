// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Recognizes a single printed pair around explicit source heads; never changes their playback. */
final class PrintedParenthesisGroups {
    record Candidate(int frame, PrintedParenthesisShape.Glyph glyph) {}

    record Group(
            List<Integer> members,
            PrintedParenthesisShape.Glyph left,
            PrintedParenthesisShape.Glyph right) {
        Group {
            members = List.copyOf(members);
        }
    }

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

        boolean refused() {
            return refused;
        }

        long remaining() {
            return remaining;
        }
    }

    private PrintedParenthesisGroups() {}

    static List<Candidate> discover(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            boolean calibrated,
            LiteralStaffContrast.CatalogBudget references,
            Budget work) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || frames == null
                || frames.size() > 128
                || references == null
                || work == null) throw new IllegalArgumentException();
        if (calibrated) return discoverCalibrated(gray, width, height, frames, references, work);
        var out = new ArrayList<Candidate>();
        for (int index = 0; index < frames.size(); index++) {
            var frame = frames.get(index);
            if (frame.heads().isEmpty() || frame.heads().size() > 4096 || !frame.track().verified())
                continue;
            int left = Math.max(1, frame.left()), right = Math.min(width - 2, frame.right());
            int top = height, bottom = -1;
            float maxGap = 0;
            for (var head : frame.heads()) {
                float gap = frame.track().at(head.x())[1];
                if (!Float.isFinite(gap) || gap < 8 || gap > 64) continue;
                maxGap = Math.max(maxGap, gap);
                top = Math.min(top, (int) Math.floor(head.y() - 2 * gap));
                bottom = Math.max(bottom, (int) Math.ceil(head.y() + 2 * gap));
            }
            if (maxGap == 0) continue;
            top = Math.max(1, top);
            bottom = Math.min(height - 2, bottom);
            LiteralStaffContrast.Field field = null;
            if (calibrated) {
                float[] phase = frame.track().at(width * .5f);
                var catalog =
                        LiteralStaffContrast.catalog(
                                gray, width, height, phase[0] - 4 * phase[1], phase[1], references);
                if (references.refused()) return List.of();
                if (catalog.isEmpty()) continue;
                var observed = catalog.get().field(left, right, references);
                if (references.refused()) return List.of();
                if (observed.isEmpty()) continue;
                field = observed.get();
                top = field.firstY();
                bottom = field.lastY();
            }
            int w = right - left + 1, h = bottom - top + 1;
            long area = (long) w * h;
            if (w < 3 || h < 3 || area > 1_000_000) continue;
            long profileWork =
                    calibrated ? 0 : (long) w * 5 * ((long) Math.ceil(.7 * maxGap) + 4) * 32;
            if (!work.take(area * 16 + profileWork + frame.heads().size() * 64L)) return List.of();
            var rules = new PrintedCurveRuleInk(null, gray, width, height, 205, frame.track());
            boolean[] ink = new boolean[(int) area];
            int[] queue = new int[(int) area];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    ink[y * w + x] =
                            calibrated
                                    ? field.cell(x + left, y + top).kind()
                                            == LiteralStaffContrast.Kind.EXTRA_CORE
                                    : rules.sample(x + left, y + top) < 205;
            for (int pixel = 0; pixel < ink.length; pixel++)
                if (ink[pixel]) {
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
                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                                int next = yy * w + xx;
                                if (ink[next]) {
                                    ink[next] = false;
                                    queue[count++] = next;
                                }
                            }
                    }
                    if (count < 12 || count > 4096 || r - l > 64 || b - t > 128) continue;
                    if (!work.take(count * 16L + frame.heads().size() * 16L + 4096))
                        return List.of();
                    int[] pixels = new int[count];
                    for (int i = 0; i < count; i++)
                        pixels[i] = (queue[i] / w + top) * width + queue[i] % w + left;
                    float gap = frame.track().at((l + r) * .5f + left)[1];
                    var glyph =
                            PrintedParenthesisShape.classify(
                                    pixels,
                                    width,
                                    height,
                                    gap,
                                    l == 0 || r == w - 1 || t == 0 || b == h - 1);
                    if (glyph.isPresent()) out.add(new Candidate(index, glyph.get()));
                }
        }
        return work.refused() || references.refused() ? List.of() : List.copyOf(out);
    }

    private static List<Candidate> discoverCalibrated(
            byte[] gray,
            int width,
            int height,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            LiteralStaffContrast.CatalogBudget references,
            Budget work) {
        var found = new LinkedHashSet<Candidate>();
        for (int index = 0; index < frames.size(); index++) {
            var frame = frames.get(index);
            if (frame.heads().isEmpty() || frame.heads().size() > 4096 || !frame.track().verified())
                continue;
            float[] phase = frame.track().at(width * .5f);
            var catalog =
                    LiteralStaffContrast.catalog(
                            gray, width, height, phase[0] - 4 * phase[1], phase[1], references);
            if (references.refused()) return List.of();
            if (catalog.isEmpty()) continue;
            for (var head : frame.heads()) {
                float gap = frame.track().at(head.x())[1];
                if (!Float.isFinite(gap) || gap < 8 || gap > 64) continue;
                int left = Math.max(1, (int) Math.floor(head.x() - 2.5f * gap));
                int right = Math.min(width - 2, (int) Math.ceil(head.x() + 2.5f * gap));
                var observed = catalog.get().field(left, right, references);
                if (references.refused()) return List.of();
                if (observed.isEmpty()) continue;
                var field = observed.get();
                int top = field.firstY(), w = right - left + 1, h = field.lastY() - top + 1;
                if (!work.take((long) w * h * 16 + frame.heads().size() * 64L)) return List.of();
                boolean[] ink = new boolean[w * h];
                int[] queue = new int[ink.length];
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        ink[y * w + x] =
                                field.cell(x + left, y + top).kind()
                                        == LiteralStaffContrast.Kind.EXTRA_CORE;
                for (int pixel = 0; pixel < ink.length; pixel++)
                    if (ink[pixel]) {
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
                                    int xx = x + dx, yy = y + dy;
                                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                                    int next = yy * w + xx;
                                    if (ink[next]) {
                                        ink[next] = false;
                                        queue[count++] = next;
                                    }
                                }
                        }
                        if (count < 12 || count > 4096 || r - l > 64 || b - t > 128) continue;
                        if (!work.take(count * 16L + frame.heads().size() * 16L + 4096))
                            return List.of();
                        int[] pixels = new int[count];
                        for (int i = 0; i < count; i++)
                            pixels[i] = (queue[i] / w + top) * width + queue[i] % w + left;
                        float localGap = frame.track().at((l + r) * .5f + left)[1];
                        var glyph =
                                PrintedParenthesisShape.classify(
                                        pixels,
                                        width,
                                        height,
                                        localGap,
                                        l == 0 || r == w - 1 || t == 0 || b == h - 1);
                        if (glyph.isPresent()) found.add(new Candidate(index, glyph.get()));
                    }
            }
        }
        return references.refused() || work.refused() ? List.of() : List.copyOf(found);
    }

    /** Retains all plausible glyphs and owners before checking unique pair membership. */
    static List<Group> bind(
            List<Candidate> ordinary,
            List<Candidate> calibrated,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames,
            byte[] raw,
            int width,
            int height,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> words,
            Budget work) {
        var candidates = new ArrayList<Candidate>(ordinary);
        for (var c : calibrated) {
            // One physical glyph seen by two feature routes is one candidate. Differing nearby
            // glyphs remain rivals; a route cannot discard them to manufacture uniqueness.
            boolean same =
                    ordinary.stream()
                            .anyMatch(a -> a.frame() == c.frame() && a.glyph().equals(c.glyph()));
            if (!same) candidates.add(c);
        }
        if (candidates.size() > 4096) return List.of();
        var anchors = new HashMap<Candidate, Integer>();
        var owners = new HashMap<Integer, Integer>();
        for (var frame : frames)
            for (var head : frame.heads()) owners.merge(head.owner(), 1, Integer::sum);
        for (var c : candidates) {
            if (c.frame() < 0 || c.frame() >= frames.size()) continue;
            var frame = frames.get(c.frame());
            var glyph = c.glyph();
            float gap = glyph.gap();
            if (!work.take(
                    frame.heads().size() * 64L
                            + (long) Math.ceil(gap * gap) * 32
                            + words.size() * 16L)) return List.of();
            if (words.stream()
                    .anyMatch(
                            w ->
                                    w.left() * width <= glyph.right()
                                            && w.right() * width >= glyph.left()
                                            && w.top() * height <= glyph.bottom()
                                            && w.bottom() * height >= glyph.top())) continue;
            var possible = new ArrayList<PrintedSlurOwners.Location>();
            for (var head : frame.heads()) {
                float dx =
                        glyph.side() == PrintedParenthesisShape.Side.LEFT
                                ? head.x() - glyph.centerX()
                                : glyph.centerX() - head.x();
                if (dx >= .45f * gap
                        && dx <= 2 * gap
                        && Math.abs(head.y() - glyph.centerY()) <= .65f * gap) possible.add(head);
            }
            if (possible.size() != 1) continue;
            var head = possible.get(0);
            if (head.grace()
                    || owners.getOrDefault(head.owner(), 0) != 1
                    || head.owner() >= notes.size()
                    || !PrintedSlurOwners.headBody(raw, width, height, head, gap)) continue;
            anchors.put(c, head.owner());
        }
        var proposals = new ArrayList<Group>();
        for (var l : candidates)
            if (l.glyph().side() == PrintedParenthesisShape.Side.LEFT && anchors.containsKey(l))
                for (var r : candidates)
                    if (r.frame() == l.frame()
                            && r.glyph().side() == PrintedParenthesisShape.Side.RIGHT
                            && anchors.containsKey(r)) {
                        if (!work.take(frames.get(l.frame()).heads().size() * 64L + 64))
                            return List.of();
                        var a = notes.get(anchors.get(l));
                        var b = notes.get(anchors.get(r));
                        if (a.measureIndex() != b.measureIndex()
                                || a.staffIndex() != b.staffIndex()
                                || a.staffCount() != b.staffCount()) continue;
                        var heads = frames.get(l.frame()).heads();
                        var first =
                                heads.stream()
                                        .filter(h -> h.owner() == anchors.get(l))
                                        .findFirst()
                                        .orElseThrow();
                        var last =
                                heads.stream()
                                        .filter(h -> h.owner() == anchors.get(r))
                                        .findFirst()
                                        .orElseThrow();
                        if (first.x() >= last.x() || last.x() - first.x() > 64 * l.glyph().gap())
                            continue;
                        var members = new ArrayList<PrintedSlurOwners.Location>();
                        boolean ambiguous = false;
                        for (var head : heads)
                            if (head.x() >= first.x() && head.x() <= last.x()) {
                                if (head.owner() >= notes.size()) {
                                    ambiguous = true;
                                    break;
                                }
                                var n = notes.get(head.owner());
                                if (!work.take(
                                        (long) Math.ceil(l.glyph().gap() * l.glyph().gap()) * 4))
                                    return List.of();
                                if (n.measureIndex() != a.measureIndex()
                                        || n.staffIndex() != a.staffIndex()
                                        || n.staffCount() != a.staffCount()
                                        || head.grace()
                                        || owners.getOrDefault(head.owner(), 0) != 1) {
                                    ambiguous = true;
                                    break;
                                }
                                if (n.kind() != ScoreNoteEvent.Kind.PITCHED
                                        || !PrintedSlurOwners.headBody(
                                                raw, width, height, head, l.glyph().gap())) {
                                    ambiguous = true;
                                    break;
                                }
                                members.add(head);
                            }
                        if (ambiguous || members.size() < 2 || members.size() > 64) continue;
                        members.sort(Comparator.comparingDouble(PrintedSlurOwners.Location::x));
                        for (int i = 1; i < members.size(); i++)
                            if (members.get(i).x() - members.get(i - 1).x() < .5f * l.glyph().gap())
                                ambiguous = true;
                        if (ambiguous) continue;
                        proposals.add(
                                new Group(
                                        members.stream()
                                                .map(PrintedSlurOwners.Location::owner)
                                                .toList(),
                                        l.glyph(),
                                        r.glyph()));
                    }
        // Reject overlapping group proposals as well as a bracket that could participate in two.
        var out = new ArrayList<Group>();
        for (var p : proposals) {
            boolean rival =
                    proposals.stream()
                            .anyMatch(
                                    q ->
                                            q != p
                                                    && q.members().stream()
                                                            .anyMatch(p.members()::contains));
            if (!rival) out.add(p);
        }
        return work.refused() ? List.of() : List.copyOf(out);
    }
}
