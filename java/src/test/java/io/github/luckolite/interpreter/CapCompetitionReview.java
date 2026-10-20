// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Independently authored sourcefree near-end head: a longer cap must not shadow its own stem. */
public final class CapCompetitionReview {
    public static void main(String[] args) {
        var p = new HeavyRestBarFragmentTest.Page();
        p.rect(90, 106, 230, 117, 1);
        p.rect(90, 64, 94, 160, 1);
        p.rect(228, 96, 230, 128, 1);
        p.head(101, 112, 5, 4);
        p.rect(106, 80, 106, 112, 1);
        p.head(300, 136, 10, 7);
        p.rect(310, 88, 310, 136, 1);
        int normalized = p.normalize()[112 * 400 + 101];
        var analysis = p.analyze();
        System.out.println("normalizedHeadLabel=" + normalized + "; expected=2");
        System.out.println("analysisNotes=" + analysis.notes().size() + "; expected=2");
        System.out.println("events=" + analysis.notes());
        if (normalized != 2 || analysis.notes().size() != 2)
            throw new AssertionError(
                    "Independent written head lost when longer cap wins shaft selection");
    }
}
