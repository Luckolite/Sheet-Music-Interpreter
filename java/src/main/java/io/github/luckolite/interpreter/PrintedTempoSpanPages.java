// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Resolves an open extension only against the immediately adjacent page's proved opening tempo. */
final class PrintedTempoSpanPages {
    static List<ScorePageInterpretation> resolve(List<ScorePageInterpretation> pages) {
        var result = new ArrayList<>(pages);
        double carried = Double.NaN;
        for (int i = 0; i < pages.size(); i++) {
            var page = pages.get(i);
            if (page == null || page.measures().isEmpty()) {
                carried = Double.NaN;
                continue;
            }
            var marks =
                    page.tempoChanges().stream()
                            .sorted(
                                    Comparator.comparingInt(ScoreTempoChange::measureIndex)
                                            .thenComparingDouble(
                                                    ScoreTempoChange::positionInMeasure))
                            .toList();
            var next = i + 1 < pages.size() ? pages.get(i + 1) : null;
            boolean adjacent =
                    next != null
                            && !next.measures().isEmpty()
                            && !(page.firstMeasureNumber() > 0
                                    && next.firstMeasureNumber() > 0
                                    && page.firstMeasureNumber() + page.measures().size()
                                            != next.firstMeasureNumber());
            var openings =
                    adjacent
                            ? next.tempoChanges().stream()
                                    .filter(
                                            t ->
                                                    t.measureIndex() == 0
                                                            && t.positionInMeasure() == 0)
                                    .toList()
                            : List.<ScoreTempoChange>of();
            long openCount =
                    page.expressiveEvents().stream().filter(PrintedTempoSpans::hasOpen).count();
            if (openCount == 1
                    && !openings.isEmpty()
                    && openings.stream().map(ScoreTempoChange::bpm).distinct().count() == 1) {
                var events = new ArrayList<ScoreExpressiveEvent>();
                for (var event : page.expressiveEvents()) {
                    var measure = ScoreExpressionDetector.measureOf(event);
                    if (!PrintedTempoSpans.hasOpen(event) || measure.isEmpty()) {
                        events.add(event);
                        continue;
                    }
                    int delta = edgeDelta(event);
                    double current = carried;
                    current =
                            latestTempo(
                                    marks.stream()
                                            .filter(
                                                    mark ->
                                                            mark.measureIndex() < measure.getAsInt()
                                                                    || mark.measureIndex()
                                                                                    == measure
                                                                                            .getAsInt()
                                                                            && mark
                                                                                            .positionInMeasure()
                                                                                    == 0)
                                            .toList(),
                                    carried);
                    boolean faster =
                            event.kind() == Kind.ACCELERANDO || PrintedTempoSpans.bareFaster(event);
                    boolean slower =
                            event.kind() == Kind.RITARDANDO || event.kind() == Kind.RALLENTANDO;
                    double target = openings.get(0).bpm();
                    boolean intervening =
                            marks.stream()
                                    .anyMatch(
                                            t ->
                                                    t.measureIndex() > measure.getAsInt()
                                                            || t.measureIndex()
                                                                            == measure.getAsInt()
                                                                    && t.positionInMeasure() > 0);
                    intervening |=
                            page.expressiveEvents().stream()
                                    .anyMatch(
                                            e ->
                                                    e != event
                                                            && Set.of(
                                                                            Kind.ACCELERANDO,
                                                                            Kind.RITARDANDO,
                                                                            Kind.RALLENTANDO,
                                                                            Kind.RITENUTO,
                                                                            Kind.A_TEMPO,
                                                                            Kind.TEMPO_PRIMO,
                                                                            Kind.METRIC_MODULATION)
                                                                    .contains(e.kind())
                                                            && ScoreExpressionDetector.measureOf(e)
                                                                            .orElse(-1)
                                                                    >= measure.getAsInt());
                    if (delta <= 0
                            || measure.getAsInt() + delta != page.measures().size()
                            || !Double.isFinite(current)
                            || intervening
                            || !(faster && target > current || slower && target < current)) {
                        events.add(event);
                        continue;
                    }
                    var evidence = new ArrayList<Evidence>();
                    for (var e : event.evidence())
                        if (!e.sourceId().startsWith(PrintedTempoSpans.OPEN_HINT)) evidence.add(e);
                    var first = event.evidence().get(0);
                    evidence.add(
                            new Evidence(
                                    "printed-tempo-dash-span",
                                    first.pageIndex(),
                                    first.visualX(),
                                    event.staffIndex(),
                                    event.staffCount(),
                                    first.printedText()));
                    evidence.add(
                            new Evidence(
                                    PrintedTempoSpans.END_HINT + delta,
                                    first.pageIndex(),
                                    first.visualX(),
                                    event.staffIndex(),
                                    event.staffCount(),
                                    first.printedText()));
                    evidence.add(
                            new Evidence(
                                    "printed-tempo-dash-adjacent-page-v1",
                                    first.pageIndex(),
                                    first.visualX(),
                                    event.staffIndex(),
                                    event.staffCount(),
                                    first.printedText()));
                    events.add(
                            new ScoreExpressiveEvent(
                                    event.eventId(),
                                    faster ? Kind.ACCELERANDO : event.kind(),
                                    event.start(),
                                    event.start().isPresent()
                                            ? Optional.of(
                                                    new ScoreAnchor(page.measures().size(), 0))
                                            : Optional.empty(),
                                    event.start().isPresent() ? Scope.SCORE : Scope.UNRESOLVED,
                                    event.staffIndex(),
                                    event.staffCount(),
                                    event.targetEventId(),
                                    event.strength(),
                                    event.qualifierText(),
                                    evidence));
                }
                result.set(i, page.withExpressiveEvents(events));
            }
            carried = latestTempo(marks, carried);
            if (!adjacent) carried = Double.NaN;
        }
        return Collections.unmodifiableList(result);
    }

    private static double latestTempo(List<ScoreTempoChange> marks, double fallback) {
        if (marks.isEmpty()) return fallback;
        var last = marks.get(marks.size() - 1);
        return marks.stream()
                                .filter(
                                        t ->
                                                t.measureIndex() == last.measureIndex()
                                                        && t.positionInMeasure()
                                                                == last.positionInMeasure())
                                .map(ScoreTempoChange::bpm)
                                .distinct()
                                .count()
                        == 1
                ? last.bpm()
                : Double.NaN;
    }

    private static int edgeDelta(ScoreExpressiveEvent event) {
        Integer found = null;
        for (var e : event.evidence())
            if (e.sourceId().startsWith(PrintedTempoSpans.OPEN_HINT)) {
                String value = e.sourceId().substring(PrintedTempoSpans.OPEN_HINT.length());
                if (!value.matches("[1-9][0-9]{0,3}")) return -1;
                int n = Integer.parseInt(value);
                if (found != null && found != n) return -1;
                found = n;
            }
        return found == null ? -1 : found;
    }
}
