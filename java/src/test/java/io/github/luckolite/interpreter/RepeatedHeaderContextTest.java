// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fictional printed headers; import labels supply no missing title characters. */
public class RepeatedHeaderContextTest {
    private static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }

    private static ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line... lines) {
        return new ScoreCreditsDetector.Page(1800, 2546, 400, List.of(lines));
    }

    private static final ScoreCreditsDetector.Line STUB = line("Vae", 1600, 110, 1710, 155);
    private static final ScoreCreditsDetector.Line TITLE =
            line("Paper Lantern Waltz", 650, 80, 1150, 132);
    private static final ScoreCreditsDetector.Line REPEAT =
            line("Paper Lantern Waltz", 1430, 160, 1715, 186);

    private static List<ScoreCreditsDetector.Line> crop() {
        return List.of(
                TITLE,
                REPEAT,
                line("Elara Vale", 1530, 120, 1715, 150),
                line("Transcribed by Rowan Moss", 1340, 200, 1715, 235));
    }

    @Test
    public void repeatedPrintedTitleSurvivesArtistFirstImportLabel() {
        assertTrue(
                ScoreCreditsDetector.preferHeaderCrop(
                        page(STUB), crop(), "Elara Vale - Paper Lantern Waltz"));
    }

    @Test
    public void repeatedPrintedTitleDoesNotDependOnImportLabel() {
        assertTrue(ScoreCreditsDetector.preferHeaderCrop(page(STUB), crop(), "Unrelated upload"));
        var merged = ScoreCreditsDetector.mergeHeaderCrop(List.of(STUB), crop(), 400);
        var result =
                ScoreCreditsDetector.detect(
                        "Unrelated upload",
                        List.of(page(merged.toArray(ScoreCreditsDetector.Line[]::new))),
                        Set.of());
        assertEquals("Paper Lantern Waltz", result.title());
        assertEquals("Rowan Moss", result.arranger());
    }

    @Test
    public void singleNewHeadingDoesNotDisplaceExistingTextWithoutCorroboration() {
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(
                        page(STUB), List.of(TITLE), "Unrelated upload"));
    }

    @Test
    public void containedDuplicateIsNotASecondPrintedHeading() {
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(
                        page(STUB),
                        List.of(TITLE, line("Paper Lantern Waltz", 660, 83, 1140, 129)),
                        "Unrelated upload"));
    }

    @Test
    public void aTrueCenteredFullPageHeadingIsRetained() {
        var actual = line("Silver Harbor", 620, 78, 1180, 134);
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(page(actual), crop(), "Unrelated upload"));
    }

    @Test
    public void aWorkIdentifierCannotBeReplacedByAFilename() {
        var actual = line("Paper Lantern Waltz No. 4", 600, 80, 1200, 132);
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(page(actual), crop(), "Paper Lantern Waltz"));
    }

    @Test
    public void differentPrintedWordsDoNotCorroborateTheHeading() {
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(
                        page(STUB),
                        List.of(TITLE, line("Paper Lantern Study", 1430, 160, 1715, 186)),
                        "Unrelated upload"));
    }

    @Test
    public void aFooterRepeatDoesNotCorroborateHeaderContext() {
        assertFalse(
                ScoreCreditsDetector.preferHeaderCrop(
                        page(STUB),
                        List.of(TITLE, line("Paper Lantern Waltz", 1430, 2300, 1715, 2326)),
                        "Unrelated upload"));
    }
}
