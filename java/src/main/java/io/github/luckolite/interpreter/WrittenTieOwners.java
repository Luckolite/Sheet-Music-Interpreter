// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.BiPredicate;

/** Implicit written ties require unique, exact releasing owners at both endpoints. */
public final class WrittenTieOwners {
    private WrittenTieOwners() {}

    public record Row(
            int staff,
            int diatonic,
            int alter,
            long start,
            long end,
            boolean pitched,
            boolean grace,
            boolean tiedFromPrevious) {
        public Row {
            if (start < 0 || end < start)
                throw new IllegalArgumentException("Invalid written tie interval");
        }
    }

    /** Indices refer to the supplied event list, not retained note owners or sorted positions. */
    public record Link(int startIndex, int endIndex) {}

    private record Indexed(int index, Row row) {}

    private record Boundary(int staff, int diatonic, long time) {}

    public static List<Link> links(List<Row> rows) {
        return links(
                rows, (earlier, current) -> rows.get(earlier).alter() == rows.get(current).alter());
    }

    /** Additional source-pitch compatibility may be stricter than the written staff/pitch key. */
    public static List<Link> links(List<Row> rows, BiPredicate<Integer, Integer> compatible) {
        Objects.requireNonNull(compatible);
        var ordered = new ArrayList<Indexed>(rows.size());
        var releases = new HashMap<Boundary, List<Indexed>>();
        for (int i = 0; i < rows.size(); i++) {
            var row = Objects.requireNonNull(rows.get(i));
            var indexed = new Indexed(i, row);
            ordered.add(indexed);
            if (row.pitched() && !row.grace() && row.end() > row.start())
                releases.computeIfAbsent(
                                new Boundary(row.staff(), row.diatonic(), row.end()),
                                unused -> new ArrayList<>())
                        .add(indexed);
        }
        ordered.sort(
                Comparator.comparingLong((Indexed n) -> n.row().start())
                        .thenComparingInt(n -> n.row().diatonic()));
        var proposed = new ArrayList<Link>();
        var uses = new HashMap<Integer, Integer>();
        for (var current : ordered) {
            var n = current.row();
            if (n.grace() || !n.pitched() || !n.tiedFromPrevious() || n.end() <= n.start())
                continue;
            var candidates = releases.get(new Boundary(n.staff(), n.diatonic(), n.start()));
            if (candidates == null) continue;
            int prior = -1;
            for (var candidate : candidates)
                if (compatible.test(candidate.index(), current.index())) {
                    if (prior >= 0) {
                        prior = -1;
                        break;
                    }
                    prior = candidate.index();
                }
            if (prior >= 0) {
                proposed.add(new Link(prior, current.index()));
                uses.merge(prior, 1, Integer::sum);
            }
        }
        // A single held owner cannot arbitrarily supply two indistinguishable continuations.
        return proposed.stream().filter(link -> uses.get(link.startIndex()) == 1).toList();
    }
}
