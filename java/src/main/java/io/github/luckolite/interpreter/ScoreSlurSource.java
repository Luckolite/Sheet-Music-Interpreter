// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Immutable printed arcs and the original identities of the current retained note slots. */
public record ScoreSlurSource(
        List<ScoreSlurPages.Span> spans,
        List<Optional<ScoreSlurPages.Anchor>> noteOrigins,
        List<ScoreSlurPages.Span> candidates) {
    /** Existing callers supply only confirmed phrases. Deferred geometry never engraves directly. */
    public ScoreSlurSource(
            List<ScoreSlurPages.Span> spans, List<Optional<ScoreSlurPages.Anchor>> noteOrigins) {
        this(spans, noteOrigins, List.of());
    }

    static final int MAX_NOTES = 1000000;

    private static final class UnownedOrigins extends AbstractList<Optional<ScoreSlurPages.Anchor>>
            implements RandomAccess {
        private final int count;

        UnownedOrigins(int count) {
            this.count = count;
        }

        @Override
        public int size() {
            return count;
        }

        @Override
        public Optional<ScoreSlurPages.Anchor> get(int index) {
            Objects.checkIndex(index, count);
            return Optional.empty();
        }
    }

    public ScoreSlurSource {
        if (noteOrigins.size() > MAX_NOTES)
            throw new IllegalArgumentException("Too many retained source note identities");
        spans = List.copyOf(spans);
        candidates = List.copyOf(candidates);
        if (!(noteOrigins instanceof UnownedOrigins)) {
            noteOrigins = List.copyOf(noteOrigins);
            if (noteOrigins.stream().allMatch(Optional::isEmpty))
                noteOrigins = new UnownedOrigins(noteOrigins.size());
        }
        var ids = new HashSet<String>();
        for (var span : spans)
            if (!ids.add(span.id()))
                throw new IllegalArgumentException("Duplicate source slur identity");
        for (var candidate : candidates)
            if (!ids.add(candidate.id()))
                throw new IllegalArgumentException("Duplicate source arc identity");
        var origins = new HashSet<ScoreSlurPages.Anchor>();
        if (!(noteOrigins instanceof UnownedOrigins))
            for (var origin : noteOrigins)
                if (origin.isPresent() && !origins.add(origin.get()))
                    throw new IllegalArgumentException("Duplicate original note identity");
    }

    public static ScoreSlurSource empty(int noteCount) {
        if (noteCount < 0 || noteCount > MAX_NOTES)
            throw new IllegalArgumentException("Invalid retained note count");
        return new ScoreSlurSource(List.of(), new UnownedOrigins(noteCount));
    }

    public static ScoreSlurSource capture(
            List<ScoreSlurPages.Span> spans, List<ScoreSlurPages.Page> orderedPages) {
        return capture(spans, List.of(), orderedPages);
    }

    /** Captures physical endpoints while their musical classification is still unknown. */
    public static ScoreSlurSource capture(
            List<ScoreSlurPages.Span> spans,
            List<ScoreSlurPages.Span> candidates,
            List<ScoreSlurPages.Page> orderedPages) {
        // Validate all IDs jointly before allocating original identities.
        var arcs = new ArrayList<ScoreSlurPages.Span>(spans);
        arcs.addAll(candidates);
        var joined = ScoreSlurPages.join(arcs, orderedPages);
        if (joined.noteCount() > MAX_NOTES)
            throw new IllegalArgumentException("Too many captured source note identities");
        if (arcs.isEmpty()) return empty(joined.noteCount());
        var origins = new ArrayList<Optional<ScoreSlurPages.Anchor>>(joined.noteCount());
        for (var page : orderedPages)
            for (int note = 0; note < page.noteCount(); note++)
                origins.add(Optional.of(new ScoreSlurPages.Anchor(page.pageIndex(), note)));
        return new ScoreSlurSource(spans, origins, candidates);
    }

    /** New list order is explicit; -1 denotes a new note without a printed source identity. */
    public ScoreSlurSource edited(List<Integer> sourceOwners) {
        var used = new HashSet<Integer>();
        var origins = new ArrayList<Optional<ScoreSlurPages.Anchor>>();
        for (var owner : sourceOwners) {
            if (owner == null || owner < -1 || owner >= noteOrigins.size())
                throw new IllegalArgumentException("Invalid retained edit note owner");
            if (owner >= 0 && !used.add(owner))
                throw new IllegalArgumentException("Ambiguous retained edit note owner");
            origins.add(owner == -1 ? Optional.empty() : noteOrigins.get(owner));
        }
        return new ScoreSlurSource(spans, origins, candidates);
    }

    /** Missing endpoints remain in spans; only fully owned arcs are eligible for engraving. */
    public Resolution resolve() {
        if (spans.isEmpty()) return new Resolution(List.of(), List.of());
        if (noteOrigins instanceof UnownedOrigins) return new Resolution(List.of(), spans);
        var owners = new HashMap<ScoreSlurPages.Anchor, Integer>();
        for (int note = 0; note < noteOrigins.size(); note++) {
            var origin = noteOrigins.get(note);
            if (origin.isPresent()) owners.put(origin.get(), note);
        }
        var resolved = new ArrayList<ScoreSlurSpan>();
        var unresolved = new ArrayList<ScoreSlurPages.Span>();
        for (var span : spans) {
            var start = owners.get(span.start());
            var end = owners.get(span.end());
            if (start == null || end == null) unresolved.add(span);
            else
                resolved.add(
                        new ScoreSlurSpan(
                                span.id(),
                                start,
                                end,
                                span.placement(),
                                span.lineType(),
                                span.evidence()));
        }
        return new Resolution(resolved, unresolved);
    }

    public boolean hasOriginalOwners() {
        return !(noteOrigins instanceof UnownedOrigins);
    }

    /** Excluded arrangement parts keep their arcs in retained source, without inventing endpoints. */
    public List<ScoreSlurSpan> engravingSpans(List<Integer> sourceOwners) {
        var selected = new HashSet<Integer>();
        for (var owner : sourceOwners) {
            if (owner == null || owner < -1 || owner >= noteOrigins.size())
                throw new IllegalArgumentException("Invalid engraving source note owner");
            if (owner >= 0 && !selected.add(owner))
                throw new IllegalArgumentException("Ambiguous engraving source note owner");
        }
        return resolve().resolved().stream()
                .filter(
                        span ->
                                selected.contains(span.startOwner())
                                        && selected.contains(span.endOwner()))
                .toList();
    }

    /** Join original identities without rewriting physical page ownership to output page numbers. */
    public static ScoreSlurSource join(List<ScoreSlurSource> pages) {
        var spans = new LinkedHashMap<String, ScoreSlurPages.Span>();
        var candidates = new LinkedHashMap<String, ScoreSlurPages.Span>();
        var origins = new ArrayList<Optional<ScoreSlurPages.Anchor>>();
        int total = 0;
        for (var page : pages) {
            total = Math.addExact(total, page.noteOrigins().size());
            if (total > MAX_NOTES)
                throw new IllegalArgumentException("Too many joined source notes");
            for (var span : page.spans()) {
                if (candidates.containsKey(span.id()))
                    throw new IllegalArgumentException("Conflicting source arc classification");
                merge(spans, span);
            }
            for (var candidate : page.candidates()) {
                if (spans.containsKey(candidate.id()))
                    throw new IllegalArgumentException("Conflicting source arc classification");
                merge(candidates, candidate);
            }
        }
        if (spans.isEmpty() && candidates.isEmpty()) return empty(total);
        for (var page : pages) origins.addAll(page.noteOrigins());
        return new ScoreSlurSource(
                List.copyOf(spans.values()), origins, List.copyOf(candidates.values()));
    }

    private static void merge(Map<String, ScoreSlurPages.Span> arcs, ScoreSlurPages.Span arc) {
        var prior = arcs.putIfAbsent(arc.id(), arc);
        if (prior != null && !prior.equals(arc))
            throw new IllegalArgumentException("Conflicting source arc identities");
    }

    public record Resolution(List<ScoreSlurSpan> resolved, List<ScoreSlurPages.Span> unresolved) {
        public Resolution {
            resolved = List.copyOf(resolved);
            unresolved = List.copyOf(unresolved);
        }
    }
}
