// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;

/** Raster proof for isolated breath signs and simple note-equals-note pulse relations. */
final class PrintedExpressionGlyphs {
    private final PortableOrnamentGlyphs pulses = new PortableOrnamentGlyphs();
    private final PortableOrnamentGlyphs breaths = new PortableOrnamentGlyphs();

    record Mark(String text, float left, float top, float right, float bottom) {
        PlayingTechniqueDetector.Word word(int width, int height) {
            return new PlayingTechniqueDetector.Word(
                    text, left / width, top / height, right / width, bottom / height);
        }
    }

    private record Part(int left, int top, int right, int bottom, int ink) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        float cy() {
            return (top + bottom) * .5f;
        }

        PortableNoteOrnaments.Bounds bounds() {
            return new PortableNoteOrnaments.Bounds(left, top, right, bottom);
        }
    }

    private record Pulse(Part part, int kind, double value) {}

    static PrintedExpressionGlyphs load(InputStream stream) throws IOException {
        var result = new PrintedExpressionGlyphs();
        try (var in = new DataInputStream(stream)) {
            if (in.readInt() != 0x45584731) throw new IOException("Not EXG1 expression templates");
            int count = in.readInt();
            if (count < 1 || count > 64) throw new IOException("Invalid template count");
            for (int i = 0; i < count; i++) {
                int kind = in.readInt(), w = in.readInt(), h = in.readInt();
                if (kind < 1 || kind > 14 || w < 1 || h < 1 || w > 256 || h > 256)
                    throw new IOException("Invalid expression template");
                byte[] gray = new byte[w * h];
                try {
                    in.readFully(gray);
                } catch (EOFException error) {
                    throw new EOFException("Truncated expression template");
                }
                (kind >= 13 ? result.breaths : result.pulses).add(gray, w, h, kind, false);
            }
            if (in.read() != -1) throw new IOException("Trailing expression template data");
        }
        return result;
    }

    List<Mark> detect(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        var result = new ArrayList<>(detect(score, staffs, gray, width, height, 175));
        for (var candidate : detect(score, staffs, gray, width, height, 155)) {
            if (MetricModulationText.parse(candidate.text()).isEmpty()) continue;
            boolean overlap = false;
            for (var old : result)
                if (old.left < candidate.right
                        && candidate.left < old.right
                        && old.top < candidate.bottom
                        && candidate.top < old.bottom) overlap = true;
            if (!overlap) result.add(candidate);
        }
        return List.copyOf(result);
    }

    private List<Mark> detect(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height,
            int threshold) {
        if (gray == null || width < 1 || height < 1 || gray.length != (long) width * height)
            return List.of();
        var result = new ArrayList<Mark>();
        var seen = new HashSet<String>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap) || gap < 6 || gap > Math.min(width, height) / 8f) continue;
            // Directions are outside the five rails. Bound work to each written system's width.
            for (boolean above : new boolean[] {true, false}) {
                int top =
                        Math.max(
                                0,
                                (int) (above ? staff.top() - gap * 7 : staff.bottom() + gap * .5f));
                int bottom =
                        Math.min(
                                height,
                                (int) (above ? staff.top() - gap * .5f : staff.bottom() + gap * 5));
                if (bottom <= top) continue;
                float center = (staff.top() + staff.bottom()) * .5f / height;
                float left = 1, right = 0;
                for (var m : score.measures())
                    if (center >= m.top() - gap / height && center <= m.bottom() + gap / height) {
                        left = Math.min(left, m.left());
                        right = Math.max(right, m.right());
                    }
                int x0 = Math.max(0, (int) (left * width - gap * 2)),
                        x1 = Math.min(width, (int) (right * width + gap * 2));
                if (x1 <= x0) continue;
                var parts = components(gray, width, x0, top, x1, bottom, threshold);
                var notes = new ArrayList<Pulse>();
                for (var part : parts) {
                    if (part.height() >= gap * .65f
                            && part.height() <= gap * 4.2f
                            && part.width() <= gap * 2.5f) {
                        var match = pulses.templateMatch(gray, width, part.bounds());
                        if (match.kind() > 0 && match.score() >= .66f && match.margin() >= .018f) {
                            double value = Math.scalb(4, -((match.kind() - 1) / 2));
                            notes.add(new Pulse(part, match.kind(), value));
                        }
                    }
                    if (!above
                            || part.height() < gap * .6f
                            || part.height() > gap * 1.9f
                            || part.width() > gap * 1.9f) continue;
                    var match = breaths.templateMatch(gray, width, part.bounds());
                    if (match.kind() < 13 || match.score() < .61f) continue;
                    // A comma embedded in a text line is punctuation, not a pause.
                    if (parts.stream()
                            .anyMatch(
                                    p ->
                                            p != part
                                                    && p.height() > gap * .28f
                                                    && Math.abs(p.cy() - part.cy()) < gap * .8f
                                                    && Math.min(
                                                                    Math.abs(p.left - part.right),
                                                                    Math.abs(part.left - p.right))
                                                            < gap * .85f)) continue;
                    if (PrintedDirectionStaff.at(
                                    List.of(staff),
                                    score.measures(),
                                    score.notes(),
                                    gray,
                                    width,
                                    height,
                                    part.left,
                                    part.top,
                                    part.bottom)
                            == null) continue;
                    String key = "breath:" + part.left + ":" + part.top;
                    if (seen.add(key))
                        result.add(
                                new Mark(
                                        match.kind() == 14 ? "breath tick" : "breath comma",
                                        part.left,
                                        part.top,
                                        part.right,
                                        part.bottom));
                }
                var bands =
                        parts.stream()
                                .filter(
                                        p ->
                                                p.width() > gap * .35f
                                                        && p.width() < gap * 1.5f
                                                        && p.height() < gap * .3f
                                                        && p.width() >= p.height() * 2.5f)
                                .toList();
                for (int i = 0; i < bands.size(); i++)
                    for (int j = i + 1; j < bands.size(); j++) {
                        var a = bands.get(i);
                        var b = bands.get(j);
                        if (!equalsBands(a, b, gap)) continue;
                        int el = Math.min(a.left, b.left), er = Math.max(a.right, b.right);
                        float ey = (a.cy() + b.cy()) * .5f;
                        var leftNotes =
                                notes.stream()
                                        .filter(
                                                n ->
                                                        n.part.right < el
                                                                && el - n.part.right < gap * 2
                                                                && Math.abs(n.part.cy() - ey)
                                                                        < gap * 1.8f)
                                        .toList();
                        var rightNotes =
                                notes.stream()
                                        .filter(
                                                n ->
                                                        n.part.left > er
                                                                && n.part.left - er < gap * 2
                                                                && Math.abs(n.part.cy() - ey)
                                                                        < gap * 1.8f)
                                        .toList();
                        if (leftNotes.size() != 1 || rightNotes.size() != 1) continue;
                        var l = leftNotes.get(0);
                        var r = rightNotes.get(0);
                        if (l.value != 4
                                && r.value != 4
                                && Math.abs(l.part.height() - r.part.height()) > gap * 1.4f)
                            continue;
                        int[] ld = dots(l, parts, gap, el), rd = dots(r, parts, gap, x1);
                        if (ld == null || rd == null) continue;
                        int y0 = Math.min(l.part.top, r.part.top),
                                y1 = Math.max(l.part.bottom, r.part.bottom);
                        if (PrintedDirectionStaff.at(
                                                List.of(staff),
                                                score.measures(),
                                                score.notes(),
                                                gray,
                                                width,
                                                height,
                                                l.part.left,
                                                y0,
                                                y1)
                                        == null
                                && PrintedDirectionStaff.at(
                                                List.of(staff),
                                                score.measures(),
                                                score.notes(),
                                                gray,
                                                width,
                                                height,
                                                r.part.right,
                                                y0,
                                                y1)
                                        == null) continue;
                        // Every component inside the equation must be a note, equals stroke or dot.
                        boolean extra = false;
                        for (var p : parts)
                            if (p.left >= l.part.left
                                    && p.right <= rd[1]
                                    && p.top >= y0 - gap * .2f
                                    && p.bottom <= y1 + gap * .2f
                                    && p != l.part
                                    && p != r.part
                                    && p != a
                                    && p != b
                                    && !dot(p, l, gap)
                                    && !dot(p, r, gap)) extra = true;
                        if (extra) continue;
                        String key = "metric:" + el + ":" + Math.round(ey);
                        if (seen.add(key))
                            result.add(
                                    new Mark(
                                            name(l.value, ld[0]) + " = " + name(r.value, rd[0]),
                                            l.part.left,
                                            y0,
                                            rd[1],
                                            y1));
                    }
            }
        }
        return List.copyOf(result);
    }

    private static String name(double value, int dots) {
        String base =
                value == 4
                        ? "whole"
                        : value == 2
                                ? "half"
                                : value == 1
                                        ? "quarter"
                                        : value == .5
                                                ? "eighth"
                                                : value == .25 ? "sixteenth" : "32nd";
        return (dots == 2 ? "double dotted " : dots == 1 ? "dotted " : "") + base;
    }

    private static boolean dot(Part p, Pulse n, float gap) {
        var b = n.part;
        float headY = (n.kind & 1) == 1 ? b.bottom - gap * .35f : b.top + gap * .35f;
        return p.left > b.right
                && p.left - b.right < gap * 1.15f
                && p.width() >= 2
                && p.height() >= 2
                && p.width() < gap * .55f
                && p.height() < gap * .55f
                && Math.abs(p.cy() - headY) < gap * .55f
                && p.ink >= p.width() * p.height() * .45f;
    }

    private static int[] dots(Pulse n, List<Part> parts, float gap, int end) {
        var dots =
                parts.stream()
                        .filter(p -> p.right < end && dot(p, n, gap))
                        .sorted(Comparator.comparingInt(Part::left))
                        .toList();
        if (dots.size() > 2) return null;
        if (dots.size() == 2 && Math.abs(dots.get(0).cy() - dots.get(1).cy()) > gap * .2f)
            return null;
        return new int[] {
            dots.size(), dots.isEmpty() ? n.part.right : dots.get(dots.size() - 1).right
        };
    }

    private static boolean equalsBands(Part a, Part b, float gap) {
        float separation = Math.abs(a.cy() - b.cy());
        return a.width() > gap * .35f
                && b.width() > gap * .35f
                && a.width() < gap * 1.5f
                && b.width() < gap * 1.5f
                && a.width() >= a.height() * 2.5f
                && b.width() >= b.height() * 2.5f
                && a.height() < gap * .3f
                && b.height() < gap * .3f
                && separation > gap * .15f
                && separation < gap * .85f
                && Math.min(a.right, b.right) - Math.max(a.left, b.left)
                        >= Math.max(a.width(), b.width()) * .8f;
    }

    private static List<Part> components(
            byte[] gray, int width, int left, int top, int right, int bottom, int threshold) {
        int w = right - left, h = bottom - top;
        var seen = new boolean[w * h];
        var queue = new int[w * h];
        var result = new ArrayList<Part>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int seed = y * w + x;
                if (seen[seed] || (gray[(top + y) * width + left + x] & 255) > threshold) continue;
                int read = 0, size = 1, minX = x, maxX = x, minY = y, maxY = y;
                queue[0] = seed;
                seen[seed] = true;
                while (read < size) {
                    int at = queue[read++], px = at % w, py = at / w;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = px + dx, ny = py + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next]
                                    && (gray[(top + ny) * width + left + nx] & 255) <= threshold) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                if (size >= 3)
                    result.add(
                            new Part(
                                    left + minX,
                                    top + minY,
                                    left + maxX + 1,
                                    top + maxY + 1,
                                    size));
            }
        return result;
    }
}
