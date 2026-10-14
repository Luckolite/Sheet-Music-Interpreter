// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A verified expanded H-rest owns its leading logical bar, without inventing a literal rest. */
final class PrintedMultiRestDirection {
    static int silentRun(ScorePageInterpretation score, int first) {
        if (first < 0 || first + 1 >= score.measures().size()) return 0;
        var m = score.measures().get(first);
        if (first > 0 && m.equals(score.measures().get(first - 1))) return 0;
        int end = first + 1;
        while (end < score.measures().size() && m.equals(score.measures().get(end))) end++;
        if (end - first < 2 || end - first > 32) return 0;
        for (var n : score.notes())
            if (n.measureIndex() >= first && n.measureIndex() < end) return 0;
        for (var r : score.rests())
            if (r.measureIndex() >= first && r.measureIndex() < end) return 0;
        return end - first;
    }

    static double provedOnset(
            ScorePageInterpretation score, int first, int staff, int count, float position) {
        return count > 0
                        && staff >= 0
                        && staff < count
                        && position == .5f
                        && silentRun(score, first) > 0
                ? 0
                : Double.NaN;
    }

    static ScoreRestEvent find(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int w,
            int h) {
        ScoreRestEvent result = null;
        for (int i = 0; i < score.measures().size(); i++) {
            if (silentRun(score, i) == 0) continue;
            var m = score.measures().get(i);
            if (!BeamedHeadQuarterRests.validMeasure(m)
                    || word.left() < m.left()
                    || word.left() > m.right()) continue;
            if (!MultiMeasureRestDetector.hasThickHorizontalBar(gray, w, h, m)) continue;
            for (var staff : staffs) {
                float gap = staff.gap(), center = (staff.top() + staff.bottom()) * .5f;
                if (!Float.isFinite(gap)
                        || gap < 6
                        || staff.count() < 1
                        || staff.index() < 0
                        || staff.index() >= staff.count()
                        || center / h < m.top()
                        || center / h > m.bottom()) continue;
                if (word.left() > m.left() + (m.right() - m.left()) * .35f) continue;
                var rules =
                        PrintedDirectionStaff.printedRules(
                                gray, w, h, word.left() * w, staff.bottom(), gap);
                if (rules == null)
                    rules =
                            PrintedDirectionStaff.printedRules(
                                    gray, w, h, m.left() * w - gap * 4, staff.bottom(), gap);
                if (rules == null)
                    rules =
                            PrintedDirectionStaff.printedRules(
                                    gray, w, h, m.right() * w + gap * 4, staff.bottom(), gap);
                if (rules == null
                        || Math.abs(rules[0] - staff.bottom()) > gap * .5f
                        || Math.abs(rules[1] - gap) > gap * .15f) continue;
                float top = rules[0] - rules[1] * 4;
                float distance =
                        word.bottom() * h <= top - gap * .25f
                                ? (top - word.bottom() * h) / gap
                                : word.top() * h >= rules[0] + gap * .25f
                                        ? (word.top() * h - rules[0]) / gap
                                        : Float.POSITIVE_INFINITY;
                if (distance > 4.5f) continue;
                if (result != null) return null;
                result =
                        ScoreRestEvent.fullMeasure(
                                i, .5f, center / h, gap / h, staff.index(), staff.count());
            }
        }
        return result;
    }
}
