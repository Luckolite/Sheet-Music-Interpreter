// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.*;

/** Literal OCR can corroborate a caller hint; a hint alone never supplies title characters. */
public final class CorroboratedPrimaryTitle {
    private CorroboratedPrimaryTitle() {}

    private static boolean singleLine(String s) {
        return s.codePoints()
                .noneMatch(
                        c ->
                                Character.isISOControl(c)
                                        || Character.getType(c) == Character.FORMAT
                                        || c == 0x2028
                                        || c == 0x2029);
    }

    private static String clean(String s) {
        return s.strip().replaceAll("\\h+", " ");
    }

    private static String key(String s) {
        return Normalizer.normalize(clean(s), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\h+", "");
    }

    private static boolean valid(
            List<ScoreCreditsDetector.Page> pages, String hint, Set<String> names) {
        if (pages == null
                || pages.isEmpty()
                || pages.size() > 10000
                || hint == null
                || names == null
                || names.stream().anyMatch(Objects::isNull)) return false;
        for (var p : pages) {
            if (p == null
                    || !Float.isFinite(p.width())
                    || !Float.isFinite(p.height())
                    || !Float.isFinite(p.notationTop())
                    || p.width() <= 0
                    || p.height() <= 0
                    || p.notationTop() < -1
                    || p.notationTop() > p.height()) return false;
            for (var l : p.lines()) {
                if (l == null
                        || l.text() == null
                        || !Float.isFinite(l.left())
                        || !Float.isFinite(l.top())
                        || !Float.isFinite(l.right())
                        || !Float.isFinite(l.bottom())
                        || l.right() <= l.left()
                        || l.bottom() <= l.top()) return false;
            }
            for (var r : p.reviewCredits())
                if (r == null || r.line() == null || r.value() == null) return false;
        }
        return true;
    }

    /** Nominate only a damaged, already selected title, never an arbitrary name or subtitle. */
    public static ScoreCreditsDetector.Line nominate(
            String hint, List<ScoreCreditsDetector.Page> pages, Set<String> names) {
        if (!valid(pages, hint, names) || clean(hint).length() < 4) return null;
        var before = ScoreCreditsDetector.detect(hint, pages, names);
        var eligible =
                ScoreHeaderReadConsensus.candidates(pages.get(0)).stream()
                        .filter(l -> clean(l.text()).equals(clean(before.title())))
                        .filter(l -> !key(l.text()).equals(key(hint)))
                        .filter(l -> l.text().matches("(?s).*(?:\\p{L}\\p{N}|\\p{N}\\p{L}|[°º]).*"))
                        .toList();
        return eligible.size() == 1 ? eligible.get(0) : null;
    }

    public record Selection(
            ScoreCreditsDetector.Result before,
            ScoreCreditsDetector.Result after,
            List<ScoreCreditsDetector.Page> originalPages,
            List<ScoreCreditsDetector.Page> pages,
            ScoreCreditsDetector.Line originalHeading,
            String literalTitle) {}

    /** Repeated original-pixel readings must supply every non-space character in the hint. */
    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> original,
            Set<String> names,
            List<ScoreHeaderReadConsensus.Reading> medium,
            List<ScoreHeaderReadConsensus.Reading> secondary) {
        var heading = nominate(hint, original, names);
        if (heading == null) return null;
        String literal = ScoreHeaderReadConsensus.select(medium);
        if (literal.isEmpty()
                || !key(literal).equals(key(hint))
                || key(literal).length() < key(heading.text()).length() * .8
                || key(literal).length() > key(heading.text()).length() * 1.5) return null;
        // Spacing also needs literal OCR evidence from at least two scales of another reader.
        if (!clean(literal).equalsIgnoreCase(clean(hint)) && secondary != null) {
            var views = new HashSet<String>();
            var spacedScales = new HashSet<Integer>();
            if (secondary.size() != 12) return null;
            for (var r : secondary) {
                if (r == null
                        || r.text() == null
                        || !singleLine(r.text())
                        || r.mode() == null
                        || !Set.of(1, 2, 4).contains(r.scale())
                        || !Set.of("tight", "horizontal").contains(r.mode())
                        || !(r.widthFactor() == 1 || r.widthFactor() == 1.5)
                        || !Double.isFinite(r.confidence())
                        || r.confidence() < 0
                        || r.confidence() > 1
                        || !views.add(r.scale() + ":" + r.mode() + ":" + r.widthFactor()))
                    return null;
                if (r.confidence() >= .8 && clean(r.text()).equalsIgnoreCase(clean(hint)))
                    spacedScales.add(r.scale());
            }
            if (spacedScales.size() >= 2) {
                literal =
                        secondary.stream()
                                .filter(
                                        r ->
                                                r.confidence() >= .8
                                                        && clean(r.text())
                                                                .equalsIgnoreCase(clean(hint)))
                                .findFirst()
                                .orElseThrow()
                                .text();
            }
        }
        var p = original.get(0);
        var lines = new ArrayList<>(p.lines());
        int index = lines.indexOf(heading);
        lines.set(
                index,
                new ScoreCreditsDetector.Line(
                        clean(literal),
                        heading.left(),
                        heading.top(),
                        heading.right(),
                        heading.bottom()));
        var pages = new ArrayList<>(original);
        pages.set(
                0,
                new ScoreCreditsDetector.Page(
                        p.width(),
                        p.height(),
                        p.notationTop(),
                        lines,
                        p.creditsOnly(),
                        p.photographicCover(),
                        p.reviewCredits()));
        var before = ScoreCreditsDetector.detect(hint, original, names);
        var after = ScoreCreditsDetector.detect(hint, pages, names);
        if (!clean(after.title()).equalsIgnoreCase(clean(literal))
                || !before.artist().equals(after.artist())
                || !before.composer().equals(after.composer())
                || !before.arranger().equals(after.arranger())
                || !new HashSet<>(after.unclassifiedCredits())
                        .containsAll(before.unclassifiedCredits())) return null;
        return new Selection(
                before, after, List.copyOf(original), List.copyOf(pages), heading, clean(literal));
    }
}
