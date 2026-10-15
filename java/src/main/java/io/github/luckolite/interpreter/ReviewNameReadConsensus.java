// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.text.Normalizer;

/** Repeated literal readings of unresolved names; never infers a credit role. */
public final class ReviewNameReadConsensus {
    public record Selection(String text, List<ScoreHeaderReadConsensus.Reading> readings) {}

    static String clean(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .strip()
                .replaceFirst("^[-–—]\\h*", "")
                .replaceAll("\\h+", " ")
                .replaceAll("\\h*/\\h*", " / ");
    }

    static String words(String text) {
        return clean(text).replace(" / ", " ").toLowerCase(Locale.ROOT);
    }

    static boolean name(String text) {
        return text.matches(
                "[\\p{L}][\\p{L}’'\\-]*(?: [\\p{L}][\\p{L}’'\\-]*){1,5}(?: / [\\p{L}][\\p{L}’'\\-]*(?: [\\p{L}][\\p{L}’'\\-]*){1,5})?");
    }

    public static Selection select(List<ScoreHeaderReadConsensus.Reading> rows) {
        return select(rows, false);
    }

    private static Selection select(
            List<ScoreHeaderReadConsensus.Reading> rows, boolean coverSurname) {
        if (rows == null || rows.size() != 12) return null;
        var views = new HashSet<String>();
        var groups = new LinkedHashMap<String, List<ScoreHeaderReadConsensus.Reading>>();
        for (var r : rows) {
            if (r == null
                    || r.text() == null
                    || r.text()
                            .codePoints()
                            .anyMatch(
                                    c ->
                                            Character.isISOControl(c)
                                                    || Character.getType(c) == Character.FORMAT
                                                    || c == 0x2028
                                                    || c == 0x2029)
                    || !Set.of(1, 2, 4).contains(r.scale())
                    || r.mode() == null
                    || !Set.of("tight", "horizontal").contains(r.mode())
                    || !(r.widthFactor() == 1 || r.widthFactor() == 1.5)
                    || !Double.isFinite(r.confidence())
                    || r.confidence() < 0
                    || r.confidence() > 1
                    || !views.add(r.scale() + ":" + r.mode() + ":" + r.widthFactor())) return null;
            if (name(clean(r.text()))
                    || (coverSurname && clean(r.text()).matches("[\\p{L}][\\p{L}’'-]{2,79}")))
                groups.computeIfAbsent(words(r.text()), k -> new ArrayList<>()).add(r);
        }
        for (var g : groups.values()) {
            if (g.size() < 9
                    || g.stream()
                                    .filter(r -> r.confidence() >= .9)
                                    .map(ScoreHeaderReadConsensus.Reading::scale)
                                    .distinct()
                                    .count()
                            != 3) continue;
            // A separator must be printed repeatedly, at the same word boundary; never invent it.
            var literal = new LinkedHashMap<String, List<ScoreHeaderReadConsensus.Reading>>();
            for (var r : g)
                if (r.confidence() >= .9)
                    literal.computeIfAbsent(
                                    clean(r.text()).toLowerCase(Locale.ROOT),
                                    k -> new ArrayList<>())
                            .add(r);
            var accepted = new ArrayList<List<ScoreHeaderReadConsensus.Reading>>();
            for (var v : literal.values())
                if (v.size() >= 4
                        && v.stream()
                                        .map(ScoreHeaderReadConsensus.Reading::scale)
                                        .distinct()
                                        .count()
                                >= 2
                        && v.stream().map(ScoreHeaderReadConsensus.Reading::mode).distinct().count()
                                == 2) accepted.add(v);
            var separators =
                    accepted.stream().filter(v -> clean(v.get(0).text()).contains(" / ")).toList();
            if (separators.size() > 1) return null;
            List<ScoreHeaderReadConsensus.Reading> chosen =
                    separators.size() == 1
                            ? separators.get(0)
                            : accepted.size() == 1 ? accepted.get(0) : null;
            if (chosen == null) return null;
            return new Selection(clean(chosen.get(0).text()), List.copyOf(rows));
        }
        return null;
    }

    private static boolean producerLine(String text) {
        return clean(text).matches("(?iu)^produced\\h+by\\h+.+$");
    }

    private static String reviewCredit(String text) {
        return text.strip().replaceAll("\\h+", " ").replaceFirst("(?iu)^produced\\h+by\\h+", "");
    }

    public static List<ScoreCreditsDetector.Line> candidates(ScoreCreditsDetector.Page p) {
        var cover = ScoreCreditsDetector.coverWorkCreditCandidates(p);
        if (!cover.isEmpty()) return cover;
        if (p == null
                || p.photographicCover()
                || p.creditsOnly()
                || !Float.isFinite(p.width())
                || !Float.isFinite(p.height())
                || !Float.isFinite(p.notationTop())
                || p.width() <= 0
                || p.height() <= 0
                || p.notationTop() <= 0) return List.of();
        var result = ScoreCreditsDetector.detect("", List.of(p), Set.of());
        return p.lines().stream()
                .filter(
                        l ->
                                l.text() != null
                                        && result.unclassifiedCredits()
                                                .contains(reviewCredit(l.text()))
                                        && Float.isFinite(l.left())
                                        && Float.isFinite(l.top())
                                        && Float.isFinite(l.right())
                                        && Float.isFinite(l.bottom())
                                        && l.left() >= 0
                                        && l.right() <= p.width()
                                        && l.right() > l.left()
                                        && l.top() >= 0
                                        && l.bottom() > l.top()
                                        && l.bottom() < p.notationTop()
                                        && l.top() < p.height() * .3f
                                        && l.bottom() - l.top() >= p.width() * .008f
                                        && l.bottom() - l.top() < p.width() * .06f
                                        && p.reviewCredits().stream()
                                                .noneMatch(r -> r.line().equals(l)))
                .limit(4)
                .toList();
    }

    private static String selectedText(
            ScoreCreditsDetector.Page page, ScoreCreditsDetector.Line line, Selection selection) {
        return ScoreCreditsDetector.coverWorkCreditCandidates(page).contains(line)
                ? selection.text().toUpperCase(Locale.ROOT)
                : selection.text();
    }

    public static ScoreCreditsDetector.Page merge(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line line,
            List<ScoreHeaderReadConsensus.Reading> rows) {
        if (!candidates(page).contains(line)) return page;
        var selection =
                select(rows, ScoreCreditsDetector.coverWorkCreditCandidates(page).contains(line));
        if (selection == null || producerLine(line.text()) != producerLine(selection.text()))
            return page;
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        for (var l : page.lines())
            lines.add(
                    l.equals(line)
                            ? new ScoreCreditsDetector.Line(
                                    selectedText(page, line, selection),
                                    l.left(),
                                    l.top(),
                                    l.right(),
                                    l.bottom())
                            : l);
        var changed =
                new ScoreCreditsDetector.Page(
                        page.width(),
                        page.height(),
                        page.notationTop(),
                        lines,
                        page.creditsOnly(),
                        page.photographicCover(),
                        page.reviewCredits());
        var before = ScoreCreditsDetector.detect("", List.of(page), Set.of());
        var after = ScoreCreditsDetector.detect("", List.of(changed), Set.of());
        if (!before.title().equals(after.title())
                || !before.artist().equals(after.artist())
                || !before.composer().equals(after.composer())
                || !before.arranger().equals(after.arranger())
                || !before.creditConflicts().equals(after.creditConflicts())) return page;
        var expected = new LinkedHashSet<>(before.unclassifiedCredits());
        expected.remove(reviewCredit(line.text()));
        expected.add(reviewCredit(selectedText(page, line, selection)));
        return expected.equals(new LinkedHashSet<>(after.unclassifiedCredits())) ? changed : page;
    }

    public record Proposal(
            int pageIndex,
            ScoreCreditsDetector.Page source,
            ScoreCreditsDetector.Line line,
            List<ScoreHeaderReadConsensus.Reading> readings) {
        public Proposal {
            readings = List.copyOf(readings);
        }
    }

    public record DocumentSelection(
            List<ScoreCreditsDetector.Page> pages,
            ScoreCreditsDetector.Result before,
            ScoreCreditsDetector.Result after,
            List<Proposal> accepted) {
        public DocumentSelection {
            pages = List.copyOf(pages);
            accepted = List.copyOf(accepted);
        }
    }

    public static DocumentSelection selectDocument(
            String hint, List<ScoreCreditsDetector.Page> pages, List<Proposal> proposals) {
        var before = ScoreCreditsDetector.detect(hint, pages, Set.of());
        var current = before;
        var selected = new ArrayList<>(pages);
        var accepted = new ArrayList<Proposal>();
        for (var p : proposals) {
            if (p.pageIndex() < 0
                    || p.pageIndex() >= pages.size()
                    || !pages.get(p.pageIndex()).equals(p.source())) continue;
            var changed = merge(selected.get(p.pageIndex()), p.line(), p.readings());
            if (changed.equals(selected.get(p.pageIndex()))) continue;
            var candidate = new ArrayList<>(selected);
            candidate.set(p.pageIndex(), changed);
            var after = ScoreCreditsDetector.detect(hint, candidate, Set.of());
            if (!current.title().equals(after.title())
                    || !current.artist().equals(after.artist())
                    || !current.composer().equals(after.composer())
                    || !current.arranger().equals(after.arranger())
                    || !current.creditConflicts().equals(after.creditConflicts())) continue;
            var expected = new LinkedHashSet<>(current.unclassifiedCredits());
            String original = reviewCredit(p.line().text());
            if (!expected.contains(original)) continue;
            boolean stillPrinted =
                    candidate.stream()
                            .flatMap(page -> page.lines().stream())
                            .anyMatch(line -> reviewCredit(line.text()).equals(original));
            if (!stillPrinted || !after.unclassifiedCredits().contains(original))
                expected.remove(original);
            expected.add(
                    reviewCredit(
                            selectedText(
                                    selected.get(p.pageIndex()),
                                    p.line(),
                                    select(
                                            p.readings(),
                                            ScoreCreditsDetector.coverWorkCreditCandidates(
                                                            selected.get(p.pageIndex()))
                                                    .contains(p.line())))));
            if (!expected.equals(new LinkedHashSet<>(after.unclassifiedCredits()))) continue;
            selected = candidate;
            current = after;
            accepted.add(p);
        }
        return new DocumentSelection(selected, before, current, accepted);
    }
}
