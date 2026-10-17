// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original analytical notes/rules and procedural header strokes with explicit OCR inputs. */
public class OpeningPartTempoTest {
    static final int W = 480, H = 500;

    static class Page {
        byte[] g = new byte[W * H];
        List<PlayingTechniqueDetector.Word> words = new ArrayList<>();
        List<MeasureNumberReconciler.NumberToken> tokens = new ArrayList<>();
        List<MeasureRegion> measures =
                new ArrayList<>(List.of(new MeasureRegion(180f / W, 450f / W, 238f / H, 310f / H)));

        Page() {
            Arrays.fill(g, (byte) 255);
            for (int y = 250; y <= 298; y += 12) box(30, y, 420, 1);
            word("Open Sky", 240, 15, 360, 31);
            word("A. Composer", 240, 40, 360, 56);
            word("for violin", 150, 68, 235, 82);
            equation(96, 220, 110);
        }

        void box(int x, int y, int w, int h) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
        }

        void erase(int x, int y, int w, int h) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = (byte) 255;
        }

        void word(String text, int left, int top, int right, int bottom) {
            words.add(
                    new PlayingTechniqueDetector.Word(
                            text,
                            left / (float) W,
                            top / (float) H,
                            right / (float) W,
                            bottom / (float) H));
            for (int x = left + 1; x < right - 1; x += 6) box(x, top + 1, 2, bottom - top - 2);
        }

        void equation(int value, int left, int top) {
            box(left - 18, top + 6, 12, 2);
            box(left - 18, top + 12, 12, 2);
            box(left, top, 3, 18);
            box(left + 12, top, 3, 18);
            box(left - 30, top - 12, 2, 28);
            for (int y = top + 9; y <= top + 16; y++)
                for (int x = left - 39; x <= left - 29; x++)
                    if (Math.pow((x - (left - 34)) / 5d, 2) + Math.pow((y - (top + 13)) / 4d, 2)
                            <= 1) g[y * W + x] = 0;
            tokens.add(
                    new MeasureNumberReconciler.NumberToken(
                            value,
                            left / (float) W,
                            top / (float) H,
                            (left + 16f) / W,
                            (top + 18f) / H,
                            (left - 40f) / W));
            words.add(
                    new PlayingTechniqueDetector.Word(
                            "J=" + value,
                            (left - 40f) / W,
                            (top - 12f) / H,
                            (left + 16f) / W,
                            (top + 18f) / H));
        }

        void caption(String text) {
            words.set(
                    2,
                    new PlayingTechniqueDetector.Word(text, 150f / W, 68f / H, 235f / W, 82f / H));
        }

        List<ScoreTempoChange> read() {
            return TempoChangeDetector.detectWithWords(tokens, words, g, W, H, measures);
        }
    }

    static void accepted(Page p) {
        assertEquals(List.of(new ScoreTempoChange(0, 0, 96)), p.read());
    }

    @Test
    public void completeDistantEquationUsesIndependentPrintedPartHeader() {
        accepted(new Page());
    }

    @Test
    public void fluteDuetUsesSameGeneralPartGrammar() {
        Page p = new Page();
        p.caption("Flute duet");
        accepted(p);
    }

    @Test
    public void pianoPartUsesSameGeneralPartGrammar() {
        Page p = new Page();
        p.caption("for piano");
        accepted(p);
    }

    @Test
    public void existingShortPartAliasIsRecognized() {
        Page p = new Page();
        p.caption("Vln.");
        accepted(p);
    }

    @Test
    public void noWordsPreservesOriginalDistanceAbstention() {
        Page p = new Page();
        assertTrue(TempoChangeDetector.detect(p.tokens, p.g, W, H, p.measures).isEmpty());
        p.words.clear();
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void genericProseForReferenceIsNotPartEvidence() {
        Page p = new Page();
        p.caption("for reference");
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void absentPartCaptionCannotUseHeaderEnvelope() {
        Page p = new Page();
        p.words.remove(2);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void captionBelowEquationDoesNotOwnIt() {
        Page p = new Page();
        p.words.remove(2);
        p.word("for violin", 150, 165, 235, 180);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void loneHeadingIsNotIndependentHeaderEnvelope() {
        Page p = new Page();
        p.words.remove(1);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void titleNumberIsNotStandaloneMetronomeSyntax() {
        Page p = new Page();
        var w = p.words.get(3);
        p.words.set(
                3,
                new PlayingTechniqueDetector.Word(
                        "Opus 96", w.left(), w.top(), w.right(), w.bottom()));
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void explicitExampleCaptionBlocksUnrelatedEquation() {
        Page p = new Page();
        p.word("Example", 260, 110, 320, 126);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void interveningTextBlocksHeaderToStaffOwnership() {
        Page p = new Page();
        p.word("comment", 260, 175, 320, 191);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void opticalStrokeInProjectedCorridorBlocksOwnership() {
        Page p = new Page();
        p.box(205, 185, 2, 5);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void secondPartCaptionIsAmbiguousEvenAcrossRow() {
        Page p = new Page();
        p.word("for flute", 340, 68, 420, 82);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void secondCloseStaffNeedsIndependentPartOwnership() {
        Page p = new Page();
        for (int y = 330; y <= 378; y += 12) p.box(30, y, 420, 1);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void normalDistantLaterSystemDoesNotCompete() {
        Page p = new Page();
        for (int y = 410; y <= 458; y += 12) p.box(30, y, 420, 1);
        accepted(p);
    }

    @Test
    public void missingPrintedStaffCannotUseOcrHeader() {
        Page p = new Page();
        p.erase(30, 250, 420, 49);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void missingBeatGlyphCannotUseOcrHeader() {
        Page p = new Page();
        p.erase(178, 96, 16, 34);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void missingEqualsCannotUseOcrHeader() {
        Page p = new Page();
        p.erase(200, 114, 16, 12);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void oneEqualsStrokeIsInsufficient() {
        Page p = new Page();
        p.erase(200, 121, 16, 4);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void unprintedCaptionOcrCannotSupplyPositiveContext() {
        Page p = new Page();
        p.erase(149, 67, 88, 17);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void unprintedHeadingOcrCannotSupplyPositiveContext() {
        Page p = new Page();
        p.erase(239, 14, 123, 19);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void solidInkBlockIsNotPrintedCaption() {
        Page p = new Page();
        p.box(150, 68, 85, 14);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void duplicateOcrDoesNotCreateCompetingPart() {
        Page p = new Page();
        p.words.add(p.words.get(2));
        p.words.add(p.words.get(3));
        accepted(p);
    }

    @Test
    public void malformedWordBoxDoesNotOwnTempo() {
        Page p = new Page();
        p.words.set(2, new PlayingTechniqueDetector.Word("for violin", Float.NaN, .1f, .4f, .2f));
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void emptyMeasureListCannotUseContext() {
        Page p = new Page();
        p.measures.clear();
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void contextReadDoesNotMutateInputs() {
        Page p = new Page();
        byte[] before = p.g.clone();
        var words = List.copyOf(p.words);
        var tokens = List.copyOf(p.tokens);
        accepted(p);
        assertArrayEquals(before, p.g);
        assertEquals(words, p.words);
        assertEquals(tokens, p.tokens);
    }

    @Test
    public void independentPartTypographyCanVary() {
        Page p = new Page();
        p.caption("Viola part");
        p.erase(239, 14, 123, 19);
        p.words.remove(0);
        p.word("Morning Air", 215, 12, 385, 34);
        accepted(p);
    }

    @Test
    public void twoMetronomeEquationsDoNotChooseAnArbitraryOne() {
        Page p = new Page();
        p.equation(90, 340, 110);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void nearbyInterveningProseIsNotStaffNotation() {
        Page p = new Page();
        p.word("reference", 260, 230, 330, 244);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void opusHeaderCannotReplaceIndependentTitleAndCredit() {
        Page p = new Page();
        var w = p.words.get(0);
        p.words.set(
                0,
                new PlayingTechniqueDetector.Word(
                        "Opus 96", w.left(), w.top(), w.right(), w.bottom()));
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void printedQuarterUnicodeWordUsesSameContextRoute() {
        Page p = new Page();
        var w = p.words.get(3);
        p.words.set(
                3,
                new PlayingTechniqueDetector.Word(
                        "\u2669=96", w.left(), w.top(), w.right(), w.bottom()));
        accepted(p);
    }
}
