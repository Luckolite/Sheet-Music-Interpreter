// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Bind a detached service mark to existing word boxes without rewriting OCR text. */
public final class PrintedServiceCreditMarks {
    private PrintedServiceCreditMarks() {}

    private static final Pattern TRANSCRIPTION =
            Pattern.compile("(?iu)^(?:transcribed|transcription)\\h+by\\h+.+$");

    @FunctionalInterface
    public interface Pixels {
        int[] read(int left, int top, int width, int height);
    }

    public record Candidate(
            ScoreCreditsDetector.Line line,
            String value,
            int left,
            int top,
            int right,
            int bottom) {}

    /** At most two payload boundaries per explicit byline; no contributor spelling is guessed. */
    public static List<Candidate> candidates(
            ScoreCreditsDetector.Page page, List<ScoreCreditsDetector.Line> words) {
        if (!Float.isFinite(page.width())
                || !Float.isFinite(page.height())
                || !Float.isFinite(page.notationTop())
                || page.width() <= 0
                || page.height() <= 0
                || words == null) return List.of();
        var result = new ArrayList<Candidate>();
        float limit = page.notationTop() > 0 ? page.notationTop() : page.height() * .85f;
        for (var line : page.lines()) {
            if (!finite(line)
                    || line.text() == null
                    || !TRANSCRIPTION.matcher(line.text().strip()).matches()
                    || line.top() < 0
                    || line.bottom() > limit) continue;
            float height = line.bottom() - line.top();
            if (height < 16 || height > page.width() * .09f) continue;
            var located =
                    words.stream()
                            .filter(w -> belongs(line, w, height))
                            .sorted(Comparator.comparingDouble(ScoreCreditsDetector.Line::left))
                            .toList();
            if (located.size() < 3
                    || located.size() > 12
                    || !located.get(0).text().matches("(?iu)transcribed|transcription")
                    || !located.get(1).text().equalsIgnoreCase("by")) continue;
            var by = located.get(1);
            for (int start = 2; start < Math.min(located.size(), 4); start++) {
                int left = (int) Math.floor(by.right()) + 1;
                int right = (int) Math.ceil(located.get(start).left()) - 1;
                int padding = (int) Math.ceil(height * .1f);
                int top = Math.max(0, (int) Math.floor(line.top()) - padding);
                int bottom =
                        Math.min((int) page.height(), (int) Math.ceil(line.bottom()) + padding);
                if (left < 0
                        || right > page.width()
                        || right - left < 16
                        || right - left > height * 2
                        || right - left > 384
                        || bottom - top < 16
                        || bottom - top > 384) continue;
                String value =
                        String.join(
                                        " ",
                                        located.subList(start, located.size()).stream()
                                                .map(ScoreCreditsDetector.Line::text)
                                                .toList())
                                .strip();
                if (value.length() > 120
                        || value.codePoints().filter(Character::isLetter).count() < 3) continue;
                result.add(new Candidate(line, value, left, top, right, bottom));
            }
        }
        return List.copyOf(result);
    }

    private static boolean belongs(
            ScoreCreditsDetector.Line line, ScoreCreditsDetector.Line word, float height) {
        if (!finite(word)) return false;
        float h = word.bottom() - word.top();
        return word.text() != null
                && !word.text().isBlank()
                && !word.text().matches(".*\\s+.*")
                && h >= height * .45f
                && h <= height * 1.8f
                && word.left() >= line.left() - 2
                && word.right() <= line.right() + 2
                && (word.top() + word.bottom()) * .5f >= line.top() - height * .35f
                && (word.top() + word.bottom()) * .5f <= line.bottom() + height * .35f;
    }

    private static boolean finite(ScoreCreditsDetector.Line line) {
        return line != null
                && Float.isFinite(line.left())
                && Float.isFinite(line.top())
                && Float.isFinite(line.right())
                && Float.isFinite(line.bottom())
                && line.left() < line.right()
                && line.top() < line.bottom();
    }

    /** Pixel confirmation carries a typed review credit; a real initial remains ordinary text. */
    public static ScoreCreditsDetector.Page retain(
            ScoreCreditsDetector.Page page, List<ScoreCreditsDetector.Line> words, Pixels pixels) {
        var review = new ArrayList<>(page.reviewCredits());
        for (var c : candidates(page, words)) {
            if (review.stream().anyMatch(e -> e.line().equals(c.line()))) continue;
            int width = c.right() - c.left(), height = c.bottom() - c.top();
            if (DetachedWaveformCreditMark.matches(
                    pixels.read(c.left(), c.top(), width, height), width, height))
                review.add(new ScoreCreditsDetector.ReviewCredit(c.line(), c.value()));
        }
        return review.equals(page.reviewCredits())
                ? page
                : new ScoreCreditsDetector.Page(
                        page.width(),
                        page.height(),
                        page.notationTop(),
                        page.lines(),
                        page.creditsOnly(),
                        page.photographicCover(),
                        review);
    }
}
