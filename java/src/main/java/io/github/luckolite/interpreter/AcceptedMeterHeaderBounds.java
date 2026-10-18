// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact sampled-support identity of accepted readings; no outside-component ownership. */
final class AcceptedMeterHeaderBounds {
    enum Producer {
        NATIVE,
        PORTABLE,
        ANDROID,
        UNKNOWN
    }

    static final class Source {
        final byte[] labels, gray;
        final int width, height;
        final List<MeasureRegion> measures;

        private Source(byte[] l, byte[] g, int w, int h, List<MeasureRegion> m) {
            labels = l;
            gray = g;
            width = w;
            height = h;
            measures = List.copyOf(m);
        }
    }

    record Header(
            Source source,
            MeasureRegion owner,
            ScoreMeterChange value,
            MeterChangeDetector.Crop crop,
            Producer producer) {}

    private record Support(BitSet pixels) {}

    private static final int MAX_CROP_PIXELS = 65536,
            MAX_COMPONENT_PIXELS = 32768,
            MAX_HEADERS = 128;

    private AcceptedMeterHeaderBounds() {}

    static Source source(byte[] labels, byte[] gray, int w, int h, List<MeasureRegion> measures) {
        if (labels == null
                || gray == null
                || w < 1
                || h < 1
                || labels.length != (long) w * h
                || gray.length != labels.length
                || measures == null) return null;
        return new Source(labels, gray, w, h, measures);
    }

    static List<MeterChangeDetector.Crop> normalize(Source source, List<Header> headers) {
        if (headers == null) return List.of();
        var original = headers.stream().map(Header::crop).toList();
        if (source == null || headers.size() > MAX_HEADERS) return original;
        var supports = new ArrayList<Support>();
        // Every retained competing reading stays visible; invalid provenance cannot manufacture a
        // pair.
        for (var h : headers) {
            if (h == null
                    || h.source() != source
                    || h.owner() == null
                    || h.value() == null
                    || h.crop() == null
                    || h.producer() == null
                    || h.producer() == Producer.UNKNOWN
                    || h.value().measureIndex() < 0
                    || h.value().measureIndex() >= source.measures.size()
                    || source.measures.get(h.value().measureIndex()) != h.owner()) return original;
            supports.add(support(source, h.crop()));
        }
        var result = new ArrayList<>(original);
        var done = new BitSet(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            if (done.get(i) || supports.get(i) == null) continue;
            var group = new ArrayList<Integer>();
            for (int j = i; j < headers.size(); j++)
                if (supports.get(j) != null
                        && supports.get(i).pixels().equals(supports.get(j).pixels())) group.add(j);
            for (int j : group) done.set(j);
            if (group.size() < 2) continue;
            var first = headers.get(i);
            boolean compatible = true;
            int left = first.crop().left(), right = first.crop().right();
            for (int j : group) {
                var h = headers.get(j);
                if (h.owner() != first.owner()
                        || !h.value().equals(first.value())
                        || h.producer() != first.producer()) compatible = false;
                left = Math.min(left, h.crop().left());
                right = Math.max(right, h.crop().right());
            }
            // Every overlapping distinct retained sample is a rival, not omitted.
            for (int j = 0; j < headers.size(); j++)
                if (supports.get(j) == null
                        || !supports.get(j).pixels().equals(supports.get(i).pixels())) {
                    var c = headers.get(j).crop();
                    for (int p = supports.get(i).pixels().nextSetBit(0);
                            p >= 0;
                            p = supports.get(i).pixels().nextSetBit(p + 1)) {
                        int x = p % source.width, y = p / source.width;
                        if (x >= c.left() && x < c.right() && y >= c.top() && y < c.bottom()) {
                            compatible = false;
                            break;
                        }
                    }
                }
            // Cross-products of differing vertical padding must not expose a new component.
            var normalized = new ArrayList<MeterChangeDetector.Crop>();
            for (int j : group) {
                var c = headers.get(j).crop();
                var n =
                        new MeterChangeDetector.Crop(
                                left, c.top(), right, c.bottom(), c.firstLine(), c.gap());
                var verified = support(source, n);
                if (verified == null || !verified.pixels().equals(supports.get(i).pixels()))
                    compatible = false;
                normalized.add(n);
            }
            if (!compatible) continue;
            for (int k = 0; k < group.size(); k++) result.set(group.get(k), normalized.get(k));
        }
        return List.copyOf(result);
    }

    private static Support support(Source s, MeterChangeDetector.Crop c) {
        int w = s.width, h = s.height;
        if (c.left() < 0
                || c.top() < 0
                || c.right() > w
                || c.bottom() > h
                || c.left() >= c.right()
                || c.top() >= c.bottom()
                || (long) (c.right() - c.left()) * (c.bottom() - c.top()) > MAX_CROP_PIXELS
                || !Float.isFinite(c.gap())
                || c.gap() <= 0) return null;
        int middle = Math.round(c.firstLine() + 2 * c.gap());
        if (middle <= c.top() || middle >= c.bottom()) return null;
        BitSet set = new BitSet(s.labels.length);
        var queue = new ArrayDeque<Integer>();
        boolean above = false, below = false, anyLiteral = false;
        for (int y = c.top(); y < c.bottom(); y++)
            for (int x = c.left(); x < c.right(); x++) {
                int seed = y * w + x;
                if (s.labels[seed] != 5 || set.get(seed)) continue;
                queue.add(seed);
                set.set(seed);
                boolean literal = false;
                int component = 0;
                while (!queue.isEmpty()) {
                    int p = queue.remove(), px = p % w, py = p / w;
                    if (++component > MAX_COMPONENT_PIXELS) return null;
                    if ((s.gray[p] & 255) < 255) literal = true;
                    if (py < middle) above = true;
                    else below = true;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = px + dx, ny = py + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int q = ny * w + nx;
                            if (s.labels[q] != 5) continue;
                            if (nx < c.left()
                                    || nx >= c.right()
                                    || ny < c.top()
                                    || ny >= c.bottom()) continue;
                            if (!set.get(q)) {
                                set.set(q);
                                queue.add(q);
                            }
                        }
                }
                anyLiteral |= literal;
            }
        return set.isEmpty() || !above || !below || !anyLiteral ? null : new Support(set);
    }
}
