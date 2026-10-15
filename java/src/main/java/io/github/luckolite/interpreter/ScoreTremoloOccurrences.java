// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Playback occurrences reference retained source pairs; printed identities are never rewritten. */
public final class ScoreTremoloOccurrences {
    private ScoreTremoloOccurrences() {}

    /** One projected note slot's owner. Occurrence is the caller's proved contiguous traversal run. */
    public record Owner(int sourceIndex, int occurrence) {
        public Owner {
            if (sourceIndex < 0 || occurrence < 0)
                throw new IllegalArgumentException("Invalid traversal note owner");
        }
    }

    public record Pair(
            ScoreTremoloSource.Pair source,
            int occurrence,
            List<Integer> first,
            List<Integer> second) {
        public Pair {
            first = List.copyOf(first);
            second = List.copyOf(second);
        }
    }

    public record Unresolved(ScoreTremoloSource.Pair source, int occurrence) {}

    public record Result(
            List<Pair> pairs,
            List<Unresolved> unresolved,
            List<ScoreTremoloSource.Pair> missingSource) {
        public Result {
            pairs = List.copyOf(pairs);
            unresolved = List.copyOf(unresolved);
            missingSource = List.copyOf(missingSource);
        }
    }

    public static Result project(ScoreTremoloSource source, List<Owner> projectedOwners) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(projectedOwners);
        Map<Integer, Map<Integer, Integer>> occurrences = new LinkedHashMap<>();
        for (int projected = 0; projected < projectedOwners.size(); projected++) {
            Owner owner = Objects.requireNonNull(projectedOwners.get(projected));
            if (owner.sourceIndex >= source.noteOrigins().size())
                throw new IllegalArgumentException("Traversal source outside notes");
            Map<Integer, Integer> map =
                    occurrences.computeIfAbsent(owner.occurrence, k -> new HashMap<>());
            if (map.putIfAbsent(owner.sourceIndex, projected) != null)
                throw new IllegalArgumentException(
                        "Duplicate source note within traversal occurrence");
        }
        var resolution = source.resolve();
        List<Pair> pairs = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        for (var occurrence : occurrences.entrySet())
            for (var pair : resolution.resolved()) {
                List<Integer> first = new ArrayList<>(), second = new ArrayList<>();
                for (int index : pair.first())
                    if (occurrence.getValue().containsKey(index))
                        first.add(occurrence.getValue().get(index));
                for (int index : pair.second())
                    if (occurrence.getValue().containsKey(index))
                        second.add(occurrence.getValue().get(index));
                if (first.isEmpty() && second.isEmpty()) continue;
                if (first.size() != pair.first().size() || second.size() != pair.second().size())
                    unresolved.add(new Unresolved(pair.source(), occurrence.getKey()));
                else pairs.add(new Pair(pair.source(), occurrence.getKey(), first, second));
            }
        return new Result(pairs, unresolved, resolution.unresolved());
    }
}
