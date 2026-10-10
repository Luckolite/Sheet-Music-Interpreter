// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class StaffSizedTitleEvidenceTest {
    @Test
    public void sectionAndAdjacentDirectionCannotReplaceDocumentHint() {
        var page =
                page(
                        List.of(
                                line("HARBOR", 340, 420, 440, 440),
                                line("Freely", 340, 455, 410, 480)));
        assertEquals("Songs of the Coast", detect("Songs of the Coast", page));
    }

    @Test
    public void sparseSquareBodyDebrisAndMarginPartLabelCannotBecomeTitle() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2500,
                        725,
                        List.of(
                                line("MROTIA", 90, 315, 180, 347),
                                line("hvu", 1070, 615, 1105, 647)));
        assertEquals("Across the Harbor", detect("Across the Harbor", page));
    }

    @Test
    public void corroboratedSmallSectionHeadingRemainsAvailable() {
        var page =
                page(
                        List.of(
                                line("HARBOR", 340, 420, 440, 440),
                                line("Freely", 340, 455, 410, 480)));
        assertEquals("Harbor", detect("Harbor", page));
    }

    @Test
    public void prominentOffCenterPrintedHeadingOverridesUnrelatedHint() {
        var page =
                page(
                        List.of(
                                line("HARBOR", 340, 150, 680, 220),
                                line("Freely", 340, 455, 410, 480)));
        assertEquals("HARBOR", detect("Untitled Import", page));
    }

    @Test
    public void smallCenteredHeadingRemainsAvailable() {
        var page = page(List.of(line("Bird", 840, 100, 960, 125)));
        assertEquals("Bird", detect("Untitled Import", page));
    }

    @Test
    public void shortSquareOpeningTitleIsNotBodyDebris() {
        var page = page(List.of(line("Sky", 880, 100, 915, 130)));
        assertEquals("Sky", detect("Untitled Import", page));
    }

    @Test
    public void photographicArtworkDoesNotUseStaffSizedDebrisRules() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800, 2500, 725, List.of(line("Sun", 1070, 615, 1110, 647)), false, true);
        assertEquals("Sun", detect("Untitled Import", page));
    }

    @Test
    public void independentlyPrintedTitleDoesNotRequireCallerCorroboration() {
        var page = page(List.of(line("HARBOR", 340, 420, 440, 440)));
        assertEquals("HARBOR", detect("Untitled Import", page));
    }

    @Test
    public void shortCenteredHeadingWellAboveStaffIsNotBodyDebris() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800, 2500, 900, List.of(line("Hymn", 880, 400, 920, 432)));
        assertEquals("Hymn", detect("Untitled Import", page));
    }

    @Test
    public void sameBaselineDirectionWordIsPartOfPrintedHeading() {
        var page =
                page(
                        List.of(
                                line("FREELY", 340, 420, 640, 470),
                                line("AT DAWN", 680, 420, 1050, 470)));
        assertEquals("FREELY AT DAWN", detect("Untitled Import", page));
    }

    @Test
    public void narrowMarginTitleWithoutBodyDebrisRemainsAvailable() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800, 2500, 725, List.of(line("VISTA", 90, 315, 180, 347)));
        assertEquals("VISTA", detect("Untitled Import", page));
    }

    private static ScoreCreditsDetector.Page page(List<ScoreCreditsDetector.Line> lines) {
        return new ScoreCreditsDetector.Page(1800, 2500, 510, lines);
    }

    private static String detect(String hint, ScoreCreditsDetector.Page page) {
        return ScoreCreditsDetector.detect(hint, List.of(page), Set.of()).title();
    }

    private static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }
}
