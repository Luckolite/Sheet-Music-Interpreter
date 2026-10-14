// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-system raw page; repeated staff identities require physical phase proof. */
public class PrintedSlurFramesTest {
    static final int W = 1200, H = 650;

    private byte[] page() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int bottom : new int[] {210, 510})
            for (int x = 30; x < W - 30; x++)
                for (int line = 0; line < 5; line++) g[(bottom - line * 10) * W + x] = 35;
        return g;
    }

    private PlayingTechniqueDetector.Staff staff(int bottom) {
        return new PlayingTechniqueDetector.Staff(bottom - 40, bottom, 10, 0, 1);
    }

    private ScoreNoteEvent note(int bottom) {
        return new ScoreNoteEvent(
                bottom == 210 ? 0 : 1, .25f, 8, 0, 1, (bottom - 40) / (float) H, false);
    }

    private List<PrintedSlurOwners.Head> heads(List<ScoreNoteEvent> notes) {
        var written = new HashMap<Integer, ScoreNoteTiming.WrittenPlacement>();
        var pitches = new HashMap<Integer, Integer>();
        for (int i = 0; i < notes.size(); i++) {
            written.put(i, new ScoreNoteTiming.WrittenPlacement(0, 1, -1, -1, false, 0));
            pitches.put(i, 77);
        }
        return PrintedSlurContext.heads(
                notes,
                List.of(new MeasureRegion(0, 1, 0, .5f), new MeasureRegion(0, 1, .5f, 1)),
                written,
                new ScoreMeterMap(4, List.of()),
                Map.of(),
                pitches,
                W,
                H);
    }

    @Test
    public void repeatedStaffIndexUsesTheCorrectPhysicalSystem() {
        var notes = List.of(note(210), note(510));
        var result =
                PrintedSlurFrames.prepare(
                        page(), W, H, List.of(staff(510), staff(210)), notes, heads(notes));
        assertEquals(2, result.frames().size());
        assertTrue(result.unboundOwners().isEmpty());
        assertEquals(1, result.frames().get(0).heads().get(0).owner());
        assertEquals(0, result.frames().get(1).heads().get(0).owner());
        assertEquals(40320, result.frames().get(0).heads().get(0).attack());
    }

    @Test
    public void duplicatePhysicalProofDoesNotChooseArbitrarily() {
        var notes = List.of(note(210));
        var result =
                PrintedSlurFrames.prepare(
                        page(), W, H, List.of(staff(210), staff(210)), notes, heads(notes));
        assertTrue(result.frames().isEmpty());
        assertEquals(Set.of(0), result.unboundOwners());
    }

    @Test
    public void missingPhysicalSystemDoesNotBorrowSameIndexElsewhere() {
        var notes = List.of(note(510));
        var result =
                PrintedSlurFrames.prepare(page(), W, H, List.of(staff(210)), notes, heads(notes));
        assertTrue(result.frames().isEmpty());
        assertEquals(Set.of(0), result.unboundOwners());
    }

    @Test
    public void stalePitchStepCannotMoveANoteToAnotherPrintedLine() {
        var n = new ScoreNoteEvent(0, .25f, 6, 0, 1, 170 / (float) H, false);
        var result =
                PrintedSlurFrames.prepare(
                        page(), W, H, List.of(staff(210)), List.of(n), heads(List.of(n)));
        assertTrue(result.frames().isEmpty());
        assertEquals(Set.of(0), result.unboundOwners());
    }

    @Test
    public void absentRawRulesCannotBeProvedByHeadCoordinates() {
        var notes = List.of(note(210));
        var g = page();
        Arrays.fill(g, (byte) 255);
        var r = PrintedSlurFrames.prepare(g, W, H, List.of(staff(210)), notes, heads(notes));
        assertEquals(1, r.unprovedStaffs());
        assertEquals(Set.of(0), r.unboundOwners());
    }

    @Test(expected = IllegalArgumentException.class)
    public void reusedHeadIdentityIsRejected() {
        var notes = List.of(note(210));
        var h = heads(notes).get(0);
        PrintedSlurFrames.prepare(page(), W, H, List.of(staff(210)), notes, List.of(h, h));
    }

    @Test
    public void unknownVoiceEvidenceStaysUnknown() {
        var notes = List.of(note(210));
        var r = PrintedSlurFrames.prepare(page(), W, H, List.of(staff(210)), notes, heads(notes));
        assertEquals(0, r.frames().get(0).heads().get(0).voice());
    }
}
