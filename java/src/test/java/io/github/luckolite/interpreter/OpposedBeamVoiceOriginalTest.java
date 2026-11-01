// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-note opposed beam with a faster independent bass attack between its owners. */
public class OpposedBeamVoiceOriginalTest {
    static final int W = 280, H = 260;

    static ScoreNoteEvent note(float x, int staff, int beams, int direction) {
        return new ScoreNoteEvent(
                        0,
                        x,
                        staff == 0 ? 1 : 6,
                        staff,
                        2,
                        staff == 0 ? .38f : .69f,
                        false,
                        1,
                        beams,
                        2,
                        0)
                .withStemDirection(direction);
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(note(.1f, 0, 1, -1), note(.25f, 1, 2, -1), note(.4f, 1, 1, 1));
    }

    static List<CrossStaffBeamDetector.Head> heads() {
        return List.of(
                new CrossStaffBeamDetector.Head(100, 100, 10),
                new CrossStaffBeamDetector.Head(135, 180, 10),
                new CrossStaffBeamDetector.Head(170, 180, 10));
    }

    static byte[] ink(boolean rail, boolean shaft) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 140; y++) g[y * W + 94] = 0;
        if (shaft) for (int y = 140; y <= 180; y++) g[y * W + 176] = 0;
        if (rail) for (int x = 94; x <= 176; x++) for (int y = 138; y <= 142; y++) g[y * W + x] = 0;
        return g;
    }

    static List<ScoreNoteEvent> marked() {
        return CrossStaffBeamDetector.mark(notes(), heads(), ink(true, true), W, H);
    }

    @Test
    public void nonAdjacentLiteralPartnersAreMarked() {
        var n = notes();
        var m = marked();
        assertEquals(n.get(0).withCrossStaffBeam(), m.get(0));
        assertEquals(n.get(1), m.get(1));
        assertEquals(n.get(2).withCrossStaffBeam(), m.get(2));
    }

    @Test
    public void physicalPairSustainIsIndependentOfOtherBassAttacks() {
        var n = marked();
        assertTrue(ScoreNoteTiming.hasIndependentDuration(n.get(2), n));
        assertFalse(ScoreNoteTiming.hasIndependentDuration(n.get(1), n));
    }

    @Test
    public void projectedSourceRetainsIndependentDuration() {
        var n = marked();
        var p = ScoreRhythmProjection.resolve(n, n, 6, List.of());
        assertTrue(p.get(2).independentDuration());
        assertEquals(.75, p.get(2).durationBeats(), 1e-6);
    }

    @Test
    public void missingRailOrShaftCannotSupplyVoiceAuthority() {
        var n = notes();
        assertEquals(n, CrossStaffBeamDetector.mark(n, heads(), ink(false, true), W, H));
        assertEquals(n, CrossStaffBeamDetector.mark(n, heads(), ink(true, false), W, H));
    }

    @Test
    public void sameVoiceRetriggerHasNoIndependentAuthority() {
        var n = notes();
        assertFalse(ScoreNoteTiming.hasIndependentDuration(n.get(2), n));
        var a = n.get(0).withCrossStaffBeam();
        var b = note(.4f, 0, 1, -1).withCrossStaffBeam();
        assertFalse(ScoreNoteTiming.hasIndependentDuration(b, List.of(a, b)));
    }

    @Test
    public void wrongStemAndUnsupportedValueCannotBorrowPartner() {
        var n = new ArrayList<>(notes());
        n.set(2, note(.4f, 1, 1, -1));
        assertEquals(n, CrossStaffBeamDetector.mark(n, heads(), ink(true, true), W, H));
        n.set(2, note(.4f, 1, 2, 1));
        assertEquals(n, CrossStaffBeamDetector.mark(n, heads(), ink(true, true), W, H));
    }

    @Test
    public void barBoundaryDoesNotJoinPhysicalParts() {
        var n = new ArrayList<>(notes());
        n.set(2, new ScoreNoteEvent(1, .4f, 6, 1, 2, .69f, false, 1, 1, 2, 0).withStemDirection(1));
        assertEquals(n, CrossStaffBeamDetector.mark(n, heads(), ink(true, true), W, H));
    }

    @Test
    public void sharedShaftAliasesRefuseNewOwnership() {
        var n = new ArrayList<>(notes());
        n.add(note(.41f, 1, 1, 1));
        var h = new ArrayList<>(heads());
        h.add(h.get(2));
        assertEquals(n, CrossStaffBeamDetector.mark(n, h, ink(true, true), W, H));
    }
}
