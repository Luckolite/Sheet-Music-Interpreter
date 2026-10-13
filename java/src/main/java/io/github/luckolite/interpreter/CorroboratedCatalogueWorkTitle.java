// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Separate work identity, source-bound literal OCR, repeated header, and role preservation gates. */
public final class CorroboratedCatalogueWorkTitle {
    private CorroboratedCatalogueWorkTitle() {}

    public record Selection(
            ScoreCreditsDetector.Result before,
            ScoreCreditsDetector.Result after,
            List<ScoreCreditsDetector.Page> originalPages,
            List<ScoreCreditsDetector.Page> pages,
            OpeningWorkHeading.Evidence sourcePair,
            MusicMetadataCatalogue.Witness catalogueWitness) {}

    private static boolean valid(List<ScoreCreditsDetector.Page> pages) {
        if (pages == null || pages.isEmpty() || pages.size() > 10000) return false;
        for (var page : pages) {
            if (page == null
                    || !Float.isFinite(page.width())
                    || !Float.isFinite(page.height())
                    || !Float.isFinite(page.notationTop())
                    || page.width() <= 0
                    || page.height() <= 0
                    || page.notationTop() < 0
                    || page.notationTop() > page.height()
                    || page.lines().size() > 100000
                    || page.reviewCredits().size() > 100000) return false;
            for (var row : page.lines()) if (!validRow(row, page)) return false;
            for (var review : page.reviewCredits())
                if (review == null || review.value() == null || !validRow(review.line(), page))
                    return false;
        }
        return true;
    }

    private static boolean validRow(ScoreCreditsDetector.Line row, ScoreCreditsDetector.Page page) {
        return row != null
                && row.text() != null
                && Float.isFinite(row.left())
                && Float.isFinite(row.top())
                && Float.isFinite(row.right())
                && Float.isFinite(row.bottom())
                && row.left() >= 0
                && row.top() >= 0
                && row.right() <= page.width()
                && row.bottom() <= page.height()
                && row.right() > row.left()
                && row.bottom() > row.top();
    }

    private static boolean close(String a, String b) {
        a = MusicMetadataCatalogue.key(a);
        b = MusicMetadataCatalogue.key(b);
        if (Math.abs(a.length() - b.length()) > 1 || a.length() < 6 || b.length() < 6) return false;
        if (a.equals(b)) return true;
        if (a.length() == b.length()) {
            int differences = 0;
            for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) differences++;
            return differences == 1;
        }
        if (a.length() > b.length()) {
            String swap = a;
            a = b;
            b = swap;
        }
        int offset = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i + offset)) {
                if (offset != 0 || a.charAt(i) != b.charAt(i + 1)) return false;
                offset = 1;
            }
        }
        return true;
    }

    private static boolean repeatedHeader(
            List<ScoreCreditsDetector.Page> pages,
            ScoreCreditsDetector.Line original,
            String literal) {
        var first = pages.get(0);
        for (int p = 1; p < pages.size(); p++) {
            var next = pages.get(p);
            if (next.creditsOnly() || next.photographicCover()) continue;
            for (var row : next.lines()) {
                if (row.text() == null
                        || !Float.isFinite(row.left())
                        || !Float.isFinite(row.top())
                        || !Float.isFinite(row.right())
                        || !Float.isFinite(row.bottom())
                        || row.left() < 0
                        || row.top() < 0
                        || row.right() > next.width()
                        || row.bottom() > next.height()
                        || row.right() <= row.left()
                        || row.bottom() <= row.top()
                        || row.top() > next.height() * .2f) continue;
                if (Math.abs(row.left() / next.width() - original.left() / first.width()) < .05
                        && Math.abs(row.center() / next.width() - original.center() / first.width())
                                < .1
                        && row.height() / next.width() > original.height() / first.width() * .6
                        && row.height() / next.width() < original.height() / first.width() * 1.5
                        && close(row.text(), literal)) return true;
            }
        }
        return false;
    }

    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> original,
            Set<String> names,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> medium,
            MusicMetadataCatalogue catalogue) {
        return select(hint, original, names, medium, Map.of(), catalogue);
    }

    private static String literal(
            ScoreCreditsDetector.Line row,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> medium,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> secondary) {
        String selected = ScoreHeaderReadConsensus.select(medium.get(row));
        return selected.isEmpty()
                ? CoherentNumberedWorkReadConsensus.select(medium.get(row), secondary.get(row))
                : selected;
    }

    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> original,
            Set<String> names,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> medium,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> secondary,
            MusicMetadataCatalogue catalogue) {
        if (hint == null
                || !valid(original)
                || names == null
                || names.stream().anyMatch(Objects::isNull)
                || medium == null
                || secondary == null
                || catalogue == null
                || !OpeningWorkHeading.hasGeometryNominee(original.get(0))) return null;
        var first = original.get(0);
        if (!first.lines().containsAll(medium.keySet())
                || !first.lines().containsAll(secondary.keySet())) return null;
        // Work ownership must be evaluated independently of the baseline name classifier.
        // A possible name may be reread; an explicit credit may never lose its role label.
        var literalRows = new ArrayList<ScoreCreditsDetector.Line>();
        for (var row : first.lines()) {
            String literal = literal(row, medium, secondary);
            literalRows.add(
                    literal.isEmpty()
                            ? row
                            : new ScoreCreditsDetector.Line(
                                    literal, row.left(), row.top(), row.right(), row.bottom()));
        }
        var merged =
                new ScoreCreditsDetector.Page(
                        first.width(),
                        first.height(),
                        first.notationTop(),
                        literalRows,
                        first.creditsOnly(),
                        first.photographicCover(),
                        first.reviewCredits());
        var pair = OpeningWorkHeading.find(merged, merged.lines(), names);
        if (pair == null) return null;
        var witness = catalogue.corroborate(pair.heading().text(), pair.caption().text());
        if (witness == null) return null;
        int headingIndex = merged.lines().indexOf(pair.heading()),
                captionIndex = merged.lines().indexOf(pair.caption());
        var rawHeading = first.lines().get(headingIndex);
        var rawCaption = first.lines().get(captionIndex);
        if (ScoreCreditsDetector.isPrintedCredit(rawHeading.text())
                || ScoreCreditsDetector.isPrintedCredit(rawCaption.text())) return null;
        if (!close(rawHeading.text(), pair.heading().text())
                || catalogue.isPersonName(rawHeading.text())
                || names.stream()
                        .anyMatch(
                                name ->
                                        MusicMetadataCatalogue.key(name)
                                                .equals(
                                                        MusicMetadataCatalogue.key(
                                                                rawHeading.text())))) return null;
        if (!MusicMetadataCatalogue.preservesCaptionIdentifier(
                        rawCaption.text(), pair.caption().text())
                || !CreditOcrIdentifierGuard.preservesIdentifiers(
                        rawCaption.text(), pair.caption().text())
                || !CreditOcrIdentifierGuard.preservesIdentifiers(
                        rawHeading.text(), pair.heading().text())) return null;
        if (!literal(rawHeading, medium, secondary).equals(pair.heading().text())
                || !literal(rawCaption, medium, secondary).equals(pair.caption().text()))
            return null;
        var before = ScoreCreditsDetector.detect(hint, original, names);
        var selectedHeaders =
                first.lines().stream()
                        .filter(row -> row.text().strip().equals(before.title().strip()))
                        .toList();
        if (selectedHeaders.size() != 1) return null;
        var header = selectedHeaders.get(0);
        String literalHeader = ScoreHeaderReadConsensus.select(medium.get(header));
        if (header.equals(rawHeading)
                || header.equals(rawCaption)
                || literalHeader.isEmpty()
                || !close(header.text(), literalHeader)
                || catalogue.isWorkAlias(header.text())
                || catalogue.isPersonName(header.text())
                || !catalogue.isInstrumentPart(literalHeader)
                || catalogue.isWorkAlias(literalHeader)
                || catalogue.isPersonName(literalHeader)
                || header.height() < rawHeading.height() * 1.1
                || header.top() >= first.height() * .2f
                || !repeatedHeader(original, header, literalHeader)) return null;
        String rawKey = MusicMetadataCatalogue.key(rawHeading.text());
        String literalKey = MusicMetadataCatalogue.key(pair.heading().text());
        // A second row with the same name could own a contributor review independently.
        for (int p = 0; p < original.size(); p++)
            for (int i = 0; i < original.get(p).lines().size(); i++) {
                if (p == 0 && i == headingIndex) continue;
                String rowKey = MusicMetadataCatalogue.key(original.get(p).lines().get(i).text());
                if (rowKey.equals(rawKey) || rowKey.equals(literalKey)) return null;
            }
        var unownedReviews =
                before.unclassifiedCredits().stream()
                        .filter(
                                value -> {
                                    String valueKey = MusicMetadataCatalogue.key(value);
                                    return !valueKey.equals(rawKey) && !valueKey.equals(literalKey);
                                })
                        .toList();
        var lines = new ArrayList<>(first.lines());
        lines.set(headingIndex, pair.heading());
        lines.set(captionIndex, pair.caption());
        var pages = new ArrayList<>(original);
        pages.set(
                0,
                new ScoreCreditsDetector.Page(
                        first.width(),
                        first.height(),
                        first.notationTop(),
                        lines,
                        first.creditsOnly(),
                        first.photographicCover(),
                        first.reviewCredits()));
        var detected = ScoreCreditsDetector.detect(hint, pages, names);
        if (!before.artist().equals(detected.artist())
                || !before.composer().equals(detected.composer())
                || !before.arranger().equals(detected.arranger())) return null;
        var extraReviews = new ArrayList<>(detected.unclassifiedCredits());
        for (var value : unownedReviews) if (!extraReviews.remove(value)) return null;
        if (extraReviews.stream()
                .anyMatch(
                        value ->
                                !MusicMetadataCatalogue.key(value)
                                        .equals(MusicMetadataCatalogue.key(pair.heading().text()))))
            return null;
        var after =
                new ScoreCreditsDetector.Result(
                        pair.title(),
                        before.artist(),
                        before.composer(),
                        before.arranger(),
                        unownedReviews);
        return new Selection(
                before, after, List.copyOf(original), List.copyOf(pages), pair, witness);
    }
}
