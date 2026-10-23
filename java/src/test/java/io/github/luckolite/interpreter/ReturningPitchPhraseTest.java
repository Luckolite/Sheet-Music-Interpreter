// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original three-attack phrases returning to the opening pitch, with voice/tie controls. */
public class ReturningPitchPhraseTest {
    private ScoreSlurSource source() {
        return ScoreSlurSource.capture(
                List.of(),
                List.of(
                        new ScoreSlurPages.Span(
                                "returning-phrase",
                                new ScoreSlurPages.Anchor(0, 0),
                                new ScoreSlurPages.Anchor(0, 2),
                                ScoreSlurSpan.Placement.ABOVE,
                                ScoreSlurSpan.LineType.SOLID,
                                List.of(new ScoreSlurSpan.ArcEvidence(0, .1f, .2f, .8f, .2f)))),
                List.of(new ScoreSlurPages.Page(0, 3)));
    }

    private ScoreSlurClassification.Note note(
            int owner, long attack, long release, int pitch, int part) {
        return new ScoreSlurClassification.Note(owner, attack, 0, pitch, release, part, 2);
    }

    private List<ScoreSlurClassification.Note> phrase() {
        return List.of(note(0, 0, 100, 60, 0), note(1, 100, 200, 62, 0), note(2, 200, 300, 60, 0));
    }

    private void pending(List<ScoreSlurClassification.Note> notes) {
        assertEquals(source(), ScoreSlurClassification.resolve(source(), notes, Set.of()));
    }

    @Test
    public void equalEndpointPitchWithAnInterveningDifferentAttackIsAPhrase() {
        var result = ScoreSlurClassification.resolve(source(), phrase(), Set.of());
        assertEquals(source().candidates(), result.spans());
        assertTrue(result.candidates().isEmpty());
        assertEquals(source().noteOrigins(), result.noteOrigins());
    }

    @Test
    public void explicitSoundingTieStillWinsOverAnyOtherVoiceEvidence() {
        var result =
                ScoreSlurClassification.resolve(
                        source(), phrase(), Set.of(new ScoreSlurClassification.Tie(0, 2)));
        assertTrue(result.spans().isEmpty());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    public void interveningAttackInAnotherPartCannotProveThePhrase() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 200, 62, 1),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void differentEndpointPartsCannotBorrowTheMiddleAttack() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 200, 62, 0),
                        note(2, 200, 300, 60, 1)));
    }

    @Test
    public void heldOpeningNoteCannotBeReclassifiedFromOverlappingPolyphony() {
        pending(
                List.of(
                        note(0, 0, 250, 60, 0),
                        note(1, 100, 200, 62, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void overlappingMiddleSustainDoesNotProveASequentialVoice() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 250, 62, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void simultaneousDifferentPitchCannotPretendToBeAnEnclosedAttack() {
        pending(List.of(note(0, 0, 100, 60, 0), note(1, 0, 100, 62, 0), note(2, 200, 300, 60, 0)));
    }

    @Test
    public void differentPitchAtTheEndTickIsNotAnEnclosedAttack() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 200, 300, 62, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void repeatedPitchAloneRemainsDeferred() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 200, 60, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void unknownSoundingPitchCannotProveTheInterior() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 200, -1, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void legacyEvidenceWithoutPartAndReleaseRemainsDeferred() {
        pending(
                List.of(
                        new ScoreSlurClassification.Note(0, 0, 0, 60),
                        new ScoreSlurClassification.Note(1, 100, 0, 62),
                        new ScoreSlurClassification.Note(2, 200, 0, 60)));
    }

    @Test
    public void zeroLengthMiddleDoesNotPretendToBeARealAttack() {
        pending(
                List.of(
                        note(0, 0, 100, 60, 0),
                        note(1, 100, 100, 62, 0),
                        note(2, 200, 300, 60, 0)));
    }

    @Test
    public void equivalentTimeUnitsDoNotChangeClassification() {
        var result =
                ScoreSlurClassification.resolve(
                        source(),
                        List.of(
                                note(0, 0, 1000, 60, 0),
                                note(1, 1000, 2000, 62, 0),
                                note(2, 2000, 3000, 60, 0)),
                        Set.of());
        assertEquals(1, result.spans().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSustainIsRejected() {
        note(0, 100, 99, 60, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSourcePartIsRejected() {
        note(0, 0, 100, 60, 2);
    }
}
