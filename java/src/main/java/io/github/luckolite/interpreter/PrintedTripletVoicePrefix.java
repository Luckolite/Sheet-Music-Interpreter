// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A complete printed triplet prefix beside a separately shafted quarter-note voice. */
final class PrintedTripletVoicePrefix {
    record Proof(
            List<ScoreNoteEvent> members,
            List<List<ScoreNoteEvent>> groups,
            List<Double> onsets,
            List<ScoreNoteEvent> quarters,
            double unit) {}

    static Proof find(ScoreNoteEvent target, List<ScoreNoteEvent> bar, double beats) {
        if (target == null || bar == null || !Double.isFinite(beats) || beats < 3 || beats > 4)
            return null;
        for (int direction : new int[] {-1, 1})
            for (int beams : new int[] {1, 2}) {
                List<ScoreNoteEvent> lane = new ArrayList<>(), quarters = new ArrayList<>();
                boolean invalid = false;
                for (var n : bar) {
                    if (n == null) {
                        invalid = true;
                        break;
                    }
                    if (n.staffIndex() != target.staffIndex()) continue;
                    if (n.measureIndex() != target.measureIndex()
                            || n.staffCount() != target.staffCount()
                            || !Float.isFinite(n.positionInMeasure())
                            || n.positionInMeasure() < 0
                            || n.positionInMeasure() > 1
                            || n.tiedFromPrevious()
                            || n.crossStaffBeam()
                            || n.augmentationDots() != 0
                            || (n.articulations() & NoteOrnament.GRACE) != 0
                            || n.kind() != ScoreNoteEvent.Kind.PITCHED) {
                        invalid = true;
                        break;
                    }
                    if (n.stemDirection() == direction
                            && n.beamCount() == beams
                            && n.unbeamedDurationBeats() == 0
                            && (n.tupletDivisor() == 1 && n.tupletNormalNotes() == 1
                                    || n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2))
                        lane.add(n);
                    else if (n.stemDirection() == -direction
                            && n.beamCount() == 0
                            && n.tupletDivisor() == 1
                            && n.tupletNormalNotes() == 1
                            && Math.abs(ScoreNoteTiming.writtenDurationBeats(n) - 1) < .001)
                        quarters.add(n);
                    else {
                        invalid = true;
                        break;
                    }
                }
                if (invalid || quarters.size() < 3 || lane.size() < 3) continue;
                var quarterGroups = columns(quarters);
                if (quarterGroups.size() < 3) continue;
                float small = Float.MAX_VALUE, large = 0;
                for (int i = 1; i < quarterGroups.size(); i++) {
                    float gap =
                            quarterGroups.get(i).get(0).positionInMeasure()
                                    - quarterGroups.get(i - 1).get(0).positionInMeasure();
                    small = Math.min(small, gap);
                    large = Math.max(large, gap);
                }
                if (small < .054f || large > small * 2) continue;
                var groups = columns(lane);
                List<List<ScoreNoteEvent>> prefix = new ArrayList<>();
                List<ScoreNoteEvent> members = new ArrayList<>();
                List<Double> onsets = new ArrayList<>();
                double unit = (beams == 1 ? .5 : .25) * 2 / 3, cursor = 0;
                boolean tail = false;
                for (var column : groups) {
                    boolean printed = column.get(0).tupletDivisor() == 3;
                    for (var n : column) if ((n.tupletDivisor() == 3) != printed) invalid = true;
                    if (!printed) {
                        tail = true;
                        continue;
                    }
                    // Later printed groups do not alter the complete opening prefix.
                    // They keep their own unresolved start after an unmarked interval.
                    if (tail) continue;
                    var first = column.get(0);
                    double lead = first.leadingRestBeats(), follow = first.followingRestBeats();
                    if (!Double.isFinite(lead)
                            || !Double.isFinite(follow)
                            || lead < 0
                            || follow < 0
                            || lead > 0 && !prefix.isEmpty()
                            || lead > 0 && Math.abs(lead - unit) > .001
                            || follow > 0 && Math.abs(follow - unit) > .001) invalid = true;
                    for (var n : column)
                        if (Math.abs(n.leadingRestBeats() - lead) > .001
                                || Math.abs(n.followingRestBeats() - follow) > .001) invalid = true;
                    cursor += lead;
                    onsets.add(cursor);
                    cursor += unit + follow;
                    prefix.add(column);
                    members.addAll(column);
                }
                // This clock ends at an independently complete written beat, not at an
                // arbitrary crop of a triplet or an unrecognized continuation.
                if (invalid
                        || prefix.size() < 3
                        || cursor < 1
                        || cursor > beats + .001
                        || Math.abs(cursor - Math.rint(cursor)) > .001
                        || prefix.get(0).get(0).positionInMeasure() > .18f) continue;
                if (quarterGroups.get(0).get(0).positionInMeasure()
                        < prefix.get(0).get(0).positionInMeasure() - .018f) continue;
                if (!members.contains(target) && !quarters.contains(target)) continue;
                return new Proof(
                        List.copyOf(members),
                        List.copyOf(prefix),
                        List.copyOf(onsets),
                        List.copyOf(quarters),
                        unit);
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
                            > .018f) groups.add(new ArrayList<>());
            groups.get(groups.size() - 1).add(n);
        }
        return groups;
    }
}
