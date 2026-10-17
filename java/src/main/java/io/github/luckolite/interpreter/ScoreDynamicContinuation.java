// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Assemble proven lexical dynamics without treating a physical page edge as their arrival. */
public final class ScoreDynamicContinuation {
    private static final String WORD_SOURCE = "dynamic-word-ocr";

    private ScoreDynamicContinuation() {}

    private static String identity(ScoreDynamicChange change) {
        return "lexical-dynamic:"
                + change.measureIndex()
                + ":"
                + Float.floatToIntBits(change.positionInMeasure())
                + ":"
                + change.staffIndex()
                + ":"
                + change.staffCount()
                + ":"
                + change.direction();
    }

    static ScoreExpressiveEvent evidence(ScoreDynamicChange change, String text, float visualX) {
        // OCR geometry cannot supply a quarter-beat anchor without the assembled meter/rhythm.
        // Preserve the printed word and its row identity; assembly resolves the dynamic range.
        return new ScoreExpressiveEvent(
                identity(change),
                change.direction() > 0
                        ? ScoreExpressiveEvent.Kind.CRESCENDO
                        : ScoreExpressiveEvent.Kind.DIMINUENDO,
                Optional.empty(),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                change.staffIndex(),
                change.staffCount(),
                Optional.empty(),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                text,
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                WORD_SOURCE,
                                0,
                                visualX,
                                change.staffIndex(),
                                change.staffCount(),
                                text)));
    }

    private static boolean lexical(ScoreDynamicChange change, List<ScoreExpressiveEvent> events) {
        for (var event : events)
            if (event.eventId().equals(identity(change))
                    && event.kind()
                            == (change.direction() > 0
                                    ? ScoreExpressiveEvent.Kind.CRESCENDO
                                    : ScoreExpressiveEvent.Kind.DIMINUENDO)
                    && event.staffIndex() == change.staffIndex()
                    && event.staffCount() == change.staffCount()
                    && event.scope() == ScoreExpressiveEvent.Scope.UNRESOLVED
                    && event.start().isEmpty()
                    && event.end().isEmpty()
                    && event.targetEventId().isEmpty()
                    && ScoreDynamicsDetector.textDirection(event.qualifierText())
                            == change.direction()
                    && event.evidence().stream()
                            .anyMatch(
                                    e ->
                                            e.sourceId().equals(WORD_SOURCE)
                                                    && e.staffIndex() == change.staffIndex()
                                                    && e.staffCount() == change.staffCount()
                                                    && ScoreDynamicsDetector.textDirection(
                                                                    e.printedText())
                                                            == change.direction())) return true;
        return false;
    }

    /** Sequential page assembly; printed movement restarts remain separate continuation scopes. */
    public static List<ScoreDynamicChange> join(List<ScorePageInterpretation> pages) {
        int[] offsets = new int[pages.size()];
        for (int p = 1; p < pages.size(); p++)
            offsets[p] = offsets[p - 1] + pages.get(p - 1).measures().size();
        return join(pages, offsets);
    }

    /** Offsets belong to the caller's selected arrangement and may retain an excerpt's origin. */
    public static List<ScoreDynamicChange> join(
            List<ScorePageInterpretation> pages, int[] offsets) {
        Objects.requireNonNull(pages);
        if (offsets.length != pages.size())
            throw new IllegalArgumentException("Page offset count differs");
        var result = new ArrayList<ScoreDynamicChange>();
        for (int p = 0; p < pages.size(); p++) {
            var page = pages.get(p);
            if (offsets[p] < 0) throw new IllegalArgumentException("Negative page offset");
            for (var change : page.dynamicChanges()) {
                var global = change.offset(offsets[p]);
                if (change.direction() != 0
                        && !change.fixedTarget()
                        && change.endMeasureIndex() == page.measures().size() - 1
                        && change.endPosition() == 1
                        && lexical(change, page.expressiveEvents())
                        && !interruptedOnPage(change, page)) {
                    var arrival = arrival(pages, offsets, p, change);
                    if (arrival != null)
                        global =
                                new ScoreDynamicChange(
                                        global.measureIndex(),
                                        global.positionInMeasure(),
                                        global.staffIndex(),
                                        global.staffCount(),
                                        arrival.measureIndex(),
                                        arrival.positionInMeasure(),
                                        global.decibels(),
                                        global.direction(),
                                        global.sharedStaffs(),
                                        global.fixedTarget(),
                                        global.sharedTiming());
                }
                result.add(global);
            }
        }
        return List.copyOf(result);
    }

    private static boolean samePart(ScoreDynamicChange a, ScoreDynamicChange b) {
        return a.staffCount() == b.staffCount()
                && a.staffIndex() == b.staffIndex()
                && a.sharedStaffs() == b.sharedStaffs();
    }

    private static boolean after(ScoreDynamicChange a, ScoreDynamicChange b) {
        return a.measureIndex() > b.measureIndex()
                || a.measureIndex() == b.measureIndex()
                        && a.positionInMeasure() > b.positionInMeasure();
    }

    private static boolean interruptedOnPage(
            ScoreDynamicChange change, ScorePageInterpretation page) {
        return page.dynamicChanges().stream()
                .anyMatch(c -> samePart(change, c) && after(c, change));
    }

    private static boolean topology(ScorePageInterpretation page, ScoreDynamicChange change) {
        // Require observed ownership on each intervening page, never borrow another arrangement.
        boolean found = false, paired = !change.sharedStaffs();
        for (var note : page.notes()) {
            if (note.staffCount() != change.staffCount()) return false;
            if (note.staffIndex() == change.staffIndex()) found = true;
            if (note.staffIndex() == change.staffIndex() + 1) paired = true;
        }
        for (var rest : page.rests()) {
            if (rest.staffCount() != change.staffCount()) return false;
            if (rest.staffIndex() == change.staffIndex()) found = true;
            if (rest.staffIndex() == change.staffIndex() + 1) paired = true;
        }
        return found && paired;
    }

    private static ScoreDynamicChange arrival(
            List<ScorePageInterpretation> pages,
            int[] offsets,
            int from,
            ScoreDynamicChange change) {
        for (int p = from + 1; p < pages.size(); p++) {
            var previous = pages.get(p - 1);
            var page = pages.get(p);
            if (page.measures().isEmpty()
                    || offsets[p] != offsets[p - 1] + previous.measures().size()
                    || page.firstMeasureNumber() > 0
                            && previous.firstMeasureNumber() > 0
                            && page.firstMeasureNumber() <= previous.firstMeasureNumber()
                    || !topology(page, change)) return null;
            // A private level in one hand makes a shared keyboard arrival ambiguous.
            if (change.sharedStaffs()
                    && page.dynamicChanges().stream()
                            .anyMatch(
                                    c ->
                                            c.staffCount() == change.staffCount()
                                                    && !c.sharedStaffs()
                                                    && c.staffIndex() >= change.staffIndex()
                                                    && c.staffIndex() <= change.staffIndex() + 1))
                return null;
            var next =
                    page.dynamicChanges().stream()
                            .filter(
                                    c ->
                                            samePart(change, c)
                                                    && c.measureIndex() < page.measures().size())
                            .min(
                                    Comparator.comparingInt(ScoreDynamicChange::measureIndex)
                                            .thenComparingDouble(
                                                    ScoreDynamicChange::positionInMeasure)
                                            .thenComparingInt(c -> c.direction() == 0 ? 0 : 1));
            if (next.isPresent()) return next.get().offset(offsets[p]);
        }
        return null;
    }

    /** Namespace generated page-local evidence while preserving independently authored semantics. */
    public static ScoreExpressiveEvent offsetEvidence(
            ScoreExpressiveEvent event, int offset, int page) {
        if (ScoreArpeggioNotation.owns(event))
            return ScoreArpeggioNotation.offsetEvidence(event, offset, page);
        if (ScorePedalDetector.owns(event))
            return ScorePedalDetector.offsetEvidence(event, offset, page);
        if (ScoreRestFermataDetector.owns(event))
            return ScoreRestFermataDetector.offsetEvidence(event, offset, page);
        if (ScoreExpressionDetector.owns(event))
            return ScoreExpressionDetector.offsetEvidence(event, offset, page);
        if (ScoreFermataDetector.owns(event))
            return ScoreFermataDetector.offsetEvidence(event, offset, page);
        if (!event.eventId().startsWith("lexical-dynamic:")
                || event.evidence().stream().noneMatch(e -> e.sourceId().equals(WORD_SOURCE)))
            return event.offset(offset);
        return new ScoreExpressiveEvent(
                "page:" + page + "/" + event.eventId(),
                event.kind(),
                event.start().map(a -> a.offset(offset)),
                event.end().map(a -> a.offset(offset)),
                event.scope(),
                event.staffIndex(),
                event.staffCount(),
                event.targetEventId(),
                event.strength(),
                event.qualifierText(),
                event.evidence().stream()
                        .map(
                                e ->
                                        new ScoreExpressiveEvent.Evidence(
                                                e.sourceId(),
                                                page,
                                                e.visualX(),
                                                e.staffIndex(),
                                                e.staffCount(),
                                                e.printedText()))
                        .toList());
    }
}
