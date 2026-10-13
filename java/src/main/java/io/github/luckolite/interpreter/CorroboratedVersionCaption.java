// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Recover a nearby version caption from literal original-pixel readings of two readers. */
public final class CorroboratedVersionCaption {
    private CorroboratedVersionCaption() {}

    private static final Pattern CAPTION =
            Pattern.compile(
                    "(?iu)^(?:(?:live|concert)\\h+performance|solo|easy|original|piano|violin|guitar|tavern|epic|wedding|concert)\\h+version(?:\\h+[0-9]+)?$");

    private static boolean singleLine(String text) {
        return text != null
                && text.codePoints()
                        .noneMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT
                                                || c == 0x2028
                                                || c == 0x2029);
    }

    private static String clean(String text) {
        return text.strip().replaceAll("\\h+", " ");
    }

    private static String decorated(String text) {
        return clean(text).replaceAll("^[~.\\-–—\\h]+|[~.\\-–—\\h]+$", "").strip();
    }

    private static String key(String text) {
        return Normalizer.normalize(clean(text), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static int distance(String a, String b) {
        int[] prior = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prior[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1];
            next[0] = i;
            for (int j = 1; j <= b.length(); j++)
                next[j] =
                        Math.min(
                                Math.min(next[j - 1] + 1, prior[j] + 1),
                                prior[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            prior = next;
        }
        return prior[b.length()];
    }

    private static boolean validLine(ScoreCreditsDetector.Line line) {
        return line != null
                && singleLine(line.text())
                && line.text().length() <= 1000
                && Float.isFinite(line.left())
                && Float.isFinite(line.top())
                && Float.isFinite(line.right())
                && Float.isFinite(line.bottom())
                && line.right() > line.left()
                && line.bottom() > line.top();
    }

    private static boolean valid(
            List<ScoreCreditsDetector.Page> pages, String hint, Set<String> names) {
        if (pages == null
                || pages.isEmpty()
                || pages.size() > 10000
                || !singleLine(hint)
                || names == null
                || names.stream().anyMatch(n -> !singleLine(n) || n.length() > 1000)) return false;
        for (var page : pages) {
            if (page == null
                    || !Float.isFinite(page.width())
                    || !Float.isFinite(page.height())
                    || !Float.isFinite(page.notationTop())
                    || page.width() <= 0
                    || page.height() <= 0
                    || page.notationTop() < -1
                    || page.notationTop() > page.height()
                    || page.lines().size() > 10000
                    || page.reviewCredits().size() > 10000) return false;
            for (var line : page.lines()) if (!validLine(line)) return false;
            for (var review : page.reviewCredits())
                if (review == null || !validLine(review.line()) || !singleLine(review.value()))
                    return false;
        }
        return true;
    }

    private static boolean overlap(ScoreCreditsDetector.Line a, ScoreCreditsDetector.Line b) {
        return Math.min(a.right(), b.right()) > Math.max(a.left(), b.left())
                && Math.min(a.bottom(), b.bottom()) > Math.max(a.top(), b.top());
    }

    private static boolean damagedCaption(String text) {
        String value = key(decorated(text));
        if (value.length() > 80 || CAPTION.matcher(value).matches()) return false;
        String[] words = value.split(" ");
        int end = words.length;
        if (end > 0 && words[end - 1].matches("[0-9]+")) end--;
        if (end < 2 || end > 3 || distance(words[end - 1], "version") > 2) return false;
        String prefix = String.join(" ", Arrays.copyOf(words, end - 1));
        // Damage to the type word only nominates a bounded source row. A type is
        // accepted later only when literal readings from both models confirm it.
        return Arrays.stream(prefix.split(" "))
                .allMatch(word -> word.matches("[\\p{L}][\\p{L}'’–-]{1,31}"));
    }

    public record Nomination(
            ScoreCreditsDetector.Line heading, ScoreCreditsDetector.Line caption) {}

    public static Nomination nominate(
            String hint, List<ScoreCreditsDetector.Page> pages, Set<String> names) {
        if (!valid(pages, hint, names)) return null;
        var page = pages.get(0);
        if (page.creditsOnly()) return null;
        var before = ScoreCreditsDetector.detect(hint, pages, names);
        var known = new HashSet<String>();
        for (String name : names) known.add(key(name));
        var eligible = ScoreHeaderReadConsensus.candidates(page);
        var headings =
                eligible.stream()
                        .filter(l -> clean(l.text()).equals(clean(before.title())))
                        .filter(l -> !known.contains(key(l.text())))
                        .toList();
        if (headings.size() != 1) return null;
        var heading = headings.get(0);
        if (heading.height() < page.width() * .025f) return null;
        var captions = new ArrayList<ScoreCreditsDetector.Line>();
        for (var line : page.lines()) {
            if (line.equals(heading)
                    || !damagedCaption(line.text())
                    || known.contains(key(line.text()))
                    || ScoreCreditsDetector.isPrintedCredit(line.text())
                    || line.top() < heading.bottom()
                    || line.top() - heading.bottom() >= heading.height() * 1.2f
                    || line.height() < heading.height() * .4f
                    || line.height() > heading.height()
                    || Math.abs(line.center() - heading.center()) >= heading.height() * .8f
                    || line.right() - line.left() >= (heading.right() - heading.left()) * 2.5f
                    || line.left() < 0
                    || line.top() < 0
                    || line.right() > page.width()
                    || line.bottom() > page.height()
                    || (page.notationTop() > 0 && line.bottom() >= page.notationTop())
                    || page.reviewCredits().stream()
                            .anyMatch(r -> overlap(line, r.line()) || overlap(heading, r.line())))
                continue;
            captions.add(line);
        }
        return captions.size() == 1 ? new Nomination(heading, captions.get(0)) : null;
    }

    private static boolean validReadings(List<ScoreHeaderReadConsensus.Reading> readings) {
        if (readings == null || readings.size() != 12) return false;
        var views = new HashSet<String>();
        for (var reading : readings) {
            if (reading == null
                    || !singleLine(reading.text())
                    || reading.text().length() > 1000
                    || !Double.isFinite(reading.confidence())
                    || reading.confidence() < 0
                    || reading.confidence() > 1
                    || !Set.of(1, 2, 4).contains(reading.scale())
                    || reading.mode() == null
                    || !Set.of("tight", "horizontal").contains(reading.mode())
                    || !(reading.widthFactor() == 1 || reading.widthFactor() == 1.5)
                    || !views.add(
                            reading.scale() + ":" + reading.mode() + ":" + reading.widthFactor()))
                return false;
        }
        return true;
    }

    /** Requiring actual complete literal captions keeps the grammar from supplying missing letters. */
    private static String literal(
            List<ScoreHeaderReadConsensus.Reading> medium,
            List<ScoreHeaderReadConsensus.Reading> english) {
        if (!validReadings(medium) || !validReadings(english)) return "";
        var groups = new LinkedHashMap<String, List<ScoreHeaderReadConsensus.Reading>>();
        for (var reading : english) {
            String text = decorated(reading.text());
            if (CAPTION.matcher(text).matches() && reading.confidence() >= .85)
                groups.computeIfAbsent(key(text), k -> new ArrayList<>()).add(reading);
        }
        String winner = "";
        for (var group : groups.entrySet()) {
            var exact = group.getValue();
            // A complete literal from the independent reader can supply the other crop
            // mode. Both readers must see the complete caption; a near spelling cannot
            // create that missing view. English still needs three literals and two
            // strong scales, and the medium grid must independently support the prefix.
            var exactViews = new ArrayList<>(exact);
            for (var reading : medium)
                if (reading.confidence() >= .9
                        && key(decorated(reading.text())).equals(group.getKey()))
                    exactViews.add(reading);
            if (exact.size() < 3
                    || exactViews.stream()
                                    .map(ScoreHeaderReadConsensus.Reading::mode)
                                    .distinct()
                                    .count()
                            < 2
                    || exact.stream()
                                    .filter(r -> r.confidence() >= .9)
                                    .map(ScoreHeaderReadConsensus.Reading::scale)
                                    .distinct()
                                    .count()
                            < 2) continue;
            // A strong competing, complete caption remains unresolved.
            if (groups.entrySet().stream()
                    .anyMatch(
                            e ->
                                    !e.getKey().equals(group.getKey())
                                            && e.getValue().stream()
                                                    .anyMatch(r -> r.confidence() >= .9))) continue;
            String candidate = group.getKey();
            int version = candidate.indexOf(" version");
            if (version < 1) continue;
            String prefix = candidate.substring(0, version);
            var support =
                    medium.stream()
                            .filter(r -> r.confidence() >= .9)
                            .filter(
                                    r -> {
                                        String value = key(decorated(r.text()));
                                        return value.startsWith(prefix + " ")
                                                && distance(value, candidate) <= 1
                                                && CreditOcrIdentifierGuard.preservesIdentifiers(
                                                        value, candidate);
                                    })
                            .toList();
            if (support.size() < 9
                    || support.stream()
                                    .map(ScoreHeaderReadConsensus.Reading::scale)
                                    .distinct()
                                    .count()
                            < 2
                    || support.stream()
                                    .map(ScoreHeaderReadConsensus.Reading::mode)
                                    .distinct()
                                    .count()
                            < 2) continue;
            if (!winner.isEmpty()) return "";
            winner =
                    decorated(
                            exact.stream()
                                    .filter(r -> r.confidence() >= .9)
                                    .findFirst()
                                    .orElseThrow()
                                    .text());
        }
        return winner;
    }

    public record Selection(
            ScoreCreditsDetector.Result before,
            ScoreCreditsDetector.Result after,
            List<ScoreCreditsDetector.Page> originalPages,
            List<ScoreCreditsDetector.Page> pages,
            Nomination nomination,
            String literalCaption) {}

    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> original,
            Set<String> names,
            ScoreCreditsDetector.Line observedCaption,
            List<ScoreHeaderReadConsensus.Reading> medium,
            List<ScoreHeaderReadConsensus.Reading> english) {
        var nomination = nominate(hint, original, names);
        if (nomination == null || !nomination.caption().equals(observedCaption)) return null;
        String literal = literal(medium, english);
        if (literal.isEmpty()
                || names.stream().anyMatch(n -> key(n).equals(key(literal)))
                || !CreditOcrIdentifierGuard.preservesIdentifiers(
                        nomination.caption().text(), literal)) return null;
        var first = original.get(0);
        var lines = new ArrayList<>(first.lines());
        var caption = nomination.caption();
        lines.set(
                lines.indexOf(caption),
                new ScoreCreditsDetector.Line(
                        literal, caption.left(), caption.top(), caption.right(), caption.bottom()));
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
        var before = ScoreCreditsDetector.detect(hint, original, names);
        var after = ScoreCreditsDetector.detect(hint, pages, names);
        if (!clean(after.title()).equals(clean(before.title()) + " " + literal)
                || !before.artist().equals(after.artist())
                || !before.composer().equals(after.composer())
                || !before.arranger().equals(after.arranger())
                || !after.unclassifiedCredits()
                        .equals(
                                before.unclassifiedCredits().stream()
                                        .filter(
                                                value ->
                                                        !clean(value).equals(clean(caption.text())))
                                        .toList())) return null;
        return new Selection(
                before, after, List.copyOf(original), List.copyOf(pages), nomination, literal);
    }
}
