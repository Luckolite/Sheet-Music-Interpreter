// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pre-implementation pixel-role oracles, separate from any mf acceptance exception. */
public final class RawCurveNotationEvidenceTest {
    DynamicNotationRawContext.Evidence capture(OwnedCurveMezzoScene s, List<ScoreNoteEvent> notes) {
        return DynamicNotationRawContext.capture(s.gray, s.W, s.H, s.staffs, s.measures, notes);
    }

    @Test
    public void finiteOwnedCurveRetainsItsLiteralCoreSamples() {
        var s = new OwnedCurveMezzoScene(true, false);
        var e = capture(s, s.notes);
        assertFalse(e.curves().isEmpty());
        assertEquals(
                Integer.valueOf(20), e.ownedPixelValues(s.gray, s.W, s.H).get(202 * s.W + 415));
    }

    @Test
    public void completeMezzoBodyIsNotClaimedByTheForeignArc() {
        var s = new OwnedCurveMezzoScene(true, false);
        assertEquals(20, s.gray[223 * s.W + 380] & 255);
        assertFalse(
                capture(s, s.notes)
                        .ownedPixelValues(s.gray, s.W, s.H)
                        .containsKey(223 * s.W + 380));
    }

    @Test
    public void everyAssignedSampleRetainsExactRawValue() {
        var s = new OwnedCurveMezzoScene(true, false);
        for (var x : capture(s, s.notes).ownedPixelValues(s.gray, s.W, s.H).entrySet())
            assertEquals(s.gray[x.getKey()] & 255, x.getValue().intValue());
    }

    @Test
    public void lightBridgeRemainsExplicitlyUnowned() {
        var s = new OwnedCurveMezzoScene(true, true);
        assertEquals(250, s.gray[205 * s.W + 420] & 255);
        assertFalse(
                capture(s, s.notes)
                        .ownedPixelValues(s.gray, s.W, s.H)
                        .containsKey(205 * s.W + 420));
    }

    @Test
    public void nearbyUnownedFaintDotIsNotPaintedAsNotation() {
        var s = new OwnedCurveMezzoScene(true, false);
        s.gray[205 * s.W + 410] = (byte) 250;
        assertFalse(
                capture(s, s.notes)
                        .ownedPixelValues(s.gray, s.W, s.H)
                        .containsKey(205 * s.W + 410));
    }

    @Test
    public void visibleCrossingBranchIsNotAnErasedBoundingBox() {
        var s = new OwnedCurveMezzoScene(true, false);
        for (int y = 202; y <= 215; y++) s.gray[y * s.W + 415] = 20;
        assertFalse(
                capture(s, s.notes)
                        .ownedPixelValues(s.gray, s.W, s.H)
                        .containsKey(214 * s.W + 415));
    }

    @Test
    public void absentActualHeadBodiesCannotCertifyProfiles() {
        var s = new OwnedCurveMezzoScene(false, false);
        assertTrue(capture(s, s.notes).curves().isEmpty());
    }

    @Test
    public void ambiguousTypedHeadOwnersCannotCertifyProfiles() {
        var s = new OwnedCurveMezzoScene(true, false);
        var n = new ArrayList<>(s.notes);
        n.add(s.notes.get(0));
        assertTrue(capture(s, n).curves().isEmpty());
    }

    @Test
    public void sourceMutationInvalidatesStoredRawAttribution() {
        var s = new OwnedCurveMezzoScene(true, false);
        var e = capture(s, s.notes);
        s.gray[20] = 0;
        assertTrue(e.ownedPixelValues(s.gray, s.W, s.H).isEmpty());
    }

    @Test
    public void capturePreservesAllRawPixelsAndOriginalBowDecision() {
        var s = new OwnedCurveMezzoScene(true, false);
        byte[] old = s.gray.clone();
        var a =
                PrintedSlurContours.find(
                        s.gray,
                        null,
                        s.W,
                        s.H,
                        300,
                        140,
                        550,
                        250,
                        s.track(),
                        null,
                        PrintedSlurOwners.headEvidence(s.heads(), s.gray, s.W, s.H, s.track()));
        var b =
                PrintedSlurContours.findWithRawEvidence(
                        s.gray,
                        null,
                        s.W,
                        s.H,
                        300,
                        140,
                        550,
                        250,
                        s.track(),
                        null,
                        PrintedSlurOwners.headEvidence(s.heads(), s.gray, s.W, s.H, s.track()));
        assertEquals(a, b.result());
        assertArrayEquals(old, s.gray);
    }
}
