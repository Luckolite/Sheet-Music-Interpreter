// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Complete silent bars veto a numbering-only pickup without guessing ambiguous contents. */
final class OpeningPickupContent {
    static boolean keepsNumberedPickup(
            List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests, List<ScoreMeterChange> meters) {
        if (notes == null || rests == null || meters == null) return true;
        int knownStaffCount = 0;
        for (var note : notes) {
            if (note == null || note.measureIndex() == 0) return true;
            knownStaffCount = Math.max(knownStaffCount, note.staffCount());
        }
        ScoreMeterChange opening = null;
        for (var meter : meters) {
            if (meter == null) return true;
            if (meter.measureIndex() != 0) continue;
            if (meter.numerator() <= 0 || meter.denominator() <= 0) return true;
            if (opening != null) return true;
            opening = meter;
        }
        if (opening == null) return true;
        double nominal = opening.numerator() * 4d / opening.denominator();
        if (!Double.isFinite(nominal) || nominal <= 0) return true;
        int count = 0;
        boolean[] present = null;
        for (var rest : rests) {
            if (rest == null) return true;
            knownStaffCount = Math.max(knownStaffCount, rest.staffCount());
            if (rest.measureIndex() != 0) continue;
            if (rest.staffCount() < 1
                    || rest.staffCount() > 16
                    || rest.staffIndex() < 0
                    || rest.staffIndex() >= rest.staffCount()
                    || !Float.isFinite(rest.positionInMeasure())
                    || rest.positionInMeasure() < 0
                    || rest.positionInMeasure() > 1
                    || !Float.isFinite(rest.pageY())
                    || rest.pageY() < 0
                    || rest.pageY() > 1
                    || !Float.isFinite(rest.pageHeight())
                    || rest.pageHeight() <= 0
                    || rest.pageHeight() > 1
                    || !Double.isFinite(rest.durationBeats())
                    || rest.durationBeats() <= 0) return true;
            if (present == null) {
                count = rest.staffCount();
                present = new boolean[count];
            }
            if (rest.staffCount() != count || present[rest.staffIndex()]) return true;
            if (!rest.isFullMeasure() && rest.durationBeats() != nominal) return true;
            present[rest.staffIndex()] = true;
        }
        if (present == null || knownStaffCount > count) return true;
        for (boolean lane : present) if (!lane) return true;
        return false;
    }
}
