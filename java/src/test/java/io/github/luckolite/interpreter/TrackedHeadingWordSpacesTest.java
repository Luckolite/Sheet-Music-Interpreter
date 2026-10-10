// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import io.github.luckolite.interpreter.TrackedHeadingWordSpaces;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Fictional text with synthetic ink rectangles; no score artwork or expected library values. */
public class TrackedHeadingWordSpacesTest {
    @Test
    public void collapsedWhiteHeadingUsesWidePixelGaps() {
        assertEquals("OVER THE SEA", refine("OVERTHESEA", "OVER THE SEA", true));
    }

    @Test
    public void trackedDarkHeadingKeepsApostropheAndSingleLetterWord() {
        assertEquals("IT'S A STORY", refine("I T ' S A S T O R Y", "IT'S A STORY", false));
    }

    @Test
    public void evenlyTrackedAcronymIsNotTurnedIntoAWord() {
        assertEquals("A B C D E F", refine("A B C D E F", "ABCDEF", false));
    }

    @Test
    public void absentRecognizedLetterCannotBeInvented() {
        assertEquals("OVERTHESE", refine("OVERTHESE", "OVER THE SEA", true));
    }

    @Test
    public void extraInkCannotBeDroppedToMakeLettersFit() {
        assertEquals("OVERTHESEA", refine("OVERTHESEA", "OVER THE SEAS", false));
    }

    @Test
    public void identifiersAreUnchanged() {
        assertEquals("AB12CD", refine("AB12CD", "AB12 CD", false));
    }

    @Test
    public void mixedCaseTextIsUnchanged() {
        assertEquals("OverTheSea", refine("OverTheSea", "Over The Sea", false));
    }

    @Test
    public void unstableContrastDoesNotSupplyWordBoundaries() {
        var raster = draw("OVER THE SEA", true);
        for (int i = 0; i < raster.pixels.length; i++)
            if (raster.pixels[i] == 0xfffafafa) raster.pixels[i] = 0xffe4e4e4;
        assertEquals(
                "OVERTHESEA",
                TrackedHeadingWordSpaces.refine("OVERTHESEA", raster.pixels, raster.width, 60));
    }

    @Test
    public void blankPixelsDoNotSupplyWordBoundaries() {
        int[] pixels = new int[600 * 60];
        Arrays.fill(pixels, 0xffffffff);
        assertEquals("OVERTHESEA", TrackedHeadingWordSpaces.refine("OVERTHESEA", pixels, 600, 60));
    }

    @Test
    public void invalidRasterIsUnchanged() {
        assertEquals(
                "OVERTHESEA", TrackedHeadingWordSpaces.refine("OVERTHESEA", new int[4], 600, 60));
    }

    @Test
    public void mergePreservesGeometryAndChangesOnlyHeadingSpaces() {
        var heading = new ScoreCreditsDetector.Line("OVERTHESEA", 120, 60, 1680, 175);
        var byline = new ScoreCreditsDetector.Line("Music by Mira North", 1200, 220, 1680, 245);
        var page = new ScoreCreditsDetector.Page(1800, 2500, 400, List.of(heading, byline));
        var merged =
                ScoreCreditsDetector.mergeTrackedHeadingWordSpaces(page, heading, "OVER THE SEA");
        assertEquals(
                new ScoreCreditsDetector.Line("OVER THE SEA", 120, 60, 1680, 175), merged.get(0));
        assertEquals(byline, merged.get(1));
    }

    @Test
    public void mergeCannotChangeRecognizedLetters() {
        var heading = new ScoreCreditsDetector.Line("OVERTHESEA", 120, 60, 1680, 175);
        var page = new ScoreCreditsDetector.Page(1800, 2500, 400, List.of(heading));
        assertEquals(
                page.lines(),
                ScoreCreditsDetector.mergeTrackedHeadingWordSpaces(page, heading, "OVER THE SEAS"));
    }

    @Test
    public void mergeCannotChangeRecognizedPunctuation() {
        var heading = new ScoreCreditsDetector.Line("I T ' S A S T O R Y", 450, 100, 1350, 170);
        var page = new ScoreCreditsDetector.Page(1800, 2500, 400, List.of(heading));
        assertEquals(
                page.lines(),
                ScoreCreditsDetector.mergeTrackedHeadingWordSpaces(page, heading, "ITS A STORY"));
    }

    @Test
    public void bodyTextAndLaterCreditsPagesAreNotSpacingCandidates() {
        var heading = new ScoreCreditsDetector.Line("OVERTHESEA", 120, 700, 1680, 815);
        assertEquals(
                null,
                ScoreCreditsDetector.trackedHeadingWordSpaceCandidate(
                        new ScoreCreditsDetector.Page(1800, 2500, 900, List.of(heading))));
        var later = new ScoreCreditsDetector.Line("OVERTHESEA", 120, 60, 1680, 175);
        assertEquals(
                null,
                ScoreCreditsDetector.trackedHeadingWordSpaceCandidate(
                        new ScoreCreditsDetector.Page(
                                1800, 2500, 400, List.of(later), true, false)));
    }

    private static String refine(String recognized, String inkText, boolean light) {
        var raster = draw(inkText, light);
        return TrackedHeadingWordSpaces.refine(recognized, raster.pixels, raster.width, 60);
    }

    private record Raster(int[] pixels, int width) {}

    private static Raster draw(String text, boolean light) {
        int width =
                text.replace(" ", "").length() * 32
                        + (int) text.chars().filter(c -> c == ' ').count() * 32;
        int[] pixels = new int[width * 60];
        Arrays.fill(pixels, light ? 0xffa5a0a0 : 0xffffffff);
        int x = 0;
        for (char letter : text.toCharArray()) {
            if (letter == ' ') {
                x += 32;
                continue;
            }
            int bottom = letter == '\'' ? 22 : 52;
            for (int y = 8; y < bottom; y++)
                for (int column = x; column < x + 20; column++)
                    pixels[y * width + column] = light ? 0xfffafafa : 0xff202020;
            x += 32;
        }
        return new Raster(pixels, width);
    }
}
