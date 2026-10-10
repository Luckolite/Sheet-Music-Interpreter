// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import io.github.luckolite.interpreter.ScoreCreditsDetector;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Line;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Page;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Recover overlapping printed title continuations only beside independently located heading/caption ink. */
public final class EmbeddedTitleContinuations {
    private EmbeddedTitleContinuations() {}

    private static final Pattern VERSION =
            Pattern.compile("(?iu)^(?:ver\\.|version)\\h+[0-9]{1,3}(?:\\.[0-9]+)?$");
    private static final Pattern HAS_VERSION =
            Pattern.compile("(?iu)\\b(?:ver\\.|version)\\h+[0-9]{1,3}(?:\\.[0-9]+)?\\b");

    private static String clean(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private static String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    private static float height(Line line) {
        return Math.max(1, line.bottom() - line.top());
    }

    private static float center(Line line) {
        return (line.left() + line.right()) * .5f;
    }

    private static float limit(Page page) {
        return page.notationTop() > 0 ? page.notationTop() : page.height() * .62f;
    }

    private static Line heading(Page page, List<Line> lines) {
        List<Line> prominent =
                lines.stream()
                        .filter(
                                line ->
                                        line.top() < Math.min(limit(page), page.height() * .15f)
                                                && line.bottom() < limit(page)
                                                && height(line) >= page.width() * .025f
                                                && line.right() - line.left() >= page.width() * .4f
                                                && Math.abs(center(line) - page.width() * .5f)
                                                        < page.width() * .12f
                                                && clean(line.text()).split(" ").length >= 3
                                                && !clean(line.text()).startsWith("(")
                                                && !ScoreCreditsDetector.isPrintedCredit(
                                                        line.text())
                                                && !line.text()
                                                        .matches(
                                                                "(?iu).*(?:https?://|copyright|all\\h+rights\\h+reserved).*"))
                        .sorted(Comparator.comparingDouble(Line::top))
                        .toList();
        return prominent.size() == 1 ? prominent.get(0) : null;
    }

    private static boolean captionNear(Line caption, Line heading, float limit) {
        String text = clean(caption.text());
        return text.startsWith("(")
                && text.endsWith(")")
                && text.length() < 120
                && text.split(" ").length >= 3
                && caption.top() < limit
                && height(caption) >= height(heading) * .4f
                && height(caption) <= height(heading) * 1.1f
                && caption.top() >= heading.bottom() - height(caption) * .3f
                && caption.top() - heading.bottom() < height(heading) * 1.4f
                && Math.abs(center(caption) - center(heading)) < height(heading) * .8f
                && caption.left() > heading.left()
                && caption.right() < heading.right();
    }

    public static boolean needsWords(Page page, List<Line> lines) {
        if (page.creditsOnly() || page.photographicCover()) return false;
        Line heading = heading(page, lines);
        return heading != null
                && lines.stream().filter(line -> captionNear(line, heading, limit(page))).count()
                        == 1;
    }

    private static List<Line> captionRow(Line start, List<Line> words, Line located) {
        List<Line> row =
                words.stream()
                        .filter(
                                word ->
                                        word.left() >= start.left() - height(start) * .15f
                                                && word.right()
                                                        <= located.right() + height(located) * .5f
                                                && height(word) >= height(start) * .8f
                                                && height(word) <= height(start) * 1.25f
                                                && Math.abs(
                                                                (word.top()
                                                                                + word.bottom()
                                                                                - start.top()
                                                                                - start.bottom())
                                                                        * .5f)
                                                        < height(start) * .2f)
                        .distinct()
                        .sorted(Comparator.comparingDouble(Line::left))
                        .toList();
        List<Line> result = new ArrayList<>();
        for (Line word : row) {
            if (result.isEmpty() && !word.equals(start)) return List.of();
            if (!result.isEmpty()) {
                Line previous = result.get(result.size() - 1);
                if (word.left() < previous.right() - height(start) * .1f
                        || word.left() - previous.right() > height(start) * .8f) return List.of();
            }
            result.add(word);
            if (clean(word.text()).endsWith(")")) break;
            if (result.size() > 16) return List.of();
        }
        return result;
    }

    public static List<Line> merge(Page page, List<Line> original, List<Line> words) {
        if (!needsWords(page, original) || words.isEmpty()) return List.copyOf(original);
        Line heading = heading(page, original);
        List<Line> printedHeading =
                words.stream()
                        .filter(
                                word ->
                                        word.left() >= heading.left() - height(heading) * .2f
                                                && word.right()
                                                        <= heading.right() + height(heading) * .2f
                                                && height(word) >= height(heading) * .7f
                                                && height(word) <= height(heading) * 1.5f
                                                && Math.abs(
                                                                (word.top()
                                                                                + word.bottom()
                                                                                - heading.top()
                                                                                - heading.bottom())
                                                                        * .5f)
                                                        < height(heading) * .35f)
                        .distinct()
                        .sorted(Comparator.comparingDouble(Line::left))
                        .toList();
        // Search bounds describe ink, so a standalone dash can be much shorter than
        // the surrounding letters. Admit it only between two already qualified words.
        List<Line> fullHeightHeading = printedHeading;
        List<Line> shortDashes =
                words.stream()
                        .filter(
                                word ->
                                        clean(word.text()).matches("[–—-]")
                                                && height(word) < height(heading) * .7f
                                                && height(word) > 0
                                                && word.right() > word.left()
                                                && word.right() - word.left()
                                                        < height(heading) * .7f
                                                && Math.abs(
                                                                (word.top()
                                                                                + word.bottom()
                                                                                - heading.top()
                                                                                - heading.bottom())
                                                                        * .5f)
                                                        < height(heading) * .35f
                                                && fullHeightHeading.stream()
                                                        .anyMatch(
                                                                prior ->
                                                                        prior.right() <= word.left()
                                                                                && word.left()
                                                                                                - prior
                                                                                                        .right()
                                                                                        < height(
                                                                                                        heading)
                                                                                                * .6f)
                                                && fullHeightHeading.stream()
                                                        .anyMatch(
                                                                next ->
                                                                        next.left() >= word.right()
                                                                                && next.left()
                                                                                                - word
                                                                                                        .right()
                                                                                        < height(
                                                                                                        heading)
                                                                                                * .6f))
                        .distinct()
                        .toList();
        if (!shortDashes.isEmpty()) {
            List<Line> withPunctuation = new ArrayList<>(printedHeading);
            withPunctuation.addAll(shortDashes);
            printedHeading =
                    withPunctuation.stream()
                            .distinct()
                            .sorted(Comparator.comparingDouble(Line::left))
                            .toList();
        }
        if (printedHeading.size() < 3
                || printedHeading.size() > 20
                || !key(String.join(
                                " ",
                                printedHeading.stream().map(word -> clean(word.text())).toList()))
                        .equals(key(heading.text()))) return List.copyOf(original);
        Line located =
                original.stream()
                        .filter(line -> captionNear(line, heading, limit(page)))
                        .findFirst()
                        .orElseThrow();
        String[] ink = clean(located.text()).split(" ");
        List<Line> matches = new ArrayList<>();
        for (Line start : words) {
            if (!clean(start.text()).startsWith("(")
                    || start.top() >= limit(page)
                    || Math.abs(start.left() - located.left()) > height(located) * .65f
                    || Math.abs(
                                    (start.top()
                                                    + start.bottom()
                                                    - located.top()
                                                    - located.bottom())
                                            * .5f)
                            > height(located) * .65f) continue;
            List<Line> row = captionRow(start, words, located);
            if (row.size() < 3 || row.size() > 16) continue;
            String printed =
                    String.join(" ", row.stream().map(word -> clean(word.text())).toList());
            String[] tokens = printed.split(" ");
            if (!printed.endsWith(")")
                    || printed.length() >= 120
                    || tokens.length < 3
                    || !key(tokens[0]).equals(key(ink[0]))
                    || !key(tokens[1]).equals(key(ink[1]))
                    || !key(tokens[2]).equals(key(ink[2]))
                    || !key(tokens[tokens.length - 1]).equals(key(ink[ink.length - 1]))) continue;
            Line last = row.get(row.size() - 1);
            if (last.right() - start.left() < (located.right() - located.left()) * .8f
                    || Math.abs(last.right() - located.right()) > height(located) * .65f) continue;
            matches.add(
                    new Line(
                            printed,
                            located.left(),
                            located.top(),
                            located.right(),
                            located.bottom()));
        }
        matches = matches.stream().distinct().toList();
        if (matches.size() != 1) return List.copyOf(original);
        Line repaired = matches.get(0);
        List<Line> versions = new ArrayList<>();
        for (Line first : words) {
            if (!clean(first.text()).matches("(?iu)^(?:ver\\.|version)$")) continue;
            for (Line number : words) {
                String text = clean(first.text()) + " " + clean(number.text());
                if (!VERSION.matcher(text).matches()
                        || number.left() < first.right()
                        || number.left() - first.right() > height(first) * .5f
                        || Math.abs(
                                        (first.top()
                                                        + first.bottom()
                                                        - number.top()
                                                        - number.bottom())
                                                * .5f)
                                > height(first) * .15f
                        || height(number) < height(first) * .8f
                        || height(number) > height(first) * 1.25f
                        || height(first) < height(heading) * .7f
                        || height(first) > height(heading) * 1.5f
                        || first.top() < heading.bottom() - height(heading) * .1f
                        || first.top() - heading.bottom() > height(heading) * 1.4f
                        || number.bottom() > limit(page)
                        || Math.abs((first.left() + number.right()) * .5f - center(heading))
                                > height(heading) * .8f
                        || number.right() - first.left() > (heading.right() - heading.left()) * .4f)
                    continue;
                versions.add(
                        new Line(
                                text,
                                first.left(),
                                first.top(),
                                number.right(),
                                Math.max(first.bottom(), number.bottom())));
            }
        }
        versions = versions.stream().distinct().toList();
        if (versions.size() != 1) return List.copyOf(original);
        Line version = versions.get(0);
        // The pair occupies the same small header region: a distant edition note cannot extend the
        // title.
        if (Math.min(version.bottom(), located.bottom()) - Math.max(version.top(), located.top())
                < Math.min(height(version), height(located)) * .5f) return List.copyOf(original);
        // Assemble the verified overlapping continuation as one heading. Otherwise a
        // parenthetical beginning with a direction word can be discarded in title selection.
        List<Line> merged = new ArrayList<>(original);
        merged.remove(located);
        merged.removeIf(
                line ->
                        VERSION.matcher(clean(line.text())).matches()
                                && Math.abs(center(line) - center(version)) < height(version) * .4f
                                && Math.abs(
                                                (line.top()
                                                                + line.bottom()
                                                                - version.top()
                                                                - version.bottom())
                                                        * .5f)
                                        < height(version) * .4f);
        merged.set(
                merged.indexOf(heading),
                new Line(
                        clean(heading.text()) + " " + version.text() + " " + repaired.text(),
                        heading.left(),
                        heading.top(),
                        heading.right(),
                        heading.bottom()));
        return List.copyOf(merged);
    }
}
