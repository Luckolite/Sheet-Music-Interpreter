// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Three strong original-PDF readings of the same located photographic heading. */
public final class LocatedCoverHeadingEvidence {
    private final ScoreCreditsDetector.Line original;
    private final ScoreCreditsDetector.Line confirmed;

    private LocatedCoverHeadingEvidence(
            ScoreCreditsDetector.Line original, ScoreCreditsDetector.Line confirmed) {
        this.original = original;
        this.confirmed = confirmed;
    }

    public ScoreCreditsDetector.Line confirmed() {
        return confirmed;
    }

    /** A coarse photographic title does not verify every located heading or caption. */
    public static boolean needsOriginalReads(
            ScoreCreditsDetector.Page page, List<ScoreCreditsDetector.Line> coarseTitle) {
        return page.photographicCover() || coarseTitle.equals(page.lines());
    }

    /** Confidence supplements exact agreement; import labels are not recognition evidence. */
    public static LocatedCoverHeadingEvidence fromOriginal(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line original,
            List<String> readings,
            List<Float> confidence) {
        if (!page.photographicCover()
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(c -> c == null || !Float.isFinite(c) || c < .95f || c > 1))
            return null;
        var confirmed = ScoreCreditsDetector.locatedCoverHeadingReading(page, original, readings);
        return confirmed == null ? null : new LocatedCoverHeadingEvidence(original, confirmed);
    }

    /** Exact-crop agreement may protect an unchanged heading without authorizing replacement.
     * Expanded crops must retain the stronger fromOriginal rule. */
    public static LocatedCoverHeadingEvidence fromExactOriginal(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line original,
            List<String> readings,
            List<Float> confidence) {
        var strong = fromOriginal(page, original, readings, confidence);
        if (strong != null) return strong;
        if (!page.photographicCover()
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(c -> c == null || !Float.isFinite(c) || c < .90f || c > 1))
            return null;
        var confirmed = ScoreCreditsDetector.locatedCoverHeadingReading(page, original, readings);
        if (confirmed == null || !confirmed.text().equals(original.text())) return null;
        return new LocatedCoverHeadingEvidence(original, original);
    }

    /** Only the unchanged nominated line may be replaced; other merged headings stay independent. */
    public static List<ScoreCreditsDetector.Line> merge(
            ScoreCreditsDetector.Page page, List<LocatedCoverHeadingEvidence> evidence) {
        if (!page.photographicCover()) return page.lines();
        var merged = new ArrayList<>(page.lines());
        for (var item : evidence) {
            int index = merged.indexOf(item.original);
            if (index >= 0) merged.set(index, item.confirmed);
        }
        return List.copyOf(merged);
    }
}
