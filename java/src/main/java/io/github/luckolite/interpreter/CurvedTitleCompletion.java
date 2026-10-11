// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

/** Review-only completion of a title already located on a curved cover row. */
public final class CurvedTitleCompletion {
    private CurvedTitleCompletion() {}

    public record NativeLine(
            ScoreCreditsDetector.Line line, List<ScoreCreditsDetector.Line> words) {}

    public record Reading(
            int scale,
            double widthFactor,
            int sourceWidth,
            int sourceHeight,
            double[] quadratic,
            double medianHeight,
            double[] mapBounds,
            int width,
            int height,
            String portable,
            double confidence,
            List<NativeLine> nativeLines) {}

    public record Proposal(
            ScoreCreditsDetector.Page originalPage,
            ScoreCreditsDetector.Page page,
            String literalOriginal,
            String suggestedText,
            List<ScoreCreditsDetector.Line> sourceFragments,
            boolean requiresReview) {}

    public static Proposal propose(ScoreCreditsDetector.Page source, List<Reading> readings) {
        if (!safePage(source)
                || readings == null
                || !source.photographicCover()
                || source.creditsOnly()
                || !Float.isFinite(source.notationTop())
                || source.notationTop() >= 0) return null;
        var before = ScoreCreditsDetector.detect("", List.of(source), Set.of());
        var originals =
                source.lines().stream().filter(l -> l.text().equals(before.title())).toList();
        if (originals.size() != 1) return null;
        var original = originals.get(0);
        String prefix = key(original.text());
        if (!oneLine(original.text()) || prefix.split(" ").length < 2) return null;
        var proposals = new ArrayList<Proposal>();
        var proposalRows = new ArrayList<Reading>();
        var acceptedRows = new ArrayList<Reading>();
        for (var first : readings) {
            if (first == null
                    || first.scale() != 1
                    || (first.widthFactor() != 1 && first.widthFactor() != 1.5)
                    || !valid(first, source)) continue;
            String candidate = key(first.portable());
            if (!candidate.startsWith(prefix + " ")) continue;
            boolean stable = true;
            for (int scale : new int[] {2, 4}) {
                var matches =
                        readings.stream()
                                .filter(
                                        r ->
                                                r != null
                                                        && r.scale() == scale
                                                        && r.widthFactor() == first.widthFactor()
                                                        && sameRow(first, r))
                                .toList();
                if (matches.size() != 1
                        || !valid(matches.get(0), source)
                        || !key(matches.get(0).portable()).equals(candidate)) {
                    stable = false;
                    break;
                }
            }
            if (!stable) continue;
            var fragments = new ArrayList<ScoreCreditsDetector.Line>();
            for (var line : source.lines())
                if (inside(line, first.mapBounds(), first.medianHeight() * .5)) fragments.add(line);
            fragments.sort(Comparator.comparingDouble(ScoreCreditsDetector.Line::left));
            if (fragments.size() < 2 || !fragments.contains(original)) continue;
            int previousEnd = -1;
            boolean matches = true;
            for (var fragment : fragments) {
                String token = key(fragment.text());
                int position = (" " + candidate + " ").indexOf(" " + token + " ");
                if (!oneLine(fragment.text())
                        || token.isEmpty()
                        || position < 0
                        || position < previousEnd) {
                    matches = false;
                    break;
                }
                previousEnd = position + token.length();
            }
            if (!matches) continue;
            var lines = new ArrayList<>(source.lines());
            lines.set(
                    lines.indexOf(original),
                    new ScoreCreditsDetector.Line(
                            first.portable().trim(),
                            original.left(),
                            original.top(),
                            original.right(),
                            original.bottom()));
            var page =
                    new ScoreCreditsDetector.Page(
                            source.width(),
                            source.height(),
                            source.notationTop(),
                            lines,
                            source.creditsOnly(),
                            source.photographicCover(),
                            source.reviewCredits());
            var after = ScoreCreditsDetector.detect("", List.of(page), Set.of());
            if (!after.title().equals(first.portable().trim()) || !sameContributors(before, after))
                continue;
            for (var accepted : acceptedRows)
                if (accepted.widthFactor() == first.widthFactor() && sameRow(first, accepted))
                    return null;
            acceptedRows.add(first);
            boolean duplicateWidth = false, corroboratedWidth = false;
            for (int i = 0; i < proposals.size(); i++) {
                var prior = proposals.get(i);
                var priorRow = proposalRows.get(i);
                if (sameRow(first, priorRow)
                        && prior.sourceFragments().equals(fragments)
                        && prior.suggestedText().equals(first.portable().trim())) {
                    if (priorRow.widthFactor() == first.widthFactor()) duplicateWidth = true;
                    else corroboratedWidth = true;
                }
            }
            if (duplicateWidth) return null;
            if (!corroboratedWidth) {
                proposals.add(
                        new Proposal(
                                source,
                                page,
                                original.text(),
                                first.portable().trim(),
                                List.copyOf(fragments),
                                true));
                proposalRows.add(first);
            }
        }
        return proposals.size() == 1 ? proposals.get(0) : null;
    }

    private static boolean safePage(ScoreCreditsDetector.Page page) {
        if (page == null
                || !Float.isFinite(page.width())
                || !Float.isFinite(page.height())
                || page.width() <= 0
                || page.height() <= 0) return false;
        for (var line : page.lines()) if (!sourceLine(line, page)) return false;
        for (var credit : page.reviewCredits())
            if (credit == null || !sourceLine(credit.line(), page) || !oneLine(credit.value()))
                return false;
        return true;
    }

    private static boolean sourceLine(
            ScoreCreditsDetector.Line line, ScoreCreditsDetector.Page page) {
        return line != null
                && oneLine(line.text())
                && Float.isFinite(line.left())
                && Float.isFinite(line.top())
                && Float.isFinite(line.right())
                && Float.isFinite(line.bottom())
                && line.left() >= 0
                && line.top() >= 0
                && line.right() <= page.width()
                && line.bottom() <= page.height()
                && line.right() > line.left()
                && line.bottom() > line.top();
    }

    private static boolean sameContributors(
            ScoreCreditsDetector.Result a, ScoreCreditsDetector.Result b) {
        return a.artist().equals(b.artist())
                && a.composer().equals(b.composer())
                && a.arranger().equals(b.arranger())
                && a.unclassifiedCredits().equals(b.unclassifiedCredits());
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean oneLine(String s) {
        return s != null
                && !s.isEmpty()
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

    private static boolean inside(ScoreCreditsDetector.Line line, double[] box, double padding) {
        return line.left() >= box[0] - padding
                && line.top() >= box[1] - padding
                && line.right() <= box[2] + padding
                && line.bottom() <= box[3] + padding;
    }

    private static boolean geometry(Reading r) {
        if (r == null
                || r.sourceWidth() <= 0
                || r.sourceHeight() <= 0
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

    private static boolean valid(Reading r, ScoreCreditsDetector.Page source) {
        if (!geometry(r)
                || r.sourceWidth() != source.width()
                || r.sourceHeight() != (int) (source.height() / 2)
                || !Double.isFinite(r.confidence())
                || r.confidence() < .95
                || r.confidence() > 1
                || !oneLine(r.portable())
                || key(r.portable()).isEmpty()
                || r.nativeLines() == null
                || r.nativeLines().size() != 1) return false;
        var nativeLine = r.nativeLines().get(0);
        if (nativeLine == null) return false;
        var line = nativeLine.line();
        if (!bounds(line, r)
                || !oneLine(line.text())
                || !key(line.text()).equals(key(r.portable()))
                || nativeLine.words() == null
                || nativeLine.words().isEmpty()) return false;
        var words = new StringBuilder();
        double end = -1;
        for (var word : nativeLine.words()) {
            if (!bounds(word, r)
                    || word.left() < line.left()
                    || word.top() < line.top()
                    || word.right() > line.right()
                    || word.bottom() > line.bottom()
                    || !oneLine(word.text())
                    || word.left() < end
                    || key(word.text()).isEmpty()
                    || key(word.text()).contains(" ")) return false;
            if (words.length() != 0) words.append(' ');
            words.append(word.text());
            end = word.right();
        }
        return key(words.toString()).equals(key(line.text()))
                && line.right() - line.left() >= r.width() * .6;
    }

    private static boolean bounds(ScoreCreditsDetector.Line line, Reading r) {
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

    static boolean sameRow(Reading a, Reading b) {
        if (!geometry(a)
                || !geometry(b)
                || a.sourceWidth() != b.sourceWidth()
                || a.sourceHeight() != b.sourceHeight()
                || b.medianHeight() / a.medianHeight() < .8
                || b.medianHeight() / a.medianHeight() > 1.25) return false;
        double[] x = a.mapBounds(), y = b.mapBounds();
        double left = Math.max(x[0], y[0]), right = Math.min(x[2], y[2]);
        double top = Math.max(x[1], y[1]), bottom = Math.min(x[3], y[3]);
        if (right <= left || bottom <= top) return false;
        double intersection = (right - left) * (bottom - top),
                union =
                        (x[2] - x[0]) * (x[3] - x[1])
                                + (y[2] - y[0]) * (y[3] - y[1])
                                - intersection;
        if (intersection / union < .85) return false;
        for (double xx : new double[] {left, (left + right) / 2, right}) {
            double t = xx / a.sourceWidth();
            double ay = (a.quadratic()[0] * t + a.quadratic()[1]) * t + a.quadratic()[2];
            double by = (b.quadratic()[0] * t + b.quadratic()[1]) * t + b.quadratic()[2];
            if (Math.abs(ay - by) > Math.min(a.medianHeight(), b.medianHeight()) * .5) return false;
        }
        return true;
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

    /** Only the original first page can be replaced; later credits are checked under the real caller hint. */
    public static Selection select(
            String titleHint, List<ScoreCreditsDetector.Page> originals, Proposal proposal) {
        var before = ScoreCreditsDetector.detect(titleHint, originals, Set.of());
        if (proposal == null
                || originals.isEmpty()
                || !originals.get(0).equals(proposal.originalPage()))
            return new Selection(originals, before, false, true);
        var candidates = new ArrayList<>(originals);
        candidates.set(0, proposal.page());
        var after = ScoreCreditsDetector.detect(titleHint, candidates, Set.of());
        if (!after.title().equals(proposal.suggestedText())
                || before.title().equals(after.title())
                || !sameContributors(before, after))
            return new Selection(originals, before, false, true);
        return new Selection(candidates, after, true, true);
    }
}
