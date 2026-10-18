// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MusicXmlOctaveShiftsOriginalTest {
    @Test
    public void signedSizesAndRestGaps() {
        for (int shift : new int[] {-2, -1, 1, 2})
            assertEquals(
                    2,
                    MusicXmlOctaveShifts.plan(
                                    List.of(
                                            new MusicXmlOctaveShifts.Note(1, 1, 0, 1, shift, false),
                                            new MusicXmlOctaveShifts.Note(
                                                    1, 1, 2, 3, shift, false)))
                            .size());
    }

    @Test
    public void overlappingTiedMembersAndAdjacentBarsAreOneSpan() {
        assertEquals(
                List.of(new MusicXmlOctaveShifts.Span(1, 1, 0, 8, 1)),
                MusicXmlOctaveShifts.plan(
                        List.of(
                                new MusicXmlOctaveShifts.Note(1, 1, 0, 4, 1, false),
                                new MusicXmlOctaveShifts.Note(1, 1, 2, 8, 1, false))));
    }

    @Test
    public void opposingSimultaneousVoicesAndStavesAreIndependent() {
        assertEquals(
                3,
                MusicXmlOctaveShifts.plan(
                                List.of(
                                        new MusicXmlOctaveShifts.Note(1, 1, 0, 4, 1, false),
                                        new MusicXmlOctaveShifts.Note(1, 2, 0, 4, -1, false),
                                        new MusicXmlOctaveShifts.Note(2, 5, 0, 4, 2, false)))
                        .size());
    }

    @Test
    public void conflictingSameVoiceCannotSilentlyChangeWrittenPitch() {
        try {
            MusicXmlOctaveShifts.plan(
                    List.of(
                            new MusicXmlOctaveShifts.Note(1, 1, 0, 4, 1, false),
                            new MusicXmlOctaveShifts.Note(1, 1, 0, 4, -1, false)));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void invalidRangeAndNegativeClockReject() {
        for (var n :
                List.of(
                        new MusicXmlOctaveShifts.Note(1, 1, 0, 1, 3, false),
                        new MusicXmlOctaveShifts.Note(1, 1, -1, 1, 1, false)))
            try {
                MusicXmlOctaveShifts.plan(List.of(n));
                fail();
            } catch (IllegalArgumentException expected) {
            }
    }
}
