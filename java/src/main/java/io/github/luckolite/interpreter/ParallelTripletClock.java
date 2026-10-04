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
        return bridged != null ? bridged : findSameStaff(target, bar, beats);
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
            double beats) {
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
