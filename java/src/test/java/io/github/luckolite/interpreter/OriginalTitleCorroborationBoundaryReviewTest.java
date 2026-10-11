// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fictional review counterexamples. No original library wording. */
public class OriginalTitleCorroborationBoundaryReviewTest {
    private ScoreCreditsDetector.Line source(String text) {
        return new ScoreCreditsDetector.Line(text, 200, 160, 1600, 350);
    }

    private OriginalTitleCorroboration.Reading reading(int scale, String text) {
        String[] tokens = text.split(" ");
        var words = new ArrayList<ScoreCreditsDetector.Line>();
        float step = 980f / tokens.length;
        for (int i = 0; i < tokens.length; i++)
            words.add(
                    new ScoreCreditsDetector.Line(
                            tokens[i],
                            (10 + i * step) * scale,
                            20 * scale,
                            (10 + (i + 1) * step - 2) * scale,
                            180 * scale));
        var line =
                new ScoreCreditsDetector.Line(
                        text, 10 * scale, 20 * scale, 990 * scale, 180 * scale);
        return new OriginalTitleCorroboration.Reading(
                scale,
                text,
                .99f,
                1000 * scale,
                200 * scale,
                List.of(new OriginalTitleCorroboration.NativeLine(line, words)));
    }

    private OriginalTitleCorroboration.Proposal proposal(
            ScoreCreditsDetector.Page page, ScoreCreditsDetector.Line original, String candidate) {
        return OriginalTitleCorroboration.propose(
                page,
                original,
                List.of(reading(1, candidate), reading(2, candidate), reading(4, candidate)));
    }

    private OriginalTitleCorroboration.Proposal proposal(String original, String candidate) {
        var line = source(original);
        return proposal(
                new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(line), false, true),
                line,
                candidate);
    }

    @Test
    public void positiveEmbeddedZeroKeepsReviewOwnership() {
        var p = proposal("C00L RIVER", "COOL RIVER");
        assertNotNull(p);
        assertTrue(p.requiresReview());
    }

    @Test
    public void intendedInteriorApostropheDifferenceCanRemainReviewEvidence() {
        var p = proposal("IM C00L", "I'M COOL");
        assertNotNull(p);
        assertTrue(p.punctuationDiffers());
    }

    @Test
    public void slashInsideZeroTokenCannotBecomeAWord() {
        assertNull(proposal("C0/0L RIVER", "COOL RIVER"));
    }

    @Test
    public void hyphenInsideZeroTokenCannotBecomeAWord() {
        assertNull(proposal("C0-0L RIVER", "COOL RIVER"));
    }

    @Test
    public void dotInsideZeroTokenCannotBecomeAWord() {
        assertNull(proposal("C0.0L RIVER", "COOL RIVER"));
    }

    @Test
    public void unrelatedSlashTokenCannotLoseItsBoundary() {
        assertNull(proposal("C00L A/B", "COOL AB"));
    }

    @Test
    public void unrelatedHyphenatedWordCannotBeJoined() {
        assertNull(proposal("C00L RE-ENTRY", "COOL REENTRY"));
    }

    @Test
    public void unrelatedCompatibilityLigatureCannotChangeCodepoints() {
        assertNull(proposal("C00L ﬂOWER", "COOL FLOWER"));
    }

    @Test
    public void suggestedControlCharacterMustBeRejected() {
        assertNull(proposal("C00L RIVER", "CO\u0000OL RIVER"));
    }

    @Test
    public void suggestedBidiControlMustBeRejected() {
        assertNull(proposal("C00L RIVER", "CO\u202EOL RIVER"));
    }

    @Test
    public void sourceEmbeddedLineBreakMustBeRejected() {
        assertNull(proposal("C00L\n RIVER", "COOL RIVER"));
    }

    @Test
    public void nonfiniteNotationTopMustBeRejected() {
        var line = source("C00L RIVER");
        assertNull(
                proposal(
                        new ScoreCreditsDetector.Page(
                                1800, 1000, Float.NaN, List.of(line), false, true),
                        line,
                        "COOL RIVER"));
    }

    @Test
    public void infinitePageHeightMustBeRejected() {
        var line = source("C00L RIVER");
        assertNull(
                proposal(
                        new ScoreCreditsDetector.Page(
                                1800, Float.POSITIVE_INFINITY, -1, List.of(line), false, true),
                        line,
                        "COOL RIVER"));
    }

    @Test
    public void originalNegativeLeftMustBeRejected() {
        var line = new ScoreCreditsDetector.Line("C00L RIVER", -1, 160, 1600, 350);
        assertNull(
                proposal(
                        new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(line), false, true),
                        line,
                        "COOL RIVER"));
    }
}
