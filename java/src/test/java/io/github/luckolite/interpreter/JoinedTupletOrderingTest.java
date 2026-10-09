// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** The same original physical drawing is invariant to input event enumeration. */
public final class JoinedTupletOrderingTest {
    static void same(List<ScoreNoteEvent> expected, List<ScoreNoteEvent> reordered) {
        var gray = JoinedBeamTripletVoiceControlsTest.image(true);
        var result = JoinedBeamTripletVoiceControlsTest.apply(gray, reordered);
        for (int i = 0; i < reordered.size(); i++) {
            int original = JoinedBeamTripletVoiceControlsTest.notes(true).indexOf(reordered.get(i));
            assertTrue(original >= 0);
            assertEquals("head " + original, expected.get(original), result.get(i));
        }
    }

    @Test
    public void reversedChordMemberOrderKeepsEveryOwnedNumeral() {
        var n = JoinedBeamTripletVoiceControlsTest.notes(true);
        var expected =
                JoinedBeamTripletVoiceControlsTest.apply(
                        JoinedBeamTripletVoiceControlsTest.image(true), n);
        for (int i = 0; i < 12; i += 2) Collections.swap(n, i, i + 1);
        same(expected, n);
    }

    @Test
    public void reversedWholeInputKeepsEveryOwnedNumeral() {
        var n = JoinedBeamTripletVoiceControlsTest.notes(true);
        var expected =
                JoinedBeamTripletVoiceControlsTest.apply(
                        JoinedBeamTripletVoiceControlsTest.image(true), n);
        Collections.reverse(n);
        same(expected, n);
    }

    @Test
    public void foreignStaffFirstKeepsEveryOwnedNumeral() {
        var n = JoinedBeamTripletVoiceControlsTest.notes(true);
        var expected =
                JoinedBeamTripletVoiceControlsTest.apply(
                        JoinedBeamTripletVoiceControlsTest.image(true), n);
        Collections.rotate(n, 3);
        same(expected, n);
    }

    @Test
    public void arbitraryEnumerationKeepsEveryOwnedNumeral() {
        var original = JoinedBeamTripletVoiceControlsTest.notes(true);
        var expected =
                JoinedBeamTripletVoiceControlsTest.apply(
                        JoinedBeamTripletVoiceControlsTest.image(true), original);
        for (int seed = 0; seed < 16; seed++) {
            var n = new ArrayList<>(original);
            Collections.shuffle(n, new Random(seed));
            same(expected, n);
        }
    }
}
