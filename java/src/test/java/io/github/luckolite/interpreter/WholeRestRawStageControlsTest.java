// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pre-metadata raw shaft and retained-event controls. */
public class WholeRestRawStageControlsTest {
    static final int W = 800, H = 360;

    static byte[] frame(boolean raised, int rawDirection) {
        var g = WholeRestClassificationControlsTest.frame(raised, 400, 0);
        WholeRestClassificationControlsTest.box(g, 140, 187, 159, 194, 0);
        if (rawDirection < 0 || rawDirection == 2)
            WholeRestClassificationControlsTest.box(g, 141, 191, 142, 242, 0);
        if (rawDirection > 0) WholeRestClassificationControlsTest.box(g, 158, 140, 159, 190, 0);
        return g;
    }

    static RawWholeRestVoiceEvidence.Head head() {
        return new RawWholeRestVoiceEvidence.Head(140, 159, 187, 194, 149.5f, 190.5f, 16, 16, 0);
    }

    static ScoreNoteEvent note(int oldDirection) {
        return WholeRestClassificationControlsTest.note(149.5f, 190.5f, oldDirection);
    }

    static List<ScoreRestEvent> detect(
            byte[] g, ScoreNoteEvent original, RawWholeRestVoiceEvidence.Head h) {
        var originals = List.of(original);
        var raw =
                RawWholeRestVoiceEvidence.directions(
                        g, W, H, originals, Collections.singletonList(h));
        return SixteenthRestDetector.detect(
                g,
                W,
                H,
                List.of(WholeRestClassificationControlsTest.M),
                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                originals,
                raw);
    }

    static void full(boolean raised, int oldDirection) {
        var r = detect(frame(raised, -1), note(oldDirection), head());
        assertTrue(r.toString(), r.stream().anyMatch(ScoreRestEvent::isFullMeasure));
    }

    static void noFull(boolean raised, int rawDirection, int oldDirection) {
        var r = detect(frame(raised, rawDirection), note(oldDirection), head());
        assertTrue(r.toString(), r.stream().noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void raisedRawDownShaftProvesIndependentLaneBeforeMetadata() {
        full(true, 0);
    }

    @Test
    public void normalRawDownShaftProvesIndependentLaneBeforeMetadata() {
        full(false, 0);
    }

    @Test
    public void wrongOldUpMetadataCannotOverrideRawDown() {
        full(true, 1);
    }

    @Test
    public void rawAbsentShaftStaysUnresolvedDespiteOldDownMetadata() {
        noFull(true, 0, -1);
    }

    @Test
    public void ordinaryAbsentShaftStaysUnresolvedDespiteOldDownMetadata() {
        noFull(false, 0, -1);
    }

    @Test
    public void rawUpShaftOverridesOldDownAndRejectsIndependentLane() {
        noFull(true, 1, -1);
    }

    @Test
    public void rawOpposedShaftsRemainUnresolved() {
        noFull(true, 2, -1);
    }

    @Test
    public void rawDirectionsNeverMutateOriginalRecords() {
        var n = note(0);
        var original = List.of(n);
        var before = original.toString();
        var proof =
                RawWholeRestVoiceEvidence.directions(
                        frame(true, -1), W, H, original, List.of(head()));
        assertEquals(before, original.toString());
        assertEquals(0, n.stemDirection());
        assertEquals(-1, proof.get(0).stemDirection());
        assertEquals(n, proof.get(0).withStemDirection(0));
    }

    @Test
    public void rawPixelsRemainExactThroughDetectorAdapter() {
        var g = frame(true, -1);
        var before = g.clone();
        detect(g, note(0), head());
        assertArrayEquals(before, g);
    }

    @Test
    public void missingHeadProofCannotBorrowSerializedDirection() {
        var r = detect(frame(true, -1), note(-1), null);
        assertTrue(r.stream().noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void invalidHeadProofCannotBorrowSerializedDirection() {
        var bad = new RawWholeRestVoiceEvidence.Head(-1, 159, 187, 194, 149.5f, 190.5f, 16, 16, 0);
        assertTrue(
                detect(frame(true, -1), note(-1), bad).stream()
                        .noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void ordinaryDetectorEntryDoesNotTrustStoredMetadata() {
        var r =
                SixteenthRestDetector.detect(
                        frame(true, 0),
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of(note(-1)));
        assertTrue(r.stream().noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void wholeSoundingNoteWithNoShaftStaysUnresolved() {
        var n =
                new ScoreNoteEvent(0, .2f, 0, 0, 1, 190f / H, false, 0, 0, 2, 4)
                        .withStemDirection(-1);
        assertTrue(
                detect(frame(true, -1), n, head()).stream()
                        .noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void shortDisconnectedShaftFragmentDoesNotProveVoice() {
        var g = frame(true, 0);
        WholeRestClassificationControlsTest.box(g, 141, 191, 142, 204, 0);
        assertTrue(detect(g, note(-1), head()).stream().noneMatch(ScoreRestEvent::isFullMeasure));
    }

    @Test
    public void kindOnlyReplacementRetainsEveryOriginalRestField() {
        var g = WholeRestClassificationControlsTest.frame(false, 400, 0);
        var geometry = WholeRestClassificationControlsTest.one(g, 4, List.of(), List.of()).event();
        var old =
                new ScoreRestEvent(
                        geometry.measureIndex(),
                        geometry.positionInMeasure() + .005f,
                        geometry.pageY() + .001f,
                        geometry.pageHeight() + .001f,
                        geometry.staffIndex(),
                        geometry.staffCount(),
                        4);
        var original = new SixteenthRestDetector.Detection(List.of(old), List.of());
        var updated =
                ClassifiedWholeRests.apply(
                        original,
                        g,
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of());
        assertEquals(1, updated.rests().size());
        var full = updated.rests().get(0);
        assertTrue(full.isFullMeasure());
        assertEquals(
                old,
                new ScoreRestEvent(
                        full.measureIndex(),
                        full.positionInMeasure(),
                        full.pageY(),
                        full.pageHeight(),
                        full.staffIndex(),
                        full.staffCount(),
                        full.durationBeats()));
    }

    @Test
    public void unresolvedNoncenteredNewGlyphIsNotInserted() {
        var g = WholeRestClassificationControlsTest.frame(false, 220, 0);
        var updated =
                ClassifiedWholeRests.apply(
                        new SixteenthRestDetector.Detection(List.of(), List.of()),
                        g,
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of());
        assertTrue(updated.rests().isEmpty());
    }

    @Test
    public void noncenteredLegacyGlyphRetainsLiteralKind() {
        var g = WholeRestClassificationControlsTest.frame(false, 220, 0);
        var candidate = WholeRestClassificationControlsTest.one(g, 4, List.of(), List.of()).event();
        var original = new SixteenthRestDetector.Detection(List.of(candidate), List.of());
        var updated =
                ClassifiedWholeRests.apply(
                        original,
                        g,
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of());
        assertEquals(original, updated);
    }

    @Test
    public void dottedRaisedGlyphRetainsSixAndItsDotOwnership() {
        var g = WholeRestClassificationControlsTest.frame(true, 400, 1);
        var updated =
                ClassifiedWholeRests.apply(
                        new SixteenthRestDetector.Detection(List.of(), List.of()),
                        g,
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of());
        assertEquals(1, updated.rests().size());
        assertEquals(6, updated.rests().get(0).durationBeats(), 0);
        assertFalse(updated.rests().get(0).isFullMeasure());
        assertEquals(1, updated.dots().size());
        assertEquals(updated.rests().get(0), updated.dots().get(0).rest());
    }

    @Test
    public void ownerSubsetRetainsCorrespondingFreshHeadProof() {
        var g = frame(true, -1);
        WholeRestClassificationControlsTest.box(g, 590, 187, 609, 194, 0);
        WholeRestClassificationControlsTest.box(g, 591, 191, 592, 242, 0);
        var a = note(0);
        var b = WholeRestClassificationControlsTest.note(599.5f, 190.5f, 0);
        var second =
                new RawWholeRestVoiceEvidence.Head(590, 609, 187, 194, 599.5f, 190.5f, 16, 16, 0);
        var all =
                RawWholeRestVoiceEvidence.directions(
                        g, W, H, List.of(a, b), List.of(head(), second));
        var subset = RawWholeRestVoiceEvidence.directions(g, W, H, List.of(b), List.of(second));
        assertEquals(all.get(1), subset.get(0));
        assertEquals(-1, subset.get(0).stemDirection());
        assertEquals(0, b.stemDirection());
    }

    @Test
    public void prunedOwnerSetDoesNotReuseOpposedDirections() {
        var g = frame(true, -1);
        WholeRestClassificationControlsTest.box(g, 590, 187, 609, 194, 0);
        WholeRestClassificationControlsTest.box(g, 608, 140, 609, 190, 0);
        var lower = note(0);
        var opposed = WholeRestClassificationControlsTest.note(599.5f, 190.5f, 0);
        var second =
                new RawWholeRestVoiceEvidence.Head(590, 609, 187, 194, 599.5f, 190.5f, 16, 16, 0);
        var all =
                RawWholeRestVoiceEvidence.directions(
                        g, W, H, List.of(lower, opposed), List.of(head(), second));
        assertEquals(1, all.get(1).stemDirection());
        var subset = RawWholeRestVoiceEvidence.directions(g, W, H, List.of(lower), List.of(head()));
        assertEquals(-1, subset.get(0).stemDirection());
        var after =
                SixteenthRestDetector.detect(
                        g,
                        W,
                        H,
                        List.of(WholeRestClassificationControlsTest.M),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of(lower),
                        subset);
        assertTrue(after.toString(), after.stream().anyMatch(ScoreRestEvent::isFullMeasure));
    }
}
