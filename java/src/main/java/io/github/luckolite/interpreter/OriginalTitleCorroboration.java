// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Original-source title evidence for explicit review; never changes caller metadata. */
public final class OriginalTitleCorroboration {
    private OriginalTitleCorroboration() {}

    public record NativeLine(
            ScoreCreditsDetector.Line line, List<ScoreCreditsDetector.Line> words) {
        public NativeLine {
            words = List.copyOf(words);
        }
    }

    public record Reading(
            int scale,
            String text,
            float confidence,
            int width,
            int height,
            List<NativeLine> nativeLines) {
        public Reading {
            nativeLines = List.copyOf(nativeLines);
        }
    }

    public record Proposal(
            String literalOriginal,
            String suggestedText,
            int recoveredZeroWords,
            boolean punctuationDiffers,
            boolean requiresReview) {}

    private static final Pattern CATALOGUE =
            Pattern.compile(
                    "(?iu)(?<![\\p{L}\\p{N}])(?:op(?:us)?\\.?|bwv|rv|hwv|kv|woo|hob\\.?|isbn|isrc|sku|id|cat(?:alogue)?\\.?|no\\.?|nr\\.?|vol(?:ume)?\\.?)\\h*(?:[.:/#-]\\h*)?[\\p{L}\\p{N}]*\\p{Nd}");
    private static final Pattern SHORT_CATALOGUE =
            Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])[KDL]\\.?\\h*0");

    public static Proposal propose(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line original,
            List<Reading> readings) {
        if (page == null
                || original == null
                || readings == null
                || readings.size() != 3
                || !page.photographicCover()
                || page.creditsOnly()
                || !Float.isFinite(page.width())
                || !Float.isFinite(page.height())
                || page.width() <= 0
                || page.height() <= 0
                || !Float.isFinite(page.notationTop())
                || page.notationTop() > 0
                || !valid(original, page.width(), page.height())
                || page.lines().stream().anyMatch(l -> !valid(l, page.width(), page.height()))
                || !ScoreCreditsDetector.locatedCoverHeadingCandidates(page).contains(original)
                || ScoreCreditsDetector.locatedCoverHeadingCandidates(page).stream()
                                .filter(l -> l.text().equals(original.text()))
                                .count()
                        != 1
                || !original.text()
                        .equals(ScoreCreditsDetector.detect("", List.of(page), Set.of()).title()))
            return null;
        if (readings.stream()
                .anyMatch(
                        r ->
                                r == null
                                        || !singleLine(r.text())
                                        || !Float.isFinite(r.confidence())
                                        || r.confidence() < .98f
                                        || r.confidence() > 1
                                        || r.width() < 1
                                        || r.height() < 1
                                        || (long) r.width() * r.height() > 20_000_000)) return null;
        var sorted = readings.stream().sorted(Comparator.comparingInt(Reading::scale)).toList();
        if (sorted.get(0).scale() != 1 || sorted.get(1).scale() != 2 || sorted.get(2).scale() != 4)
            return null;
        Reading base = sorted.get(0);
        for (Reading r : sorted)
            if (Math.abs((long) r.width() - base.width() * (long) r.scale()) > r.scale()
                    || Math.abs((long) r.height() - base.height() * (long) r.scale()) > r.scale())
                return null;
        String candidate = clean(base.text());
        if (sorted.stream().anyMatch(r -> !clean(r.text()).equals(candidate))) return null;
        List<String> after = wordKeys(candidate);
        String[] afterRaw = candidate.split("\\h+");
        for (Reading r : sorted.subList(1, 3)) {
            NativeLine primary = dominant(r);
            if (primary == null) return null;
            if (r.scale() == 4 && !wordKeys(primary.line().text()).equals(after)) return null;
            if (r.scale() == 2
                    && !middleCorroborates(original.text(), candidate, primary.line().text()))
                return null;
        }
        String beforeText = clean(original.text());
        if (catalogue(beforeText) || catalogue(candidate)) return null;
        String[] before = beforeText.split("\\h+");
        if (before.length != after.size()) return null;
        var repaired = new ArrayList<String>();
        int changes = 0;
        for (int i = 0; i < before.length; i++) {
            String key = key(before[i]), next = after.get(i);
            if (key.equals(next)) {
                repaired.add(before[i]);
                continue;
            }
            if (before[i].codePointCount(0, before[i].length()) < 4
                    || !before[i].matches("\\p{L}[\\p{L}0]*\\p{L}")
                    || !afterRaw[i].matches("\\p{L}+")
                    || !key.contains("0")
                    || !key.replace('0', 'o').equals(next)) return null;
            repaired.add(before[i].replace('0', 'O'));
            changes++;
        }
        if (changes == 0
                || !CreditOcrIdentifierGuard.preservesIdentifiers(
                        String.join(" ", repaired), candidate)) return null;
        // Two engines can share a spelling prior. A deliberate B00K can look like BOOK.
        // Keep the literal original and expose the suggestion for an explicit autofill decision.
        return new Proposal(
                original.text(),
                candidate,
                changes,
                !punctuation(beforeText).equals(punctuation(candidate)),
                true);
    }

    private static NativeLine dominant(Reading r) {
        if (r.nativeLines().isEmpty()
                || r.nativeLines().stream().anyMatch(l -> !valid(l.line(), r))) return null;
        var lines =
                r.nativeLines().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (NativeLine l) ->
                                                        l.line().bottom() - l.line().top())
                                        .reversed())
                        .toList();
        NativeLine primary = lines.get(0);
        var line = primary.line();
        float h = line.bottom() - line.top();
        if (h < r.height() * .6f
                || line.right() - line.left() < r.width() * .75f
                || lines.size() > 1
                        && lines.get(1).line().bottom() - lines.get(1).line().top() > h * .5f
                || primary.words().size() < 2) return null;
        StringBuilder words = new StringBuilder();
        float end = -1;
        for (var word : primary.words()) {
            if (!valid(word, r)
                    || word.left() < line.left()
                    || word.right() > line.right()
                    || word.top() < line.top()
                    || word.bottom() > line.bottom()
                    || word.left() < end
                    || clean(word.text()).split("\\h+").length != 1) return null;
            if (words.length() != 0) words.append(' ');
            words.append(word.text());
            end = word.right();
        }
        return clean(words.toString()).equals(clean(line.text())) ? primary : null;
    }

    /** Middle-scale evidence may retain fewer original zeros; the largest read must be exact. */
    private static boolean middleCorroborates(
            String original, String candidate, String nativeText) {
        String[] source = clean(original).split("\\h+"),
                suggested = clean(candidate).split("\\h+"),
                nativeWords = clean(nativeText).split("\\h+");
        if (source.length != suggested.length || nativeWords.length != suggested.length)
            return false;
        for (int i = 0; i < source.length; i++) {
            String before = key(source[i]),
                    after = key(suggested[i]),
                    observed = key(nativeWords[i]);
            if (observed.equals(after)) continue;
            if (source[i].codePointCount(0, source[i].length()) < 4
                    || !source[i].matches("\\p{L}[\\p{L}0]*\\p{L}")
                    || !suggested[i].matches("\\p{L}+")
                    || !nativeWords[i].matches("\\p{L}[\\p{L}0]*\\p{L}")
                    || !before.replace('0', 'o').equals(after)
                    || observed.length() != after.length()
                    || before.length() != after.length()) return false;
            long originalZeros = before.chars().filter(c -> c == '0').count();
            long remainingZeros = observed.chars().filter(c -> c == '0').count();
            if (remainingZeros < 1 || remainingZeros >= originalZeros) return false;
            for (int j = 0; j < observed.length(); j++) {
                char current = observed.charAt(j);
                if (current == after.charAt(j)) continue;
                if (current != '0' || before.charAt(j) != '0' || after.charAt(j) != 'o')
                    return false;
            }
        }
        return true;
    }

    private static boolean valid(ScoreCreditsDetector.Line l, Reading r) {
        return valid(l, r.width(), r.height());
    }

    private static boolean valid(ScoreCreditsDetector.Line l, float width, float height) {
        return l != null
                && singleLine(l.text())
                && Float.isFinite(l.left())
                && Float.isFinite(l.top())
                && Float.isFinite(l.right())
                && Float.isFinite(l.bottom())
                && l.left() >= 0
                && l.top() >= 0
                && l.right() > l.left()
                && l.bottom() > l.top()
                && l.right() <= width
                && l.bottom() <= height;
    }

    /** Reject invisible edits and multiple lines before any whitespace/Unicode normalization. */
    private static boolean singleLine(String s) {
        return s != null
                && !s.isBlank()
                && s.codePoints()
                        .noneMatch(
                                c -> {
                                    int type = Character.getType(c);
                                    return type == Character.CONTROL
                                            || type == Character.FORMAT
                                            || type == Character.SURROGATE
                                            || type == Character.LINE_SEPARATOR
                                            || type == Character.PARAGRAPH_SEPARATOR;
                                });
    }

    private static String clean(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).strip().replaceAll("\\h+", " ");
    }

    private static boolean catalogue(String s) {
        // An allowed apostrophe variation must not reveal or conceal a protected code prefix.
        return CATALOGUE.matcher(s).find()
                || SHORT_CATALOGUE.matcher(s).find()
                || CATALOGUE.matcher(key(s)).find()
                || SHORT_CATALOGUE.matcher(key(s)).find();
    }

    /** Only internal apostrophes can differ; slashes, hyphens and other punctuation stay literal. */
    private static String key(String s) {
        return clean(s).toLowerCase(Locale.ROOT).replaceAll("(?<=\\p{L})['’](?=\\p{L})", "");
    }

    private static List<String> wordKeys(String s) {
        return List.of(clean(s).split("\\h+")).stream()
                .map(OriginalTitleCorroboration::key)
                .toList();
    }

    private static String punctuation(String s) {
        return clean(s).replaceAll("[\\p{L}\\p{N}\\p{M}]", "");
    }
}
