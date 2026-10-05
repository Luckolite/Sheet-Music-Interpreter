// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Transports resolved rhythm by written note identity without changing written notes. */
public final class ScorePlacementMap {
    private ScorePlacementMap() {}

    public static Map<Integer, ScoreRhythmProjection.Placement> validated(
            Map<Integer, ScoreRhythmProjection.Placement> placements, int noteCount) {
        if (placements == null || placements.isEmpty()) return Map.of();
        var result = new LinkedHashMap<Integer, ScoreRhythmProjection.Placement>();
        for (var entry : placements.entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey() < 0
                    || entry.getKey() >= noteCount
                    || entry.getValue() == null)
                throw new IllegalArgumentException("Source rhythm outside retained notes");
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    public static Map<Integer, ScoreNoteTiming.WrittenPlacement> validatedWritten(
            Map<Integer, ScoreNoteTiming.WrittenPlacement> placements, List<ScoreNoteEvent> notes) {
        if (placements == null || placements.isEmpty()) return Map.of();
        var result = new LinkedHashMap<Integer, ScoreNoteTiming.WrittenPlacement>();
        for (var entry : placements.entrySet()) {
            Integer index = entry.getKey();
            var p = entry.getValue();
            if (index == null
                    || index < 0
                    || index >= notes.size()
                    || p == null
                    || !Double.isFinite(p.onsetBeats())
                    || p.onsetBeats() < 0
                    || !Double.isFinite(p.durationBeats())
                    || p.durationBeats() <= 0
                    || !Double.isFinite(p.stealPercent())
                    || p.stealPercent() < 0
                    || p.stealPercent() > 100)
                throw new IllegalArgumentException("Invalid retained written rhythm");
            boolean grace = (notes.get(index).articulations() & NoteOrnament.GRACE) != 0;
            if (p.grace()
                    && (notes.get(index).kind() == ScoreNoteEvent.Kind.UNPITCHED
                            || p.principalIndex() >= 0
                                    && p.principalIndex() < notes.size()
                                    && notes.get(p.principalIndex()).kind()
                                            == ScoreNoteEvent.Kind.UNPITCHED))
                throw new IllegalArgumentException(
                        "Unpitched grace ownership requires an explicit realization");
            if (grace != p.grace()
                    || p.principalIndex() >= notes.size()
                    || p.grace()
                            && (p.ordinal() < 0
                                    || p.principalIndex() == index
                                    || (notes.get(p.principalIndex()).articulations()
                                                    & NoteOrnament.GRACE)
                                            != 0
                                    || notes.get(p.principalIndex()).measureIndex()
                                            != notes.get(index).measureIndex())
                    || !p.grace()
                            && (p.principalIndex() != -1
                                    || p.ordinal() != -1
                                    || p.afterGrace()
                                    || p.stealPercent() != 0))
                throw new IllegalArgumentException("Invalid retained grace ownership");
            result.put(index, p);
        }
        var groups = new HashMap<String, Set<Integer>>();
        for (var p : result.values())
            if (p.grace()) {
                var main = result.get(p.principalIndex());
                if (main == null || main.grace())
                    throw new IllegalArgumentException(
                            "A retained grace note lost its written principal");
                var ordinals =
                        groups.computeIfAbsent(
                                p.principalIndex() + ":" + p.afterGrace(),
                                ignored -> new HashSet<>());
                if (!ordinals.add(p.ordinal()))
                    throw new IllegalArgumentException("Duplicate retained grace ordinal");
            }
        for (var ordinals : groups.values())
            for (int ordinal = 0; ordinal < ordinals.size(); ordinal++)
                if (!ordinals.contains(ordinal))
                    throw new IllegalArgumentException("Incomplete retained grace ordinal map");
        return Map.copyOf(result);
    }

    public static Map<Integer, ScoreNoteTiming.WrittenPlacement> writtenByOwners(
            Map<Integer, ScoreNoteTiming.WrittenPlacement> placements,
            List<ScoreNoteEvent> source,
            List<Integer> owners) {
        var checked = validatedWritten(placements, source);
        var indices = new HashMap<Integer, Integer>();
        for (int i = 0; i < owners.size(); i++) {
            int owner = owners.get(i);
            if (owner < 0 || owner >= source.size() || indices.put(owner, i) != null)
                throw new IllegalArgumentException("Invalid selected written note owner");
        }
        var result = new LinkedHashMap<Integer, ScoreNoteTiming.WrittenPlacement>();
        for (int i = 0; i < owners.size(); i++) {
            var p = checked.get(owners.get(i));
            if (p == null) continue;
            int principal = -1;
            if (p.grace()) {
                Integer selectedPrincipal = indices.get(p.principalIndex());
                if (selectedPrincipal == null)
                    throw new IllegalArgumentException(
                            "A selected grace note lost its written principal");
                principal = selectedPrincipal;
            }
            result.put(
                    i,
                    new ScoreNoteTiming.WrittenPlacement(
                            p.onsetBeats(),
                            p.durationBeats(),
                            principal,
                            p.ordinal(),
                            p.afterGrace(),
                            p.stealPercent()));
        }
        return Map.copyOf(result);
    }

    public static Map<Integer, ScoreNoteTiming.WrittenPlacement> resolveWritten(
            List<ScoreNoteEvent> notes, float beatsPerMeasure, List<ScoreMeterChange> meters) {
        return resolveWritten(notes, new ScoreMeterMap(beatsPerMeasure, meters));
    }

    public static Map<Integer, ScoreNoteTiming.WrittenPlacement> resolveWritten(
            List<ScoreNoteEvent> notes, ScoreMeterMap meter) {
        var result = new LinkedHashMap<Integer, ScoreNoteTiming.WrittenPlacement>();
        var indices = new HashMap<Integer, List<Integer>>();
        for (int i = 0; i < notes.size(); i++)
            indices.computeIfAbsent(notes.get(i).measureIndex(), ignored -> new ArrayList<>())
                    .add(i);
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (var group : indices.entrySet()) {
                var measureNotes = group.getValue().stream().map(notes::get).toList();
                for (int i : group.getValue()) {
                    var note = notes.get(i);
                    var p =
                            ScoreNoteTiming.writtenPlacement(
                                            note,
                                            measureNotes,
                                            meter.beatsInMeasure(note.measureIndex()))
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "A note has no proved written placement"));
                    int principal = p.grace() ? group.getValue().get(p.principalIndex()) : -1;
                    result.put(
                            i,
                            new ScoreNoteTiming.WrittenPlacement(
                                    p.onsetBeats(),
                                    p.durationBeats(),
                                    principal,
                                    p.ordinal(),
                                    p.afterGrace(),
                                    p.stealPercent()));
                }
            }
        }
        return validatedWritten(result, notes);
    }

    public static Map<Integer, ScoreRhythmProjection.Placement> resolvePlayback(
            List<ScoreNoteEvent> notes, float beatsPerMeasure, List<ScoreMeterChange> meters) {
        return resolvePlayback(notes, new ScoreMeterMap(beatsPerMeasure, meters));
    }

    public static Map<Integer, ScoreRhythmProjection.Placement> resolvePlayback(
            List<ScoreNoteEvent> notes, ScoreMeterMap meter) {
        var result = new LinkedHashMap<Integer, ScoreRhythmProjection.Placement>();
        var indices = new HashMap<Integer, List<Integer>>();
        for (int i = 0; i < notes.size(); i++)
            indices.computeIfAbsent(notes.get(i).measureIndex(), ignored -> new ArrayList<>())
                    .add(i);
        for (var group : indices.values()) {
            var measureNotes = group.stream().map(notes::get).toList();
            var placed =
                    ScoreRhythmProjection.resolve(
                            measureNotes,
                            measureNotes,
                            meter.beatsInMeasure(measureNotes.get(0).measureIndex()),
                            List.of());
            for (var entry : placed.entrySet())
                result.put(group.get(entry.getKey()), entry.getValue());
        }
        return validated(result, notes.size());
    }

    public static List<Integer> editOwners(
            List<ScoreNoteEvent> source, List<ScoreNoteEvent> edited) {
        var owners = new ArrayList<Integer>();
        var used = new HashSet<Integer>();
        for (var note : edited) {
            var matches = new ArrayList<Integer>();
            for (int i = 0; i < source.size(); i++)
                if (!used.contains(i) && sameEditIdentity(source.get(i), note)) matches.add(i);
            if (matches.size() != 1)
                throw new IllegalArgumentException("Edited note needs its stable source identity");
            int owner = matches.get(0);
            owners.add(owner);
            used.add(owner);
        }
        return List.copyOf(owners);
    }

    /** The renderer preserves source order within each measure; validate every musical slot. */
    public static List<Integer> renderedOwners(
            List<ScoreNoteEvent> projected, List<ScoreNoteEvent> visible) {
        if (projected.size() != visible.size())
            throw new IllegalArgumentException("Saved visible note map is incomplete");
        var measures = new HashMap<Integer, ArrayDeque<Integer>>();
        for (int i = 0; i < projected.size(); i++)
            measures.computeIfAbsent(projected.get(i).measureIndex(), ignored -> new ArrayDeque<>())
                    .add(i);
        var result = new ArrayList<Integer>();
        for (var note : visible) {
            var slots = measures.get(note.measureIndex());
            if (slots == null || slots.isEmpty())
                throw new IllegalArgumentException("Saved visible measure map is incomplete");
            int owner = slots.removeFirst();
            var expected = projected.get(owner);
            int a =
                    expected.kind() == ScoreNoteEvent.Kind.UNPITCHED
                            ? expected.staffStep()
                            : expected.staffStep()
                                    + expected.clefBottomDiatonic()
                                    + expected.octaveShift() * 7;
            int b =
                    note.kind() == ScoreNoteEvent.Kind.UNPITCHED
                            ? note.staffStep()
                            : note.staffStep() + note.clefBottomDiatonic() + note.octaveShift() * 7;
            if (expected.kind() != note.kind()
                    || a != b
                    || !sameWrittenClock(expected, note)
                    || expected.writtenAccidental() != note.writtenAccidental()
                    || expected.tiedFromPrevious() != note.tiedFromPrevious()
                    || expected.articulations() != note.articulations()
                    || expected.followingRestBeats() != note.followingRestBeats()
                    || expected.leadingRestBeats() != note.leadingRestBeats())
                throw new IllegalArgumentException(
                        "Saved visible note does not match its retained source slot");
            result.add(owner);
        }
        return List.copyOf(result);
    }

    /** Exact selected records retain their source index; combined records need an explicit owner. */
    public static Map<Integer, ScoreRhythmProjection.Placement> select(
            List<ScoreNoteEvent> source,
            List<ScoreNoteEvent> selected,
            Map<Integer, ScoreRhythmProjection.Placement> placements) {
        var checked = validated(placements, source.size());
        if (checked.isEmpty()) return Map.of();
        var result = new LinkedHashMap<Integer, ScoreRhythmProjection.Placement>();
        for (int i = 0; i < selected.size(); i++) {
            ScoreNoteEvent note = selected.get(i);
            ScoreRhythmProjection.Placement found = null;
            boolean owned = false;
            int matches = 0;
            for (int j = 0; j < source.size(); j++)
                if (source.get(j).equals(note)) {
                    if (note.kind() == ScoreNoteEvent.Kind.UNPITCHED && ++matches > 1)
                        throw new IllegalArgumentException(
                                "Selected unpitched note needs its stable source index");
                    var candidate = checked.get(j);
                    if (candidate == null) continue;
                    if (owned
                            && (note.kind() == ScoreNoteEvent.Kind.UNPITCHED
                                    || !Objects.equals(found, candidate)))
                        throw new IllegalArgumentException(
                                "Selected note has ambiguous source rhythm");
                    found = candidate;
                    owned = true;
                }
            if (owned) result.put(i, found);
        }
        return Map.copyOf(result);
    }

    /** Caller-proved owners handle selection/reduction without coordinate guesses. */
    public static Map<Integer, ScoreRhythmProjection.Placement> byOwners(
            Map<Integer, ScoreRhythmProjection.Placement> placements,
            int sourceCount,
            List<Integer> owners) {
        var checked = validated(placements, sourceCount);
        var result = new LinkedHashMap<Integer, ScoreRhythmProjection.Placement>();
        for (int i = 0; i < owners.size(); i++) {
            int owner = owners.get(i);
            if (owner < 0 || owner >= sourceCount)
                throw new IllegalArgumentException("Invalid source note owner");
            var placement = checked.get(owner);
            if (placement != null) result.put(i, placement);
        }
        return Map.copyOf(result);
    }

    /** Pitch/width changes and deletion preserve the established clocks of surviving notes. */
    public static Map<Integer, ScoreRhythmProjection.Placement> edited(
            List<ScoreNoteEvent> source,
            List<ScoreNoteEvent> edited,
            Map<Integer, ScoreRhythmProjection.Placement> placements,
            List<Integer> sourceIds) {
        if (sourceIds.size() != edited.size())
            throw new IllegalArgumentException("Note identity map is incomplete");
        var checked = validated(placements, source.size());
        var result = new LinkedHashMap<Integer, ScoreRhythmProjection.Placement>();
        var changedMeasures = new HashSet<Integer>();
        var seen = new HashSet<Integer>();
        for (int i = 0; i < edited.size(); i++) {
            int owner = sourceIds.get(i);
            if (owner < 0 || owner >= source.size() || !seen.add(owner))
                throw new IllegalArgumentException("Invalid edited note identity");
            var before = source.get(owner);
            var after = edited.get(i);
            if (!sameWrittenClock(before, after)) {
                changedMeasures.add(before.measureIndex());
                changedMeasures.add(after.measureIndex());
            }
            var placement = checked.get(owner);
            if (placement != null) result.put(i, placement);
        }
        result.keySet().removeIf(i -> changedMeasures.contains(edited.get(i).measureIndex()));
        return Map.copyOf(result);
    }

    /** Compatibility path only accepts unambiguous surviving geometry/rhythm identities. */
    public static Map<Integer, ScoreRhythmProjection.Placement> edited(
            List<ScoreNoteEvent> source,
            List<ScoreNoteEvent> edited,
            Map<Integer, ScoreRhythmProjection.Placement> placements) {
        if (placements == null || placements.isEmpty()) return Map.of();
        return edited(source, edited, placements, editOwners(source, edited));
    }

    public record Clocks(
            Map<Integer, ScoreRhythmProjection.Placement> playback,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written) {
        public Clocks {
            playback = Map.copyOf(playback);
            written = Map.copyOf(written);
        }
    }

    /** Grace deletion recomputes stealing from explicit written principals, never spacing. */
    public static Clocks editedClocks(
            List<ScoreNoteEvent> source,
            List<ScoreNoteEvent> changed,
            Map<Integer, ScoreRhythmProjection.Placement> playback,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            List<Integer> owners) {
        var selected = new HashSet<>(owners);
        var graceMeasures = new HashSet<Integer>();
        for (int i = 0; i < source.size(); i++)
            if (!selected.contains(i) && (source.get(i).articulations() & NoteOrnament.GRACE) != 0)
                graceMeasures.add(source.get(i).measureIndex());
        var result = new LinkedHashMap<>(edited(source, changed, playback, owners));
        var print = new LinkedHashMap<>(writtenByOwners(written, source, owners));
        print.keySet().retainAll(result.keySet());
        if (!playback.isEmpty())
            for (int measure : graceMeasures) {
                var groups = new HashMap<String, List<Integer>>();
                for (int i = 0; i < changed.size(); i++)
                    if (changed.get(i).measureIndex() == measure) {
                        var p = print.get(i);
                        if (p == null)
                            throw new IllegalArgumentException(
                                    "Changed grace measure has no complete written clock");
                        if (p.grace())
                            groups.computeIfAbsent(
                                            p.principalIndex() + ":" + p.afterGrace(),
                                            ignored -> new ArrayList<>())
                                    .add(i);
                    }
                for (var group : groups.values()) {
                    group.sort(Comparator.comparingInt(i -> print.get(i).ordinal()));
                    var first = print.get(group.get(0));
                    var main = print.get(first.principalIndex());
                    if (main == null || main.grace())
                        throw new IllegalArgumentException(
                                "Changed grace group lost its principal");
                    double budget = Math.min(.25, main.durationBeats() * .25);
                    for (int ordinal = 0; ordinal < group.size(); ordinal++) {
                        int i = group.get(ordinal);
                        var p = print.get(i);
                        print.put(
                                i,
                                new ScoreNoteTiming.WrittenPlacement(
                                        p.onsetBeats(),
                                        p.durationBeats(),
                                        p.principalIndex(),
                                        ordinal,
                                        p.afterGrace(),
                                        100 * budget / group.size() / main.durationBeats()));
                    }
                }
                for (int i = 0; i < changed.size(); i++)
                    if (changed.get(i).measureIndex() == measure) {
                        var p = print.get(i);
                        var old = playback.get(owners.get(i));
                        if (old == null)
                            throw new IllegalArgumentException(
                                    "Changed grace measure has no playback clock");
                        double onset = p.onsetBeats(), duration = p.durationBeats();
                        if (p.grace()) {
                            var main = print.get(p.principalIndex());
                            var group = groups.get(p.principalIndex() + ":" + p.afterGrace());
                            double budget = Math.min(.25, main.durationBeats() * .25);
                            onset =
                                    main.onsetBeats()
                                            + (p.afterGrace() ? main.durationBeats() - budget : 0)
                                            + p.ordinal() * budget / group.size();
                            duration = budget / group.size();
                        } else {
                            double before =
                                    groups.containsKey(i + ":false")
                                            ? Math.min(.25, p.durationBeats() * .25)
                                            : 0;
                            double after =
                                    groups.containsKey(i + ":true")
                                            ? Math.min(.25, p.durationBeats() * .25)
                                            : 0;
                            onset += before;
                            duration -= before + after;
                        }
                        result.put(
                                i,
                                new ScoreRhythmProjection.Placement(
                                        onset, duration, old.independentDuration()));
                    }
            }
        return new Clocks(validated(result, changed.size()), validatedWritten(print, changed));
    }

    private static boolean sameEditIdentity(ScoreNoteEvent a, ScoreNoteEvent b) {
        return a.kind() == b.kind()
                && a.measureIndex() == b.measureIndex()
                && a.positionInMeasure() == b.positionInMeasure()
                && a.staffIndex() == b.staffIndex()
                && a.staffCount() == b.staffCount()
                && a.pageY() == b.pageY()
                && (a.kind() == ScoreNoteEvent.Kind.UNPITCHED
                        ? a.staffStep() == b.staffStep()
                        : Math.floorMod(a.staffStep(), 7) == Math.floorMod(b.staffStep(), 7))
                && a.writtenAccidental() == b.writtenAccidental()
                && sameWrittenClock(a, b);
    }

    private static boolean sameWrittenClock(ScoreNoteEvent a, ScoreNoteEvent b) {
        return a.kind() == b.kind()
                && a.measureIndex() == b.measureIndex()
                && a.positionInMeasure() == b.positionInMeasure()
                && a.augmentationDots() == b.augmentationDots()
                && a.beamCount() == b.beamCount()
                && a.unbeamedDurationBeats() == b.unbeamedDurationBeats()
                && a.tupletDivisor() == b.tupletDivisor()
                && a.tupletNormalNotes() == b.tupletNormalNotes()
                && (a.articulations() & NoteOrnament.GRACE)
                        == (b.articulations() & NoteOrnament.GRACE);
    }
}
