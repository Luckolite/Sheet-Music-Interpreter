// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Private candidate: printed two-endpoint tremolos retain identity independently of note order. */
public record ScoreTremoloSource(List<Pair> pairs, List<Optional<Anchor>> noteOrigins) {
    public record Anchor(int pageIndex, int noteIndex) {
        public Anchor {
            if (pageIndex < 0 || noteIndex < 0)
                throw new IllegalArgumentException("Invalid tremolo origin");
        }
    }

    /** Each endpoint may be a chord. Stroke count is notation, not a performance-rate policy. */
    public record Evidence(int pageIndex, float left, float top, float right, float bottom) {
        public Evidence {
            if (pageIndex < 0
                    || !Float.isFinite(left)
                    || !Float.isFinite(top)
                    || !Float.isFinite(right)
                    || !Float.isFinite(bottom)
                    || left < 0
                    || top < 0
                    || right > 1
                    || bottom > 1
                    || left >= right
                    || top >= bottom)
                throw new IllegalArgumentException("Invalid tremolo source geometry");
        }
    }

    public enum ClockPolicy {
        LEGACY_SHARED_DURATION(0),
        UNRESOLVED(1),
        SHARED_DURATION(2),
        INDEPENDENT_ENDPOINT_DURATIONS(3);
        private final int wireCode;

        ClockPolicy(int wireCode) {
            this.wireCode = wireCode;
        }

        int wireCode() {
            return wireCode;
        }

        static ClockPolicy fromWireCode(int code) {
            for (ClockPolicy value : values()) if (value.wireCode == code) return value;
            return null;
        }
    }

    public record Pair(
            String id,
            List<Anchor> first,
            List<Anchor> second,
            int strokes,
            List<Evidence> evidence,
            ClockPolicy clockPolicy) {
        public Pair {
            Objects.requireNonNull(id);
            first = List.copyOf(first);
            second = List.copyOf(second);
            evidence = List.copyOf(evidence);
            Objects.requireNonNull(clockPolicy);
            if (id.isBlank()
                    || id.length() > 256
                    || first.isEmpty()
                    || second.isEmpty()
                    || first.size() > 128
                    || second.size() > 128
                    || strokes < 1
                    || strokes > 8
                    || evidence.isEmpty()
                    || evidence.size() > 64)
                throw new IllegalArgumentException("Invalid printed tremolo pair");
            Set<Anchor> owners = new HashSet<>();
            for (Anchor anchor : first)
                if (!owners.add(anchor))
                    throw new IllegalArgumentException("Duplicate tremolo endpoint");
            for (Anchor anchor : second)
                if (!owners.add(anchor))
                    throw new IllegalArgumentException("Overlapping tremolo endpoints");
        }

        /** Source-compatible legacy constructor; preserves published pair clock behavior. */
        public Pair(
                String id,
                List<Anchor> first,
                List<Anchor> second,
                int strokes,
                List<Evidence> evidence) {
            this(id, first, second, strokes, evidence, ClockPolicy.LEGACY_SHARED_DURATION);
        }
    }

    public record Resolved(Pair source, List<Integer> first, List<Integer> second) {
        public Resolved {
            first = List.copyOf(first);
            second = List.copyOf(second);
        }
    }

    public record Resolution(List<Resolved> resolved, List<Pair> unresolved) {
        public Resolution {
            resolved = List.copyOf(resolved);
            unresolved = List.copyOf(unresolved);
        }
    }

    public ScoreTremoloSource {
        pairs = List.copyOf(pairs);
        noteOrigins = List.copyOf(noteOrigins);
        if (noteOrigins.size() > 1_000_000)
            throw new IllegalArgumentException("Too many tremolo note owners");
        Set<String> ids = new HashSet<>();
        Set<Anchor> endpoints = new HashSet<>();
        for (Pair pair : pairs) {
            if (!ids.add(pair.id)) throw new IllegalArgumentException("Duplicate tremolo identity");
            for (List<Anchor> side : List.of(pair.first, pair.second))
                for (Anchor anchor : side)
                    if (!endpoints.add(anchor))
                        throw new IllegalArgumentException("Ambiguous tremolo endpoint ownership");
        }
        Set<Anchor> retained = new HashSet<>();
        for (Optional<Anchor> origin : noteOrigins)
            if (origin.isPresent() && !retained.add(origin.get()))
                throw new IllegalArgumentException("Duplicate original note identity");
    }

    public static ScoreTremoloSource empty(int count) {
        if (count < 0 || count > 1_000_000)
            throw new IllegalArgumentException("Invalid note count");
        return new ScoreTremoloSource(List.of(), Collections.nCopies(count, Optional.empty()));
    }

    /** Capture final retained note slots, never transient segmentation component IDs. */
    public static ScoreTremoloSource capture(int pageIndex, int noteCount, List<Pair> pairs) {
        if (pageIndex < 0 || noteCount < 0 || noteCount > 1_000_000)
            throw new IllegalArgumentException("Invalid captured page");
        for (Pair pair : pairs)
            for (List<Anchor> side : List.of(pair.first, pair.second))
                for (Anchor anchor : side)
                    if (anchor.pageIndex != pageIndex || anchor.noteIndex >= noteCount)
                        throw new IllegalArgumentException("Pair endpoint outside captured page");
        if (pairs.isEmpty()) return empty(noteCount);
        List<Optional<Anchor>> origins = new ArrayList<>();
        for (int i = 0; i < noteCount; i++) origins.add(Optional.of(new Anchor(pageIndex, i)));
        return new ScoreTremoloSource(pairs, origins);
    }

    /** -1 inserts an unowned note. Deleted endpoints remain evidence, never guessed replacements. */
    public ScoreTremoloSource edited(List<Integer> sourceOwners) {
        Set<Integer> used = new HashSet<>();
        List<Optional<Anchor>> origins = new ArrayList<>();
        for (Integer owner : sourceOwners) {
            if (owner == null
                    || owner < -1
                    || owner >= noteOrigins.size()
                    || owner >= 0 && !used.add(owner))
                throw new IllegalArgumentException("Invalid or ambiguous edit owner");
            origins.add(owner < 0 ? Optional.empty() : noteOrigins.get(owner));
        }
        return new ScoreTremoloSource(pairs, origins);
    }

    /** Retain only uniquely matched printed heads after annotation/ornament filtering. */
    public ScoreTremoloSource retainFinalHeads(
            List<ScoreNoteEvent> original, List<ScoreNoteEvent> retained) {
        if (original.size() != noteOrigins.size())
            throw new IllegalArgumentException("Original tremolo note count");
        if (pairs.isEmpty()) return empty(retained.size());
        record Head(int measure, int staff, int count, float position, float y) {}
        var candidates = new HashMap<Head, List<Integer>>();
        for (int i = 0; i < original.size(); i++) {
            var n = original.get(i);
            var key =
                    new Head(
                            n.measureIndex(),
                            n.staffIndex(),
                            n.staffCount(),
                            n.positionInMeasure(),
                            n.pageY());
            candidates.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        List<Integer> owners = new ArrayList<>();
        var counts = new HashMap<Integer, Integer>();
        for (var n : retained) {
            var matches =
                    candidates.getOrDefault(
                            new Head(
                                    n.measureIndex(),
                                    n.staffIndex(),
                                    n.staffCount(),
                                    n.positionInMeasure(),
                                    n.pageY()),
                            List.of());
            int owner = matches.size() == 1 ? matches.get(0) : -1;
            owners.add(owner);
            if (owner >= 0) counts.merge(owner, 1, Integer::sum);
        }
        for (int i = 0; i < owners.size(); i++)
            if (counts.getOrDefault(owners.get(i), 0) > 1) owners.set(i, -1);
        return edited(owners);
    }

    public static ScoreTremoloSource join(List<ScoreTremoloSource> pages) {
        List<Pair> pairs = new ArrayList<>();
        List<Optional<Anchor>> origins = new ArrayList<>();
        for (ScoreTremoloSource page : pages) {
            pairs.addAll(page.pairs);
            origins.addAll(page.noteOrigins);
        }
        return new ScoreTremoloSource(pairs, origins);
    }

    /** Ownership remains available through resolve even when source clock policy is unknown. */
    public boolean hasPerformancePairs() {
        return pairs.stream().anyMatch(pair -> pair.clockPolicy != ClockPolicy.UNRESOLVED);
    }

    /** Clock-eligible source ownership only; timing still validates all written values. */
    public Resolution performanceResolution() {
        Resolution ownership = resolve();
        return new Resolution(
                ownership.resolved.stream()
                        .filter(pair -> pair.source.clockPolicy != ClockPolicy.UNRESOLVED)
                        .toList(),
                ownership.unresolved.stream()
                        .filter(pair -> pair.clockPolicy != ClockPolicy.UNRESOLVED)
                        .toList());
    }

    public Resolution resolve() {
        Map<Anchor, Integer> indices = new HashMap<>();
        for (int i = 0; i < noteOrigins.size(); i++)
            if (noteOrigins.get(i).isPresent()) indices.put(noteOrigins.get(i).get(), i);
        List<Resolved> resolved = new ArrayList<>();
        List<Pair> unresolved = new ArrayList<>();
        for (Pair pair : pairs) {
            List<Integer> first = new ArrayList<>(), second = new ArrayList<>();
            for (Anchor anchor : pair.first)
                if (indices.containsKey(anchor)) first.add(indices.get(anchor));
            for (Anchor anchor : pair.second)
                if (indices.containsKey(anchor)) second.add(indices.get(anchor));
            if (first.size() != pair.first.size() || second.size() != pair.second.size())
                unresolved.add(pair);
            else resolved.add(new Resolved(pair, first, second));
        }
        return new Resolution(resolved, unresolved);
    }

    /** Assign the physical source page once, before joining or editing local recognition output. */
    public ScoreTremoloSource onPage(int pageIndex) {
        if (pageIndex < 0) throw new IllegalArgumentException("Invalid physical page");
        if (pairs.isEmpty()) return this;
        for (var origin : noteOrigins)
            if (origin.isPresent() && origin.get().pageIndex() != 0)
                throw new IllegalArgumentException(
                        "Only local recognition origins may be assigned a page");
        java.util.function.Function<Anchor, Anchor> map =
                a -> {
                    if (a.pageIndex() != 0)
                        throw new IllegalArgumentException("Nonlocal tremolo endpoint");
                    return new Anchor(pageIndex, a.noteIndex());
                };
        List<Pair> relocated = new ArrayList<>();
        for (Pair pair : pairs) {
            List<Evidence> proof = new ArrayList<>();
            for (Evidence e : pair.evidence) {
                if (e.pageIndex() != 0)
                    throw new IllegalArgumentException("Nonlocal tremolo evidence");
                proof.add(new Evidence(pageIndex, e.left, e.top, e.right, e.bottom));
            }
            relocated.add(
                    new Pair(
                            "page:" + pageIndex + ":" + pair.id,
                            pair.first.stream().map(map).toList(),
                            pair.second.stream().map(map).toList(),
                            pair.strokes,
                            proof,
                            pair.clockPolicy));
        }
        return new ScoreTremoloSource(
                relocated, noteOrigins.stream().map(o -> o.map(map)).toList());
    }
}
