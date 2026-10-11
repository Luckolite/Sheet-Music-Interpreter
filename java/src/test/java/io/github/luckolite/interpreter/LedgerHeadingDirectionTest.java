// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural staff and heading; no source-score geometry or text hints. */
public class LedgerHeadingDirectionTest {
    static final int W = 1000, H = 650;

    private List<ScoreExpressiveEvent> detect(
            int rails, boolean absent, boolean cross, float headX, float headY, float wordTop) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < rails; line++)
            for (int x = 180; x < 820; x++) gray[(300 + line * 14) * W + x] = 20;
        var note =
                new ScoreNoteEvent(
                        0, (headX - 200) / 600, 16, 0, 1, headY / H, false, 0, 0, 2, 1, 1, 0, 0);
        if (cross) note = note.withCrossStaffBeam();
        var notes = absent ? List.<ScoreNoteEvent>of() : List.of(note);
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.2f, .8f, .43f, .64f)),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)));
        byte[] before = gray.clone();
        var result =
                ScoreExpressionDetector.apply(
                        score,
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "Free time", .36f, wordTop, .49f, wordTop + .025f)),
                        List.of(new PlayingTechniqueDetector.Staff(300, 356, 14, 0, 1)),
                        gray,
                        W,
                        H);
        assertArrayEquals(before, gray);
        assertEquals(notes, result.notes());
        assertEquals(score.tempoChanges(), result.tempoChanges());
        return ScoreExpressionDetector.resolve(result, 4).expressiveEvents();
    }

    @Test
    public void headingAboveLedgerNotesKeepsPrintedTextAndProvedAnchor() {
        var events = detect(5, false, false, 430, 244, .25f);
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, event.kind());
        assertTrue(event.start().isPresent());
        assertEquals(0, event.start().get().measureIndex());
        assertEquals("Free time", event.evidence().get(0).printedText());
    }

    @Test
    public void fourRailsCannotAttachHeading() {
        assertTrue(detect(4, false, false, 430, 244, .25f).isEmpty());
    }

    @Test
    public void noRailsCannotAttachHeading() {
        assertTrue(detect(0, false, false, 430, 244, .25f).isEmpty());
    }

    @Test
    public void missingHeadCannotAttachHeading() {
        assertTrue(detect(5, true, false, 430, 244, .25f).isEmpty());
    }

    @Test
    public void crossStaffHeadCannotAttachHeading() {
        assertTrue(detect(5, false, true, 430, 244, .25f).isEmpty());
    }

    @Test
    public void remoteHeadCannotAttachHeading() {
        assertTrue(detect(5, false, false, 800, 244, .25f).isEmpty());
    }

    @Test
    public void wrongHeadFloorCannotAttachHeading() {
        assertTrue(detect(5, false, false, 430, 200, .25f).isEmpty());
    }

    @Test
    public void remoteHeadingCannotAttachStaff() {
        assertTrue(detect(5, false, false, 430, 244, .05f).isEmpty());
    }
}
