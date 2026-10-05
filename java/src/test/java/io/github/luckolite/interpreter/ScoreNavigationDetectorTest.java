// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScorePlaybackDirection.Kind.*;

public class ScoreNavigationDetectorTest {
    @Test
    public void allCapoSegnoFineVariantsHaveExplicitMeanings() {
        assertEquals(DA_CAPO, ScoreNavigationDetector.kind("D.C."));
        assertEquals(DAL_SEGNO, ScoreNavigationDetector.kind("Dal Segno"));
        assertEquals(DA_CAPO_AL_FINE, ScoreNavigationDetector.kind("Da Capo al Fine"));
        assertEquals(DAL_SEGNO_AL_FINE, ScoreNavigationDetector.kind("D.S. al Fine"));
        assertEquals(DA_CAPO_AL_CODA, ScoreNavigationDetector.kind("D.C. al Coda"));
        assertEquals(FINE, ScoreNavigationDetector.kind("Fine"));
        assertNull(ScoreNavigationDetector.kind("fine tuning"));
    }

    @Test
    public void splitJumpKeepsCompleteInstructionNotConflictingPlainPrefix() {
        for (var variant : List.of("Fine", "Coda")) {
            var kind = variant.equals("Fine") ? DA_CAPO_AL_FINE : DA_CAPO_AL_CODA;
            assertEquals(
                    List.of(new ScorePlaybackDirection(4, kind)),
                    detect(
                            List.of(
                                    word("D.C.", .72f, .76f),
                                    word("al", .77f, .8f),
                                    word(variant, .81f, .9f)),
                            List.of()));
        }
        assertEquals(
                List.of(new ScorePlaybackDirection(4, DAL_SEGNO_AL_FINE)),
                detect(
                        List.of(
                                word("Dal", .71f, .75f),
                                word("Segno", .76f, .8f),
                                word("al", .81f, .83f),
                                word("Fine", .84f, .9f)),
                        List.of()));
    }

    @Test
    public void nearbyIndependentCodaDestinationIsNotSwallowedByJumpPhrase() {
        assertEquals(
                List.of(
                        new ScorePlaybackDirection(3, CODA),
                        new ScorePlaybackDirection(4, DA_CAPO_AL_CODA)),
                detect(
                        List.of(word("Coda", .715f, .755f), word("D.C. al Coda", .77f, .9f)),
                        List.of()));
    }

    private final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(new PlayingTechniqueDetector.Staff(100, 140, 10, 0, 1));
    private final List<MeasureRegion> bars =
            List.of(
                    new MeasureRegion(.1f, .3f, .2f, .4f),
                    new MeasureRegion(.31f, .5f, .2f, .4f),
                    new MeasureRegion(.51f, .7f, .2f, .4f),
                    new MeasureRegion(.71f, .9f, .2f, .4f));

    private PlayingTechniqueDetector.Word word(String text, float left, float right) {
        return new PlayingTechniqueDetector.Word(text, left, .18f, right, .22f);
    }

    private List<ScorePlaybackDirection> detect(
            List<PlayingTechniqueDetector.Word> words, List<ScoreNavigationDetector.Glyph> glyphs) {
        return ScoreNavigationDetector.detect(words, glyphs, staffs, bars, 1000, 400);
    }

    @Test
    public void exactDirectionsAcceptPunctuationAndNotOrdinaryWords() {
        assertEquals(DAL_SEGNO_AL_CODA, ScoreNavigationDetector.kind("D.S. al Coda"));
        assertEquals(DAL_SEGNO_AL_CODA, ScoreNavigationDetector.kind("Dal Segno al Coda"));
        assertNull(ScoreNavigationDetector.kind("Coda-like"));
        assertNull(ScoreNavigationDetector.kind("The coda"));
    }

    @Test
    public void outgoingSignsAnchorAfterTheirPrintedBar() {
        assertEquals(
                List.of(
                        new ScorePlaybackDirection(3, TO_CODA),
                        new ScorePlaybackDirection(4, DAL_SEGNO_AL_CODA)),
                detect(
                        List.of(word("To Coda", .57f, .66f), word("D.S. al Coda", .78f, .9f)),
                        List.of()));
    }

    @Test
    public void incomingSignBeforeLongHeaderAnchorsAtFirstBar() {
        assertEquals(
                List.of(new ScorePlaybackDirection(0, CODA)),
                detect(List.of(word("CODA", .01f, .03f)), List.of()));
    }

    @Test
    public void destinationAtRoundedSixSpaceLimitKeepsItsStaff() {
        assertEquals(
                List.of(new ScorePlaybackDirection(0, CODA)),
                detect(
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "Coda", .12f, .078f, .18f, .098f)),
                        List.of()));
    }

    @Test
    public void destinationBeyondOnePixelAllowanceStaysUnowned() {
        assertTrue(
                detect(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "Coda", .12f, .076f, .18f, .096f)),
                                List.of())
                        .isEmpty());
    }

    @Test
    public void verifiedSegnoGlyphDoesNotRequireAnOcrToken() {
        assertEquals(
                List.of(new ScorePlaybackDirection(0, SEGNO)),
                detect(
                        List.of(),
                        List.of(new ScoreNavigationDetector.Glyph(SEGNO, .12f, .18f, .22f))));
    }

    @Test
    public void splitDalSegnoPhraseDoesNotInventCodaDestination() {
        assertEquals(
                List.of(new ScorePlaybackDirection(4, DAL_SEGNO_AL_CODA)),
                detect(
                        List.of(
                                word("D.S.", .72f, .76f),
                                word("al", .77f, .80f),
                                word("Coda", .81f, .9f)),
                        List.of()));
    }

    @Test
    public void distantWordsAreNotJoined() {
        assertFalse(
                detect(List.of(word("D.S.", .15f, .2f), word("al Coda", .8f, .9f)), List.of())
                        .stream()
                        .anyMatch(d -> d.kind() == DAL_SEGNO_AL_CODA));
    }

    @Test
    public void belowStaffLyricsDoNotBecomeDirections() {
        assertEquals(
                List.of(),
                detect(
                        List.of(new PlayingTechniqueDetector.Word("coda", .15f, .42f, .2f, .46f)),
                        List.of()));
    }

    @Test
    public void remotePageTitleIsNotAStaffDirection() {
        assertEquals(
                List.of(),
                detect(
                        List.of(new PlayingTechniqueDetector.Word("CODA", .15f, .01f, .2f, .04f)),
                        List.of()));
    }

    @Test
    public void duplicateStaffTokensProduceOneAnchor() {
        var w = word("To Coda", .57f, .66f);
        assertEquals(1, detect(List.of(w, w), List.of()).size());
    }

    @Test
    public void outgoingMultiRestDirectionFollowsItsFinalExpandedBar() {
        var same = bars.get(0);
        var repeated = List.of(same, same, same, bars.get(1));
        assertEquals(
                List.of(new ScorePlaybackDirection(3, TO_CODA)),
                ScoreNavigationDetector.detect(
                        List.of(word("To Coda", .15f, .25f)),
                        List.of(),
                        staffs,
                        repeated,
                        1000,
                        400));
    }

    @Test
    public void emptyInputsStayEmpty() {
        assertEquals(List.of(), detect(List.of(), List.of()));
    }

    @Test
    public void normalizationKeepsAsciiSeparatorsAndWholeUnicodeBoundaries() {
        assertEquals(DA_CAPO_AL_FINE, ScoreNavigationDetector.kind("\tD.,\nC:\ral;\fFine\u000b"));
        assertEquals(SEGNO, ScoreNavigationDetector.kind(" \t𝄋\n"));
        assertEquals(CODA, ScoreNavigationDetector.kind(" \t𝄌\n"));
        assertNull(ScoreNavigationDetector.kind(null));
        for (String text :
                List.of(
                        "D\u00a0C",
                        "D\u2003C",
                        "Ｄ.C.",
                        "fine-tuning",
                        "\u00a0𝄋",
                        "D.C. al Fine suffix")) {
            assertNull(text, ScoreNavigationDetector.kind(text));
        }
        assertEquals(DA_CAPO, ScoreNavigationDetector.kind("D.C."));
    }

    @Test
    public void punctuatedPhraseKeepsIndependentDestinationAndCallerOrder() {
        var jump = word("D, C; al: Coda.", .77f, .9f);
        var destination = word("Coda", .715f, .755f);
        var words = List.of(jump, destination);
        assertEquals(
                List.of(
                        new ScorePlaybackDirection(3, CODA),
                        new ScorePlaybackDirection(4, DA_CAPO_AL_CODA)),
                detect(words, List.of()));
        assertSame(jump, words.get(0));
        assertSame(destination, words.get(1));
        assertEquals("D, C; al: Coda.", jump.text());
        assertEquals("Coda", destination.text());
        assertEquals(
                List.of(new ScorePlaybackDirection(4, DA_CAPO)),
                detect(List.of(word("D.C.", .77f, .9f)), List.of()));
    }
}
