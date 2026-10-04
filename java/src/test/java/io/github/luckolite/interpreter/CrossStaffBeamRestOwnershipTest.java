// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossStaffBeamRestOwnershipTest {
    private byte[] image(boolean bridge) {
        byte[] gray = new byte[240 * 360];
        Arrays.fill(gray, (byte) 255);
        for (int y = 140; y <= 260; y++) gray[y * 240 + 126] = 0;
        for (int y = 140; y <= 200; y++) gray[y * 240 + 166] = 0;
        if (bridge)
            for (int x = 126; x <= 166; x++)
                for (int dy = -2; dy <= 2; dy++) gray[(140 + dy) * 240 + x] = 0;
        return gray;
    }

    private List<ScoreNoteEvent> notes(boolean sameStaff, boolean nextBar) {
        var a =
                new ScoreNoteEvent(
                        0,
                        .3f,
                        7,
                        1,
                        2,
                        .7f,
                        false,
                        0,
                        1,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        0,
                        1,
                        1,
                        0,
                        ScoreNoteEvent.CLEF_BASS);
        var b =
                new ScoreNoteEvent(nextBar ? 1 : 0, .4f, -2, sameStaff ? 1 : 0, 2, .5f, false, 0, 1)
                        .withLeadingRest(2);
        return List.of(a, b);
    }

    private List<ScoreNoteEvent> mark(List<ScoreNoteEvent> n, boolean bridge) {
        return CrossStaffBeamDetector.mark(
                n,
                List.of(
                        new CrossStaffBeamDetector.Head(120, 260, 10),
                        new CrossStaffBeamDetector.Head(160, 200, 10)),
                image(bridge),
                240,
                360);
    }

    @Test
    public void physicalBridgeSurvivesOtherVoiceRestHints() {
        var n = notes(false, false);
        var result = mark(n, true);
        assertTrue(result.get(0).crossStaffBeam());
        assertTrue(result.get(1).crossStaffBeam());
        assertEquals(n.get(0).withCrossStaffBeam(), result.get(0));
        assertEquals(n.get(1).withCrossStaffBeam(), result.get(1));
    }

    @Test
    public void restsCannotSupplyMissingPhysicalInk() {
        var n = notes(false, false);
        assertEquals(n, mark(n, false));
    }

    @Test
    public void sameStaffAndBarBoundaryNeverBecomeCrossStaffLinks() {
        var same = notes(true, false);
        assertEquals(same, mark(same, true));
        var boundary = notes(false, true);
        assertEquals(boundary, mark(boundary, true));
    }

    @Test
    public void provedQuarterKeepsItsValueBesideAnotherVoiceBeam() {
        var beamed = notes(false, false);
        var quarter =
                new ScoreNoteEvent(
                        0,
                        .3f,
                        7,
                        1,
                        2,
                        .7f,
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1,
                        1,
                        0,
                        0,
                        ScoreNoteEvent.CLEF_BASS);
        var notes = List.of(quarter, beamed.get(1));
        assertEquals(notes, mark(notes, true));
    }
}
