// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Rest-owned directions require an identified silent glyph and complete local printed rails. */
final class PrintedRestDirection {
    static boolean allows(ScoreExpressiveEvent.Kind kind) {
        return switch (kind) {
            case RITARDANDO,
                            RALLENTANDO,
                            RITENUTO,
                            ACCELERANDO,
                            A_TEMPO,
                            TEMPO_PRIMO,
                            SAME_TEMPO,
                            METRIC_MODULATION ->
                    true;
            default -> false;
        };
    }

    static ScoreRestEvent find(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int w,
            int h) {
        if (gray == null || w <= 0 || h <= 0 || gray.length != (long) w * h) return null;
        float x = word.left() * w, top = word.top() * h, bottom = word.bottom() * h;
        if (!Float.isFinite(x)
                || !Float.isFinite(top)
                || !Float.isFinite(bottom)
                || !Float.isFinite(word.right())
                || word.right() < word.left()
                || word.right() > 1
                || x < 0
                || x >= w
                || top < 0
                || bottom >= h
                || bottom < top) return null;
        ScoreRestEvent best = null;
        float rank = Float.MAX_VALUE;
        boolean ambiguous = false;
        for (var rest : score.rests()) {
            if (rest.measureIndex() < 0
                    || rest.measureIndex() >= score.measures().size()
                    || !Float.isFinite(rest.pageY())
                    || !Float.isFinite(rest.positionInMeasure())
                    || rest.positionInMeasure() < 0
                    || rest.positionInMeasure() > 1) continue;
            var m = score.measures().get(rest.measureIndex());
            if (word.left() < m.left() || word.left() > m.right()) continue;
            float rx = (m.left() + rest.positionInMeasure() * (m.right() - m.left())) * w;
            float dx = Math.max(0, Math.max(x - rx, rx - word.right() * w));
            for (var staff : staffs) {
                float gap = staff.gap(), center = (staff.top() + staff.bottom()) * .5f;
                if (staff.index() != rest.staffIndex()
                        || staff.count() != rest.staffCount()
                        || !Float.isFinite(gap)
                        || gap < 6
                        || gap > Math.min(w, h) / 8f
                        || center / h < m.top()
                        || center / h > m.bottom()
                        || Math.abs(rest.pageY() * h - center) > gap * 2.5f
                        || dx > gap * 3) continue;
                var rules = PrintedDirectionStaff.printedRules(gray, w, h, x, staff.bottom(), gap);
                if (rules == null
                        || Math.abs(rules[0] - staff.bottom()) > gap * .5f
                        || Math.abs(rules[1] - gap) > gap * .15f) continue;
                float localTop = rules[0] - rules[1] * 4;
                float vertical =
                        bottom <= localTop - gap * .25f
                                ? (localTop - bottom) / gap
                                : top >= rules[0] + gap * .25f
                                        ? (top - rules[0]) / gap
                                        : Float.POSITIVE_INFINITY;
                if (vertical > 4.5f) continue;
                float distance = vertical + dx / gap * .05f;
                if (distance < rank - .1f) {
                    best = rest;
                    rank = distance;
                    ambiguous = false;
                } else if (Math.abs(distance - rank) <= .1f && best != rest) ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    static double onset(
            ScorePageInterpretation score,
            int measure,
            int staff,
            int count,
            float position,
            float beats) {
        if (measure < 0
                || measure >= score.measures().size()
                || !Float.isFinite(beats)
                || beats <= 0) return Double.NaN;
        ScoreRestEvent target = null;
        for (var r : score.rests())
            if (r.measureIndex() == measure
                    && r.staffIndex() == staff
                    && r.staffCount() == count
                    && Math.abs(r.positionInMeasure() - position) <= .018f) {
                if (target != null) return Double.NaN;
                target = r;
            }
        if (target == null) return Double.NaN;
        if (target.isFullMeasure()) {
            for (var n : score.notes())
                if (n.measureIndex() == measure
                        && n.staffIndex() == staff
                        && n.staffCount() == count) return Double.NaN;
            for (var r : score.rests())
                if (r != target
                        && r.measureIndex() == measure
                        && r.staffIndex() == staff
                        && r.staffCount() == count) return Double.NaN;
        }
        return ScoreRestFermataDetector.provedOnset(target, score.rests(), score.notes(), beats);
    }
}
