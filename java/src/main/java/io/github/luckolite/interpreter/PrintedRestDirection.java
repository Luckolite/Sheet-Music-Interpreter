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
        return find(score, staffs, word, gray, w, h, false);
    }

    static ScoreRestEvent findPedalCaption(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int w,
            int h) {
        if (!ExpressiveDirectionText.pedalRingCaption(word.text())) return null;
        return find(score, staffs, word, gray, w, h, true);
    }

    static boolean allowsPedalCaption(ScoreExpressiveEvent.Kind kind, String text) {
        return kind == ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                && ExpressiveDirectionText.pedalRingCaption(text);
    }

    static boolean pedalCaption(ScoreExpressiveEvent event) {
        return event.kind() == ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                && !event.evidence().isEmpty()
                && event.targetEventId()
                        .filter(id -> id.startsWith("expression-rest-column:"))
                        .isPresent()
                && event.evidence().stream()
                        .allMatch(
                                e ->
                                        e.sourceId().equals("printed-expression-word")
                                                && allowsPedalCaption(
                                                        event.kind(), e.printedText()));
    }

    private static boolean uniqueFullRest(ScorePageInterpretation score, ScoreRestEvent rest) {
        if (!rest.isFullMeasure()) return false;
        for (var note : score.notes())
            if (note.measureIndex() == rest.measureIndex()
                    && note.staffIndex() == rest.staffIndex()
                    && note.staffCount() == rest.staffCount()) return false;
        for (var other : score.rests())
            if (other != rest
                    && other.measureIndex() == rest.measureIndex()
                    && other.staffIndex() == rest.staffIndex()
                    && other.staffCount() == rest.staffCount()) return false;
        return true;
    }

    private static ScoreRestEvent find(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int w,
            int h,
            boolean caption) {
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
            float ownerX = caption ? (word.left() + word.right()) * .5f : word.left();
            if (ownerX < m.left()
                    || ownerX > m.right()
                    || caption && (!uniqueFullRest(score, rest) || word.right() > m.right()))
                continue;
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
                        || (dx > gap * 3 && !measureStartWholeRest(score, rest, word, gap, w)))
                    continue;
                var rules =
                        PrintedDirectionStaff.printedRules(
                                gray, w, h, ownerX * w, staff.bottom(), gap);
                if (rules == null
                        || Math.abs(rules[0] - staff.bottom()) > gap * .5f
                        || Math.abs(rules[1] - gap) > gap * .15f) continue;
                float localTop = rules[0] - rules[1] * 4;
                float observedTop = top;
                if (caption && top < rules[0] + gap * .25f && bottom > rules[0]) {
                    float body = CaptionRuleSeparator.bodyTop(gray, w, h, word, rules[0], rules[1]);
                    if (Float.isFinite(body)) observedTop = body;
                }
                float vertical =
                        bottom <= localTop - gap * .25f
                                ? (localTop - bottom) / gap
                                : observedTop >= rules[0] + gap * .25f
                                        ? (observedTop - rules[0]) / gap
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
        if (ambiguous) return null;
        return best != null
                ? best
                : caption ? null : PrintedMultiRestDirection.find(score, staffs, word, gray, w, h);
    }

    /** A leading direction can precede the centered glyph of an otherwise silent bar. */
    private static boolean measureStartWholeRest(
            ScorePageInterpretation score,
            ScoreRestEvent rest,
            PlayingTechniqueDetector.Word word,
            float gap,
            int width) {
        if (rest.durationBeats() != 4
                || rest.positionInMeasure() < .35f
                || rest.positionInMeasure() > .65f) return false;
        var measure = score.measures().get(rest.measureIndex());
        float center =
                measure.left() + rest.positionInMeasure() * (measure.right() - measure.left());
        if ((word.left() - measure.left()) * width > 2 * gap || word.right() >= center)
            return false;
        for (var n : score.notes())
            if (n.measureIndex() == rest.measureIndex()
                    && n.staffIndex() == rest.staffIndex()
                    && n.staffCount() == rest.staffCount()) return false;
        for (var r : score.rests())
            if (r != rest
                    && r.measureIndex() == rest.measureIndex()
                    && r.staffIndex() == rest.staffIndex()
                    && r.staffCount() == rest.staffCount()) return false;
        return true;
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
        if (target == null)
            return PrintedMultiRestDirection.provedOnset(score, measure, staff, count, position);
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

    static List<Integer> currentTargetIndices(
            ScorePageInterpretation score, int measure, int staff, int count, float position) {
        if (measure < 0 || measure >= score.measures().size()) return List.of();
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < score.rests().size(); i++) {
            var r = score.rests().get(i);
            if (r.measureIndex() == measure
                    && r.staffIndex() == staff
                    && r.staffCount() == count
                    && Math.abs(r.positionInMeasure() - position) <= .018f) indices.add(i);
        }
        return List.copyOf(indices);
    }
}
