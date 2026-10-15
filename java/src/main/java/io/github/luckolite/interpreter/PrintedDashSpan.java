// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optical continuation only. A closed run still needs a proved musical endpoint. */
final class PrintedDashSpan {
    record SystemBand(float left, float right, float top, float bottom, float lastColumn) {
        SystemBand(float left, float right, float top, float bottom) {
            this(left, right, top, bottom, right);
        }
    }

    record WordBox(float left, float right, float top, float bottom) {}

    enum End {
        CLOSED,
        PAGE_EDGE,
        MISSING_CONTINUATION
    }

    record Span(int firstSystem, int lastSystem, List<PrintedDashChains.Run> runs, End end) {
        Span {
            runs = List.copyOf(runs);
        }
    }

    static Optional<Span> follow(
            List<PrintedDashChains.Run> runs,
            List<SystemBand> systems,
            WordBox word,
            float gapX,
            float gapY) {
        if (word == null
                || !Float.isFinite(gapX)
                || !Float.isFinite(gapY)
                || gapX <= 0
                || gapY <= 0) return Optional.empty();
        PrintedDashChains.Run first = null;
        int firstSystem = -1;
        for (var run : runs) {
            if (run.y() < word.top() - gapY * .4f
                    || run.y() > word.bottom() + gapY * .4f
                    || Math.abs(run.left() - word.right()) > gapX * 2) continue;
            int owner = owner(run, systems, gapY);
            if (owner < 0) continue;
            if (first != null) return Optional.empty();
            first = run;
            firstSystem = owner;
        }
        if (first == null) return Optional.empty();
        var path = new ArrayList<PrintedDashChains.Run>();
        path.add(first);
        int row = firstSystem;
        for (; ; ) {
            var last = path.get(path.size() - 1);
            var system = systems.get(row);
            // Engravers can leave blank trailing space after the final sounding column.
            // Reaching that proved column is enough to look for a continuation, not to end a span.
            float edge =
                    system.lastColumn() >= system.left() && system.lastColumn() <= system.right()
                            ? system.lastColumn()
                            : system.right();
            if (last.right() < edge - gapX)
                return Optional.of(new Span(firstSystem, row, path, End.CLOSED));
            if (row + 1 == systems.size())
                return Optional.of(new Span(firstSystem, row, path, End.PAGE_EDGE));
            var nextSystem = systems.get(row + 1);
            if (nextSystem.top() <= system.top()) return Optional.empty();
            PrintedDashChains.Run next = null;
            for (var run : runs) {
                if (owner(run, systems, gapY) != row + 1
                        || Math.abs(run.left() - nextSystem.left()) > gapX * 2) continue;
                if (next != null) return Optional.empty();
                next = run;
            }
            if (next == null)
                return Optional.of(new Span(firstSystem, row, path, End.MISSING_CONTINUATION));
            path.add(next);
            row++;
        }
    }

    private static int owner(PrintedDashChains.Run run, List<SystemBand> systems, float gapY) {
        int found = -1;
        for (int i = 0; i < systems.size(); i++) {
            var system = systems.get(i);
            boolean below =
                    run.y() >= system.bottom() - gapY * .5f
                            && run.y() <= system.bottom() + gapY * 4;
            boolean above =
                    run.y() >= system.top() - gapY * 4 && run.y() <= system.top() + gapY * .5f;
            if (!below && !above) continue;
            if (found >= 0) return -1;
            found = i;
        }
        return found;
    }
}
