// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.ToIntFunction;

/** Complete grace groups and retained clocks are part of a principal unison's identity. */
public final class ScoreUnisonOwnership {
    public record GraceAlias(int firstOwner, int secondOwner) {}

    private final List<ScoreNoteEvent> source;
    private final Map<Integer, ScoreNoteTiming.WrittenPlacement> written;
    private final Map<Integer, ScoreRhythmProjection.Placement> playback;
    private final Map<Integer, List<Integer>> graces = new HashMap<>();
    private final Set<Integer> uncertainMeasures = new HashSet<>();

    public ScoreUnisonOwnership(
            List<ScoreNoteEvent> source,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            Map<Integer, ScoreRhythmProjection.Placement> playback) {
        this.source = List.copyOf(source);
        this.written = ScorePlacementMap.validatedWritten(written, this.source);
        this.playback = ScorePlacementMap.validated(playback, this.source.size());
        for (int owner = 0; owner < this.source.size(); owner++) {
            var note = this.source.get(owner);
            var clock = this.written.get(owner);
            if ((note.articulations() & NoteOrnament.GRACE) == 0) continue;
            if (clock == null) uncertainMeasures.add(note.measureIndex());
            else
                graces.computeIfAbsent(clock.principalIndex(), unused -> new ArrayList<>())
                        .add(owner);
        }
        for (var group : graces.values())
            group.sort(
                    Comparator.comparing((Integer owner) -> this.written.get(owner).afterGrace())
                            .thenComparingInt(owner -> this.written.get(owner).ordinal()));
    }

    /** Empty optional means retain distinct principals. Empty list means an ordinary unison.
     * Caller still proves principal pitch, registration and written-duration compatibility. */
    public Optional<List<GraceAlias>> equivalent(
            int first,
            int second,
            Set<Integer> selected,
            ToIntFunction<ScoreNoteEvent> renderedPitch) {
        Objects.requireNonNull(selected);
        Objects.requireNonNull(renderedPitch);
        if (first < 0
                || second < 0
                || first >= source.size()
                || second >= source.size()
                || first == second
                || !selected.contains(first)
                || !selected.contains(second)) return Optional.empty();
        var a = source.get(first);
        var b = source.get(second);
        if (a.kind() != ScoreNoteEvent.Kind.PITCHED
                || b.kind() != ScoreNoteEvent.Kind.PITCHED
                || ((a.articulations() | b.articulations()) & NoteOrnament.GRACE) != 0
                || a.measureIndex() != b.measureIndex()) return Optional.empty();
        if (uncertainMeasures.contains(a.measureIndex())
                || uncertainMeasures.contains(b.measureIndex())) return Optional.empty();
        if (!sameKnownClock(written, first, second) || !sameKnownClock(playback, first, second))
            return Optional.empty();
        var firstGroup = graces.getOrDefault(first, List.of());
        var secondGroup = graces.getOrDefault(second, List.of());
        if (firstGroup.size() != secondGroup.size()) return Optional.empty();
        if (firstGroup.isEmpty()) return Optional.of(List.of());
        // A written resemblance alone cannot discard a distinct performed attack.
        if (!written.containsKey(first)
                || !written.containsKey(second)
                || !playback.containsKey(first)
                || !playback.containsKey(second)) return Optional.empty();
        var pairs = new ArrayList<GraceAlias>();
        for (int index = 0; index < firstGroup.size(); index++) {
            int x = firstGroup.get(index), y = secondGroup.get(index);
            if (!selected.contains(x)
                    || !selected.contains(y)
                    || !playback.containsKey(x)
                    || !playback.containsKey(y)) return Optional.empty();
            var xNote = source.get(x);
            var yNote = source.get(y);
            var p = written.get(x);
            var q = written.get(y);
            if (xNote.kind() != ScoreNoteEvent.Kind.PITCHED
                    || yNote.kind() != ScoreNoteEvent.Kind.PITCHED
                    || renderedPitch.applyAsInt(xNote) != renderedPitch.applyAsInt(yNote)
                    || xNote.writtenAccidental() != yNote.writtenAccidental()
                    || xNote.articulations() != yNote.articulations()
                    || xNote.tiedFromPrevious() != yNote.tiedFromPrevious()
                    || Double.compare(p.onsetBeats(), q.onsetBeats()) != 0
                    || Double.compare(p.durationBeats(), q.durationBeats()) != 0
                    || p.ordinal() != q.ordinal()
                    || p.afterGrace() != q.afterGrace()
                    || Double.compare(p.stealPercent(), q.stealPercent()) != 0
                    || !playback.get(x).equals(playback.get(y))) return Optional.empty();
            pairs.add(new GraceAlias(x, y));
        }
        return Optional.of(List.copyOf(pairs));
    }

    private static <T> boolean sameKnownClock(Map<Integer, T> clocks, int first, int second) {
        if (clocks.isEmpty()) return true;
        T a = clocks.get(first), b = clocks.get(second);
        return a != null && a.equals(b);
    }
}
