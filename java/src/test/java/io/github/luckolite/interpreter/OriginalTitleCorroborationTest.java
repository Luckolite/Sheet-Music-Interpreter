// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class OriginalTitleCorroborationTest {
    private ScoreCreditsDetector.Line source(String text) {
        return new ScoreCreditsDetector.Line(text, 200, 160, 1600, 350);
    }

    private ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line original) {
        return new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(original), false, true);
    }

    private OriginalTitleCorroboration.Reading reading(int scale, String text, float confidence) {
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
                confidence,
                1000 * scale,
                200 * scale,
                List.of(new OriginalTitleCorroboration.NativeLine(line, words)));
    }

    private List<OriginalTitleCorroboration.Reading> readings(String text) {
        return List.of(reading(1, text, .99f), reading(2, text, .99f), reading(4, text, .99f));
    }

    private OriginalTitleCorroboration.Proposal proposal(String original, String suggested) {
        var line = source(original);
        return OriginalTitleCorroboration.propose(page(line), line, readings(suggested));
    }

    @Test
    public void embeddedZerosProposedWithLiteralRetained() {
        var p = proposal("C00L RIVER", "COOL RIVER");
        assertNotNull(p);
        assertEquals("C00L RIVER", p.literalOriginal());
        assertEquals("COOL RIVER", p.suggestedText());
        assertEquals(1, p.recoveredZeroWords());
        assertTrue(p.requiresReview());
    }

    @Test
    public void intentionalStylizedSpellingRemainsAnExplicitAmbiguity() {
        var p = proposal("B00K CLUB", "BOOK CLUB");
        assertNotNull(p);
        assertTrue(p.requiresReview());
        assertEquals("B00K CLUB", p.literalOriginal());
    }

    @Test
    public void otherDigitNeverReplaced() {
        assertNull(proposal("C20L RIVER", "CZOL RIVER"));
    }

    @Test
    public void standaloneZeroNeverReplaced() {
        assertNull(proposal("COOL 0 RIVER", "COOL O RIVER"));
    }

    @Test
    public void zeroAtWordBoundaryNeverReplaced() {
        assertNull(proposal("C00 RIVER", "COO RIVER"));
        assertNull(proposal("00L RIVER", "OOL RIVER"));
    }

    @Test
    public void catalogueCodeNeverReplaced() {
        for (String code : List.of("BWV0O", "RV0O", "HWV0O", "KV0O", "OP00S", "K00L"))
            assertNull(code, proposal(code + " RIVER", code.replace('0', 'O') + " RIVER"));
    }

    @Test
    public void separateCatalogueContextVetoes() {
        assertNull(proposal("C00L RIVER Op. 7", "COOL RIVER Op. 7"));
    }

    @Test
    public void unrelatedNumeralsCannotChange() {
        assertNull(proposal("C00L RIVER 12", "COOL RIVER 13"));
    }

    @Test
    public void standaloneRomanCannotChange() {
        assertNull(proposal("C00L RIVER IV", "COOL RIVER V"));
    }

    @Test
    public void unrelatedLettersCannotChange() {
        assertNull(proposal("C00L RIVAR", "COOL RIVER"));
    }

    @Test
    public void diacriticCannotBeDropped() {
        assertNull(proposal("C00L CAFÉ", "COOL CAFE"));
    }

    @Test
    public void wordBoundariesCannotChange() {
        assertNull(proposal("C00LRIVER", "COOL RIVER"));
    }

    @Test
    public void punctuationDifferenceIsExplicit() {
        var p = proposal("IM C00L", "I'M COOL");
        assertNotNull(p);
        assertTrue(p.punctuationDiffers());
        assertTrue(p.requiresReview());
    }

    @Test
    public void noChangeProducesNoProposal() {
        assertNull(proposal("COOL RIVER", "COOL RIVER"));
    }

    @Test
    public void allThreePortableReadingsMustAgree() {
        var o = source("C00L RIVER");
        var r = new ArrayList<>(readings("COOL RIVER"));
        r.set(0, reading(1, "COLD RIVER", .99f));
        assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
    }

    @Test
    public void allPortableConfidenceGatesRemain() {
        for (float bad : new float[] {.97999f, Float.NaN, Float.POSITIVE_INFINITY, 1.1f}) {
            var o = source("C00L RIVER");
            var r = new ArrayList<>(readings("COOL RIVER"));
            r.set(0, reading(1, "COOL RIVER", bad));
            assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
        }
    }

    @Test
    public void distinctOriginalScaleSlotsRequired() {
        var o = source("C00L RIVER");
        assertNull(
                OriginalTitleCorroboration.propose(
                        page(o),
                        o,
                        List.of(
                                reading(1, "COOL RIVER", .99f),
                                reading(2, "COOL RIVER", .99f),
                                reading(2, "COOL RIVER", .99f))));
    }

    @Test
    public void bothHigherNativeReadsMustAgree() {
        var o = source("C00L RIVER");
        var r = new ArrayList<>(readings("COOL RIVER"));
        var old = r.get(2);
        r.set(
                2,
                new OriginalTitleCorroboration.Reading(
                        4,
                        old.text(),
                        old.confidence(),
                        old.width(),
                        old.height(),
                        reading(4, "COLD RIVER", .99f).nativeLines()));
        assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
    }

    @Test
    public void largeCompetingRowDeclines() {
        var o = source("C00L RIVER");
        var r = new ArrayList<>(readings("COOL RIVER"));
        var old = r.get(2);
        var lines = new ArrayList<>(old.nativeLines());
        lines.add(reading(4, "OTHER TITLE", .99f).nativeLines().get(0));
        r.set(
                2,
                new OriginalTitleCorroboration.Reading(
                        4, old.text(), old.confidence(), old.width(), old.height(), lines));
        assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
    }

    @Test
    public void originalMustBePrimary() {
        var o = source("C00L RIVER");
        var big = new ScoreCreditsDetector.Line("OTHER TITLE", 100, 10, 1700, 220);
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(o, big), false, true);
        assertNull(OriginalTitleCorroboration.propose(p, o, readings("COOL RIVER")));
    }

    @Test
    public void sourceMustBeNonnotatedPhotographicCover() {
        var o = source("C00L RIVER");
        for (var p :
                List.of(
                        new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(o), false, false),
                        new ScoreCreditsDetector.Page(1800, 1000, 500, List.of(o), false, true),
                        new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(o), true, true)))
            assertNull(OriginalTitleCorroboration.propose(p, o, readings("COOL RIVER")));
    }

    @Test
    public void nativeWordPositionsMustBeValid() {
        var o = source("C00L RIVER");
        var r = new ArrayList<>(readings("COOL RIVER"));
        var old = r.get(2);
        var n = old.nativeLines().get(0);
        var words = new ArrayList<>(n.words());
        words.set(0, new ScoreCreditsDetector.Line("COOL", -1, 80, 2000, 720));
        r.set(
                2,
                new OriginalTitleCorroboration.Reading(
                        4,
                        old.text(),
                        old.confidence(),
                        old.width(),
                        old.height(),
                        List.of(new OriginalTitleCorroboration.NativeLine(n.line(), words))));
        assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
    }

    @Test
    public void typographicInternalApostropheCanBeFlagged() {
        var p = proposal("IM C00L", "I’M COOL");
        assertNotNull(p);
        assertTrue(p.punctuationDiffers());
    }

    @Test
    public void edgeApostrophesStayLiteral() {
        assertNull(proposal("C00L 'RIVER", "COOL RIVER"));
        assertNull(proposal("C00L RIVER'", "COOL RIVER"));
    }

    @Test
    public void punctuationAddedToChangedWordRejects() {
        assertNull(proposal("C00L RIVER", "CO'OL RIVER"));
        assertNull(proposal("C00L RIVER", "(COOL) RIVER"));
    }

    @Test
    public void verticalSeparatorsAndInvisibleSourceControlsReject() {
        for (String bad : List.of("\u2028", "\u2029", "\u0000", "\u200D", "\t"))
            assertNull(proposal("C00L" + bad + " RIVER", "COOL RIVER"));
    }

    @Test
    public void originalOutsidePageRejects() {
        var o = new ScoreCreditsDetector.Line("C00L RIVER", 200, 160, 1801, 350);
        assertNull(OriginalTitleCorroboration.propose(page(o), o, readings("COOL RIVER")));
    }

    @Test
    public void invalidOtherSourceLineRejectsBeforeSelector() {
        var o = source("C00L RIVER");
        var invalid = new ScoreCreditsDetector.Line("caption", Float.NaN, 500, 800, 600);
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(o, invalid), false, true);
        assertNull(OriginalTitleCorroboration.propose(p, o, readings("COOL RIVER")));
    }

    @Test
    public void invalidPageDimensionsReject() {
        var o = source("C00L RIVER");
        for (float bad : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertNull(
                    OriginalTitleCorroboration.propose(
                            new ScoreCreditsDetector.Page(bad, 1000, -1, List.of(o), false, true),
                            o,
                            readings("COOL RIVER")));
    }

    @Test
    public void nativeInvisibleCharactersReject() {
        var o = source("C00L RIVER");
        var r = new ArrayList<>(readings("COOL RIVER"));
        var old = r.get(2);
        r.set(
                2,
                new OriginalTitleCorroboration.Reading(
                        4,
                        old.text(),
                        old.confidence(),
                        old.width(),
                        old.height(),
                        reading(4, "CO\u202EOL RIVER", .99f).nativeLines()));
        assertNull(OriginalTitleCorroboration.propose(page(o), o, r));
    }

    @Test
    public void apostropheCannotRevealCataloguePrefix() {
        assertNull(proposal("C00L O'P. 7", "COOL OP. 7"));
        assertNull(proposal("C00L N'O. 7", "COOL NO. 7"));
    }

    @Test
    public void unchangedApostropheCannotConcealCataloguePrefix() {
        assertNull(proposal("C00L O'P. 7", "COOL O'P. 7"));
    }

    @Test
    public void duplicateTextCannotGiveSmallerBoxPrimaryOwnership() {
        var first = source("C00L RIVER");
        var second = new ScoreCreditsDetector.Line("C00L RIVER", 600, 700, 1200, 760);
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(first, second), false, true);
        assertEquals(first.text(), ScoreCreditsDetector.detect("", List.of(p), Set.of()).title());
        assertTrue(ScoreCreditsDetector.locatedCoverHeadingCandidates(p).contains(second));
        assertNull(OriginalTitleCorroboration.propose(p, second, readings("COOL RIVER")));
    }

    @Test
    public void duplicateTextAlsoDeclinesLargeBoxWithoutUniqueOwnership() {
        var first = source("C00L RIVER");
        var second = new ScoreCreditsDetector.Line("C00L RIVER", 600, 700, 1200, 760);
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(first, second), false, true);
        assertNull(OriginalTitleCorroboration.propose(p, first, readings("COOL RIVER")));
    }

    private OriginalTitleCorroboration.Proposal middle(
            String original, String candidate, String middleText, String highestText) {
        var o = source(original);
        var r = new ArrayList<>(readings(candidate));
        for (int slot : new int[] {1, 2}) {
            var old = r.get(slot);
            r.set(
                    slot,
                    new OriginalTitleCorroboration.Reading(
                            old.scale(),
                            old.text(),
                            old.confidence(),
                            old.width(),
                            old.height(),
                            reading(old.scale(), slot == 1 ? middleText : highestText, .99f)
                                    .nativeLines()));
        }
        return OriginalTitleCorroboration.propose(page(o), o, r);
    }

    @Test
    public void monotonicPartialMiddleZeroCanCorroborateExactLargest() {
        var p = middle("C00L RIVER", "COOL RIVER", "CO0L RIVER", "COOL RIVER");
        assertNotNull(p);
        assertTrue(p.requiresReview());
        assertEquals("C00L RIVER", p.literalOriginal());
    }

    @Test
    public void otherOriginalZeroPositionCanRemain() {
        assertNotNull(middle("C00L RIVER", "COOL RIVER", "C0OL RIVER", "COOL RIVER"));
    }

    @Test
    public void unchangedMiddleZerosCannotCorroborate() {
        assertNull(middle("C00L RIVER", "COOL RIVER", "C00L RIVER", "COOL RIVER"));
    }

    @Test
    public void singleUnrecoveredZeroCannotCorroborate() {
        assertNull(middle("CO0L RIVER", "COOL RIVER", "CO0L RIVER", "COOL RIVER"));
    }

    @Test
    public void middleNewZeroPositionRejects() {
        assertNull(middle("C00L ROVER", "COOL ROVER", "CO0L R0VER", "COOL ROVER"));
    }

    @Test
    public void middleOtherLetterOrDigitRejects() {
        assertNull(middle("C00L RIVER", "COOL RIVER", "CO0L RIVAR", "COOL RIVER"));
        assertNull(middle("C00L RIVER", "COOL RIVER", "C10L RIVER", "COOL RIVER"));
    }

    @Test
    public void middlePunctuationCannotBeLaundered() {
        assertNull(middle("C00L RIVER", "COOL RIVER", "C0/OL RIVER", "COOL RIVER"));
    }

    @Test
    public void largestPartialZeroStillRejects() {
        assertNull(middle("C00L RIVER", "COOL RIVER", "COOL RIVER", "CO0L RIVER"));
    }

    @Test
    public void eachAffectedMiddleWordMustImprove() {
        assertNull(middle("C00L B00K", "COOL BOOK", "CO0L B00K", "COOL BOOK"));
        assertNotNull(middle("C00L B00K", "COOL BOOK", "CO0L BO0K", "COOL BOOK"));
    }

    @Test
    public void middleCannotChangeWordBoundaries() {
        assertNull(middle("C00L RIVER", "COOL RIVER", "CO0LRIVER", "COOL RIVER"));
    }

    @Test
    public void partialMiddleDoesNotBypassCatalogue() {
        assertNull(middle("C00L OP. 7", "COOL OP. 7", "CO0L OP. 7", "COOL OP. 7"));
    }
}
