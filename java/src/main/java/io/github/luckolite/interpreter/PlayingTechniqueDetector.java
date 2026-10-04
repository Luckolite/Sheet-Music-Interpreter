// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class PlayingTechniqueDetector {
    record Staff(float top, float bottom, float gap, int index, int count) {}

    record Word(String text, float left, float top, float right, float bottom) {}

    private static final Pattern TOKEN_PUNCTUATION = Pattern.compile("^[.,:;]+|[.,:;]+$");
    private static final Pattern TOKEN_SPACE = Pattern.compile("\\s+");
    private static final Pattern DYNAMIC_TOKEN = Pattern.compile("ppp|pp|p|mp|mf|fff|ff|f");

    private PlayingTechniqueDetector() {}

    static int technique(String word) {
        if (word == null) return -1;
        String clean =
                TOKEN_PUNCTUATION.matcher(word.trim().toLowerCase(Locale.ROOT)).replaceAll("");
        // A dynamic and an expression can share one OCR line. Accept only a
        // complete two-token direction, never arbitrary prose or negation.
        String[] phrase = TOKEN_SPACE.split(clean);
        if (phrase.length == 2 && DYNAMIC_TOKEN.matcher(phrase[0]).matches())
            clean = TOKEN_PUNCTUATION.matcher(phrase[1]).replaceAll("");
        return switch (clean) {
            case "pizz", "pizzicato" -> ScoreTechniqueChange.PIZZICATO;
            case "arco" -> ScoreTechniqueChange.ARCO;
            case "cantabile" -> ScoreTechniqueChange.CANTABILE;
            case "sostenuto" -> ScoreTechniqueChange.SOSTENUTO;
            case "marcato" -> ScoreTechniqueChange.MARCATO;
            case "ordinario", "ord" -> ScoreTechniqueChange.ORDINARIO;
            default -> -1;
        };
    }

    static List<ScoreTechniqueChange> detect(
            List<Word> words,
            List<Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        List<ScoreTechniqueChange> result = new ArrayList<>();
        var localRegions = TechniqueTextRegions.above(staffs, measures, notes, width, height);
        for (Word word : words) {
            int technique = technique(word.text);
            if (technique < 0) continue;
            Staff owner = null;
            float best = Float.MAX_VALUE, second = Float.MAX_VALUE;
            boolean ownerBelow = false;
            for (Staff staff : staffs) {
                var frame = TechniqueTextRegions.local(staff, word.left * width, localRegions);
                float above = (frame.top - word.bottom * height) / staff.gap;
                float below = (word.top * height - frame.bottom) / staff.gap;
                boolean upper = above >= -.6f && above <= 4.2f;
                // OCR boxes and the row-wide staff frame can differ by half a gap.
                // Require the expression to extend beyond the bottom rule too.
                boolean lower =
                        technique >= ScoreTechniqueChange.CANTABILE
                                && below >= -.6f
                                && below <= 4.2f
                                && word.bottom * height >= frame.bottom + staff.gap * .5f;
                if (!upper && !lower) continue;
                boolean isBelow = lower && (!upper || Math.abs(below) < Math.abs(above));
                float distance = isBelow ? Math.abs(below) : Math.abs(above);
                if (distance < best) {
                    second = best;
                    best = distance;
                    owner = staff;
                    ownerBelow = isBelow;
                } else second = Math.min(second, distance);
            }
            // A below-staff expression must have an unambiguous nearest lane.
            // Keep the established above-staff attachment behavior otherwise.
            if (owner == null || (ownerBelow && second - best < .6f)) continue;
            int measure = -1;
            float closest = Float.MAX_VALUE;
            for (int m = 0; m < measures.size(); m++) {
                var region = measures.get(m);
                float center = (owner.top + owner.bottom) * .5f / height;
                if (center < region.top() - owner.gap / height
                        || center > region.bottom() + owner.gap / height) continue;
                if (word.left > region.right() + owner.gap / width * .5f) continue;
                float distance = Math.max(0, region.left() - word.left);
                if (distance < closest) {
                    closest = distance;
                    measure = m;
                }
            }
            if (measure < 0 || closest > owner.gap / width * 5) continue;
            var region = measures.get(measure);
            float position =
                    Math.max(
                            0,
                            Math.min(
                                    1,
                                    (word.left - region.left())
                                            / (region.right() - region.left())));
            // Engravers center labels slightly to the right of the first affected head.
            // Snap to that head only within one staff gap; otherwise retain the printed slot.
            float nearest = owner.gap / width / (region.right() - region.left());
            float printedPosition = position;
            for (var note : notes)
                if (note.measureIndex() == measure && note.staffIndex() == owner.index) {
                    float d = Math.abs(note.positionInMeasure() - printedPosition);
                    if (d <= nearest) {
                        nearest = d;
                        position = note.positionInMeasure();
                    }
                }
            var change =
                    new ScoreTechniqueChange(
                            measure, position, owner.index, owner.count, technique);
            boolean duplicate = false;
            for (var prior : result)
                if (prior.measureIndex() == measure
                        && prior.staffIndex() == owner.index
                        && prior.technique() == technique
                        && Math.abs(prior.positionInMeasure() - position) < .03f) duplicate = true;
            if (!duplicate) result.add(change);
        }
        result.sort(
                Comparator.comparingInt(ScoreTechniqueChange::measureIndex)
                        .thenComparingDouble(ScoreTechniqueChange::positionInMeasure)
                        .thenComparingInt(ScoreTechniqueChange::staffIndex));
        return List.copyOf(result);
    }
}
