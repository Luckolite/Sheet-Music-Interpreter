// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.*;

/** A printed article/quoted heading above a numbered work caption at the opening staff's left.
 * No spelling repair, name lookup, filename matching, or reconstruction of missing text. */
public final class OpeningWorkHeading {
    private OpeningWorkHeading() {}

    public record Evidence(
            ScoreCreditsDetector.Line heading, ScoreCreditsDetector.Line caption, String title) {}

    private static final Pattern WORK =
            Pattern.compile(
                    "(?iu)^(concerto|sonat[ae]|symphony|sinfonia|suite|quartet|quintet|trio|sextet|octet|prelude|fugue|étude|etude|study|waltz|polonaise|nocturne|rhapsody|fantasia|fantasy)\\h*(?:(no\\.?)\\h*)?([IVXLCDM]+|[0-9]{1,3})$");
    private static final Pattern ARTICLE =
            Pattern.compile(
                    "(?iu)^(?:l['’]|(?:the|a|an|le|la|les|il|lo|gli|der|die|das|el|los|las|un|una)\\h+)\\p{L}.*");

    private static String clean(String s) {
        return s == null ? "" : s.strip().replaceAll("\\h+", " ");
    }

    private static String key(String s) {
        return java.text.Normalizer.normalize(clean(s), java.text.Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean valid(ScoreCreditsDetector.Line l, ScoreCreditsDetector.Page p) {
        return l != null
                && l.text() != null
                && Float.isFinite(l.left())
                && Float.isFinite(l.top())
                && Float.isFinite(l.right())
                && Float.isFinite(l.bottom())
                && l.left() >= 0
                && l.top() >= 0
                && l.right() > l.left()
                && l.bottom() > l.top()
                && l.right() <= p.width()
                && l.bottom() <= p.height();
    }

    private static boolean protectedRow(
            ScoreCreditsDetector.Line line, ScoreCreditsDetector.Page p) {
        for (var credit : p.reviewCredits()) {
            if (credit == null
                    || credit.line() == null
                    || credit.value() == null
                    || !valid(credit.line(), p)) return true;
            var other = credit.line();
            if (Math.min(line.right(), other.right()) > Math.max(line.left(), other.left())
                    && Math.min(line.bottom(), other.bottom()) > Math.max(line.top(), other.top()))
                return true;
        }
        return false;
    }

    private static String caption(String source) {
        var m = WORK.matcher(clean(source));
        if (!m.matches()) return "";
        String number = m.group(3);
        if (number.matches("[0-9]+")) {
            if (number.startsWith("0") || Integer.parseInt(number) == 0) return "";
        } else if (!number.matches("(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})") || number.isEmpty())
            return "";
        return m.group(1) + " " + (m.group(2) == null ? "" : m.group(2) + " ") + number;
    }

    private static boolean titlePhrase(String text) {
        String s = clean(text);
        if (s.length() < 4
                || s.length() > 120
                || s.split("\\h+").length > 10
                || s.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT)
                || s.matches(".*[0-9].*")) return false;
        return ARTICLE.matcher(s).matches() || s.matches("^[\"“].{3,118}[\"”]$");
    }

    /** Geometry nominates an opening reread only; it never assigns text or ownership. */
    public record Geometry(ScoreCreditsDetector.Line heading, ScoreCreditsDetector.Line caption) {}

    public static boolean hasGeometryNominee(ScoreCreditsDetector.Page p) {
        return !geometryCandidates(p).isEmpty();
    }

    public static List<Geometry> geometryCandidates(ScoreCreditsDetector.Page p) {
        if (p == null
                || p.creditsOnly()
                || p.photographicCover()
                || !Float.isFinite(p.notationTop())
                || p.notationTop() <= 0
                || p.lines().size() > 10000) return List.of();
        for (var row : p.lines()) if (!valid(row, p)) return List.of();
        var result = new ArrayList<Geometry>();
        for (var work : p.lines()) {
            if (!valid(work, p)) return List.of();
            float wh = work.bottom() - work.top();
            if (work.left() >= p.width() * .25f
                    || work.right() >= p.width() * .48f
                    || wh < p.width() * .018f
                    || work.top() < p.notationTop() - wh
                    || work.bottom() > p.notationTop() + p.height() * .06f
                    || protectedRow(work, p)) continue;
            for (var heading : p.lines()) {
                if (!valid(heading, p)) return List.of();
                float hh = heading.bottom() - heading.top(),
                        center = (heading.left() + heading.right()) / 2;
                if (heading == work
                        || protectedRow(heading, p)
                        || heading.bottom() > work.top()
                        || heading.top() >= p.notationTop()
                        || heading.top() > p.height() * .2f
                        || hh < p.width() * .01f
                        || wh < hh * 1.1f
                        || work.top() - heading.bottom() > Math.max(wh * 1.5f, p.height() * .04f)
                        || Math.abs(heading.left() - work.left()) > Math.max(wh, p.width() * .04f)
                        || center < work.left()
                        || center > work.right()) continue;
                result.add(new Geometry(heading, work));
                if (result.size() == 5) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    /** Bounded source crops for independently proving a larger instrument-part header. */
    public static List<ScoreCreditsDetector.Line> headerCandidates(ScoreCreditsDetector.Page p) {
        var pairs = geometryCandidates(p);
        if (pairs.isEmpty() || pairs.size() > 4) return List.of();
        var result = new LinkedHashSet<ScoreCreditsDetector.Line>();
        for (var row : p.lines()) {
            if (protectedRow(row, p) || ScoreCreditsDetector.isPrintedCredit(row.text())) continue;
            for (var pair : pairs) {
                if (row.equals(pair.heading())
                        || row.equals(pair.caption())
                        || row.height() < pair.heading().height() * 1.1f
                        || row.top() >= p.height() * .2f
                        || row.bottom() > p.notationTop()
                        || row.top() >= pair.heading().top()
                        || row.center() <= pair.heading().right()) continue;
                result.add(row);
            }
        }
        return List.copyOf(result);
    }

    public static Evidence find(
            ScoreCreditsDetector.Page p,
            List<ScoreCreditsDetector.Line> lines,
            Set<String> knownNames) {
        if (p == null
                || lines == null
                || knownNames == null
                || p.creditsOnly()
                || p.photographicCover()
                || !Float.isFinite(p.width())
                || !Float.isFinite(p.height())
                || !Float.isFinite(p.notationTop())
                || p.width() <= 0
                || p.height() <= 0
                || p.notationTop() <= 0
                || lines.size() > 10000) return null;
        for (var line : lines) if (!valid(line, p)) return null;
        var names = new HashSet<String>();
        for (String name : knownNames) names.add(key(name));
        var result = new ArrayList<Evidence>();
        for (var work : lines) {
            String workText = caption(work.text());
            float wh = work.bottom() - work.top();
            if (workText.isEmpty()
                    || work.left() >= p.width() * .25f
                    || work.right() >= p.width() * .48f
                    || wh < p.width() * .018f
                    || work.top() < p.notationTop() - wh
                    || work.bottom() > p.notationTop() + p.height() * .06f
                    || protectedRow(work, p)) continue;
            for (var heading : lines) {
                float hh = heading.bottom() - heading.top(),
                        center = (heading.left() + heading.right()) / 2;
                if (heading == work
                        || !titlePhrase(heading.text())
                        || names.contains(key(heading.text()))
                        || ScoreCreditsDetector.isPrintedCredit(heading.text())
                        || protectedRow(heading, p)
                        || heading.bottom() > work.top()
                        || heading.top() >= p.notationTop()
                        || heading.top() > p.height() * .2f
                        || hh < p.width() * .01f
                        || wh < hh * 1.1f
                        || work.top() - heading.bottom() > Math.max(wh * 1.5f, p.height() * .04f)
                        || Math.abs(heading.left() - work.left()) > Math.max(wh, p.width() * .04f)
                        || center < work.left()
                        || center > work.right()) continue;
                boolean credited = false;
                for (var line : lines)
                    if (ScoreCreditsDetector.isPrintedCredit(line.text())
                            && key(line.text()).contains(key(heading.text()))) credited = true;
                if (!credited)
                    result.add(new Evidence(heading, work, clean(heading.text()) + " " + workText));
            }
        }
        return result.size() == 1 ? result.get(0) : null;
    }
}
