// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedSlurOwnersTest {
    private static final int W = 240, H = 220;

    private StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 150, 12}, new float[] {W - 1, 150, 12}));
    }

    private byte[] pixels(boolean shaft) {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 40; x <= 190; x++) {
            double t = 2.0 * (x - 40) / 150 - 1;
            gray[(int) Math.round(55 + 14 * t * t) * W + x] = 0;
        }
        if (shaft)
            for (int x = 41; x <= 42; x++) for (int y = 79; y <= 152; y++) gray[y * W + x] = 0;
        for (int y = 150; y <= 154; y++) for (int x = 30; x <= 38; x++) gray[y * W + x] = 0;
        for (int y = 126; y <= 130; y++) for (int x = 186; x <= 194; x++) gray[y * W + x] = 0;
        return gray;
    }

    private PrintedSlurContours.Bow bow(byte[] gray) {
        var bows = PrintedSlurContours.find(gray, null, W, H, 0, 20, W - 1, H - 1, track()).bows();
        assertEquals(1, bows.size());
        return bows.get(0);
    }

    private PrintedSlurOwners.Head start(int pitch, long tick) {
        return new PrintedSlurOwners.Head(7, 34, 152, 0, 1, 1, tick, pitch);
    }

    private PrintedSlurOwners.Head end(int pitch, long tick, int voice) {
        return new PrintedSlurOwners.Head(8, 190, 128, 0, 1, voice, tick, pitch);
    }

    private PrintedSlurOwners.Result resolve(
            byte[] gray, List<PrintedSlurOwners.Head> heads, Set<PrintedSlurOwners.Tie> ties) {
        return PrintedSlurOwners.resolve(
                "original-procedural-arc", 3, bow(gray), heads, ties, gray, W, H, track());
    }

    @Test
    public void actualLongShaftBindsTheFirstHeadWithoutSamePitchRestriction() {
        var result = resolve(pixels(true), List.of(start(60, 0), end(72, 100, 1)), Set.of());
        assertEquals(PrintedSlurOwners.Status.RESOLVED, result.status());
        var span = result.span().orElseThrow();
        assertEquals(new ScoreSlurPages.Anchor(3, 7), span.start());
        assertEquals(new ScoreSlurPages.Anchor(3, 8), span.end());
        assertEquals(ScoreSlurSpan.Placement.ABOVE, span.placement());
    }

    @Test
    public void absentLongShaftCannotBeReplacedByVerticalProximity() {
        var result = resolve(pixels(false), List.of(start(60, 0), end(72, 100, 1)), Set.of());
        assertEquals(PrintedSlurOwners.Status.NO_START_OWNER, result.status());
        assertTrue(result.span().isEmpty());
    }

    @Test
    public void overlappingHeadOwnershipStaysUnresolved() {
        var other = new PrintedSlurOwners.Head(9, 34, 152, 0, 1, 2, 0, 67);
        var result = resolve(pixels(true), List.of(start(60, 0), other, end(72, 100, 1)), Set.of());
        assertEquals(PrintedSlurOwners.Status.AMBIGUOUS_START, result.status());
    }

    @Test
    public void writtenClockAndVoiceMustAgreeWithThePhraseDirection() {
        assertEquals(
                PrintedSlurOwners.Status.REVERSED_CLOCK,
                resolve(pixels(true), List.of(start(60, 100), end(72, 0, 1)), Set.of()).status());
        assertEquals(
                PrintedSlurOwners.Status.DIFFERENT_LANES,
                resolve(pixels(true), List.of(start(60, 0), end(72, 100, 2)), Set.of()).status());
    }

    @Test
    public void exactRetainedTieDoesNotProduceADuplicateSlur() {
        var result =
                resolve(
                        pixels(true),
                        List.of(start(60, 0), end(60, 100, 1)),
                        Set.of(new PrintedSlurOwners.Tie(7, 8)));
        assertEquals(PrintedSlurOwners.Status.TIE_ALREADY_RETAINED, result.status());
    }

    @Test
    public void sameSoundingPitchWithoutTieProofIsAnExplicitAmbiguity() {
        assertEquals(
                PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED,
                resolve(pixels(true), List.of(start(60, 0), end(60, 100, 1)), Set.of()).status());
    }

    @Test
    public void contradictedRetainedTieIsNotTreatedAsValidatedGeometry() {
        assertEquals(
                PrintedSlurOwners.Status.TIE_PITCH_CONFLICT,
                resolve(
                                pixels(true),
                                List.of(start(60, 0), end(72, 100, 1)),
                                Set.of(new PrintedSlurOwners.Tie(7, 8)))
                        .status());
    }

    @Test
    public void duplicateHeadIdentitiesFailBeforeResolving() {
        try {
            resolve(pixels(true), List.of(start(60, 0), start(60, 0), end(72, 100, 1)), Set.of());
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void graceAndPrincipalOwnershipUseWrittenOrderWithoutRetimingEitherNote() {
        var grace = new PrintedSlurOwners.Head(7, 34, 152, 0, 1, 1, 100, -1000000, 60);
        assertEquals(
                PrintedSlurOwners.Status.RESOLVED,
                resolve(pixels(true), List.of(grace, end(72, 100, 1)), Set.of()).status());
        var after = new PrintedSlurOwners.Head(8, 190, 128, 0, 1, 1, 100, 1, 72);
        assertEquals(
                PrintedSlurOwners.Status.RESOLVED,
                resolve(pixels(true), List.of(start(60, 100), after), Set.of()).status());
    }

    @Test
    public void nearHeadEdgeHasItsOwnRawBodyProofWithoutAnUpwardStem() {
        var gray = pixels(false);
        for (int y = 81; y <= 87; y++) for (int x = 30; x <= 38; x++) gray[y * W + x] = 0;
        var head = new PrintedSlurOwners.Head(7, 34, 84, 0, 1, 1, 0, 83);
        assertEquals(
                PrintedSlurOwners.Status.RESOLVED,
                resolve(gray, List.of(head, end(69, 100, 1)), Set.of()).status());
    }

    @Test
    public void bareRailCannotSupplyNearHeadEdgeBodyProof() {
        var gray = pixels(false);
        for (int x = 25; x <= 42; x++) gray[84 * W + x] = 0;
        var head = new PrintedSlurOwners.Head(7, 34, 84, 0, 1, 1, 0, 83);
        assertEquals(
                PrintedSlurOwners.Status.NO_START_OWNER,
                resolve(gray, List.of(head, end(69, 100, 1)), Set.of()).status());
    }

    @Test
    public void rawHeadHintsDoNotInventMissingLongStemEvidence() {
        var gray = pixels(false);
        var heads = List.of(start(60, 0), end(72, 100, 1));
        var evidence = PrintedSlurOwners.headEvidence(heads, gray, W, H, track());
        assertTrue(evidence.contains(34, 152));
        assertFalse(evidence.contains(34, 100));
        assertFalse(evidence.closes(new PrintedSlurContours.Point(40, 69), 34, 152, true));
    }

    @Test
    public void rawRailCannotCreateAHeadOcclusionHint() {
        var gray = pixels(false);
        for (int x = 25; x <= 42; x++) gray[84 * W + x] = 0;
        var head = new PrintedSlurOwners.Head(7, 34, 84, 0, 1, 1, 0, 83);
        var hint = PrintedSlurOwners.headEvidence(List.of(head), gray, W, H, track());
        assertFalse(hint.contains(34, 84));
    }

    @Test
    public void subpixelHeadCentroidDoesNotReverseATerminalOnTheSameRasterColumn() {
        var gray = pixels(false);
        var head = new PrintedSlurOwners.Head(7, 34.4f, 152, 0, 1, 1, 0, 60);
        var hint = PrintedSlurOwners.headEvidence(List.of(head), gray, W, H, track());
        assertTrue(hint.closes(new PrintedSlurContours.Point(34, 140), 34, 152, true));
        assertFalse(hint.closes(new PrintedSlurContours.Point(32, 140), 34, 152, true));
    }
}
