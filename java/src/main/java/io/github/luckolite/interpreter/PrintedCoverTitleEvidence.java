// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.text.Normalizer;
import io.github.luckolite.interpreter.ScoreCreditsDetector;

/** Selects connected printed title fragments; hints supply no missing characters. */
public final class PrintedCoverTitleEvidence {
    private static float height(ScoreCreditsDetector.Line line) {
        return Math.max(1, line.bottom() - line.top());
    }

    public record Evidence(String text, List<ScoreCreditsDetector.Line> lines) {}

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String words(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\h]", "")
                .strip()
                .replaceAll("\\h+", " ");
    }

    public static Evidence select(String hint, float width, List<ScoreCreditsDetector.Line> raw) {
        String full = fold(hint);
        String base =
                fold(
                        hint.replaceFirst(
                                "(?iu)\\h+(?:vol(?:ume)?\\.?|book|part)\\h*[-.:]?\\h*[0-9]+\\h*$",
                                ""));
        // Import labels may add a generic document suffix absent from the cover.
        // The full printed reading still wins when the cover actually includes it.
        String documentBase = fold(hint.replaceFirst("(?iu)\\h+book\\h*$", ""));
        if (documentBase.length() >= 5 && documentBase.length() < base.length())
            base = documentBase;
        if (base.length() < 5) return null;
        List<ScoreCreditsDetector.Line> lines = new ArrayList<>(new LinkedHashSet<>(raw));
        lines.removeIf(l -> height(l) < width * .025f || fold(l.text()).isEmpty());
        lines.sort(
                Comparator.comparingDouble(ScoreCreditsDetector.Line::top)
                        .thenComparingDouble(ScoreCreditsDetector.Line::left));
        List<Evidence> found = new ArrayList<>();
        for (var line : lines) {
            String key = fold(line.text());
            if (full.startsWith(key) || base.startsWith(key))
                walk(full, base, lines, List.of(line), line.text().strip(), found);
        }
        // Preserve the longest complete printed reading; never append an unprinted hint suffix.
        Evidence exact =
                found.stream()
                        .max(
                                Comparator.comparingInt((Evidence e) -> fold(e.text()).length())
                                        .thenComparingInt(
                                                e ->
                                                        words(hint).equals(words(e.text()))
                                                                        || words(hint)
                                                                                .startsWith(
                                                                                        words(
                                                                                                        e
                                                                                                                .text())
                                                                                                + " ")
                                                                ? 1
                                                                : 0)
                                        .thenComparingDouble(
                                                e ->
                                                        e.lines().stream()
                                                                .mapToDouble(
                                                                        l -> l.right() - l.left())
                                                                .sum()))
                        .orElse(null);
        if (exact != null && exact.lines().size() == 1) return exact;
        String label =
                hint.replaceFirst(
                                "(?iu)\\h+(?:vol(?:ume)?\\.?|book|part)\\h*[-.:]?\\h*[0-9]+\\h*$",
                                "")
                        .replaceFirst("(?iu)\\h+book\\h*$", "");
        String[] tokens = words(label).split(" ");
        if (tokens.length < 2
                || new HashSet<>(List.of(tokens)).size() < 2
                || tokens[0].length() < 3
                || tokens[tokens.length - 1].length() < 3) return exact;
        List<Evidence> expanded = new ArrayList<>();
        stackedTitle(label, tokens, width, lines, expanded);
        for (var line : lines)
            if (orderedPrefix(tokens, line.text()))
                expandedWalk(label, tokens, lines, List.of(line), line.text().strip(), expanded);
        Evidence complete =
                expanded.stream()
                        .min(
                                Comparator.comparingInt(
                                                (Evidence e) -> words(e.text()).split(" ").length)
                                        .thenComparingDouble(
                                                e ->
                                                        -e.lines().stream()
                                                                .mapToDouble(
                                                                        l -> l.right() - l.left())
                                                                .sum()))
                        .orElse(null);
        // The hint may omit a middle printed word. Do not prefer its shorter match
        // over a geometrically connected complete source heading.
        Evidence chosen =
                complete != null
                                && (exact == null
                                        || fold(complete.text()).length()
                                                > fold(exact.text()).length())
                        ? complete
                        : exact;
        if (chosen != null) return chosen;
        List<Evidence> approximate = new ArrayList<>();
        for (var line : lines)
            if (nearPrefix(tokens, line.text()))
                nearWalk(label, tokens, lines, List.of(line), line.text().strip(), approximate);
        // Ambiguous alternatives cannot be resolved from a misspelled import label.
        if (approximate.stream().map(e -> words(e.text())).distinct().count() != 1) return null;
        return approximate.stream()
                .max(
                        Comparator.comparingDouble(
                                e ->
                                        e.lines().stream()
                                                .mapToDouble(l -> l.right() - l.left())
                                                .sum()))
                .orElse(null);
    }

    private static boolean oneLetter(String a, String b) {
        if (a.length() < 5 || b.length() < 5 || !a.matches("\\p{L}+") || !b.matches("\\p{L}+"))
            return false;
        if (a.length() != b.length()) return false;
        int i = 0, j = 0, changes = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++changes > 1) return false;
            if (a.length() >= b.length()) i++;
            if (b.length() >= a.length()) j++;
        }
        return changes + (a.length() - i) + (b.length() - j) == 1;
    }

    private static boolean nearPrefix(String[] hint, String text) {
        String[] printed = words(text).split(" ");
        if (printed.length > hint.length) return false;
        int changes = 0;
        for (int i = 0; i < printed.length; i++)
            if (!printed[i].equals(hint[i])) {
                if (!oneLetter(printed[i], hint[i]) || ++changes > 1) return false;
            }
        return true;
    }

    private static void nearWalk(
            String label,
            String[] hint,
            List<ScoreCreditsDetector.Line> lines,
            List<ScoreCreditsDetector.Line> path,
            String text,
            List<Evidence> found) {
        String[] printed = words(text).split(" ");
        if (printed.length == hint.length) {
            int anchors = 0;
            for (int i = 0; i < hint.length; i++)
                if (printed[i].equals(hint[i]) && hint[i].length() >= 4) anchors++;
            if (anchors > 0 && CreditOcrIdentifierGuard.preservesIdentifiers(label, text))
                found.add(new Evidence(text, List.copyOf(path)));
            return;
        }
        if (path.size() >= 6) return;
        var previous = path.get(path.size() - 1);
        for (var next : lines) {
            float largest = Math.max(height(previous), height(next));
            if (path.contains(next)
                    || (next.top() + next.bottom() - previous.top() - previous.bottom()) * .5f
                            < Math.min(height(previous), height(next)) * .3f
                    || next.top() - previous.bottom() > largest * .9f
                    || Math.min(height(previous), height(next)) < largest * .4f
                    || Math.min(previous.right(), next.right())
                            <= Math.max(previous.left(), next.left())) continue;
            String joined = text + " " + next.text().strip();
            if (!nearPrefix(hint, joined)) continue;
            var extended = new ArrayList<>(path);
            extended.add(next);
            nearWalk(label, hint, lines, extended, joined, found);
        }
    }

    private static void stackedTitle(
            String label,
            String[] hint,
            float width,
            List<ScoreCreditsDetector.Line> lines,
            List<Evidence> found) {
        for (var left : lines)
            for (var right : lines) {
                float big = Math.max(height(left), height(right));
                if (left == right
                        || right.left() <= left.right()
                        || right.left() - left.right() > big * 2
                        || Math.min(height(left), height(right)) < big * .7f
                        || Math.min(left.bottom(), right.bottom())
                                        - Math.max(left.top(), right.top())
                                < big * .7f
                        || words(left.text()).split(" ").length > 3
                        || words(right.text()).split(" ").length > 3) continue;
                for (var upper : lines)
                    for (var lower : lines) {
                        if (upper == lower
                                || upper == left
                                || upper == right
                                || lower == left
                                || lower == right
                                || height(upper) < big * .25f
                                || height(lower) < big * .25f
                                || height(upper) > big * .65f
                                || height(lower) > big * .65f
                                || upper.left() < left.right() - big * .1f
                                || lower.left() < left.right() - big * .1f
                                || upper.right() > right.left() + big * .1f
                                || lower.right() > right.left() + big * .1f
                                || Math.min(upper.right(), lower.right())
                                        <= Math.max(upper.left(), lower.left())
                                || lower.top() < upper.bottom() - big * .1f
                                || lower.top() - upper.bottom() > big * .25f
                                || upper.top() < Math.min(left.top(), right.top()) - big * .1f
                                || lower.bottom()
                                        > Math.max(left.bottom(), right.bottom()) + big * .1f
                                || !words(upper.text()).matches("\\p{L}{1,8}")
                                || !words(lower.text()).matches("\\p{L}{1,8}")) continue;
                        String text =
                                left.text().strip()
                                        + " "
                                        + upper.text().strip()
                                        + " "
                                        + lower.text().strip()
                                        + " "
                                        + right.text().strip();
                        String[] printed = words(text).split(" ");
                        int cursor = 0, extra = 0;
                        for (String word : printed) {
                            if (cursor < hint.length && word.equals(hint[cursor])) cursor++;
                            else extra++;
                        }
                        if (cursor == hint.length
                                && extra <= 2
                                && printed[0].equals(hint[0])
                                && printed[printed.length - 1].equals(hint[hint.length - 1])
                                && CreditOcrIdentifierGuard.preservesIdentifiers(label, text))
                            found.add(new Evidence(text, List.of(left, upper, lower, right)));
                    }
            }
    }

    private static boolean orderedPrefix(String[] hint, String text) {
        String[] printed = words(text).split(" ");
        if (printed.length == 0 || !printed[0].equals(hint[0])) return false;
        int cursor = 0, extra = 0;
        for (String word : printed) {
            if (cursor == hint.length) {
                if (++extra > 2 || !word.matches("\\p{L}+")) return false;
                continue;
            }
            if (word.equals(hint[cursor])) cursor++;
            else if (++extra > 2 || !word.matches("\\p{L}+")) return false;
        }
        return true;
    }

    private static void expandedWalk(
            String label,
            String[] hint,
            List<ScoreCreditsDetector.Line> available,
            List<ScoreCreditsDetector.Line> path,
            String text,
            List<Evidence> found) {
        String[] printed = words(text).split(" ");
        int cursor = 0;
        for (String word : printed) if (cursor < hint.length && word.equals(hint[cursor])) cursor++;
        if (cursor == hint.length
                && printed.length > hint.length
                && CreditOcrIdentifierGuard.preservesIdentifiers(label, text)) {
            found.add(new Evidence(text, List.copyOf(path)));
            return;
        }
        // Once the complete label is printed, do not absorb a later caption row.
        if (cursor == hint.length) return;
        if (path.size() >= 6) return;
        var previous = path.get(path.size() - 1);
        for (var next : available) {
            float largest = Math.max(height(previous), height(next));
            if (path.contains(next)
                    || (next.top() + next.bottom() - previous.top() - previous.bottom()) * .5f
                            < Math.min(height(previous), height(next)) * .3f
                    || next.top() - previous.bottom() > largest * .9f
                    || Math.min(height(previous), height(next)) < largest * .4f
                    || Math.min(previous.right(), next.right())
                            <= Math.max(previous.left(), next.left())) continue;
            String joined = text + " " + next.text().strip();
            if (!orderedPrefix(hint, joined)) continue;
            List<ScoreCreditsDetector.Line> extended = new ArrayList<>(path);
            extended.add(next);
            expandedWalk(label, hint, available, extended, joined, found);
        }
    }

    private static void walk(
            String full,
            String base,
            List<ScoreCreditsDetector.Line> available,
            List<ScoreCreditsDetector.Line> path,
            String text,
            List<Evidence> found) {
        String key = fold(text);
        if (key.equals(full) || key.equals(base)) {
            found.add(new Evidence(text, List.copyOf(path)));
            if (key.equals(full)) return;
        }
        if (path.size() >= 6) return;
        var previous = path.get(path.size() - 1);
        for (var next : available) {
            float largest = Math.max(height(previous), height(next));
            if (path.contains(next)
                    || (next.top() + next.bottom() - previous.top() - previous.bottom()) * .5f
                            < Math.min(height(previous), height(next)) * .3f
                    || next.top() - previous.bottom() > largest * .9f
                    || Math.min(height(previous), height(next)) < largest * .4f
                    || Math.min(previous.right(), next.right())
                            <= Math.max(previous.left(), next.left())) continue;
            String joined = text + " " + next.text().strip(), normalized = fold(joined);
            if (!(full.startsWith(normalized) || base.startsWith(normalized))) continue;
            List<ScoreCreditsDetector.Line> extended = new ArrayList<>(path);
            extended.add(next);
            walk(full, base, available, extended, joined, found);
        }
    }
}
