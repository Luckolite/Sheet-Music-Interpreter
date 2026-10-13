// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Explicit physical-page ownership before local note slots become joined-score indices. */
public final class ScoreSlurPages {
    private ScoreSlurPages() {}

    public record Anchor(int pageIndex, int noteIndex) {
        public Anchor {
            if (pageIndex < 0 || noteIndex < 0)
                throw new IllegalArgumentException("Invalid page-local slur owner");
        }
    }

    public record Span(
            String id,
            Anchor start,
            Anchor end,
            ScoreSlurSpan.Placement placement,
            ScoreSlurSpan.LineType lineType,
            List<ScoreSlurSpan.ArcEvidence> evidence) {
        public Span {
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            // Reuse the joined span's identity, style, and geometry validation.
            var checked = new ScoreSlurSpan(id, 0, 1, placement, lineType, evidence);
            evidence = checked.evidence();
            if (start.equals(end)) throw new IllegalArgumentException("Identical slur endpoints");
        }
    }

    /** List order is the chosen joined-score order; physical pages need not be consecutive. */
    public record Page(int pageIndex, int noteCount) {
        public Page {
            if (pageIndex < 0 || noteCount < 0)
                throw new IllegalArgumentException("Invalid source page note count");
        }
    }

    public enum Missing {
        NONE,
        PAGE_NOT_JOINED,
        NOTE_NOT_ON_PAGE
    }

    public record Unresolved(Span source, Missing start, Missing end) {
        public Unresolved {
            Objects.requireNonNull(source);
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            if (start == Missing.NONE && end == Missing.NONE)
                throw new IllegalArgumentException("Unresolved slur needs a missing owner");
        }
    }

    public record Joined(List<ScoreSlurSpan> resolved, List<Unresolved> unresolved, int noteCount) {
        public Joined {
            resolved = List.copyOf(resolved);
            unresolved = List.copyOf(unresolved);
            ScoreSlurOwners.validated(resolved, noteCount);
        }
    }

    private record Layout(int offset, int count) {}

    private static Missing missing(Anchor anchor, Map<Integer, Layout> pages) {
        var page = pages.get(anchor.pageIndex());
        if (page == null) return Missing.PAGE_NOT_JOINED;
        return anchor.noteIndex() < page.count() ? Missing.NONE : Missing.NOTE_NOT_ON_PAGE;
    }

    /** No offset is inferred from page number, measure count, pitch, or visual proximity. */
    public static Joined join(List<Span> spans, List<Page> orderedPages) {
        var pages = new HashMap<Integer, Layout>();
        int count = 0;
        for (var page : orderedPages) {
            if (pages.putIfAbsent(page.pageIndex(), new Layout(count, page.noteCount())) != null)
                throw new IllegalArgumentException("Duplicate physical source page");
            count = Math.addExact(count, page.noteCount());
        }
        var ids = new HashSet<String>();
        var resolved = new ArrayList<ScoreSlurSpan>();
        var unresolved = new ArrayList<Unresolved>();
        for (var span : spans) {
            if (!ids.add(span.id()))
                throw new IllegalArgumentException("Duplicate page slur identity");
            var a = missing(span.start(), pages);
            var b = missing(span.end(), pages);
            if (a != Missing.NONE || b != Missing.NONE) {
                unresolved.add(new Unresolved(span, a, b));
                continue;
            }
            int start =
                    Math.addExact(
                            pages.get(span.start().pageIndex()).offset(), span.start().noteIndex());
            int end =
                    Math.addExact(
                            pages.get(span.end().pageIndex()).offset(), span.end().noteIndex());
            resolved.add(
                    new ScoreSlurSpan(
                            span.id(),
                            start,
                            end,
                            span.placement(),
                            span.lineType(),
                            span.evidence()));
        }
        return new Joined(resolved, unresolved, count);
    }
}
