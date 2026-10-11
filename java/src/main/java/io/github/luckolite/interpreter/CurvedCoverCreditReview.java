// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Retains a separately printed curved cover name for explicit review, without assigning a role. */
public final class CurvedCoverCreditReview {
    private CurvedCoverCreditReview() {}

    public record Proposal(
            ScoreCreditsDetector.Page source,
            CurvedTitleCompletion.Proposal work,
            String value,
            CurvedTitleCompletion.Reading anchor,
            List<CurvedTitleCompletion.Reading> readings,
            boolean requiresReview) {
        public Proposal {
            readings = snapshot(readings);
            anchor = anchor == null ? null : snapshot(List.of(anchor)).get(0);
        }

        @Override
        public CurvedTitleCompletion.Reading anchor() {
            return anchor == null ? null : snapshot(List.of(anchor)).get(0);
        }

        @Override
        public List<CurvedTitleCompletion.Reading> readings() {
            return snapshot(readings);
        }
    }

    public record Selection(
            List<ScoreCreditsDetector.Page> pages,
            ScoreCreditsDetector.Result result,
            boolean accepted,
            boolean requiresReview) {
        public Selection {
            pages = List.copyOf(pages);
        }
    }

    public static Proposal propose(
            ScoreCreditsDetector.Page source, List<CurvedTitleCompletion.Reading> readings) {
        if (source == null || readings == null) return null;
        var work = CurvedTitleCompletion.propose(source, readings);
        if (work == null) return null;
        var names = new ArrayList<CurvedTitleCompletion.Reading>();
        var workRows = new ArrayList<CurvedTitleCompletion.Reading>();
        for (var first : readings) {
            if (first == null
                    || first.scale() != 1
                    || !geometry(first, source)
                    || !key(first.portable()).equals(key(work.suggestedText()))
                    || !cohort(first, readings, source, false)) continue;
            workRows.add(first);
        }
        if (workRows.isEmpty()) return null;
        for (var first : readings) {
            if (first == null
                    || first.scale() != 1
                    || !name(first.portable())
                    || !cohort(first, readings, source, true)
                    || phrase(work.suggestedText(), first.portable())
                    || source.lines().stream()
                            .anyMatch(line -> phrase(line.text(), first.portable()))) continue;
            // The polynomial baselines distinguish concentric rows whose axis-aligned boxes
            // overlap.
            if (workRows.stream().anyMatch(row -> !above(first, row))) continue;
            if (names.stream()
                    .anyMatch(
                            row ->
                                    row.widthFactor() == first.widthFactor()
                                            && CurvedTitleCompletion.sameRow(first, row)))
                return null;
            names.add(first);
        }
        if (names.isEmpty()) return null;
        var first = names.get(0);
        for (double width : new double[] {1, 1.5})
            for (int scale : new int[] {1, 2, 4}) {
                long count =
                        readings.stream()
                                .filter(
                                        r ->
                                                r != null
                                                        && r.widthFactor() == width
                                                        && r.scale() == scale
                                                        && CurvedTitleCompletion.sameRow(first, r))
                                .count();
                if (count > 1) return null;
            }
        for (int i = 1; i < names.size(); i++) {
            var other = names.get(i);
            if (other.widthFactor() == first.widthFactor()
                    || !CurvedTitleCompletion.sameRow(first, other)
                    || !key(other.portable()).equals(key(first.portable()))) return null;
        }
        return new Proposal(source, work, first.portable().trim(), first, readings, true);
    }

    /** Runs after the existing title selection. Raw OCR lines and all four assigned fields remain exact; the review payload is retained on page one. */
    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> originals,
            List<ScoreCreditsDetector.Page> selected,
            Proposal proposal) {
        var baseline = ScoreCreditsDetector.detect(hint, selected, Set.of());
        var declined = new Selection(selected, baseline, false, true);
        if (proposal == null
                || proposal.work() == null
                || originals.isEmpty()
                || selected.size() != originals.size()
                || !originals.get(0).equals(proposal.source())) return declined;
        var verified = propose(originals.get(0), proposal.readings());
        if (verified == null
                || !verified.value().equals(proposal.value())
                || !verified.work().page().equals(proposal.work().page())
                || !selected.get(0).equals(verified.work().page())) return declined;
        for (int i = 1; i < originals.size(); i++)
            if (!originals.get(i).equals(selected.get(i))) return declined;
        var work = CurvedTitleCompletion.select(hint, originals, verified.work());
        if (!work.accepted() || !work.pages().equals(selected) || !work.result().equals(baseline))
            return declined;
        String value = verified.value();
        if (phrase(baseline.title(), value)
                || phrase(baseline.artist(), value)
                || phrase(baseline.composer(), value)
                || phrase(baseline.arranger(), value)
                || baseline.unclassifiedCredits().stream().anyMatch(old -> phrase(old, value)))
            return declined;
        var anchor = verified.anchor();
        if (anchor == null) return declined;
        var b = anchor.mapBounds();
        var marker =
                new ScoreCreditsDetector.ReviewCredit(
                        new ScoreCreditsDetector.Line(
                                value, (float) b[0], (float) b[1], (float) b[2], (float) b[3]),
                        value);
        var first = selected.get(0);
        var reviews = new ArrayList<>(first.reviewCredits());
        reviews.add(marker);
        var retained =
                new ScoreCreditsDetector.Page(
                        first.width(),
                        first.height(),
                        first.notationTop(),
                        first.lines(),
                        first.creditsOnly(),
                        first.photographicCover(),
                        reviews);
        if (!retainedValue(retained, marker).equals(value)) return declined;
        var pages = new ArrayList<>(selected);
        pages.set(0, retained);
        var result = ScoreCreditsDetector.detect(hint, pages, Set.of());
        var expected = new ArrayList<>(baseline.unclassifiedCredits());
        expected.add(value);
        if (!result.title().equals(baseline.title())
                || !result.artist().equals(baseline.artist())
                || !result.composer().equals(baseline.composer())
                || !result.arranger().equals(baseline.arranger())
                || !result.unclassifiedCredits().equals(expected)) return declined;
        return new Selection(pages, result, true, true);
    }

    /** Existing typed review payload; the derived row never enters ordinary title/role OCR lines. */
    public static String retainedValue(
            ScoreCreditsDetector.Page page, ScoreCreditsDetector.ReviewCredit credit) {
        if (page == null
                || credit == null
                || credit.line() == null
                || !page.photographicCover()
                || page.creditsOnly()
                || !Float.isFinite(page.width())
                || !Float.isFinite(page.height())
                || !Float.isFinite(page.notationTop())
                || page.width() <= 0
                || page.height() <= 0
                || page.notationTop() >= 0
                || !name(credit.value())
                || !credit.value().equals(credit.line().text())) return "";
        var line = credit.line();
        if (!Float.isFinite(line.left())
                || !Float.isFinite(line.top())
                || !Float.isFinite(line.right())
                || !Float.isFinite(line.bottom())
                || line.left() < 0
                || line.top() < 0
                || line.right() > page.width()
                || line.bottom() > page.height() / 2
                || line.bottom() <= line.top()
                || line.right() <= line.left()
                || line.height() < page.width() * .025
                || line.height() > page.width() * .2
                || line.right() - line.left() < page.width() * .2
                || line.right() - line.left() > page.width() * .8
                || page.lines().stream()
                        .anyMatch(old -> old.equals(line) || phrase(old.text(), credit.value())))
            return "";
        return credit.value();
    }

    private static boolean cohort(
            CurvedTitleCompletion.Reading first,
            List<CurvedTitleCompletion.Reading> readings,
            ScoreCreditsDetector.Page source,
            boolean review) {
        if (!geometry(first, source) || first.widthFactor() != 1 && first.widthFactor() != 1.5)
            return false;
        boolean exact = false;
        for (int scale : new int[] {1, 2, 4}) {
            var matches =
                    readings.stream()
                            .filter(
                                    r ->
                                            r != null
                                                    && r.scale() == scale
                                                    && r.widthFactor() == first.widthFactor()
                                                    && CurvedTitleCompletion.sameRow(first, r))
                            .toList();
            if (matches.size() != 1) return false;
            var r = matches.get(0);
            if (!geometry(r, source)
                    || !literal(r.portable())
                    || !key(r.portable()).equals(key(first.portable()))
                    || !Double.isFinite(r.confidence())
                    || r.confidence() < (review ? .99 : .95)
                    || r.confidence() > 1
                    || r.nativeLines() == null
                    || r.nativeLines().size() != 1) return false;
            String nativeText = nativeText(r);
            if (nativeText == null) return false;
            if (key(nativeText).equals(key(r.portable()))) exact = true;
            else if (!review || !oneOmittedLetter(key(r.portable()), key(nativeText))) return false;
        }
        return exact;
    }

    private static String nativeText(CurvedTitleCompletion.Reading r) {
        var nativeLine = r.nativeLines().get(0);
        if (nativeLine == null
                || !bounds(nativeLine.line(), r)
                || !literal(nativeLine.line().text())
                || nativeLine.words() == null
                || nativeLine.words().isEmpty()) return null;
        var line = nativeLine.line();
        if (line.right() - line.left() < r.width() * .6) return null;
        double end = -1;
        var words = new StringBuilder();
        for (var word : nativeLine.words()) {
            if (!bounds(word, r)
                    || !literal(word.text())
                    || key(word.text()).contains(" ")
                    || word.left() < end
                    || word.left() < line.left()
                    || word.top() < line.top()
                    || word.right() > line.right()
                    || word.bottom() > line.bottom()) return null;
            if (words.length() > 0) words.append(' ');
            words.append(word.text());
            end = word.right();
        }
        return key(words.toString()).equals(key(line.text())) ? line.text() : null;
    }

    private static boolean oneOmittedLetter(String printed, String nativeText) {
        int[] a = printed.codePoints().toArray(), b = nativeText.codePoints().toArray();
        if (a.length < 10 || a.length != b.length + 1) return false;
        int i = 0, j = 0, missing = -1;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++;
                j++;
            } else if (missing < 0) {
                missing = i++;
            } else return false;
        }
        if (missing < 0) missing = i;
        return missing < a.length && Character.isLetter(a[missing]);
    }

    private static boolean above(
            CurvedTitleCompletion.Reading name, CurvedTitleCompletion.Reading work) {
        if (name.medianHeight() < work.medianHeight() * 1.2
                || name.medianHeight() < name.sourceWidth() * .02
                || name.medianHeight() > name.sourceWidth() * .1) return false;
        var a = name.mapBounds();
        var b = work.mapBounds();
        if (a[2] - a[0] < name.sourceWidth() * .2
                || a[2] - a[0] > name.sourceWidth() * .8
                || Math.abs((a[0] + a[2] - b[0] - b[2]) * .5) > name.sourceWidth() * .1)
            return false;
        double left = Math.max(a[0], b[0]) / name.sourceWidth();
        double right = Math.min(a[2], b[2]) / name.sourceWidth();
        if (left >= right) return false;
        double[] n = name.quadratic(), w = work.quadratic();
        double qa = w[0] - n[0], qb = w[1] - n[1], qc = w[2] - n[2];
        double minimum = Math.min((qa * left + qb) * left + qc, (qa * right + qb) * right + qc);
        if (qa > 0) {
            double vertex = -qb / (2 * qa);
            if (vertex > left && vertex < right)
                minimum = Math.min(minimum, (qa * vertex + qb) * vertex + qc);
        }
        return minimum >= (name.medianHeight() + work.medianHeight()) * .6;
    }

    private static boolean geometry(
            CurvedTitleCompletion.Reading r, ScoreCreditsDetector.Page page) {
        if (r == null
                || r.sourceWidth() != page.width()
                || r.sourceHeight() != (int) (page.height() / 2)
                || r.width() <= 0
                || r.height() <= 0
                || r.quadratic() == null
                || r.quadratic().length != 3
                || r.mapBounds() == null
                || r.mapBounds().length != 4
                || !Double.isFinite(r.medianHeight())
                || r.medianHeight() <= 0) return false;
        for (double v : r.quadratic()) if (!Double.isFinite(v)) return false;
        for (double v : r.mapBounds()) if (!Double.isFinite(v)) return false;
        var b = r.mapBounds();
        return b[0] >= 0
                && b[1] >= 0
                && b[2] < r.sourceWidth()
                && b[3] < r.sourceHeight()
                && b[2] > b[0]
                && b[3] > b[1];
    }

    private static boolean bounds(ScoreCreditsDetector.Line line, CurvedTitleCompletion.Reading r) {
        return line != null
                && Float.isFinite(line.left())
                && Float.isFinite(line.top())
                && Float.isFinite(line.right())
                && Float.isFinite(line.bottom())
                && line.left() >= 0
                && line.top() >= 0
                && line.right() <= r.width()
                && line.bottom() <= r.height()
                && line.right() > line.left()
                && line.bottom() > line.top();
    }

    private static boolean name(String value) {
        if (!literal(value)) return false;
        String text = value.trim();
        return text.codePointCount(0, text.length()) >= 6
                && text.codePointCount(0, text.length()) <= 80
                && text.split("\\s+").length <= 4
                && text.matches("[\\p{L}][\\p{L} .'’&-]*[\\p{L}]")
                && !text.matches(
                        "(?iu).*\\b(?:copyright|rights|reserved|publish(?:ed|er|ers|ing)?|press(?:es)?|websites?|printed|editions?|versions?|volumes?|books?|series|collections?|www|https?|transcribed|arranged|composed|performed|featuring)\\b.*");
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean phrase(String text, String value) {
        return (" " + key(text) + " ").contains(" " + key(value) + " ");
    }

    private static boolean literal(String s) {
        return s != null
                && !s.trim().isEmpty()
                && Normalizer.isNormalized(s, Normalizer.Form.NFC)
                && s.codePoints()
                        .noneMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT
                                                || Character.getType(c) == Character.LINE_SEPARATOR
                                                || Character.getType(c)
                                                        == Character.PARAGRAPH_SEPARATOR);
    }

    private static List<CurvedTitleCompletion.Reading> snapshot(
            List<CurvedTitleCompletion.Reading> readings) {
        var result = new ArrayList<CurvedTitleCompletion.Reading>();
        for (var r : readings) {
            if (r == null) {
                result.add(null);
                continue;
            }
            List<CurvedTitleCompletion.NativeLine> lines = null;
            if (r.nativeLines() != null) {
                var copy = new ArrayList<CurvedTitleCompletion.NativeLine>();
                for (var line : r.nativeLines())
                    copy.add(
                            line == null
                                    ? null
                                    : new CurvedTitleCompletion.NativeLine(
                                            line.line(),
                                            line.words() == null
                                                    ? null
                                                    : java.util.Collections.unmodifiableList(
                                                            new ArrayList<>(line.words()))));
                lines = java.util.Collections.unmodifiableList(copy);
            }
            result.add(
                    new CurvedTitleCompletion.Reading(
                            r.scale(),
                            r.widthFactor(),
                            r.sourceWidth(),
                            r.sourceHeight(),
                            r.quadratic() == null ? null : r.quadratic().clone(),
                            r.medianHeight(),
                            r.mapBounds() == null ? null : r.mapBounds().clone(),
                            r.width(),
                            r.height(),
                            r.portable(),
                            r.confidence(),
                            lines));
        }
        return java.util.Collections.unmodifiableList(result);
    }
}
