// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Assemble two independently captured ends of a printed slur across adjacent pages. */
final class PrintedPageSlurContinuation {
    private static final String SOURCE = "printed-page-slur-edge";

    private record Fragment(
            ScoreSlurPages.Anchor owner, int staff, int count, boolean above, boolean outgoing,
            float phase, ScoreSlurSpan.ArcEvidence arc) {}

    private record Pair(Fragment first, Fragment last) {}

    private PrintedPageSlurContinuation() {}

    static ScoreExpressiveEvent evidence(
            int page, int owner, int staff, int count, boolean above, boolean outgoing,
            float phase, ScoreSlurSpan.ArcEvidence arc) {
        return new ScoreExpressiveEvent(
                SOURCE + ":" + page + ":" + owner + ":" + above + ":" + outgoing,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.empty(), Optional.empty(), ScoreExpressiveEvent.Scope.UNRESOLVED,
                staff, count, Optional.empty(), ScoreExpressiveEvent.Strength.UNSPECIFIED,
                owner + "," + above + "," + outgoing + "," + phase,
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                SOURCE, page, arc.startX(), staff, count, Float.toString(arc.startY())),
                        new ScoreExpressiveEvent.Evidence(
                                SOURCE, page, arc.endX(), staff, count, Float.toString(arc.endY()))));
    }

    static boolean owns(ScoreExpressiveEvent event) {
        return event.eventId().startsWith(SOURCE + ":")
                && event.evidence().size() == 2
                && event.evidence().stream().allMatch(e -> e.sourceId().equals(SOURCE));
    }

    private static Optional<Fragment> decode(
            ScoreExpressiveEvent event, ScorePageInterpretation page) {
        if (!owns(event) || event.kind() != ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                || event.scope() != ScoreExpressiveEvent.Scope.UNRESOLVED
                || event.start().isPresent() || event.end().isPresent()) return Optional.empty();
        var values = event.qualifierText().split(",", -1);
        if (values.length != 4 || !Set.of("true", "false").contains(values[1])
                || !Set.of("true", "false").contains(values[2])) return Optional.empty();
        try {
            int owner = Integer.parseInt(values[0]);
            boolean above = Boolean.parseBoolean(values[1]);
            boolean outgoing = Boolean.parseBoolean(values[2]);
            float phase = Float.parseFloat(values[3]);
            var a = event.evidence().get(0);
            var b = event.evidence().get(1);
            if (owner < 0 || !Float.isFinite(phase) || Math.abs(phase) > 32
                    || a.pageIndex() != b.pageIndex() || a.visualX() >= b.visualX()
                    || a.staffIndex() != event.staffIndex() || b.staffIndex() != event.staffIndex()
                    || a.staffCount() != event.staffCount() || b.staffCount() != event.staffCount())
                return Optional.empty();
            var anchor = new ScoreSlurPages.Anchor(a.pageIndex(), owner);
            int index = page.slurSource().noteOrigins().indexOf(Optional.of(anchor));
            if (index < 0 || index >= page.notes().size()) return Optional.empty();
            var note = page.notes().get(index);
            if (note.staffIndex() != event.staffIndex() || note.staffCount() != event.staffCount()
                    || note.measureIndex() != (outgoing ? page.measures().size() - 1 : 0))
                return Optional.empty();
            return Optional.of(new Fragment(anchor, event.staffIndex(), event.staffCount(), above,
                    outgoing, phase, new ScoreSlurSpan.ArcEvidence(a.pageIndex(), a.visualX(),
                            Float.parseFloat(a.printedText()), b.visualX(),
                            Float.parseFloat(b.printedText()))));
        } catch (IllegalArgumentException invalidEvidence) {
            return Optional.empty();
        }
    }

    private static List<Fragment> fragments(ScorePageInterpretation page) {
        return page.expressiveEvents().stream().filter(PrintedPageSlurContinuation::owns)
                .limit(65).map(e -> decode(e, page)).flatMap(Optional::stream).distinct().toList();
    }

    private static boolean sameDocument(ScorePageInterpretation a, ScorePageInterpretation b) {
        var first = a.clockContextSource().origin();
        var next = b.clockContextSource().origin();
        if (first.documentSha256().isEmpty() || next.documentSha256().isEmpty()) return true;
        return first.documentSha256().equals(next.documentSha256())
                && first.physicalPageIndex() + 1 == next.physicalPageIndex()
                && first.physicalPageCount() == next.physicalPageCount();
    }

    static ScoreSlurSource join(List<ScorePageInterpretation> pages) {
        var base = ScoreSlurSource.join(pages.stream().map(ScorePageInterpretation::slurSource).toList());
        var additions = new ArrayList<ScoreSlurPages.Span>();
        List<Fragment> prior = List.of();
        for (int page = 0; page < pages.size(); page++) {
            var next = fragments(pages.get(page));
            if (page > 0 && prior.size() <= 64 && next.size() <= 64
                    && sameDocument(pages.get(page - 1), pages.get(page))
                    && pages.get(page - 1).firstMeasureNumber() > 0
                    && pages.get(page - 1).firstMeasureNumber() + pages.get(page - 1).measures().size()
                            == pages.get(page).firstMeasureNumber()) {
                var pairs = new ArrayList<Pair>();
                var uses = new HashMap<Fragment, Integer>();
                for (var a : prior)
                    for (var b : next) {
                        if (!a.outgoing() || b.outgoing()
                                || a.owner().pageIndex() + 1 != b.owner().pageIndex()
                                || a.staff() != b.staff() || a.count() != b.count()
                                || a.above() != b.above() || a.phase() * b.phase() <= 0
                                || Math.abs(a.phase() - b.phase()) > 3f)
                            continue;
                        pairs.add(new Pair(a, b));
                        uses.merge(a, 1, Integer::sum);
                        uses.merge(b, 1, Integer::sum);
                    }
                for (var pair : pairs) {
                    var a = pair.first();
                    var b = pair.last();
                    if (uses.get(a) != 1 || uses.get(b) != 1) continue;
                    additions.add(new ScoreSlurPages.Span(
                            SOURCE + ":" + a.owner().pageIndex() + ":" + a.owner().noteIndex()
                                    + ":" + b.owner().noteIndex() + ":" + a.above(),
                            a.owner(), b.owner(),
                            a.above() ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                            ScoreSlurSpan.LineType.SOLID, List.of(a.arc(), b.arc())));
                }
            }
            prior = next;
        }
        if (additions.isEmpty()) return base;
        var candidates = new ArrayList<>(base.candidates());
        candidates.addAll(additions);
        return new ScoreSlurSource(base.spans(),
                pages.stream().flatMap(p -> p.slurSource().noteOrigins().stream()).toList(),
                candidates);
    }
}
