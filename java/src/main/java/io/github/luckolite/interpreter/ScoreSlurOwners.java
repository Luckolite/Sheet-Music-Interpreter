// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Remaps explicit source indices without inferring phrase ownership from pitch or geometry. */
public final class ScoreSlurOwners {
    private ScoreSlurOwners() {}

    /** The original span remains available for review and undo when an endpoint is deleted. */
    public record Unresolved(ScoreSlurSpan source, boolean missingStart, boolean missingEnd) {
        public Unresolved {
            Objects.requireNonNull(source);
            if (!missingStart && !missingEnd)
                throw new IllegalArgumentException("Unresolved slur needs a missing endpoint");
        }
    }

    public record Edited(List<ScoreSlurSpan> resolved, List<Unresolved> unresolved) {
        public Edited {
            resolved = List.copyOf(resolved);
            unresolved = List.copyOf(unresolved);
        }
    }

    public static List<ScoreSlurSpan> validated(List<ScoreSlurSpan> spans, int sourceCount) {
        if (sourceCount < 0) throw new IllegalArgumentException("Invalid source note count");
        var result = List.copyOf(spans);
        var identities = new HashSet<String>();
        for (var span : result) {
            if (!identities.add(span.id()))
                throw new IllegalArgumentException("Duplicate slur span identity");
            if (span.startOwner() >= sourceCount || span.endOwner() >= sourceCount)
                throw new IllegalArgumentException("Slur endpoint is outside the retained source");
        }
        return result;
    }

    /** Each new note supplies its old index, or -1 for an explicitly inserted note. */
    public static Edited edited(
            List<ScoreSlurSpan> spans, int sourceCount, List<Integer> sourceOwners) {
        var source = validated(spans, sourceCount);
        var newIndices = new HashMap<Integer, Integer>();
        for (int i = 0; i < sourceOwners.size(); i++) {
            Integer owner = sourceOwners.get(i);
            if (owner == null || owner < -1 || owner >= sourceCount)
                throw new IllegalArgumentException("Invalid edited source note owner");
            if (owner >= 0 && newIndices.putIfAbsent(owner, i) != null)
                throw new IllegalArgumentException("Ambiguous edited source note owner");
        }
        var resolved = new ArrayList<ScoreSlurSpan>();
        var unresolved = new ArrayList<Unresolved>();
        for (var span : source) {
            Integer start = newIndices.get(span.startOwner());
            Integer end = newIndices.get(span.endOwner());
            if (start == null || end == null) {
                unresolved.add(new Unresolved(span, start == null, end == null));
            } else {
                resolved.add(
                        new ScoreSlurSpan(
                                span.id(),
                                start,
                                end,
                                span.placement(),
                                span.lineType(),
                                span.evidence()));
            }
        }
        return new Edited(resolved, unresolved);
    }
}
