// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Optional;

/** Synthetic anchorless page projection; contains no score-derived coordinates. */
public final class PublicNaturalHarmonicPageProjectionOriginal {
    public static void main(String[] args) {
        var mark =
                new ScoreExpressiveEvent(
                        "synthetic:page-projection",
                        ScoreExpressiveEvent.Kind.NATURAL_HARMONIC,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        1,
                        Optional.of("natural-harmonic-note-v1:2:0:1:3f000000:3:3e800000"),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-natural-harmonic", 0, .5f, 0, 1, "circle")));
        var selected = ScorePageInterpretation.expressionsStartingOnPage(List.of(mark), 2, 3);
        if (selected.size() != 1) throw new AssertionError("Target measure was not page-owned");
        if (!selected.get(0)
                .targetEventId()
                .orElseThrow()
                .equals("natural-harmonic-note-v1:0:0:1:3f000000:3:3e800000"))
            throw new AssertionError("Anchorless target did not follow page measure offset");
        if (!ScorePageInterpretation.expressionsStartingOnPage(List.of(mark), 0, 2).isEmpty())
            throw new AssertionError("Anchorless target leaked onto an unrelated page");
        if (!ScorePageInterpretation.expressionsStartingOnPage(List.of(mark), 3, 4).isEmpty())
            throw new AssertionError("Target beyond page range was retained");
        System.out.println("PASS synthetic anchorless natural-harmonic page ownership and offset");
    }
}
