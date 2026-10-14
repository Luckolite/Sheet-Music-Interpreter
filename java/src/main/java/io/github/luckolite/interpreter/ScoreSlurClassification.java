// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Classifies proved pixel endpoints only after actual assembled musical owners are available. */
public final class ScoreSlurClassification {
    public record Note(int owner, long attack, int graceOrder, int soundingPitch) {
        public Note {
            if (owner < 0 || attack < 0 || soundingPitch < -1 || soundingPitch > 127)
                throw new IllegalArgumentException("Invalid assembled arc owner");
        }
    }

    public record Tie(int startOwner, int endOwner) {
        public Tie {
            if (startOwner < 0 || endOwner < 0 || startOwner == endOwner)
                throw new IllegalArgumentException("Invalid assembled tie ownership");
        }
    }

    private ScoreSlurClassification() {}

    /** Existing confirmed intent survives edits; unavailable or ambiguous candidates stay retained. */
    public static ScoreSlurSource resolve(ScoreSlurSource source, List<Note> notes, Set<Tie> ties) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(notes);
        Objects.requireNonNull(ties);
        var byOwner = new HashMap<Integer, Note>();
        for (var note : notes)
            if (note.owner() >= source.noteOrigins().size()
                    || byOwner.putIfAbsent(note.owner(), note) != null)
                throw new IllegalArgumentException("Duplicate or stale assembled arc ownership");
        for (var tie : ties)
            if (!byOwner.containsKey(tie.startOwner()) || !byOwner.containsKey(tie.endOwner()))
                throw new IllegalArgumentException("A retained tie has no assembled note owner");
        if (source.candidates().isEmpty()) return source;
        var origins = new HashMap<ScoreSlurPages.Anchor, Integer>();
        for (int i = 0; i < source.noteOrigins().size(); i++) {
            var origin = source.noteOrigins().get(i);
            if (origin.isPresent()) origins.put(origin.get(), i);
        }
        var confirmed = new ArrayList<>(source.spans());
        var pending = new ArrayList<ScoreSlurPages.Span>();
        for (var arc : source.candidates()) {
            var start = origins.get(arc.start());
            var end = origins.get(arc.end());
            Note a = start == null ? null : byOwner.get(start),
                    b = end == null ? null : byOwner.get(end);
            int order = a == null || b == null ? 0 : Long.compare(a.attack(), b.attack());
            if (order == 0 && a != null && b != null)
                order = Integer.compare(a.graceOrder(), b.graceOrder());
            if (a == null
                    || b == null
                    || order >= 0
                    || a.soundingPitch() < 0
                    || b.soundingPitch() < 0) {
                pending.add(arc);
                continue;
            }
            if (ties.contains(new Tie(start, end))) {
                if (a.soundingPitch() != b.soundingPitch()) pending.add(arc);
                continue; // An exact, equal-pitch sounding tie is already represented.
            }
            if (a.soundingPitch() == b.soundingPitch()) pending.add(arc);
            else confirmed.add(arc);
        }
        return new ScoreSlurSource(confirmed, source.noteOrigins(), pending);
    }
}
