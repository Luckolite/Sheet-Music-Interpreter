// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural plate, voice, dot, span, and rejection controls; no score-derived pixels. */
public class WholeRestClassificationControlsTest {
    static final int W = 800, H = 360, GAP = 16, TOP = 100;
    static final MeasureRegion M = new MeasureRegion(.02f, .98f, .16f, .88f);

    static void box(byte[] g, int l, int t, int r, int b, int c) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) c;
    }

    static byte[] frame(boolean raised, int cx, int dots) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 5; i++) box(g, 20, TOP + i * GAP, 779, TOP + i * GAP, 0);
        int rule = raised ? TOP - GAP : TOP + GAP;
        if (raised) box(g, cx - 24, rule, cx + 24, rule, 0);
        box(g, cx - 13, rule + 1, cx + 13, rule + 6, 0);
        for (int i = 0; i < dots; i++)
            box(g, cx + 22 + 10 * i, rule + 7, cx + 25 + 10 * i, rule + 10, 0);
        return g;
    }

    static ScoreNoteEvent note(float x, float y, int stem) {
        return new ScoreNoteEvent(
                        0,
                        (x / W - M.left()) / (M.right() - M.left()),
                        0,
                        0,
                        1,
                        y / H,
                        false,
                        0,
                        0,
                        2,
                        2)
                .withStemDirection(stem);
    }

    static List<WholeRestClassifier.Decision> read(
            byte[] g, double span, List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        return WholeRestClassifier.detect(
                g,
                W,
                H,
                List.of(M),
                List.of(new SixteenthRestDetector.Staff(TOP, TOP + GAP * 4, GAP, 0, 1)),
                notes,
                rests);
    }

    static WholeRestClassifier.Decision one(
            byte[] g, double span, List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        var found = read(g, span, notes, rests);
        assertEquals(found.toString(), 1, found.size());
        return found.get(0);
    }

    static void full(boolean raised, double span) {
        var d = one(frame(raised, 400, 0), span, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.PROVED_FULL, d.status());
        assertTrue(d.event().isFullMeasure());
        assertEquals(4, d.event().durationBeats(), 0);
        assertEquals(span, d.event().resolvedDurationBeats(span), 0);
    }

    static void literal(boolean raised, int cx, int dots, double span, double value) {
        var d = one(frame(raised, cx, dots), span, List.of(), List.of());
        assertEquals(
                dots == 0
                        ? WholeRestClassifier.Status.UNRESOLVED
                        : WholeRestClassifier.Status.PROVED_LITERAL,
                d.status());
        assertFalse(d.event().isFullMeasure());
        assertEquals(value, d.event().durationBeats(), 0);
        assertEquals(dots, d.dots().size());
    }

    static void unresolved(boolean raised, List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        var d = one(frame(raised, 400, 0), 6, notes, rests);
        assertEquals(d.toString(), WholeRestClassifier.Status.UNRESOLVED, d.status());
        assertFalse(d.event().isFullMeasure());
        assertEquals(4, d.event().durationBeats(), 0);
    }

    static void absent(byte[] g) {
        assertTrue(
                read(g, 4, List.of(), List.of()).toString(),
                read(g, 4, List.of(), List.of()).isEmpty());
    }

    @Test
    public void normalFullThreeFourRetainsWholeGlyph() {
        full(false, 3);
    }

    @Test
    public void raisedFullThreeFourRetainsWholeGlyph() {
        full(true, 3);
    }

    @Test
    public void normalFullFourFour() {
        full(false, 4);
    }

    @Test
    public void raisedFullFourFour() {
        full(true, 4);
    }

    @Test
    public void normalFullSixEight() {
        full(false, 3);
    }

    @Test
    public void raisedFullSixEight() {
        full(true, 3);
    }

    @Test
    public void normalFullLongSixQuarterBar() {
        full(false, 6);
    }

    @Test
    public void raisedFullLongSevenQuarterBar() {
        full(true, 7);
    }

    @Test
    public void independentlyProvedPickupSpanDoesNotAlterGlyph() {
        full(true, 1.5);
    }

    @Test
    public void normalLiteralWholeInSixQuarterBar() {
        literal(false, 220, 0, 6, 4);
    }

    @Test
    public void raisedLiteralWholeInSixQuarterBar() {
        literal(true, 220, 0, 6, 4);
    }

    @Test
    public void normalSingleDottedWholeInEightQuarterBar() {
        literal(false, 400, 1, 8, 6);
    }

    @Test
    public void raisedSingleDottedWholeInEightQuarterBar() {
        literal(true, 400, 1, 8, 6);
    }

    @Test
    public void normalDoubleDottedWholeInEightQuarterBar() {
        literal(false, 400, 2, 8, 7);
    }

    @Test
    public void raisedDoubleDottedWholeInEightQuarterBar() {
        literal(true, 400, 2, 8, 7);
    }

    @Test
    public void noncenteredRightPlateRemainsLiteral() {
        literal(true, 575, 0, 4, 4);
    }

    @Test
    public void normalDownStemLowerVoiceIsIndependent() {
        var d =
                one(
                        frame(false, 400, 0),
                        4,
                        List.of(note(150, 180, -1), note(630, 175, -1)),
                        List.of());
        assertTrue(d.toString(), d.event().isFullMeasure());
    }

    @Test
    public void raisedDownStemLowerVoiceIsIndependent() {
        var d =
                one(
                        frame(true, 400, 0),
                        4,
                        List.of(note(150, 180, -1), note(630, 175, -1)),
                        List.of());
        assertTrue(d.toString(), d.event().isFullMeasure());
    }

    @Test
    public void raisedUpStemLaneDoesNotAssertSilentVoice() {
        unresolved(true, List.of(note(150, 180, 1)), List.of());
    }

    @Test
    public void normalUpStemLaneDoesNotAssertSilentVoice() {
        unresolved(false, List.of(note(150, 180, 1)), List.of());
    }

    @Test
    public void unknownStemDoesNotAssertIndependentVoice() {
        unresolved(true, List.of(note(150, 180, 0)), List.of());
    }

    @Test
    public void mixedStemLaneIsUnresolved() {
        unresolved(true, List.of(note(150, 180, -1), note(650, 180, 1)), List.of());
    }

    @Test
    public void downStemUpperHeadIsStillConflicting() {
        unresolved(false, List.of(note(150, 105, -1)), List.of());
    }

    @Test
    public void otherWholeRestDoesNotProveSeparateVoice() {
        unresolved(true, List.of(), List.of(new ScoreRestEvent(0, .2f, .3f, .02f, 0, 1, 4)));
    }

    @Test
    public void sameStaffShortRestDoesNotProveSeparateVoice() {
        unresolved(false, List.of(), List.of(new ScoreRestEvent(0, .8f, .4f, .02f, 0, 1, 2)));
    }

    @Test
    public void literalWholeAndRemainingTwoBeatsNeverPromotes() {
        var d =
                one(
                        frame(false, 400, 0),
                        6,
                        List.of(),
                        List.of(new ScoreRestEvent(0, .85f, .4f, .02f, 0, 1, 2)));
        assertEquals(4, d.event().durationBeats(), 0);
        assertFalse(d.event().isFullMeasure());
    }

    @Test
    public void unknownMeterRetainsProvedGlyphButDoesNotGuessSpan() {
        var d = one(frame(true, 400, 0), Double.NaN, List.of(), List.of());
        assertTrue(d.event().isFullMeasure());
        assertTrue(Double.isNaN(d.event().resolvedDurationBeats(Double.NaN)));
    }

    @Test
    public void zeroSpanDoesNotResolvePerformedFullDuration() {
        var d = one(frame(false, 400, 0), 0, List.of(), List.of());
        assertTrue(d.event().isFullMeasure());
        assertTrue(Double.isNaN(d.event().resolvedDurationBeats(0)));
    }

    @Test
    public void rawPixelsAreNeverMutated() {
        var g = frame(true, 400, 2);
        var before = g.clone();
        read(g, 8, List.of(), List.of());
        assertArrayEquals(before, g);
    }

    @Test
    public void missingSupportingLedgerRejects() {
        var g = frame(true, 400, 0);
        box(g, 375, 84, 425, 84, 255);
        absent(g);
    }

    @Test
    public void missingPlateRejects() {
        var g = frame(true, 400, 0);
        box(g, 380, 85, 420, 94, 255);
        absent(g);
    }

    @Test
    public void detachedNormalPlateRejects() {
        var g = frame(false, 400, 0);
        box(g, 380, 117, 420, 118, 255);
        absent(g);
    }

    @Test
    public void absentFifthPrintedRuleRejects() {
        var g = frame(true, 400, 0);
        box(g, 20, 164, 779, 164, 255);
        absent(g);
    }

    @Test
    public void raisedLeftArmCropRejects() {
        var g = frame(true, 400, 0);
        box(g, 376, 84, 389, 84, 255);
        absent(g);
    }

    @Test
    public void raisedRightArmCropRejects() {
        var g = frame(true, 400, 0);
        box(g, 410, 84, 424, 84, 255);
        absent(g);
    }

    @Test
    public void beamletWithJoinedShaftRejects() {
        var g = frame(true, 400, 0);
        box(g, 376, 60, 377, 100, 0);
        absent(g);
    }

    @Test
    public void longBeamCannotBeRaisedSupport() {
        var g = frame(true, 400, 0);
        box(g, 320, 84, 480, 84, 0);
        absent(g);
    }

    @Test
    public void normalJoinedShaftRejects() {
        var g = frame(false, 400, 0);
        box(g, 402, 80, 403, 145, 0);
        absent(g);
    }

    @Test
    public void excessivePlateDepthRejects() {
        var g = frame(true, 400, 0);
        box(g, 387, 85, 413, 99, 0);
        absent(g);
    }

    @Test
    public void thinPlateFragmentRejects() {
        var g = frame(false, 400, 0);
        box(g, 380, 119, 420, 122, 255);
        absent(g);
    }

    @Test
    public void noteHeadOwnsPlateRatherThanFullSilence() {
        assertTrue(read(frame(true, 400, 0), 4, List.of(note(400, 87, -1)), List.of()).isEmpty());
    }

    @Test
    public void unconnectedFloatingRectangleRejects() {
        var g = frame(false, 400, 0);
        box(g, 380, 117, 420, 122, 255);
        box(g, 387, 125, 413, 130, 0);
        absent(g);
    }

    @Test
    public void legacyConstructorDurationFourRemainsLiteral() {
        var r = new ScoreRestEvent(0, .5f, .3f, .02f, 0, 1, 4);
        assertFalse(r.isFullMeasure());
        assertEquals(4, r.resolvedDurationBeats(3), 0);
    }

    @Test
    public void legacyRestCannotSeedFreshFullWithoutRawPlate() {
        var g = frame(true, 400, 0);
        box(g, 375, 84, 425, 95, 255);
        assertTrue(
                read(g, 3, List.of(), List.of(new ScoreRestEvent(0, .5f, .3f, .02f, 0, 1, 4)))
                        .isEmpty());
    }

    @Test
    public void freshPlateMayReclassifyExactLegacyCopy() {
        var g = frame(true, 400, 0);
        var first = one(g, 3, List.of(), List.of());
        var old =
                new ScoreRestEvent(
                        0,
                        first.event().positionInMeasure(),
                        first.event().pageY(),
                        first.event().pageHeight(),
                        0,
                        1,
                        4);
        assertTrue(one(g, 3, List.of(), List.of(old)).event().isFullMeasure());
        assertFalse(old.isFullMeasure());
    }

    @Test
    public void unrelatedLowerNoteDoesNotStealActualRestDot() {
        var d = one(frame(true, 400, 1), 8, List.of(note(420, 190, -1)), List.of());
        assertEquals(6, d.event().durationBeats(), 0);
        assertEquals(1, d.dots().size());
        assertFalse(d.event().isFullMeasure());
    }

    @Test
    public void neighboringDottedNoteCannotCreateFullSilence() {
        var g = frame(true, 400, 1);
        var n =
                new ScoreNoteEvent(0, (414f / W - .02f) / .96f, 0, 0, 1, 92f / H, false, 1, 0)
                        .withStemDirection(-1);
        var r = read(g, 8, List.of(n), List.of());
        assertTrue(r.stream().noneMatch(d -> d.event().isFullMeasure()));
        assertTrue(r.stream().noneMatch(d -> d.event().durationBeats() == 6));
    }

    @Test
    public void faintRestDotIsUnresolvedRatherThanIgnored() {
        var g = frame(true, 400, 0);
        box(g, 422, 91, 425, 94, 205);
        var d = one(g, 8, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
        assertFalse(d.event().isFullMeasure());
    }

    @Test
    public void tinyRestDotDoesNotProveUndottedFullBar() {
        var g = frame(false, 400, 0);
        box(g, 424, 124, 424, 124, 0);
        var d = one(g, 8, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
    }

    @Test
    public void dotClippedBySearchBandIsUnresolved() {
        var g = frame(true, 400, 0);
        box(g, 422, 96, 425, 101, 0);
        var d = one(g, 8, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
    }

    @Test
    public void connectedDotAndShaftIsUnresolved() {
        var g = frame(true, 400, 0);
        box(g, 422, 91, 425, 96, 0);
        box(g, 424, 95, 425, 110, 0);
        var d = one(g, 8, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
    }

    @Test
    public void thirdAugmentationMarkDoesNotClaimUndottedBar() {
        var g = frame(true, 400, 2);
        box(g, 446, 91, 449, 94, 0);
        var d = one(g, 10, List.of(), List.of());
        assertFalse(d.event().isFullMeasure());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
    }

    @Test
    public void crowdedUnidentifiedInkPreventsFullBar() {
        var g = frame(false, 400, 0);
        box(g, 444, 120, 445, 128, 0);
        var d = one(g, 4, List.of(), List.of());
        assertEquals(WholeRestClassifier.Status.UNRESOLVED, d.status());
    }

    @Test
    public void normalSupportCropRejects() {
        var g = frame(false, 400, 0);
        box(g, 375, 116, 390, 116, 255);
        absent(g);
    }

    @Test
    public void ordinarySittingHalfPlateIsNotWhole() {
        var g = frame(false, 400, 0);
        box(g, 380, 117, 420, 125, 255);
        box(g, 387, 126, 413, 131, 0);
        absent(g);
    }

    @Test
    public void rawSizeMismatchRejectsBoundedly() {
        assertTrue(
                WholeRestClassifier.detect(
                                new byte[2],
                                W,
                                H,
                                List.of(M),
                                List.of(new SixteenthRestDetector.Staff(TOP, 164, GAP, 0, 1)),
                                List.of(),
                                List.of())
                        .isEmpty());
    }

    @Test
    public void invalidNotePositionDoesNotPromoteFull() {
        unresolved(true, List.of(note(-100, 180, -1)), List.of());
    }

    @Test
    public void otherStaffRestCannotVetoFreshPlate() {
        var d =
                one(
                        frame(true, 400, 0),
                        4,
                        List.of(),
                        List.of(new ScoreRestEvent(0, .2f, .3f, .02f, 1, 2, 2)));
        assertTrue(d.event().isFullMeasure());
    }

    @Test
    public void otherMeasureRestCannotVetoFreshPlate() {
        var d =
                one(
                        frame(false, 400, 0),
                        4,
                        List.of(),
                        List.of(new ScoreRestEvent(1, .2f, .3f, .02f, 0, 1, 2)));
        assertTrue(d.event().isFullMeasure());
    }

    @Test
    public void normalThickPrintedRulesKeepCompletePlate() {
        var g = frame(false, 400, 0);
        for (int i = 0; i < 5; i++) box(g, 20, 100 + i * 16, 779, 101 + i * 16, 0);
        box(g, 387, 118, 413, 123, 0);
        var d = one(g, 4, List.of(), List.of());
        assertTrue(d.toString(), d.event().isFullMeasure());
    }

    @Test
    public void raisedThickLedgerDoesNotBecomeUnknownDot() {
        var g = frame(true, 400, 0);
        box(g, 376, 84, 424, 87, 0);
        box(g, 387, 88, 413, 93, 0);
        var d = one(g, 4, List.of(), List.of());
        assertTrue(d.toString(), d.event().isFullMeasure());
    }
}
