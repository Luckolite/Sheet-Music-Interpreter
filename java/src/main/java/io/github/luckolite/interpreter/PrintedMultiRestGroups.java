// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Capture only accepted producer groups, never infer one from silence or equal regions. */
final class PrintedMultiRestGroups {
    private PrintedMultiRestGroups() {}

    static List<ScoreMultiRestGroup> capture(
            OmrScoreInterpreter.Analysis score,
            List<MeasureRegion> measures,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            RecognizedMultiRestOrigins origins) {
        if (score == null
                || measures == null
                || origins == null
                || measures.size() > 4096
                || score.notes().size() > 100000
                || score.rests().size() > 100000
                || !origins.matches(labels, gray, w, h)) return List.of();
        int n = measures.size();
        boolean[] conflict = new boolean[n];
        int[] full = new int[n];
        for (var note : score.notes())
            if (note.measureIndex() >= 0 && note.measureIndex() < n)
                conflict[note.measureIndex()] = true;
        for (var rest : score.rests()) {
            int i = rest.measureIndex();
            if (i < 0 || i >= n) continue;
            if (!rest.isFullMeasure() || rest.staffIndex() != 0 || rest.staffCount() != 1)
                conflict[i] = true;
            else full[i]++;
        }
        var occurrences = new IdentityHashMap<MeasureRegion, Integer>();
        for (var region : measures) occurrences.merge(region, 1, Integer::sum);
        var groups = new ArrayList<ScoreMultiRestGroup>();
        for (int first = 0; first < n; ) {
            var parent = measures.get(first);
            int end = first + 1;
            while (end < n && measures.get(end) == parent) end++;
            var admission = origins.admission(parent);
            var physical = origins.physical(parent);
            boolean proved =
                    admission != null
                            && physical != null
                            && physical.members.size() == 1
                            && origins.expansion(parent, first, end)
                            && end - first == admission.token().value()
                            && occurrences.get(parent) == end - first;
            if (proved) {
                for (int i = first; i < end; i++) if (conflict[i] || full[i] != 1) proved = false;
            }
            if (proved) {
                var window = admission.candidate().region();
                int x = Math.round((window.left() + window.right()) * .5f * w);
                if (physical.members.get(0).physicalRows(x, w) != null)
                    groups.add(new ScoreMultiRestGroup(first, end - first, 0, 1));
            }
            first = end;
        }
        return List.copyOf(groups);
    }

    static List<ScoreMultiRestGroup> validate(
            List<ScoreMultiRestGroup> groups,
            int measures,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests) {
        if (groups == null || groups.size() > 2048 || measures < 0 || measures > 100000)
            throw new IllegalArgumentException("Invalid printed rest groups");
        var safe = List.copyOf(groups);
        if (safe.isEmpty()) return safe;
        var facts = facts(notes, rests);
        var occupied = new HashSet<String>();
        for (var group : safe) {
            if (group.firstMeasureIndex() > measures - group.measureCount()
                    || !silent(group, facts))
                throw new IllegalArgumentException("Printed rest group conflicts with score");
            for (int d = 0; d < group.measureCount(); d++)
                if (!occupied.add(
                        group.staffIndex()
                                + ":"
                                + group.staffCount()
                                + ":"
                                + (group.firstMeasureIndex() + d)))
                    throw new IllegalArgumentException("Overlapping printed rest groups");
        }
        return safe;
    }

    private record Facts(
            Set<Integer> noteMeasures,
            Set<Long> noteLanes,
            Map<Long, Integer> full,
            Set<Long> other,
            Set<Integer> foreignSingle) {}

    private static long lane(int measure, int staff, int staffs) {
        return ((long) measure << 10) | ((long) (staffs - 1) << 5) | staff;
    }

    private static Facts facts(List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        if (notes.size() > 100000 || rests.size() > 100000)
            throw new IllegalArgumentException("Printed rest ownership input too large");
        var allNotes = new HashSet<Integer>();
        var noteLanes = new HashSet<Long>();
        var full = new HashMap<Long, Integer>();
        var other = new HashSet<Long>();
        var foreign = new HashSet<Integer>();
        for (var note : notes) {
            allNotes.add(note.measureIndex());
            noteLanes.add(lane(note.measureIndex(), note.staffIndex(), note.staffCount()));
        }
        for (var rest : rests) {
            long key = lane(rest.measureIndex(), rest.staffIndex(), rest.staffCount());
            if (rest.isFullMeasure()) full.merge(key, 1, Integer::sum);
            else other.add(key);
            if (rest.staffIndex() != 0 || rest.staffCount() != 1) foreign.add(rest.measureIndex());
        }
        return new Facts(allNotes, noteLanes, full, other, foreign);
    }

    private static boolean silent(ScoreMultiRestGroup group, Facts facts) {
        for (int d = 0; d < group.measureCount(); d++) {
            int m = group.firstMeasureIndex() + d;
            long key = lane(m, group.staffIndex(), group.staffCount());
            if (facts.noteLanes.contains(key)
                    || group.staffCount() == 1 && facts.noteMeasures.contains(m)
                    || facts.other.contains(key)
                    || facts.full.getOrDefault(key, 0) != 1
                    || group.staffCount() == 1 && facts.foreignSingle.contains(m)) return false;
        }
        return true;
    }

    static List<ScoreMultiRestGroup> retainSilent(
            List<ScoreMultiRestGroup> groups,
            int measures,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests) {
        if (groups.isEmpty()) return List.of();
        var facts = facts(notes, rests);
        return groups.stream()
                .filter(
                        g ->
                                g.firstMeasureIndex() <= measures - g.measureCount()
                                        && silent(g, facts))
                .toList();
    }

    /** Preserve a whole ordered block only; partial selection keeps per-bar rests, not the H-bar. */
    static List<ScoreMultiRestGroup> remap(
            List<ScoreMultiRestGroup> groups, List<Integer> newToOriginal) {
        if (groups == null
                || newToOriginal == null
                || groups.size() > 2048
                || newToOriginal.size() > 4096) return List.of();
        var location = new HashMap<Integer, Integer>();
        var duplicate = new HashSet<Integer>();
        for (int i = 0; i < newToOriginal.size(); i++) {
            Integer old = newToOriginal.get(i);
            if (old == null || old < 0) return List.of();
            if (location.putIfAbsent(old, i) != null) duplicate.add(old);
        }
        var result = new ArrayList<ScoreMultiRestGroup>();
        for (var group : groups) {
            if (group == null) return List.of();
            Integer first = location.get(group.firstMeasureIndex());
            if (first == null || first > newToOriginal.size() - group.measureCount()) continue;
            boolean complete = true;
            for (int d = 0; d < group.measureCount(); d++) {
                int old = group.firstMeasureIndex() + d;
                if (duplicate.contains(old) || !Objects.equals(location.get(old), first + d))
                    complete = false;
            }
            if (complete)
                result.add(
                        new ScoreMultiRestGroup(
                                first,
                                group.measureCount(),
                                group.staffIndex(),
                                group.staffCount()));
        }
        result.sort(Comparator.comparingInt(ScoreMultiRestGroup::firstMeasureIndex));
        return List.copyOf(result);
    }
}
