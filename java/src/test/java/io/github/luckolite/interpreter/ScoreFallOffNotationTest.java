// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreFallOffNotationTest {
    static ScoreNoteEvent note(int measure, float x, int step, ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(
                measure, x, step, 0, 1, .3f, false, 0, 0, 0, 2f, 1, 0, 0, 28, false, 0, false, 0, 0,
                1, 0, kind);
    }

    static ScoreNoteEvent note() {
        return note(0, .4f, 14, ScoreNoteEvent.Kind.PITCHED);
    }

    static ScoreExpressiveEvent mark(ScoreNoteEvent n) {
        return mark(n, Optional.empty(), "printed-fall-off", ScoreFallOffNotation.CURVED);
    }

    static ScoreExpressiveEvent mark(
            ScoreNoteEvent n, Optional<ScoreAnchor> start, String source, String qualifier) {
        return new ScoreExpressiveEvent(
                "fall:exact",
                ScoreExpressiveEvent.Kind.FALL_OFF,
                start,
                Optional.empty(),
                start.isPresent()
                        ? ScoreExpressiveEvent.Scope.NOTE
                        : ScoreExpressiveEvent.Scope.UNRESOLVED,
                n.staffIndex(),
                n.staffCount(),
                Optional.of(ScoreFallOffNotation.target(n)),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                qualifier,
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                source, 2, .5f, n.staffIndex(), n.staffCount(), "curved falloff")));
    }

    static byte[] write(ScoreExpressiveEvent m, int guide) throws IOException {
        var b = new ByteArrayOutputStream();
        ScoreSemanticWire.writeExpressions(new DataOutputStream(b), List.of(m), 8, guide);
        return b.toByteArray();
    }

    static List<ScoreExpressiveEvent> read(byte[] b, int guide) throws IOException {
        return ScoreSemanticWire.readExpressions(
                new DataInputStream(new ByteArrayInputStream(b)), 8, guide);
    }

    @Test
    public void newKindAppendsWithoutRenumberingHarmonic() {
        assertEquals(20, ScoreExpressiveEvent.Kind.NATURAL_HARMONIC.ordinal());
        assertEquals(21, ScoreExpressiveEvent.Kind.FALL_OFF.ordinal());
    }

    @Test
    public void canonicalTargetHasRawFloatIdentity() {
        assertEquals(
                "fall-off-note-v1:0:0:1:3ecccccd:14:3e99999a", ScoreFallOffNotation.target(note()));
    }

    @Test
    public void exactSourceOwnsOneNote() {
        var n = note();
        assertEquals(0, ScoreFallOffNotation.owner(mark(n), List.of(n)));
    }

    @Test
    public void duplicateExactSourceCannotChooseAnOwner() {
        var n = note();
        assertEquals(-1, ScoreFallOffNotation.owner(mark(n), List.of(n, n)));
    }

    @Test
    public void adjacentFloatPositionDoesNotStealSource() {
        var n = note();
        var adjacent = note(0, Math.nextUp(.4f), 14, ScoreNoteEvent.Kind.PITCHED);
        assertEquals(1, ScoreFallOffNotation.owner(mark(n), List.of(adjacent, n)));
    }

    @Test
    public void wrongPitchStepDoesNotReceiveMark() {
        var n = note();
        assertEquals(
                -1,
                ScoreFallOffNotation.owner(
                        mark(n), List.of(note(0, .4f, 15, ScoreNoteEvent.Kind.PITCHED))));
    }

    @Test
    public void wrongMeasureDoesNotReceiveMark() {
        var n = note();
        assertEquals(
                -1,
                ScoreFallOffNotation.owner(
                        mark(n), List.of(note(1, .4f, 14, ScoreNoteEvent.Kind.PITCHED))));
    }

    @Test
    public void unpitchedSourceCannotBecomePitchedFall() {
        assertNull(ScoreFallOffNotation.target(note(0, .4f, 14, ScoreNoteEvent.Kind.UNPITCHED)));
    }

    @Test
    public void nonfinitePositionCannotCreateIdentity() {
        assertNull(
                ScoreFallOffNotation.target(note(0, Float.NaN, 14, ScoreNoteEvent.Kind.PITCHED)));
    }

    @Test
    public void foreignEvidenceCannotOwnSource() {
        var n = note();
        assertEquals(
                -1,
                ScoreFallOffNotation.owner(
                        mark(n, Optional.empty(), "different-proof", ScoreFallOffNotation.CURVED),
                        List.of(n)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void arbitraryQualifierIsNotAPrintedCurve() {
        mark(note(), Optional.empty(), "printed-fall-off", "pitch=-2");
    }

    @Test
    public void unresolvedMarkWaitsForFinalClock() {
        assertTrue(mark(note()).start().isEmpty());
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, mark(note()).scope());
    }

    @Test
    public void finalAttackResolvesWithoutInventingTime() {
        var n = note();
        assertEquals(
                Optional.of(new ScoreAnchor(0, 0)),
                ScoreFallOffNotation.resolveFinalAttack(
                        mark(n), List.of(n), 0, 0, new long[] {0, 40320}, 10080));
    }

    @Test
    public void finalAttackMayUseWholeScoreCanonicalClock() {
        var n = note();
        assertEquals(
                Optional.of(new ScoreAnchor(0, 1)),
                ScoreFallOffNotation.resolveFinalAttack(
                        mark(n), List.of(n), 0, 10080, new long[] {0, 40320}, 10080));
    }

    @Test
    public void wrongOwnerIndexCannotResolve() {
        var n = note();
        assertTrue(
                ScoreFallOffNotation.resolveFinalAttack(
                                mark(n), List.of(n), 1, 0, new long[] {0, 40320}, 10080)
                        .isEmpty());
    }

    @Test
    public void outsideMeasureAttackCannotResolve() {
        var n = note();
        assertTrue(
                ScoreFallOffNotation.resolveFinalAttack(
                                mark(n), List.of(n), 0, 40320, new long[] {0, 40320}, 10080)
                        .isEmpty());
    }

    @Test
    public void staleSuppliedAttackCannotResolve() {
        var n = note();
        assertTrue(
                ScoreFallOffNotation.resolveFinalAttack(
                                mark(
                                        n,
                                        Optional.of(new ScoreAnchor(0, 1)),
                                        "printed-fall-off",
                                        ScoreFallOffNotation.CURVED),
                                List.of(n),
                                0,
                                0,
                                new long[] {0, 40320},
                                10080)
                        .isEmpty());
    }

    @Test
    public void exactSuppliedAttackResolves() {
        var n = note();
        assertEquals(
                Optional.of(new ScoreAnchor(0, 1)),
                ScoreFallOffNotation.resolveFinalAttack(
                        mark(
                                n,
                                Optional.of(new ScoreAnchor(0, 1)),
                                "printed-fall-off",
                                ScoreFallOffNotation.CURVED),
                        List.of(n),
                        0,
                        10080,
                        new long[] {0, 40320},
                        10080));
    }

    @Test
    public void offsetMovesTargetAndKeepsPhysicalEvidence() {
        var m = mark(note());
        var shifted = m.offset(3);
        assertEquals(
                Optional.of(3),
                ScoreFallOffNotation.targetMeasure(shifted.targetEventId().orElseThrow()).stream()
                        .boxed()
                        .findFirst());
        assertEquals(m.evidence(), shifted.evidence());
        assertEquals(m.eventId(), shifted.eventId());
        assertTrue(shifted.start().isEmpty());
    }

    @Test
    public void pageSplitRetainsUnresolvedMarkOnItsSourcePageOnly() {
        var m = mark(note(3, .4f, 14, ScoreNoteEvent.Kind.PITCHED));
        assertTrue(ScorePageInterpretation.expressionsStartingOnPage(List.of(m), 0, 2).isEmpty());
        var split = ScorePageInterpretation.expressionsStartingOnPage(List.of(m), 2, 4);
        assertEquals(1, split.size());
        assertEquals(
                1,
                ScoreFallOffNotation.targetMeasure(split.get(0).targetEventId().orElseThrow())
                        .orElseThrow());
        assertEquals(m.evidence(), split.get(0).evidence());
    }

    @Test(expected = IllegalArgumentException.class)
    public void targetCannotMoveBeforeScore() {
        mark(note()).offset(-1);
    }

    @Test
    public void malformedTargetCannotNameSourceMeasure() {
        assertTrue(
                ScoreFallOffNotation.targetMeasure("fall-off-note-v1:0:0:1:7fc00000:14:3e99999a")
                        .isEmpty());
        assertTrue(
                ScoreFallOffNotation.targetMeasure(
                                "natural-harmonic-note-v1:0:0:1:3ecccccd:14:3e99999a")
                        .isEmpty());
    }

    @Test
    public void guide295RoundtripKeepsAllFields() throws Exception {
        var m = mark(note());
        assertEquals(List.of(m), read(write(m, 295), 295));
    }

    @Test
    public void guide295EncodesKind21Explicitly() throws Exception {
        var in = new DataInputStream(new ByteArrayInputStream(write(mark(note()), 295)));
        assertEquals(1, in.readInt());
        assertTrue(in.readInt() > 0);
        in.skipNBytes(in.readInt());
        assertEquals(21, in.readInt());
    }

    @Test(expected = IOException.class)
    public void guide294WriterRejectsNewKind() throws Exception {
        write(mark(note()), 294);
    }

    @Test(expected = IOException.class)
    public void guide294ReaderRejectsNewKind() throws Exception {
        read(write(mark(note()), 295), 294);
    }

    @Test
    public void oldHeaderWriteRefusesBeforeAnyPrefix() throws Exception {
        var out = new ByteArrayOutputStream();
        try {
            ScoreSemanticWire.writeExpressions(
                    new DataOutputStream(out), List.of(mark(note())), 8, 294);
            fail();
        } catch (IOException expected) {
            assertEquals(0, out.size());
        }
    }

    private List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames() {
        var t =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 116, 14}, new float[] {319, 116, 14}));
        return List.of(
                new PrintedSlurPageCapture.Frame<>(
                        0,
                        0,
                        319,
                        219,
                        t,
                        List.of(new PrintedSlurOwners.GeometryHead(17, 84, 83, 0, 1))));
    }

    private PrintedFallOffContours.Fall fall(int left) {
        return new PrintedFallOffContours.Fall(17, left, 90, left + 23, 110);
    }

    @Test
    public void oneRouteMaySupplyAnIndependentlyOwnedMark() {
        assertEquals(
                List.of(fall(100)),
                PrintedFallOffDetector.agree(List.of(fall(100)), List.of(), frames()));
    }

    @Test
    public void matchingRoutesProduceOneMark() {
        assertEquals(
                List.of(fall(100)),
                PrintedFallOffDetector.agree(List.of(fall(100)), List.of(fall(101)), frames()));
    }

    @Test
    public void conflictingRoutesCannotSelectTheFavorableOne() {
        assertTrue(
                PrintedFallOffDetector.agree(List.of(fall(100)), List.of(fall(120)), frames())
                        .isEmpty());
    }

    @Test
    public void sameRouteRivalCannotDisappearBeforeConsensus() {
        assertTrue(
                PrintedFallOffDetector.agree(
                                List.of(fall(100), fall(120)), List.of(fall(100)), frames())
                        .isEmpty());
    }

    @Test
    public void calibratedRouteRivalCannotDisappearBeforeConsensus() {
        assertTrue(
                PrintedFallOffDetector.agree(
                                List.of(fall(100)), List.of(fall(100), fall(120)), frames())
                        .isEmpty());
    }

    @Test
    public void duplicatePhysicalFrameCannotOwnCrossRouteAgreement() {
        assertTrue(
                PrintedFallOffDetector.agree(
                                List.of(fall(100)),
                                List.of(fall(100)),
                                List.of(frames().get(0), frames().get(0)))
                        .isEmpty());
    }
}
