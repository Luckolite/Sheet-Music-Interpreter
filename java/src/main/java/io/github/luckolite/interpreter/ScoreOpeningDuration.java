// Copyright 2026 Luckolite

// SPDX-License-Identifier: Apache-2.0

package io.github.luckolite.interpreter;

import java.util.*;

/** Actual opening-bar duration without replacing its printed time signature. */
public final class ScoreOpeningDuration {

    private ScoreOpeningDuration() {}

    /** All source consumers share the same independently proved opening clock. */
    public static double provedQuarterBeats(ScorePageInterpretation source, float nominal) {
        if (source == null) return Double.NaN;
        double silent =
                provedNumberedSilentPickup(
                        source.notes(), source.rests(), nominal, source.openingPickup());
        return Double.isFinite(silent)
                ? silent
                : provedQuarterBeats(
                        source.notes(),
                        source.rests(),
                        source.measures(),
                        nominal,
                        source.firstMeasureNumber());
    }

    /** Silent pickups require independent numbering proof and explicit rests on every staff. */
    public static double provedNumberedSilentPickup(
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests,
            float nominal,
            boolean numberedPickup) {
        if (!numberedPickup
                || notes == null
                || rests == null
                || !Float.isFinite(nominal)
                || nominal <= 0
                || notes.stream().anyMatch(n -> n != null && n.measureIndex() == 0))
            return Double.NaN;
        List<ScoreRestEvent> opening =
                rests.stream().filter(r -> r != null && r.measureIndex() == 0).toList();
        if (opening.isEmpty()) return Double.NaN;
        int count = opening.get(0).staffCount();
        if (count < 1 || count > 16) return Double.NaN;
        double[] durations = new double[count];
        boolean[] present = new boolean[count];
        for (ScoreRestEvent rest : opening) {
            if (rest.staffCount() != count
                    || rest.staffIndex() < 0
                    || rest.staffIndex() >= count
                    || rest.isFullMeasure()
                    || !Float.isFinite(rest.positionInMeasure())
                    || rest.positionInMeasure() < 0
                    || rest.positionInMeasure() > 1
                    || !Double.isFinite(rest.durationBeats())
                    || rest.durationBeats() <= 0) return Double.NaN;
            // Multiple rests can represent voices or duplicates: leave ambiguous lanes alone.
            if (present[rest.staffIndex()]) return Double.NaN;
            present[rest.staffIndex()] = true;
            durations[rest.staffIndex()] = rest.durationBeats();
        }
        double span = durations[0];
        for (int staff = 0; staff < count; staff++)
            if (!present[staff] || Math.abs(durations[staff] - span) > .001) return Double.NaN;
        return span >= .125 && span < nominal - .001 ? span : Double.NaN;
    }

    /** Returns NaN unless a short layout and every printed staff agree on explicit contents. */
    public static double provedQuarterBeats(
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests,
            List<MeasureRegion> measures,
            float nominal,
            int firstMeasureNumber) {

        if (firstMeasureNumber > 1
                || notes == null
                || rests == null
                || measures == null
                || measures.size() < 3
                || !Float.isFinite(nominal)
                || nominal <= 0) return Double.NaN;

        MeasureRegion first = measures.get(0);

        float width = first.right() - first.left(), height = first.bottom() - first.top();

        List<Float> widths = new ArrayList<>();

        for (int i = 1; i < measures.size(); i++) {

            MeasureRegion m = measures.get(i);

            if (Math.abs(m.top() - first.top()) > height * .25f
                    || Math.abs(m.bottom() - first.bottom()) > height * .25f) break;

            if (m.left() <= first.right()) return Double.NaN;

            widths.add(m.right() - m.left());
        }

        if (widths.size() < 2 || width <= 0) return Double.NaN;

        widths.sort(Float::compare);

        if (width > widths.get(widths.size() / 2) * .5f) return Double.NaN;

        return explicitSpan(notes, rests, 0, nominal, false);
    }

    /** A shortened ending requires agreeing independent staves and explicit terminal rests. */
    public static double provedClosingQuarterBeats(
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests,
            int measureCount,
            float nominal) {

        if (notes == null
                || rests == null
                || measureCount < 1
                || !Float.isFinite(nominal)
                || nominal <= 0) return Double.NaN;

        return explicitSpan(notes, rests, measureCount - 1, nominal, true);
    }

    private static double explicitSpan(
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests,
            int measure,
            float nominal,
            boolean terminal) {

        List<ScoreNoteEvent> opening =
                notes.stream()
                        .filter(
                                n ->
                                        n != null
                                                && n.measureIndex() == measure
                                                && (n.articulations() & NoteOrnament.GRACE) == 0)
                        .toList();

        List<ScoreRestEvent> silence =
                rests.stream().filter(r -> r != null && r.measureIndex() == measure).toList();

        if (opening.isEmpty()) return Double.NaN;

        int staffs = opening.get(0).staffCount();

        if (staffs < (terminal ? 2 : 1)
                || staffs > 16
                || opening.stream()
                        .anyMatch(
                                n ->
                                        n.staffCount() != staffs
                                                || n.staffIndex() < 0
                                                || n.staffIndex() >= staffs
                                                || !Float.isFinite(n.positionInMeasure())
                                                || n.tiedFromPrevious()
                                                || n.crossStaffBeam()
                                                || n.boundaryTies() != 0)
                || silence.stream()
                        .anyMatch(
                                r ->
                                        r.staffCount() != staffs
                                                || r.staffIndex() < 0
                                                || r.staffIndex() >= staffs
                                                || !Float.isFinite(r.positionInMeasure())))
            return Double.NaN;

        double span = Double.NaN;
        int explicitLanes = 0;

        for (int staff = 0; staff < staffs; staff++) {

            final int lane = staff;

            List<ScoreNoteEvent> voice =
                    opening.stream()
                            .filter(n -> n.staffIndex() == lane)
                            .sorted(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure))
                            .toList();

            List<ScoreRestEvent> gaps =
                    silence.stream().filter(r -> r.staffIndex() == lane).toList();

            if (voice.isEmpty() && gaps.isEmpty()) return Double.NaN;
            if (voice.isEmpty() && gaps.size() == 1 && gaps.get(0).isFullMeasure()) continue;
            gaps = gaps.stream().filter(r -> !r.isFullMeasure()).toList();
            if (voice.isEmpty() && gaps.isEmpty()) return Double.NaN;

            if (terminal) {

                if (gaps.isEmpty()) return Double.NaN;

                float lastNote =
                        voice.isEmpty() ? -1 : voice.get(voice.size() - 1).positionInMeasure();

                if (gaps.stream().noneMatch(r -> r.positionInMeasure() > lastNote))
                    return Double.NaN;
            }

            double total = 0;

            for (ScoreRestEvent rest : gaps) {

                if (!Double.isFinite(rest.durationBeats()) || rest.durationBeats() <= 0)
                    return Double.NaN;

                total += rest.durationBeats();
            }

            float lastPosition = Float.NaN;

            double lastDuration = Double.NaN;

            for (ScoreNoteEvent note : voice) {

                double duration = ScoreNoteTiming.writtenDurationBeats(note);

                if (!Double.isFinite(duration) || duration <= 0) return Double.NaN;

                double before = 0, after = 0;

                for (ScoreRestEvent rest : gaps) {

                    if (rest.positionInMeasure() < note.positionInMeasure())
                        before += rest.durationBeats();
                    else after += rest.durationBeats();
                }

                if (note.leadingRestBeats() > before + .001
                        || note.followingRestBeats() > after + .001) return Double.NaN;

                if (Float.isFinite(lastPosition)
                        && note.positionInMeasure() - lastPosition < .003f) {

                    if (Math.abs(duration - lastDuration) > .001) return Double.NaN;

                } else {

                    total += duration;

                    lastPosition = note.positionInMeasure();

                    lastDuration = duration;
                }
            }

            if (!voice.isEmpty() && gaps.isEmpty() && voice.get(0).positionInMeasure() < .4f)
                return Double.NaN;

            if (Double.isFinite(span) && Math.abs(span - total) > .001) return Double.NaN;

            span = total;
            explicitLanes++;
        }

        return (!terminal || explicitLanes >= 2)
                        && Double.isFinite(span)
                        && span >= .125
                        && span < nominal - .001
                ? span
                : Double.NaN;
    }
}
