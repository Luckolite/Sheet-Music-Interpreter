// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic shafts and curved paths; no document pixels or song data. */
public class TabOwnedContinuationStemTest {
    private static final int W = 500, H = 300;
    private static final float GAP = 20;

    @Test
    public void tallDetachedStemPreservesOneBlankContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        var out = run(gray, List.of(fret(100, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void isolatedShortNarrowStemStillProvesABlankContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 211);
        var out = run(gray, List.of(fret(100, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void tallFlaggedStemRetainsOwnedContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        for (int x = 181; x <= 194; x++) gray[(234 - (x - 181) / 2) * W + x] = 0;
        var out = run(gray, List.of(fret(100, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void tallBeamedStemRetainsOwnedContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        for (int y = 230; y <= 233; y++) for (int x = 165; x <= 195; x++) gray[y * W + x] = 0;
        var out = run(gray, List.of(fret(100, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void shortWideSerifComponentCannotInventAChordContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        curve(gray, 110, 175, 140, 6, 6);
        serif(gray, 180);
        var out = run(gray, List.of(fret(100, 2, 7), fret(100, 3, 5)));
        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(TablatureDecoder.Fret::tied));
    }

    @Test
    public void shortBranchingLetterLikeComponentCannotInventContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 206, 223);
        for (int d = 0; d <= 7; d++) {
            gray[(214 - d) * W + 180 + d] = 0;
            gray[(214 + d) * W + 180 + d] = 0;
        }
        assertEquals(1, run(gray, List.of(fret(100, 2, 7))).size());
    }

    @Test
    public void shortNarrowComponentBesideOtherTextInkIsNotIsolated() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 211);
        for (int y = 198; y <= 207; y++) gray[y * W + 184] = 0;
        assertEquals(1, run(gray, List.of(fret(100, 2, 7))).size());
    }

    @Test
    public void tallStemWithoutConnectingBowCannotInventContinuation() {
        var gray = page();
        stem(gray, 180, 194, 234);
        assertEquals(1, run(gray, List.of(fret(100, 2, 7))).size());
    }

    @Test
    public void existingPrintedTargetDoesNotRequireBlankStemOwnership() {
        var gray = page();
        curve(gray, 118, 155, 120, 3, 8);
        serif(gray, 180);
        var out = run(gray, List.of(fret(100, 2, 7), fret(180, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void parsedHarmonicTargetKeepsItsAttackAndEffectUnderAStemAndBow() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        var harmonic = TabNotation.parse("<7>", 170, 190, 120, 2).get(0);
        var out = runWithPerformance(gray, List.of(fret(100, 2, 7), harmonic));
        assertEquals(2, out.size());
        assertFalse(out.get(1).tied());
        assertEquals(harmonic.marks(), out.get(1).marks());
        assertEquals(TabEffect.HARMONIC, TabEffect.kind(out.get(1).marks()));
    }

    @Test
    public void premarkedTapTargetKeepsItsAttackAndEffectUnderAStemAndBow() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        var tapped = markedFret(180, 2, 7, TabEffect.encode(TabEffect.TAP, 0));
        var out = runWithPerformance(gray, List.of(fret(100, 2, 7), tapped));
        assertEquals(2, out.size());
        assertFalse(out.get(1).tied());
        assertEquals(tapped.marks(), out.get(1).marks());
        assertEquals(TabEffect.TAP, TabEffect.kind(out.get(1).marks()));
    }

    @Test
    public void outgoingTapCanRingIntoAnUnannotatedPrintedTarget() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        var tapped = markedFret(100, 2, 7, TabEffect.encode(TabEffect.TAP, 0));
        var out = runWithPerformance(gray, List.of(tapped, fret(180, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
        assertEquals(tapped.marks(), out.get(1).marks());
    }

    @Test
    public void outgoingTapCanRingIntoAnOwnedBlankContinuation() {
        var gray = page();
        curve(gray, 110, 175, 120, 6, 6);
        stem(gray, 180, 194, 234);
        var tapped = markedFret(100, 2, 7, TabEffect.encode(TabEffect.TAP, 0));
        var out = runWithPerformance(gray, List.of(tapped));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
        assertEquals(tapped.marks(), out.get(1).marks());
    }

    @Test
    public void matchingParsedVibratoAtShortSpacingRemainsHeld() {
        var gray = page();
        curve(gray, 110, 125, 120, 6, 6);
        stem(gray, 130, 194, 234);
        var source = TabNotation.parse("7~", 90, 110, 120, 2).get(0);
        var target = TabNotation.parse("7~", 120, 140, 120, 2).get(0);
        var out = runWithPerformance(gray, List.of(source, target));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
        assertEquals(TabEffect.VIBRATO, out.get(1).marks());
    }

    @Test
    public void matchingPalmMuteAtShortSpacingRemainsHeld() {
        var gray = page();
        curve(gray, 110, 125, 120, 6, 6);
        stem(gray, 130, 194, 234);
        var source = markedFret(100, 2, 7, TabEffect.PALM_MUTE);
        var target = markedFret(130, 2, 7, TabEffect.PALM_MUTE);
        var out = runWithPerformance(gray, List.of(source, target));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
        assertEquals(TabEffect.PALM_MUTE, out.get(1).marks());
    }

    @Test
    public void changedTargetModulationIsNotErasedByContinuationInheritance() {
        var gray = page();
        curve(gray, 110, 125, 120, 6, 6);
        stem(gray, 130, 194, 234);
        var target = markedFret(130, 2, 7, TabEffect.VIBRATO);
        var out = runWithPerformance(gray, List.of(fret(100, 2, 7), target));
        assertEquals(2, out.size());
        assertFalse(out.get(1).tied());
        assertEquals(target.marks(), out.get(1).marks());
    }

    @Test
    public void explicitTargetOrnamentKeepsItsAttackAndMetadata() {
        var gray = page();
        curve(gray, 110, 125, 120, 6, 6);
        stem(gray, 130, 194, 234);
        var target = markedFret(130, 2, 7, NoteOrnament.TRILL);
        var out = runWithPerformance(gray, List.of(fret(100, 2, 7), target));
        assertEquals(2, out.size());
        assertFalse(out.get(1).tied());
        assertEquals(target.marks(), out.get(1).marks());
    }

    @Test
    public void returningTargetShoulderInsideFiniteOuterWindowProvesTie() {
        var gray = page();
        curve(gray, 99, 215, 120, 3, 8);
        var out = run(gray, List.of(fret(80, 2, 7), fret(240, 2, 7)));
        assertEquals(2, out.size());
        assertTrue(out.get(1).tied());
    }

    @Test
    public void fragmentEndingBeforeFiniteTargetWindowCannotProveTie() {
        var gray = page();
        curve(gray, 99, 210, 120, 3, 8);
        assertFalse(run(gray, List.of(fret(80, 2, 7), fret(240, 2, 7))).get(1).tied());
    }

    @Test
    public void returningFragmentAcrossAnUnbrokenWhiteGapCannotProveTie() {
        var gray = page();
        curve(gray, 99, 215, 120, 3, 8);
        for (int x = 145; x <= 149; x++)
            for (int y = 100; y < 120; y++) gray[y * W + x] = (byte) 255;
        assertFalse(run(gray, List.of(fret(80, 2, 7), fret(240, 2, 7))).get(1).tied());
    }

    @Test
    public void flatTargetShoulderAndChangedFretRemainAttacks() {
        var gray = page();
        for (int x = 99; x <= 215; x++) gray[113 * W + x] = 0;
        assertFalse(run(gray, List.of(fret(80, 2, 7), fret(240, 2, 7))).get(1).tied());
        gray = page();
        curve(gray, 99, 215, 120, 3, 8);
        assertFalse(run(gray, List.of(fret(80, 2, 7), fret(240, 2, 8))).get(1).tied());
        assertTrue(
                run(gray, List.of(fret(80, 2, 7), fret(240, 3, 7))).stream()
                        .noneMatch(TablatureDecoder.Fret::tied));
    }

    private static TablatureDecoder.Fret fret(float x, int string, int value) {
        return new TablatureDecoder.Fret(x, 80 + string * GAP, string, value);
    }

    private static TablatureDecoder.Fret markedFret(float x, int string, int value, int marks) {
        return new TablatureDecoder.Fret(x, 80 + string * GAP, string, value, 0, 0, 0, marks);
    }

    private static List<TablatureDecoder.Fret> runWithPerformance(
            byte[] gray, List<TablatureDecoder.Fret> frets) {
        var tab = new TablatureDecoder.Staff(80, GAP, -1, frets, List.of(20f, 480f));
        var ties = TabRhythmContinuations.apply(List.of(tab), gray, W, H);
        return TabPerformanceMarks.apply(ties, List.of(), W, H).get(0).frets();
    }

    private static List<TablatureDecoder.Fret> run(byte[] gray, List<TablatureDecoder.Fret> frets) {
        var tab = new TablatureDecoder.Staff(80, GAP, -1, frets, List.of(20f, 480f));
        return TabRhythmContinuations.apply(List.of(tab), gray, W, H).get(0).frets();
    }

    private static byte[] page() {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int string = 0; string < 6; string++)
            for (int x = 20; x <= 480; x++) gray[(80 + string * 20) * W + x] = 0;
        return gray;
    }

    private static void stem(byte[] gray, int x, int from, int to) {
        for (int y = from; y <= to; y++) gray[y * W + x] = 0;
    }

    private static void serif(byte[] gray, int x) {
        stem(gray, x, 206, 223);
        for (int dx = -5; dx <= 5; dx++) {
            gray[206 * W + x + dx] = 0;
            gray[223 * W + x + dx] = 0;
        }
    }

    private static void curve(byte[] gray, int left, int right, int cy, float base, float rise) {
        int previous = Math.round(cy - base);
        for (int x = left; x <= right; x++) {
            int y =
                    (int)
                            Math.round(
                                    cy
                                            - base
                                            - rise
                                                    * Math.sin(
                                                            Math.PI * (x - left) / (right - left)));
            int steps = Math.max(1, Math.abs(y - previous));
            for (int step = 0; step <= steps; step++) {
                float t = step / (float) steps;
                int px = x == left ? x : Math.round(x - 1 + t),
                        py = Math.round(previous + (y - previous) * t);
                gray[py * W + px] = 0;
            }
            previous = y;
        }
    }
}
