// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A physically bridged uniform triplet lane can run beneath an opposing printed voice. */
final class ParallelTripletClock {
    private static final float SAME = .018f;

    private ParallelTripletClock() {}

    static Clock find(ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        if (target == null || bar == null || !Double.isFinite(beats) || beats < 2 || beats > 4)
            return null;
        Clock bridged = findCrossStaff(target, bar, beats);
        if (bridged != null) return bridged;
        Clock continued = findCompleteCrossStaffPhrase(target, bar, beats);
        if (continued != null) return continued;
        Clock restContinuation = findCompleteRestContinuation(target, bar, beats);
        if (restContinuation != null) return restContinuation;
        Clock restLane = findPrintedRestLane(target, bar, beats);
        if (restLane != null) return restLane;
        Clock printed = findPrintedChangingStem(target, bar, beats);
        if (printed != null) return printed;
        Clock same = findSameStaff(target, bar, beats);
        if (same != null) return same;
        var prefix = PrintedTripletVoicePrefix.find(target, bar, beats);
        if (prefix == null) return null;
        var attacks = new ArrayList<Attack>();
        for (int i = 0; i < prefix.groups().size(); i++)
            for (var note : prefix.groups().get(i))
                attacks.add(new Attack(note, prefix.onsets().get(i)));
        // The printed prefix proves this voice's duration, but does not prove
        // where the countervoice begins. Preserve its independently resolved onset.
        for (var quarter : prefix.quarters()) attacks.add(new Attack(quarter, Double.NaN));
        return new Clock(
                prefix.members(), prefix.groups(), prefix.unit(), beats, List.copyOf(attacks));
    }

    private static Clock findCrossStaff(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        if (target == null
                || target.staffCount() != 2
                || !Double.isFinite(beats)
                || beats < 2
                || beats > 4) return null;
        int[] directions = new int[2];
        int bridges = 0;
        for (var n : bar)
            if (n.crossStaffBeam()) {
                if (n.staffIndex() < 0 || n.staffIndex() > 1 || n.stemDirection() == 0) return null;
                int staff = n.staffIndex();
                if (directions[staff] != 0 && directions[staff] != n.stemDirection()) return null;
                directions[staff] = n.stemDirection();
                bridges++;
            }
        if (bridges < 2 || directions[0] == 0 || directions[1] == 0) return null;
        for (int beams : new int[] {1, 2}) {
            double unit = beams == 1 ? .5 : .25;
            var members = new ArrayList<ScoreNoteEvent>();
            boolean parallel = false, rests = false;
            for (var n : bar) {
                if (n.staffIndex() < 0 || n.staffIndex() > 1) return null;
                if (n.stemDirection() != 0 && n.stemDirection() != directions[n.staffIndex()])
                    parallel = true;
                if (n.beamCount() != beams
                        || n.augmentationDots() != 0
                        || n.unbeamedDurationBeats() != 0
                        || (n.articulations() & NoteOrnament.GRACE) != 0
                        || n.stemDirection() != directions[n.staffIndex()]) continue;
                if (n.tupletDivisor() != 1
                        && !(n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2)) continue;
                members.add(n);
                rests |= n.leadingRestBeats() > 0 || n.followingRestBeats() > 0;
            }
            boolean ambiguous = false;
            for (var n : bar)
                if (n.staffIndex() >= 0
                        && n.staffIndex() < 2
                        && n.stemDirection() == directions[n.staffIndex()]
                        && !members.contains(n)
                        && !ScoreNoteTiming.hasIndependentSustain(n)
                        && (n.articulations() & NoteOrnament.GRACE) == 0) ambiguous = true;
            // A touching stem is not permission to steal a quarter-pulse voice.
            // Its distinct written values need their own rhythm/beam evidence.
            if (ambiguous || !parallel || members.size() < 6) continue;
            members.sort(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
            var groups = new ArrayList<List<ScoreNoteEvent>>();
            for (var n : members) {
                if (groups.isEmpty()
                        || n.positionInMeasure()
                                        - groups.get(groups.size() - 1).get(0).positionInMeasure()
                                > SAME) groups.add(new ArrayList<>());
                groups.get(groups.size() - 1).add(n);
            }
            if (groups.size() % 3 != 0
                    || Math.abs(groups.size() * unit * 2 / 3 - beats) > .001
                    || groups.get(0).get(0).positionInMeasure() > .18f
                    || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
            float smallest = Float.MAX_VALUE, largest = 0;
            for (int i = 1; i < groups.size(); i++) {
                float distance =
                        groups.get(i).get(0).positionInMeasure()
                                - groups.get(i - 1).get(0).positionInMeasure();
                smallest = Math.min(smallest, distance);
                largest = Math.max(largest, distance);
            }
            if (smallest <= 0 || largest > smallest * 2.4f) continue;
            // Rest hints attach to staves; the complete lane and opposing shafts prove
            // those rests cannot interrupt this voice. Keep the written metadata intact.
            return new Clock(List.copyOf(members), groups, unit * 2 / 3, beats);
        }
        return null;
    }

    /** Isolate a complete beamed voice only when opposite shafts prove a second full voice. */
    private static Clock findSameStaff(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        int staff = target.staffIndex();
        if (staff < 0 || staff >= target.staffCount()) return null;
        for (int direction : new int[] {-1, 1}) {
            for (int beams : new int[] {1, 2}) {
                var members = new ArrayList<ScoreNoteEvent>();
                var opposition = new ArrayList<ScoreNoteEvent>();
                boolean ambiguous = false;
                for (var n : bar) {
                    if (n.staffIndex() != staff) continue;
                    if (n.stemDirection() == 0
                            || n.crossStaffBeam()
                            || (n.articulations() & NoteOrnament.GRACE) != 0
                            || n.leadingRestBeats() > 0
                            || n.followingRestBeats() > 0) {
                        ambiguous = true;
                        break;
                    }
                    if (n.stemDirection() != direction) {
                        opposition.add(n);
                        continue;
                    }
                    if (n.beamCount() != beams
                            || n.augmentationDots() != 0
                            || n.unbeamedDurationBeats() != 0
                            || n.tiedFromPrevious()
                            || (n.tupletDivisor() != 1
                                    && !(n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2))) {
                        ambiguous = true;
                        break;
                    }
                    members.add(n);
                }
                if (ambiguous || opposition.isEmpty() || members.size() < 6) continue;
                var groups = columns(members);
                double unit = (beams == 1 ? .5 : .25) * 2 / 3;
                if (groups.size() % 3 != 0
                        || Math.abs(groups.size() * unit - beats) > .001
                        || groups.get(0).get(0).positionInMeasure() > .18f
                        || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
                float smallest = Float.MAX_VALUE, largest = 0;
                for (int i = 1; i < groups.size(); i++) {
                    float distance =
                            groups.get(i).get(0).positionInMeasure()
                                    - groups.get(i - 1).get(0).positionInMeasure();
                    smallest = Math.min(smallest, distance);
                    largest = Math.max(largest, distance);
                }
                if (smallest <= 0 || largest > smallest * 2.4f) continue;
                Clock clock = new Clock(List.copyOf(members), groups, unit, beats);
                double cursor = 0;
                boolean complete = true;
                for (var column : columns(opposition)) {
                    var first = column.get(0);
                    double duration = ScoreNoteTiming.writtenDurationBeats(first);
                    for (var n : column) {
                        double onset = clock.onset(n, bar);
                        if (!Double.isFinite(onset)
                                || Math.abs(onset - cursor) > .001
                                || Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - duration)
                                        > .001) complete = false;
                    }
                    cursor += duration;
                }
                if (complete && Math.abs(cursor - beats) < .001) return clock;
            }
        }
        return null;
    }

    /** Independent quarter attacks can own real rest slots of a complete printed triplet voice, including quarters with the same stem direction. */
    private static Clock findPrintedRestLane(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        for (int direction : new int[] {-1, 1})
            for (int beams : new int[] {1, 2}) {
                var members = new ArrayList<ScoreNoteEvent>();
                var opposition = new ArrayList<ScoreNoteEvent>();
                boolean ambiguous = false;
                for (var n : bar) {
                    if (n.staffIndex() != target.staffIndex()) continue;
                    if (n.stemDirection() == 0
                            || n.crossStaffBeam()
                            || (n.articulations() & NoteOrnament.GRACE) != 0) {
                        ambiguous = true;
                        break;
                    }
                    if (n.stemDirection() != direction
                            || n.beamCount() == 0
                                    && n.augmentationDots() == 0
                                    && n.tupletDivisor() == 1
                                    && Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - 1)
                                            < .001) {
                        opposition.add(n);
                        continue;
                    }
                    if (n.beamCount() != beams
                            || n.augmentationDots() != 0
                            || n.unbeamedDurationBeats() != 0
                            || n.tupletDivisor() != 3
                            || n.tupletNormalNotes() != 2
                            || n.leadingRestBeats() > 0) {
                        ambiguous = true;
                        break;
                    }
                    members.add(n);
                }
                if (ambiguous || opposition.isEmpty()) continue;
                var groups = columns(members);
                if (groups.size() < 6
                        || groups.get(0).get(0).positionInMeasure() > .18f
                        || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
                double unit = (beams == 1 ? .5 : .25) * 2 / 3, cursor = 0;
                var attacks = new ArrayList<Attack>();
                var rests = new ArrayList<RestGap>();
                float smallest = Float.MAX_VALUE, largest = 0;
                for (int i = 0; i < groups.size(); i++) {
                    var column = groups.get(i);
                    double after = column.get(0).followingRestBeats();
                    if (after < 0 || after > 0 && Math.abs(after - unit) > .001) ambiguous = true;
                    for (var n : column) {
                        if (Math.abs(n.followingRestBeats() - after) > .001) ambiguous = true;
                        attacks.add(new Attack(n, cursor));
                    }
                    cursor += unit;
                    if (after > 0) {
                        if (i + 1 == groups.size()) ambiguous = true;
                        else
                            rests.add(
                                    new RestGap(
                                            column.get(0).positionInMeasure(),
                                            groups.get(i + 1).get(0).positionInMeasure(),
                                            cursor));
                        cursor += after;
                    }
                    if (i > 0) {
                        float gap =
                                column.get(0).positionInMeasure()
                                        - groups.get(i - 1).get(0).positionInMeasure();
                        smallest = Math.min(smallest, gap);
                        largest = Math.max(largest, gap);
                    }
                }
                if (ambiguous
                        || rests.isEmpty()
                        || (groups.size() + rests.size()) % 3 != 0
                        || Math.abs(cursor - beats) > .001
                        || smallest <= SAME
                        || largest > smallest * 3) continue;
                var counter = columns(opposition);
                if (counter.size() != rests.size()) continue;
                cursor = rests.get(0).onset;
                for (int i = 0; i < counter.size(); i++) {
                    var column = counter.get(i);
                    var rest = rests.get(i);
                    if (Math.abs(cursor - rest.onset) > .001) ambiguous = true;
                    for (var n : column) {
                        if (n.positionInMeasure() <= rest.left + SAME
                                || n.positionInMeasure() >= rest.right - SAME
                                || n.beamCount() != 0
                                || n.augmentationDots() != 0
                                || n.tupletDivisor() != 1
                                || Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - 1) > .001
                                || n.followingRestBeats() > 0
                                || n.leadingRestBeats() > 0
                                        && Math.abs(n.leadingRestBeats() - cursor) > .001)
                            ambiguous = true;
                        attacks.add(new Attack(n, cursor));
                    }
                    cursor += 1;
                }
                if (!ambiguous && Math.abs(cursor - beats) < .001)
                    return new Clock(
                            List.copyOf(members), groups, unit, beats, List.copyOf(attacks));
            }
        return null;
    }

    private record RestGap(float left, float right, double onset) {}

    /** Explicit triplets may reverse stems at beam-group boundaries beneath a held voice. */
    private static Clock findPrintedChangingStem(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        int staff = target.staffIndex();
        for (int beams : new int[] {1, 2}) {
            var members = new ArrayList<ScoreNoteEvent>();
            var melody = new ArrayList<ScoreNoteEvent>();
            boolean ambiguous = false;
            for (var n : bar) {
                if (n.staffIndex() != staff) continue;
                if (n.crossStaffBeam()
                        || (n.articulations() & NoteOrnament.GRACE) != 0
                        || n.leadingRestBeats() > 0
                        || n.followingRestBeats() > 0) {
                    ambiguous = true;
                    break;
                }
                if (n.beamCount() == beams
                        && n.augmentationDots() == 0
                        && n.unbeamedDurationBeats() == 0
                        && n.tupletDivisor() == 3
                        && n.tupletNormalNotes() == 2) members.add(n);
                else melody.add(n);
            }
            if (ambiguous || melody.isEmpty()) continue;
            var groups = columns(members);
            double unit = (beams == 1 ? .5 : .25) * 2 / 3;
            if (groups.size() < 6
                    || groups.size() % 3 != 0
                    || Math.abs(groups.size() * unit - beats) > .001
                    || groups.get(0).get(0).positionInMeasure() > .18f
                    || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
            int previous = 0, directions = 0;
            for (int i = 0; i < groups.size(); i++) {
                int direction = 0;
                for (var n : groups.get(i)) {
                    if (n.stemDirection() == 0) continue;
                    if (direction != 0 && direction != n.stemDirection()) ambiguous = true;
                    direction = n.stemDirection();
                }
                if (direction == 0 || (i % 3 != 0 && direction != previous)) ambiguous = true;
                directions |= direction < 0 ? 1 : 2;
                previous = direction;
            }
            if (ambiguous || directions != 3) continue;
            var melodyGroups = columns(melody);
            var clock = new Clock(List.copyOf(members), groups, unit, beats);
            double cursor = clock.column(melodyGroups.get(0).get(0).positionInMeasure());
            if (!Double.isFinite(cursor)) continue;
            var attacks = new ArrayList<Attack>();
            int opposing = 0;
            for (var column : melodyGroups) {
                var first = column.get(0);
                double duration = ScoreNoteTiming.writtenDurationBeats(first);
                if (!Double.isFinite(duration)
                        || duration <= 0
                        || !clock.positionFits(first.positionInMeasure(), cursor)) ambiguous = true;
                int nearest =
                        Math.min(groups.size() - 1, Math.max(0, (int) Math.round(cursor / unit)));
                int direction =
                        groups.get(nearest).stream()
                                .mapToInt(ScoreNoteEvent::stemDirection)
                                .filter(x -> x != 0)
                                .findFirst()
                                .orElse(0);
                for (var n : column) {
                    if (n.stemDirection() == 0
                            || Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - duration) > .001)
                        ambiguous = true;
                    if (n.stemDirection() == -direction) opposing++;
                    attacks.add(new Attack(n, cursor));
                }
                cursor += duration;
            }
            if (!ambiguous && opposing >= 2 && Math.abs(cursor - beats) < .001)
                return new Clock(List.copyOf(members), groups, unit, beats, List.copyOf(attacks));
        }
        return null;
    }

    /** A complete physical cross-staff phrase can change shaft directions between triplet groups. */
    private static Clock findCompleteCrossStaffPhrase(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        if (target.staffCount() != 2) return null;
        for (int beams : new int[] {1, 2}) {
            double unit = (beams == 1 ? .5 : .25) * 2 / 3;
            var members = new ArrayList<ScoreNoteEvent>();
            var other = new ArrayList<ScoreNoteEvent>();
            boolean invalid = false;
            for (var n : bar) {
                if (n == null
                        || n.staffCount() != 2
                        || n.staffIndex() < 0
                        || n.staffIndex() > 1
                        || !Float.isFinite(n.positionInMeasure())
                        || n.positionInMeasure() < 0
                        || n.positionInMeasure() > 1
                        || n.leadingRestBeats() != 0
                        || n.followingRestBeats() != 0
                        || (n.articulations() & NoteOrnament.GRACE) != 0) {
                    invalid = true;
                    break;
                }
                if (n.beamCount() == beams
                        && n.augmentationDots() == 0
                        && n.unbeamedDurationBeats() == 0
                        && (n.tupletDivisor() == 1
                                || n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2))
                    members.add(n);
                else other.add(n);
            }
            if (invalid || other.isEmpty()) continue;
            var groups = columns(members);
            if (groups.size() < 6
                    || groups.size() % 3 != 0
                    || Math.abs(groups.size() * unit - beats) > .001
                    || groups.get(0).get(0).positionInMeasure() > .18f
                    || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
            float small = Float.MAX_VALUE, large = 0;
            for (int i = 1; i < groups.size(); i++) {
                float gap =
                        groups.get(i).get(0).positionInMeasure()
                                - groups.get(i - 1).get(0).positionInMeasure();
                small = Math.min(small, gap);
                large = Math.max(large, gap);
            }
            if (small <= 0 || large > small * 2.4f) continue;
            int bridges = 0;
            for (int i = 1; i < groups.size(); i++) {
                var a = groups.get(i - 1).get(0);
                var b = groups.get(i).get(0);
                if (a.staffIndex() == b.staffIndex()) continue;
                if (a.crossStaffBeam()
                        && b.crossStaffBeam()
                        && a.stemDirection() != 0
                        && b.stemDirection() != 0
                        && (i - 1) / 3 == i / 3) bridges++;
                else invalid = true;
            }
            if (bridges == 0 || invalid) continue;
            for (int begin = 0; begin < groups.size(); begin += 3) {
                int[] directions = new int[2];
                boolean known = false;
                for (int i = begin; i < begin + 3; i++) {
                    var column = groups.get(i);
                    int chordDirection = 0;
                    for (var n : column) {
                        int direction = n.stemDirection();
                        if (column.size() > 1 && direction == 0
                                || chordDirection != 0 && direction != chordDirection)
                            invalid = true;
                        if (direction != 0) chordDirection = direction;
                        if (direction == 0) continue;
                        known = true;
                        int staff = n.staffIndex();
                        if (directions[staff] != 0 && directions[staff] != direction)
                            invalid = true;
                        directions[staff] = direction;
                    }
                }
                if (!known) {
                    // Only the terminal trio can borrow a clock anchor from an independently
                    // read quarter in its opening column, after a physically proved bridge.
                    var first = groups.get(begin).get(0);
                    boolean closing =
                            begin + 3 == groups.size()
                                    && other.stream()
                                            .anyMatch(
                                                    n ->
                                                            n.staffIndex() == first.staffIndex()
                                                                    && n.beamCount() == 0
                                                                    && n.stemDirection() != 0
                                                                    && Math.abs(
                                                                                    n
                                                                                                    .positionInMeasure()
                                                                                            - first
                                                                                                    .positionInMeasure())
                                                                            <= SAME
                                                                    && Math.abs(
                                                                                    ScoreNoteTiming
                                                                                                    .writtenDurationBeats(
                                                                                                            n)
                                                                                            - 3
                                                                                                    * unit)
                                                                            < .001);
                    if (!closing) invalid = true;
                }
            }
            if (invalid) continue;
            var clock = new Clock(List.copyOf(members), groups, unit, beats);
            var attacks = new ArrayList<Attack>();
            var unanchored = new ArrayList<ScoreNoteEvent>();
            for (var n : other) {
                double duration = ScoreNoteTiming.writtenDurationBeats(n);
                double onset = clock.column(n.positionInMeasure());
                if (!Double.isFinite(duration) || duration <= 0) {
                    invalid = true;
                    break;
                }
                if (!Double.isFinite(onset)) {
                    unanchored.add(n);
                    continue;
                }
                if (onset + duration > beats + .001) {
                    invalid = true;
                    break;
                }
                attacks.add(new Attack(n, onset));
            }
            for (var n : unanchored) {
                double duration = ScoreNoteTiming.writtenDurationBeats(n);
                double onset = Double.NaN;
                if (n.beamCount() <= 0
                        || n.positionInMeasure()
                                < groups.get(groups.size() - 1).get(0).positionInMeasure() - SAME) {
                    invalid = true;
                    break;
                }
                for (var anchor : attacks) {
                    var prior = anchor.note();
                    if (prior.staffIndex() != n.staffIndex()
                            || prior.beamCount() <= 0
                            || prior.positionInMeasure() >= n.positionInMeasure()
                            || prior.stemDirection() != 0
                                    && n.stemDirection() != 0
                                    && prior.stemDirection() != n.stemDirection()) continue;
                    double next = anchor.onset() + ScoreNoteTiming.writtenDurationBeats(prior);
                    if (Math.abs(next + duration - beats) >= .001) continue;
                    if (Double.isFinite(onset) && Math.abs(onset - next) > .001) invalid = true;
                    onset = next;
                }
                if (!Double.isFinite(onset)) {
                    invalid = true;
                    break;
                }
                attacks.add(new Attack(n, onset));
            }
            if (!invalid)
                return new Clock(List.copyOf(members), groups, unit, beats, List.copyOf(attacks));
        }
        return null;
    }

    /** A complete, evenly spaced lane can continue triplets through a printed rest slot. */
    private static Clock findCompleteRestContinuation(
            ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        if (target.staffCount() != 2) return null;
        for (int beams : new int[] {1, 2}) {
            double writtenUnit = beams == 1 ? .5 : .25;
            double unit = writtenUnit * 2 / 3;
            var members = new ArrayList<ScoreNoteEvent>();
            var other = new ArrayList<ScoreNoteEvent>();
            boolean invalid = false;
            for (var n : bar) {
                if (n == null
                        || n.staffCount() != 2
                        || n.staffIndex() < 0
                        || n.staffIndex() > 1
                        || !Float.isFinite(n.positionInMeasure())
                        || n.positionInMeasure() < 0
                        || n.positionInMeasure() > 1
                        || n.crossStaffBeam()
                        || (n.articulations() & NoteOrnament.GRACE) != 0) {
                    invalid = true;
                    break;
                }
                if (n.staffIndex() == target.staffIndex()
                        && n.beamCount() == beams
                        && n.augmentationDots() == 0
                        && n.unbeamedDurationBeats() == 0
                        && n.stemDirection() != 0
                        && (n.tupletDivisor() == 1
                                || n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2))
                    members.add(n);
                else other.add(n);
            }
            if (invalid || other.isEmpty()) continue;
            var groups = columns(members);
            if (groups.size() < 5
                    || groups.get(0).get(0).positionInMeasure() > .25f
                    || groups.get(groups.size() - 1).get(0).positionInMeasure() < .8f) continue;
            var slotIndices = new ArrayList<Integer>();
            int cursor = 0, restSlots = 0;
            var directions = new HashMap<Integer, Integer>();
            for (int i = 0; i < groups.size(); i++) {
                var column = groups.get(i);
                double lead = column.get(0).leadingRestBeats();
                double follow = column.get(0).followingRestBeats();
                if (lead < 0
                        || follow < 0
                        || lead > 0 && i != 0
                        || lead > 0 && Math.abs(lead - writtenUnit) > .001
                        || follow > 0 && Math.abs(follow - writtenUnit) > .001) invalid = true;
                if (lead > 0) {
                    cursor++;
                    restSlots++;
                }
                slotIndices.add(cursor);
                for (var n : column) {
                    if (Math.abs(n.leadingRestBeats() - lead) > .001
                            || Math.abs(n.followingRestBeats() - follow) > .001) invalid = true;
                    int direction = n.stemDirection();
                    Integer old = directions.putIfAbsent(cursor / 3, direction);
                    if (old != null && old != direction) invalid = true;
                }
                cursor++;
                if (follow > 0) {
                    cursor++;
                    restSlots++;
                }
            }
            if (invalid
                    || restSlots == 0
                    || cursor % 3 != 0
                    || Math.abs(cursor * unit - beats) > .001) continue;
            float small = Float.MAX_VALUE, large = 0;
            for (int i = 1; i < groups.size(); i++) {
                float distance =
                        (groups.get(i).get(0).positionInMeasure()
                                        - groups.get(i - 1).get(0).positionInMeasure())
                                / (slotIndices.get(i) - slotIndices.get(i - 1));
                small = Math.min(small, distance);
                large = Math.max(large, distance);
            }
            if (small <= SAME || large > small * 1.35f) continue;
            float step =
                    (groups.get(groups.size() - 1).get(0).positionInMeasure()
                                    - groups.get(0).get(0).positionInMeasure())
                            / (slotIndices.get(slotIndices.size() - 1) - slotIndices.get(0));
            float first = groups.get(0).get(0).positionInMeasure() - slotIndices.get(0) * step;
            if (first < -SAME || first > .18f) continue;
            var attacks = new ArrayList<Attack>();
            for (int i = 0; i < groups.size(); i++) {
                if (Math.abs(
                                groups.get(i).get(0).positionInMeasure()
                                        - (first + slotIndices.get(i) * step))
                        > SAME) invalid = true;
                for (var n : groups.get(i)) attacks.add(new Attack(n, slotIndices.get(i) * unit));
            }
            boolean completeHold = false, halfHold = false, closingHalf = false;
            for (var n : other) {
                double duration = ScoreNoteTiming.writtenDurationBeats(n);
                int slot = Math.round((n.positionInMeasure() - first) / step);
                if (n.leadingRestBeats() != 0
                        || n.followingRestBeats() != 0
                        || !Double.isFinite(duration)
                        || duration < 1
                        || slot < 0
                        || slot >= cursor
                        || Math.abs(n.positionInMeasure() - (first + slot * step)) > SAME
                        || slot * unit + duration > beats + .001) {
                    invalid = true;
                    break;
                }
                double onset = slot * unit;
                attacks.add(new Attack(n, onset));
                completeHold |= slot == 0 && Math.abs(duration - beats) < .001;
                halfHold |= slot == 0 && Math.abs(duration * 2 - beats) < .001;
                closingHalf |=
                        Math.abs(onset * 2 - beats) < .001 && Math.abs(duration * 2 - beats) < .001;
            }
            // A held full measure or two half-measure voices supplies an independent
            // clock. A half hold may instead end where complete triplet groups turn stems.
            boolean halfTurn = false;
            if (halfHold) {
                int middle = (int) Math.round(beats / (2 * unit));
                Integer before = directions.get(0), after = directions.get(middle / 3);
                halfTurn =
                        Math.abs(middle * unit * 2 - beats) < .001
                                && middle % 3 == 0
                                && before != null
                                && after != null
                                && !before.equals(after);
            }
            if (!invalid && (completeHold || halfHold && (closingHalf || halfTurn)))
                return new Clock(List.copyOf(members), groups, unit, beats, List.copyOf(attacks));
        }
        return null;
    }

    private record Attack(ScoreNoteEvent note, double onset) {}

    private static List<List<ScoreNoteEvent>> columns(List<ScoreNoteEvent> notes) {
        notes.sort(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
        var groups = new ArrayList<List<ScoreNoteEvent>>();
        for (var n : notes) {
            if (groups.isEmpty()
                    || n.positionInMeasure()
                                    - groups.get(groups.size() - 1).get(0).positionInMeasure()
                            > SAME) groups.add(new ArrayList<>());
            groups.get(groups.size() - 1).add(n);
        }
        return groups;
    }

    record Clock(
            List<ScoreNoteEvent> members,
            List<List<ScoreNoteEvent>> groups,
            double unit,
            double beats,
            List<Attack> attacks) {
        Clock(
                List<ScoreNoteEvent> members,
                List<List<ScoreNoteEvent>> groups,
                double unit,
                double beats) {
            this(members, groups, unit, beats, List.of());
        }

        private boolean positionFits(float position, double onset) {
            double index = onset / unit;
            int nearest = Math.min(groups.size() - 1, Math.max(0, (int) Math.round(index)));
            double left =
                    nearest == 0
                            ? 0
                            : (groups.get(nearest - 1).get(0).positionInMeasure()
                                            + groups.get(nearest).get(0).positionInMeasure())
                                    * .5;
            double right =
                    nearest == groups.size() - 1
                            ? 1
                            : (groups.get(nearest).get(0).positionInMeasure()
                                            + groups.get(nearest + 1).get(0).positionInMeasure())
                                    * .5;
            return position >= left && position <= right;
        }

        double duration(ScoreNoteEvent target) {
            return members.contains(target) ? unit : ScoreNoteTiming.writtenDurationBeats(target);
        }

        private double column(float position) {
            for (int i = 0; i < groups.size(); i++)
                if (Math.abs(groups.get(i).get(0).positionInMeasure() - position) <= SAME)
                    return i * unit;
            return Double.NaN;
        }

        double onset(ScoreNoteEvent target, List<ScoreNoteEvent> bar) {
            for (var attack : attacks) if (attack.note().equals(target)) return attack.onset();
            double aligned = column(target.positionInMeasure());
            if (Double.isFinite(aligned)) return aligned;
            if (target.stemDirection() == 0) return Double.NaN;
            var melody = new ArrayList<ScoreNoteEvent>();
            for (var n : bar)
                if (!members.contains(n)
                        && n.staffIndex() == target.staffIndex()
                        && n.stemDirection() == target.stemDirection()
                        && (n.articulations() & NoteOrnament.GRACE) == 0) melody.add(n);
            melody.sort(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
            double cursor = Double.NaN;
            for (int at = 0; at < melody.size(); ) {
                var first = melody.get(at);
                double anchor = column(first.positionInMeasure());
                if (Double.isFinite(anchor)) {
                    if (Double.isFinite(cursor) && Math.abs(cursor - anchor) > .03125)
                        return Double.NaN;
                    cursor = anchor;
                } else if (at == 0 && first.leadingRestBeats() > 0)
                    cursor = first.leadingRestBeats();
                int next = at;
                double advance = ScoreNoteTiming.writtenDurationBeats(first), silence = 0;
                boolean found = false;
                while (next < melody.size()
                        && Math.abs(
                                        melody.get(next).positionInMeasure()
                                                - first.positionInMeasure())
                                <= SAME) {
                    var n = melody.get(next++);
                    found |= n.equals(target);
                    if (Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - advance) > .001)
                        return Double.NaN;
                    silence = Math.max(silence, n.followingRestBeats());
                }
                if (found)
                    return Double.isFinite(cursor)
                                    && cursor >= 0
                                    && cursor + advance <= beats + .001
                            ? cursor
                            : Double.NaN;
                cursor += advance + silence;
                at = next;
            }
            return Double.NaN;
        }
    }
}
