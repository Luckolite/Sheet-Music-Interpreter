/* SPDX-License-Identifier: Apache-2.0 */
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact repeated readings of the same original header row; no spelling guesses. */
public final class ScoreHeaderReadConsensus {
    private ScoreHeaderReadConsensus() {}

    public record Reading(
            int scale, String mode, double widthFactor, String text, double confidence) {}

    public static List<ScoreCreditsDetector.Line> candidates(ScoreCreditsDetector.Page p) {
        if (p == null
                || p.creditsOnly()
                || !Float.isFinite(p.width())
                || !Float.isFinite(p.height())
                || p.width() <= 0
                || p.height() <= 0) return List.of();
        var protectedCredits =
                new HashSet<>(
                        ScoreCreditsDetector.detect("", List.of(p), Set.of())
                                .unclassifiedCredits());
        return p.lines().stream()
                .filter(
                        l ->
                                l.text() != null
                                        && singleLine(l.text())
                                        && l.text().length() >= 4
                                        && !protectedCredits.contains(
                                                l.text().strip().replaceAll("\\h+", " "))
                                        && !l.text()
                                                .strip()
                                                .matches(
                                                        "(?iu)^(?:by\\b|(?:music|composed|arranged|performed|transcribed|adapted|edited|words|lyrics)\\h+by\\b).*")
                                        && Float.isFinite(l.left())
                                        && Float.isFinite(l.top())
                                        && Float.isFinite(l.right())
                                        && Float.isFinite(l.bottom())
                                        && l.left() >= 0
                                        && l.top() >= 0
                                        && l.right() > l.left()
                                        && l.right() <= p.width()
                                        && l.bottom() > l.top()
                                        && l.bottom() <= p.height()
                                        && p.reviewCredits().stream()
                                                .noneMatch(r -> r.line().equals(l))
                                        && l.top()
                                                < p.height() * (p.photographicCover() ? .85f : .25f)
                                        && l.bottom() - l.top() >= p.width() * .01f)
                .sorted(
                        Comparator.comparingDouble(ScoreCreditsDetector.Line::top)
                                .thenComparingDouble(ScoreCreditsDetector.Line::left))
                .limit(10)
                .toList();
    }

    private static boolean singleLine(String s) {
        return s.codePoints()
                .noneMatch(
                        c ->
                                Character.isISOControl(c)
                                        || Character.getType(c) == Character.FORMAT
                                        || c == 0x2028
                                        || c == 0x2029);
    }

    public static String select(List<Reading> readings) {
        if (readings == null || readings.size() != 12) return "";
        var views = new HashSet<String>();
        var groups = new LinkedHashMap<String, List<Reading>>();
        for (var r : readings) {
            if (r == null
                    || r.text() == null
                    || !singleLine(r.text())
                    || r.mode() == null
                    || !Double.isFinite(r.confidence())
                    || r.confidence() < 0
                    || r.confidence() > 1
                    || !Set.of(1, 2, 4).contains(r.scale())
                    || !Set.of("tight", "horizontal").contains(r.mode())
                    || !(r.widthFactor() == 1 || r.widthFactor() == 1.5)) return "";
            if (!views.add(r.scale() + ":" + r.mode() + ":" + r.widthFactor())) return "";
            String s = r.text().strip().replaceAll("\\h+", " ");
            if (!s.isEmpty()) groups.computeIfAbsent(s, k -> new ArrayList<>()).add(r);
        }
        for (var e : groups.entrySet()) {
            if (e.getValue().size() <= readings.size() / 2) continue;
            if (e.getValue().stream()
                            .filter(r -> r.confidence() >= .9)
                            .map(Reading::scale)
                            .distinct()
                            .count()
                    >= 2) return e.getKey();
        }
        return "";
    }

    public static ScoreCreditsDetector.Page merge(
            ScoreCreditsDetector.Page page,
            Map<ScoreCreditsDetector.Line, List<Reading>> readings) {
        if (page == null || readings == null || readings.isEmpty()) return page;
        var eligible = candidates(page);
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        for (var line : page.lines()) {
            String text = eligible.contains(line) ? select(readings.get(line)) : "";
            lines.add(
                    text.isEmpty()
                            ? line
                            : new ScoreCreditsDetector.Line(
                                    text, line.left(), line.top(), line.right(), line.bottom()));
        }
        return new ScoreCreditsDetector.Page(
                page.width(),
                page.height(),
                page.notationTop(),
                lines,
                page.creditsOnly(),
                page.photographicCover(),
                page.reviewCredits());
    }
}
