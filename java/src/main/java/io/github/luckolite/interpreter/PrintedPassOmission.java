// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Evidence for a bracketed instruction to omit a staff passage on its second traversal. */
public final class PrintedPassOmission {
    private static final String SOURCE = "printed-second-pass-omission:v1";

    private PrintedPassOmission() {}

    static ScorePageInterpretation apply(
            ScorePageInterpretation score, List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs, byte[] gray, int width, int height,
            int physicalPage) {
        if (physicalPage < 0 || gray == null || gray.length != (long) width * height)
            return score;
        var events = new ArrayList<>(score.expressiveEvents());
        for (var word : words) {
            String text = normalize(word.text()).replaceFirst("[-–—]+$", "").stripTrailing();
            if (!text.equals("don't play 2nd time") && !text.equals("don't play second time")) continue;
            for (int m = 0; m + 1 < score.measures().size(); m++) {
                var bar = score.measures().get(m);
                var next = score.measures().get(m + 1);
                if (word.left() <= bar.left() || word.right() >= bar.right()
                        || Math.abs(next.top() - bar.top()) * height > 2) continue;
                for (var staff : staffs) {
                    float gap = staff.gap(), center = (staff.top() + staff.bottom()) / (2 * height);
                    if (gap < 6 || center < bar.top() || center > bar.bottom()
                            || word.bottom() * height > staff.top()
                            || staff.top() - word.bottom() * height > 4.5f * gap) continue;
                    boolean resumes = words.stream().anyMatch(w -> normalize(w.text()).equals("play both times")
                            && Math.abs(w.left() - next.left()) * width <= gap
                            && Math.abs(w.bottom() - word.bottom()) * height <= 3 * gap);
                    if (!resumes || !dashesReachBarline(gray, width, height, word, bar, gap)) continue;
                    var attack = beamStart(score, physicalPage, m, staff, bar, word.left(), gap / width);
                    if (attack == null) continue;
                    String id = SOURCE + ":" + physicalPage + ":" + m + ":" + staff.index();
                    if (events.stream().noneMatch(event -> event.eventId().equals(id)))
                        events.add(new ScoreExpressiveEvent(id,
                                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                                Optional.of(new ScoreAnchor(m, 0)), Optional.of(new ScoreAnchor(m + 1, 0)),
                                ScoreExpressiveEvent.Scope.UNRESOLVED, staff.index(), staff.count(),
                                Optional.of(Integer.toString(Float.floatToIntBits(attack.positionInMeasure()))),
                                ScoreExpressiveEvent.Strength.UNSPECIFIED, "",
                                List.of(new ScoreExpressiveEvent.Evidence(SOURCE, physicalPage,
                                        word.left(), staff.index(), staff.count(), word.text()))));
                }
            }
        }
        return events.size() == score.expressiveEvents().size() ? score : score.withExpressiveEvents(events);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'').strip().replaceAll("\\s+", " ");
    }

    private static ScoreNoteEvent beamStart(
            ScorePageInterpretation score, int page, int measure, PlayingTechniqueDetector.Staff staff,
            MeasureRegion bar, float left, float tolerance) {
        ScoreNoteEvent found = null;
        for (var group : score.beamSource().groups()) {
            var column = group.columns().get(0).members();
            if (column.size() != 1) continue;
            var owner = column.get(0);
            if (owner.pageIndex() != page || owner.noteIndex() >= score.notes().size()) continue;
            var note = score.notes().get(owner.noteIndex());
            if (note.measureIndex() != measure || note.staffIndex() != staff.index()
                    || note.staffCount() != staff.count() || note.tiedFromPrevious()) continue;
            float x = bar.left() + (bar.right() - bar.left()) * note.positionInMeasure();
            if (Math.abs(x - left) > tolerance) continue;
            if (found != null) return null;
            found = note;
        }
        return found;
    }

    private static boolean dashesReachBarline(
            byte[] gray, int width, int height, PlayingTechniqueDetector.Word word,
            MeasureRegion bar, float gap) {
        int left = Math.max(0, Math.round(word.right() * width));
        int right = Math.min(width - 1, Math.round(bar.right() * width));
        for (int y = Math.max(0, Math.round(word.top() * height));
                y <= Math.min(height - 1, Math.round(word.bottom() * height)); y++) {
            int run = 0, count = 0, last = -1;
            for (int x = left; x <= right + 1; x++) {
                if (x <= right && (gray[y * width + x] & 255) < 150) {
                    run++;
                    continue;
                }
                if (run >= gap * .15f && run <= gap * .65f) {
                    if (last < 0 || x - run - last > gap) count = 0;
                    count++;
                    last = x - 1;
                }
                run = 0;
            }
            if (count >= 6 && right - last <= gap) return true;
        }
        return false;
    }

    /** Drop only attacks whose exact source staff and second occurrence belong to the bracket. */
    public static ScoreNavigationNoteProjection.Result project(
            ScoreNavigationNoteProjection.Result result, List<ScoreExpressiveEvent> events,
            List<ScoreNoteEvent> notes, ScoreNavigationPlan plan, ScoreMeterMap meter) {
        var muted = new HashSet<String>();
        for (var event : events) {
            if (!event.eventId().startsWith(SOURCE + ":")
                    || event.kind() != ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                    || event.evidence().size() != 1 || !event.evidence().get(0).sourceId().equals(SOURCE)
                    || event.start().isEmpty() || event.end().isEmpty()
                    || event.targetEventId().isEmpty()) continue;
            int bar = event.start().get().measureIndex();
            if (bar >= plan.sourceMeasureCount() || event.start().get().quarterBeatOffset() != 0
                    || !event.end().get().equals(new ScoreAnchor(bar + 1, 0))) continue;
            int owner = -1;
            for (int i = 0; i < notes.size(); i++) {
                var note = notes.get(i);
                if (note.measureIndex() != bar || note.staffIndex() != event.staffIndex()
                        || note.staffCount() != event.staffCount() || note.tiedFromPrevious()
                        || !Integer.toString(Float.floatToIntBits(note.positionInMeasure()))
                                .equals(event.targetEventId().get())) continue;
                if (owner >= 0) {
                    owner = -1;
                    break;
                }
                owner = i;
            }
            if (owner < 0) continue;
            int ownerIndex = owner;
            var onsets = result.notes().stream()
                    .filter(note -> note.sourceIndex() == ownerIndex && !note.clippedEntry()).toList();
            if (onsets.size() < 2) continue;
            var second = onsets.get(1);
            double sourceEnd = meter.startBeat(bar + 1);
            if (second.sourceStartBeat() < meter.startBeat(bar) || second.sourceStartBeat() >= sourceEnd)
                continue;
            var occurrence = plan.traversal().occurrences().stream()
                    .filter(o -> o.occurrenceId().equals(second.navigationOccurrences().get(0)))
                    .findFirst().orElse(null);
            if (occurrence == null || occurrence.end().absoluteBeat(meter) != sourceEnd) continue;
            for (var note : result.notes()) {
                var source = notes.get(note.sourceIndex());
                if (note.startBeat() >= second.startBeat()
                        && note.startBeat() < occurrence.performanceEndBeat()
                        && note.navigationOccurrences().get(0).equals(occurrence.occurrenceId())
                        && source.staffIndex() == event.staffIndex() && source.staffCount() == event.staffCount())
                    muted.add(note.performanceId());
            }
        }
        if (muted.isEmpty()) return result;
        return new ScoreNavigationNoteProjection.Result(result.notes().stream()
                .filter(note -> !muted.contains(note.performanceId())).toList(), result.performedBeats());
    }
}
