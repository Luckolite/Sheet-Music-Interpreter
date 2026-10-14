// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Locale;

/** Bounded original-pixel fallback for clipped outside letters in a located cover heading. */
public final class LocatedCoverHeadingExpansion {
    private LocatedCoverHeadingExpansion() {}

    public record Region(float left, float top, float right, float bottom) {}

    public static Region region(ScoreCreditsDetector.Page page, ScoreCreditsDetector.Line line) {
        if (!Float.isFinite(page.width())
                || !Float.isFinite(page.height())
                || page.width() <= 0
                || page.height() <= 0
                || !Float.isFinite(line.left())
                || !Float.isFinite(line.top())
                || !Float.isFinite(line.right())
                || !Float.isFinite(line.bottom())
                || line.left() < 0
                || line.top() < 0
                || line.right() > page.width()
                || line.bottom() > page.height()
                || line.right() <= line.left()
                || line.bottom() <= line.top()) return null;
        if (!page.photographicCover()
                || !ScoreCreditsDetector.locatedCoverHeadingCandidates(page).contains(line))
            return null;
        float pad = Math.min(page.width() * .02f, Math.max(4, (line.bottom() - line.top()) * .15f));
        float left = Math.max(0, line.left() - pad),
                right = Math.min(page.width(), line.right() + pad);
        if (!Float.isFinite(left) || !Float.isFinite(right) || right <= left) return null;
        for (var other : page.lines()) {
            if (other.equals(line)) continue;
            if (other.left() < right
                    && other.right() > left
                    && other.top() < line.bottom()
                    && other.bottom() > line.top()) return null;
        }
        return new Region(left, line.top(), right, line.bottom());
    }

    public static LocatedCoverHeadingEvidence confirm(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line original,
            List<String> readings,
            List<Float> confidence) {
        if (region(page, original) == null
                || readings == null
                || readings.size() != 3
                || readings.get(0) == null) return null;
        String before = original.text().strip().toLowerCase(Locale.ROOT),
                after = readings.get(0).strip().toLowerCase(Locale.ROOT);
        int extra = after.length() - before.length();
        if (extra < 1 || extra > 2) return null;
        String added =
                after.endsWith(before)
                        ? after.substring(0, extra)
                        : after.startsWith(before) ? after.substring(before.length()) : "";
        if (added.length() != extra || !added.codePoints().allMatch(Character::isLetter))
            return null;
        return LocatedCoverHeadingEvidence.fromOriginal(page, original, readings, confidence);
    }
}
