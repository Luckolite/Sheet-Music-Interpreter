// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A title suggestion for the explicit review UI, retaining its literal source page. */
public final class OriginalTitleReview {
    private OriginalTitleReview() {}

    public record Evidence(
            ScoreCreditsDetector.Page originalPage,
            ScoreCreditsDetector.Page page,
            OriginalTitleCorroboration.Proposal proposal) {}

    public static Evidence qualify(
            ScoreCreditsDetector.Page source,
            ScoreCreditsDetector.Line original,
            List<OriginalTitleCorroboration.Reading> readings) {
        var proposal = OriginalTitleCorroboration.propose(source, original, readings);
        if (proposal == null) return new Evidence(source, source, null);
        var lines = new ArrayList<>(source.lines());
        int index = lines.indexOf(original);
        if (index < 0) return new Evidence(source, source, null);
        lines.set(
                index,
                new ScoreCreditsDetector.Line(
                        proposal.suggestedText(),
                        original.left(),
                        original.top(),
                        original.right(),
                        original.bottom()));
        var review =
                new ScoreCreditsDetector.Page(
                        source.width(),
                        source.height(),
                        source.notationTop(),
                        lines,
                        source.creditsOnly(),
                        source.photographicCover(),
                        source.reviewCredits());
        var before = ScoreCreditsDetector.detect("", List.of(source), Set.of());
        var after = ScoreCreditsDetector.detect("", List.of(review), Set.of());
        if (!after.title().equals(proposal.suggestedText()) || !sameContributors(before, after))
            return new Evidence(source, source, null);
        return new Evidence(source, review, proposal);
    }

    public record Selection(
            List<ScoreCreditsDetector.Page> pages,
            ScoreCreditsDetector.Result result,
            boolean proposalAccepted) {
        public Selection {
            pages = List.copyOf(pages);
        }
    }

    /** Later-page credits can depend on the chosen title. Restore the whole source input on any role change. */
    public static Selection select(
            String titleHint,
            List<ScoreCreditsDetector.Page> originalPages,
            List<ScoreCreditsDetector.Page> candidatePages) {
        var before = ScoreCreditsDetector.detect(titleHint, originalPages, Set.of());
        var after = ScoreCreditsDetector.detect(titleHint, candidatePages, Set.of());
        if (before.title().equals(after.title()) || !sameContributors(before, after))
            return new Selection(originalPages, before, false);
        return new Selection(candidatePages, after, true);
    }

    public static boolean sameContributors(
            ScoreCreditsDetector.Result first, ScoreCreditsDetector.Result second) {
        return first.artist().equals(second.artist())
                && first.composer().equals(second.composer())
                && first.arranger().equals(second.arranger())
                && first.unclassifiedCredits().equals(second.unclassifiedCredits());
    }
}
