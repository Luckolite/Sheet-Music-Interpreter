// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original generated rasters. No song names, copied score pixels, or title-dependent behavior. */
public class TabExistingTargetTieTest {
    private static final int W = 500, H = 320;
    private static final float TOP = 80, GAP = 20;

    private byte[] page() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int s = 0; s < 6; s++) for (int x = 20; x <= 480; x++) gray[(80 + s * 20) * W + x] = 0;
        for (int x : new int[] {20, 250, 480}) for (int y = 70; y <= 190; y++) gray[y * W + x] = 0;
        return gray;
    }

    private TablatureDecoder.Fret fret(float x, int string, int value) {
        return new TablatureDecoder.Fret(
                x, TOP + string * GAP, string, value, 4, 0, 0, 0, false, 1);
    }

    private TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> frets) {
        return new TablatureDecoder.Staff(TOP, GAP, -1, frets, List.of(20f, 250f, 480f));
    }

    private List<TablatureDecoder.Fret> apply(byte[] gray, TablatureDecoder.Fret... frets) {
        return TabRhythmContinuations.apply(List.of(staff(List.of(frets))), gray, W, H)
                .get(0)
                .frets();
    }

    private void bow(byte[] gray, int string, int direction) {
        bow(gray, string, direction, 100, 340);
    }

    private void bow(byte[] gray, int string, int direction, int from, int to) {
        int left = from + 8, right = to - 13;
        float cy = TOP + string * GAP;
        for (int x = left; x <= right; x++) {
            double phase = (x - left) / (double) (right - left);
            int y =
                    Math.round(
                            cy + direction * GAP * (.1f + .7f * (float) Math.sin(Math.PI * phase)));
            gray[y * W + x] = 0;
        }
    }

    private void assertUntied(List<TablatureDecoder.Fret> frets) {
        assertTrue(frets.stream().noneMatch(TablatureDecoder.Fret::tied));
    }

    @Test
    public void wholeNoteTargetsWithVisibleArcsTieWithoutStems() {
        byte[] gray = page();
        var inputs = new ArrayList<TablatureDecoder.Fret>();
        for (int string = 1; string < 6; string++) {
            inputs.add(fret(100, string, 7 - string));
            inputs.add(fret(340, string, 7 - string));
            bow(gray, string, string < 4 ? -1 : 1);
        }
        var out = TabRhythmContinuations.apply(List.of(staff(inputs)), gray, W, H).get(0);
        assertEquals(inputs.size(), out.frets().size());
        assertEquals(5, out.frets().stream().filter(TablatureDecoder.Fret::tied).count());
        for (var f : out.frets()) {
            assertEquals(f.x() == 340, f.tied());
            assertEquals(4, f.duration(), 0);
        }
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()), List.of(out), W, H);
        assertEquals(10, score.notes().size());
        assertEquals(5, score.notes().stream().filter(ScoreNoteEvent::tiedFromPrevious).count());
        assertTrue(score.notes().stream().allMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void existingExplicitTargetPreservesEveryNonTieField() {
        byte[] gray = page();
        bow(gray, 2, -1);
        int marks = TabEffect.VIBRATO | TabEffect.PALM_MUTE;
        var source = new TablatureDecoder.Fret(100, 120, 2, 7, 2, 1, 1, marks, false, 3);
        var target = new TablatureDecoder.Fret(340, 120, 2, 7, 1, 2, 2, marks, false, 5);
        var out = apply(gray, source, target);
        assertEquals(source, out.get(0));
        assertEquals(
                new TablatureDecoder.Fret(
                        target.x(),
                        target.y(),
                        target.string(),
                        target.fret(),
                        target.duration(),
                        target.beams(),
                        target.dots(),
                        target.marks(),
                        true,
                        target.tuplet(),
                        target.wholeRestGlyph()),
                out.get(1));
    }

    @Test
    public void parenthesesWithoutAnArcKeepSeparateAttacks() {
        var source = TabNotation.parse("7", 90, 110, 120, 2).get(0);
        var target = TabNotation.parse("(7)", 330, 350, 120, 2).get(0);
        var out = apply(page(), source, target);
        assertEquals(2, out.size());
        assertUntied(out);
    }

    @Test
    public void straightRuleIsNotAnExistingTargetTie() {
        byte[] gray = page();
        for (int x = 108; x <= 327; x++) gray[112 * W + x] = 0;
        assertUntied(apply(gray, fret(100, 2, 7), fret(340, 2, 7)));
    }

    @Test
    public void changedFretCurveKeepsBothPitches() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var out = apply(gray, fret(100, 2, 7), fret(340, 2, 9));
        assertEquals(2, out.size());
        assertUntied(out);
    }

    @Test
    public void curveCannotBorrowThePreviousTokenFromAnotherString() {
        byte[] gray = page();
        bow(gray, 2, -1);
        assertUntied(apply(gray, fret(100, 3, 7), fret(340, 2, 7)));
    }

    @Test
    public void neighboringStringArcCannotProveAnUnmarkedStringTie() {
        byte[] gray = page();
        bow(gray, 3, -1);
        assertUntied(apply(gray, fret(100, 2, 7), fret(340, 2, 7)));
    }

    @Test
    public void interveningSameStringPitchStopsTheTie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        assertUntied(apply(gray, fret(100, 2, 7), fret(220, 2, 9), fret(340, 2, 7)));
    }

    @Test
    public void interveningMuteStopsTheTie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        assertUntied(apply(gray, fret(100, 2, 7), fret(220, 2, -1), fret(340, 2, 7)));
    }

    @Test
    public void interveningRestStopsTheTie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        assertUntied(apply(gray, fret(100, 2, 7), fret(220, 0, -2), fret(340, 2, 7)));
    }

    @Test
    public void interveningGraceNoteStopsTheTie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var grace = new TablatureDecoder.Fret(220, 140, 3, 9, 0, 0, 0, NoteOrnament.GRACE);
        assertUntied(apply(gray, fret(100, 2, 7), grace, fret(340, 2, 7)));
    }

    @Test
    public void interveningEffectStopsTheTie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var pull =
                new TablatureDecoder.Fret(
                        220, 140, 3, 9, 0, 0, 0, TabEffect.encode(TabEffect.PULL, 2));
        assertUntied(apply(gray, fret(100, 2, 7), pull, fret(340, 2, 7)));
    }

    @Test
    public void validHammerPullPhraseKeepsSeparatePitches() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var frets = TabNotation.parse("5h7p5", 90, 350, 120, 2);
        var out = TabRhythmContinuations.apply(List.of(staff(frets)), gray, W, H).get(0).frets();
        assertEquals(frets, out);
        assertUntied(out);
        assertEquals(TabEffect.HAMMER, TabEffect.kind(out.get(1).marks()));
        assertEquals(TabEffect.PULL, TabEffect.kind(out.get(2).marks()));
    }

    @Test
    public void arcWithoutAnExistingTargetOrStemStillAddsNothing() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var source = fret(100, 2, 7);
        assertEquals(List.of(source), apply(gray, source));
    }

    @Test
    public void disconnectedCurveAwayFromBarlineDoesNotProveATie() {
        byte[] gray = page();
        bow(gray, 2, -1);
        for (int y = 95; y < 120; y++) gray[y * W + 200] = (byte) 255;
        assertUntied(apply(gray, fret(100, 2, 7), fret(340, 2, 7)));
    }

    @Test
    public void curveNeedsBothFretEndpoints() {
        byte[] gray = page();
        bow(gray, 2, -1);
        for (int x = 108; x < 128; x++) for (int y = 95; y < 120; y++) gray[y * W + x] = (byte) 255;
        assertUntied(apply(gray, fret(100, 2, 7), fret(340, 2, 7)));
    }

    @Test
    public void pairedStandardStaffRemainsOutsideThisRepair() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var t =
                new TablatureDecoder.Staff(
                        TOP,
                        GAP,
                        20,
                        List.of(fret(100, 2, 7), fret(340, 2, 7)),
                        List.of(20f, 250f, 480f));
        assertEquals(t, TabRhythmContinuations.apply(List.of(t), gray, W, H).get(0));
    }

    private TablatureDecoder.Word annotation(String text, float x) {
        return new TablatureDecoder.Word(text, (x - 5) / W, 50f / H, (x + 5) / W, 64f / H);
    }

    private List<TablatureDecoder.Fret> rasterWithWords(
            byte[] gray, String text, float wordX, TablatureDecoder.Fret... frets) {
        return TabNotation.rasterRhythm(
                        List.of(staff(List.of(frets))),
                        gray,
                        W,
                        H,
                        List.of(annotation(text, wordX)))
                .get(0)
                .frets();
    }

    @Test
    public void explicitSameFretTapWinsOverAnOtherwiseProvedTieInFullWordPipeline() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var out = rasterWithWords(gray, "T", 340, fret(100, 2, 7), fret(340, 2, 7));
        assertEquals(2, out.size());
        assertUntied(out);
        assertEquals(TabEffect.TAP, TabEffect.kind(out.get(1).marks()));
        assertEquals(4, out.get(1).duration(), 0);
    }

    @Test
    public void aTappedSourceCanStillRingIntoItsUnannotatedTieTarget() {
        byte[] gray = page();
        bow(gray, 2, -1);
        var out = rasterWithWords(gray, "T", 100, fret(100, 2, 7), fret(340, 2, 7));
        assertFalse(out.get(0).tied());
        assertTrue(out.get(1).tied());
        assertEquals(TabEffect.TAP, TabEffect.kind(out.get(0).marks()));
        assertEquals(out.get(0).marks(), out.get(1).marks());
    }

    @Test
    public void explicitHammerAttackAndPitchSurviveTheFullWordPipeline() {
        byte[] gray = page();
        bow(gray, 2, -1, 100, 260);
        var target = new TablatureDecoder.Fret(260, 120, 2, 9, 4, 0, 0, 0, true, 1);
        var out = rasterWithWords(gray, "H", 260, fret(100, 2, 7), target);
        assertEquals(2, out.size());
        assertUntied(out);
        assertEquals(9, out.get(1).fret());
        assertEquals(TabEffect.HAMMER, TabEffect.kind(out.get(1).marks()));
    }

    @Test
    public void explicitPullAttackAndPitchSurviveTheFullWordPipeline() {
        byte[] gray = page();
        bow(gray, 2, -1, 100, 260);
        var target = new TablatureDecoder.Fret(260, 120, 2, 5, 4, 0, 0, 0, true, 1);
        var out = rasterWithWords(gray, "P", 260, fret(100, 2, 7), target);
        assertEquals(2, out.size());
        assertUntied(out);
        assertEquals(5, out.get(1).fret());
        assertEquals(TabEffect.PULL, TabEffect.kind(out.get(1).marks()));
    }

    @Test
    public void invalidSameFretHammerLetterDoesNotEraseATrueTie() {
        byte[] gray = page();
        bow(gray, 2, -1, 100, 260);
        var out = rasterWithWords(gray, "H", 260, fret(100, 2, 7), fret(260, 2, 7));
        assertTrue(out.get(1).tied());
        assertEquals(0, TabEffect.kind(out.get(1).marks()));
    }

    @Test
    public void wideFretGlyphCanMoveTheVisibleSourceEndpointInsideItsOuterWindow() {
        byte[] gray = page();
        int left = 122, right = 327;
        for (int x = left; x <= right; x++) {
            float phase = (x - left) / (float) (right - left);
            int y = Math.round(120 - GAP * (.1f + .7f * (float) Math.sin(Math.PI * phase)));
            gray[y * W + x] = 0;
        }
        var out = apply(gray, fret(100, 2, 12), fret(340, 2, 12));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void antialiasedContourUsesCombinedInkAndItsCenterInsteadOfOneDarkPixel() {
        byte[] gray = page();
        for (int x = 108; x <= 327; x++) {
            float phase = (x - 108) / 219f;
            int y = Math.round(120 - GAP * (.1f + .7f * (float) Math.sin(Math.PI * phase)));
            gray[y * W + x] = (byte) 223;
            gray[(y + 1) * W + x] = (byte) 223;
        }
        var out = apply(gray, fret(100, 2, 7), fret(340, 2, 7));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void insufficientPaleInkCannotSupplyAContour() {
        byte[] gray = page();
        for (int x = 108; x <= 327; x++) {
            float phase = (x - 108) / 219f;
            int y = Math.round(120 - GAP * (.1f + .7f * (float) Math.sin(Math.PI * phase)));
            gray[y * W + x] = (byte) 223;
        }
        assertUntied(apply(gray, fret(100, 2, 7), fret(340, 2, 7)));
    }

    private void finiteBow(byte[] gray, int left, int right, float cy, int direction, float rise) {
        for (int x = left; x <= right; x++) {
            double phase = (x - left) / (double) (right - left);
            int y = Math.round(cy + direction * (2 + rise * (float) Math.sin(Math.PI * phase)));
            gray[y * W + x] = 0;
        }
    }

    private void courtesyParentheses(byte[] gray, int center, int cy) {
        for (int dy = -9; dy <= 9; dy++) {
            int bow = Math.round(4 * (float) Math.sqrt(1 - dy * dy / 100f));
            gray[(cy + dy) * W + center - 14 - bow] = 0;
            gray[(cy + dy) * W + center + 14 + bow] = 0;
        }
    }

    @Test
    public void longArcCanEndOutsideWideParenthesizedTargetGlyph() {
        byte[] gray = page();
        finiteBow(gray, 122, 318, 120, -1, 14);
        courtesyParentheses(gray, 340, 120);
        var target = TabNotation.parse("(12)", 330, 350, 120, 2).get(0);
        var out = apply(gray, fret(100, 2, 12), target);
        assertEquals(2, out.size());
        assertFalse(out.get(0).tied());
        assertTrue(out.get(1).tied());
        assertEquals(target.duration(), out.get(1).duration(), 0);
        assertEquals(target.fret(), out.get(1).fret());
    }

    @Test
    public void arcReturningAtFiniteTargetWindowBoundaryCanProveATie() {
        // Target 340 minus 1.30 * gap 20 is exactly 314. Parentheses remain separate.
        byte[] gray = page();
        finiteBow(gray, 122, 314, 120, -1, 14);
        courtesyParentheses(gray, 340, 120);
        var out = apply(gray, fret(100, 2, 12), fret(340, 2, 12));
        assertEquals(2, out.size());
        assertFalse(out.get(0).tied());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void arcEndingBeforeFiniteTargetWindowCannotProveATie() {
        // Strictly outside the finite 1.30-gap window; no disconnected glyph may fill it.
        byte[] gray = page();
        finiteBow(gray, 122, 312, 120, -1, 14);
        courtesyParentheses(gray, 340, 120);
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }

    @Test
    public void targetEndpointWindowCannotBridgeAnEarlierDisconnectedTail() {
        byte[] gray = page();
        finiteBow(gray, 122, 318, 120, -1, 14);
        for (int y = 95; y < 120; y++) gray[y * W + 310] = (byte) 255;
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }

    @Test
    public void incompleteRisingCurveInsideTargetWindowDoesNotHold() {
        byte[] gray = page();
        for (int x = 122; x <= 318; x++) {
            double phase = (x - 122) / 196.0;
            int y = Math.round(120 - (2 + 14 * (float) Math.sin(Math.PI * phase * .5)));
            gray[y * W + x] = 0;
        }
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }

    @Test
    public void faintFlatRuleThroughTargetWindowDoesNotHold() {
        byte[] gray = page();
        for (int x = 122; x <= 318; x++) {
            gray[112 * W + x] = (byte) 223;
            gray[113 * W + x] = (byte) 223;
        }
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }

    @Test
    public void neighboringStringArcEndingInTargetWindowCannotBeBorrowed() {
        byte[] gray = page();
        finiteBow(gray, 122, 318, 140, -1, 14);
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }

    @Test
    public void shortBowedFragmentNeedsMinimumActuallyConnectedSpan() {
        byte[] gray = page();
        finiteBow(gray, 122, 127, 120, -1, 4);
        assertUntied(apply(gray, fret(100, 2, 12), fret(144, 2, 12)));
    }

    @Test
    public void courtesyParenthesesWithoutConnectingContourStaySeparate() {
        byte[] gray = page();
        courtesyParentheses(gray, 100, 120);
        courtesyParentheses(gray, 340, 120);
        assertUntied(apply(gray, fret(100, 2, 12), fret(340, 2, 12)));
    }
}
