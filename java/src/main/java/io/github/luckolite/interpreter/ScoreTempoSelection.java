// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** The selected opening tempo controls playback; printed later changes keep their proportions. */
public final class ScoreTempoSelection {
    private ScoreTempoSelection() {}

    /** Non-quarter printed pulses take precedence over the meter's default pulse unit. */
    public static double selectedQuarterBpm(
            double selectedMarkedBpm, double fallbackQuarterBpm, List<ScoreTempoChange> printed) {
        if (printed != null)
            for (var mark : printed)
                if (mark.measureIndex() == 0
                        && mark.positionInMeasure() == 0
                        && mark.beatUnit() != 1) return selectedMarkedBpm * mark.beatUnit();
        return fallbackQuarterBpm;
    }

    public static List<ScoreTempoChange> atOpeningTempo(
            double selectedQuarterBpm, List<ScoreTempoChange> printed) {
        if (printed == null || printed.isEmpty()) return List.of();
        ScoreTempoChange opening = null;
        for (ScoreTempoChange mark : printed)
            if (mark.measureIndex() == 0 && mark.positionInMeasure() == 0) {
                opening = mark;
                break;
            }
        if (opening == null) return List.copyOf(printed);
        double ratio = selectedQuarterBpm / opening.bpm();
        var result = new java.util.ArrayList<ScoreTempoChange>();
        for (ScoreTempoChange mark : printed)
            result.add(
                    new ScoreTempoChange(
                            mark.measureIndex(),
                            mark.positionInMeasure(),
                            Math.max(15, Math.min(1600, mark.bpm() * ratio)),
                            mark.beatUnit()));
        return List.copyOf(result);
    }

    /** Re-engraving uses the selected tempo without losing the printed pulse or later changes. */
    public static List<ScoreTempoChange> forEngraving(
            double selectedMarkedBpm,
            double fallbackQuarterBpm,
            double fallbackBeatUnit,
            List<ScoreTempoChange> printed) {
        var ordered =
                (printed == null ? List.<ScoreTempoChange>of() : printed)
                        .stream()
                                .sorted(
                                        java.util.Comparator.comparingInt(
                                                        ScoreTempoChange::measureIndex)
                                                .thenComparingDouble(
                                                        ScoreTempoChange::positionInMeasure))
                                .toList();
        if (selectedMarkedBpm <= 0) return ordered;
        double quarterBpm = selectedQuarterBpm(selectedMarkedBpm, fallbackQuarterBpm, ordered);
        return forEngravingQuarterBpm(quarterBpm, fallbackBeatUnit, ordered);
    }

    /** Re-engraves an already converted quarter-note clock without applying the printed unit again. */
    public static List<ScoreTempoChange> forEngravingQuarterBpm(
            double quarterBpm, double fallbackBeatUnit, List<ScoreTempoChange> printed) {
        var ordered =
                (printed == null ? List.<ScoreTempoChange>of() : printed)
                        .stream()
                                .sorted(
                                        java.util.Comparator.comparingInt(
                                                        ScoreTempoChange::measureIndex)
                                                .thenComparingDouble(
                                                        ScoreTempoChange::positionInMeasure))
                                .toList();
        if (quarterBpm <= 0) return ordered;
        if (ordered.stream()
                .anyMatch(mark -> mark.measureIndex() == 0 && mark.positionInMeasure() == 0))
            return atOpeningTempo(quarterBpm, ordered);
        var result = new java.util.ArrayList<ScoreTempoChange>();
        result.add(new ScoreTempoChange(0, 0, quarterBpm, fallbackBeatUnit));
        result.addAll(ordered);
        return List.copyOf(result);
    }
}
