// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OctaveMeasurePrefixTest {
    private int shift(String text) {
        var g = OctaveMarkDetectorTest.page();
        OctaveMarkDetectorTest.dash(g, 120, 600, 55);
        return OctaveMarkDetectorTest.apply(
                        g,
                        List.of(OctaveMarkDetectorTest.word(text, 80, 40)),
                        List.of(OctaveMarkDetectorTest.note(570, 0)))
                .get(0)
                .octaveShift();
    }

    @Test
    public void joinedSystemNumberRetainsParenthesizedOctave() {
        assertEquals(1, shift("31 (8va)."));
    }

    @Test
    public void joinedSystemNumberRetainsTwoOctaves() {
        assertEquals(2, shift("127 (15ma)"));
    }

    @Test
    public void ordinaryNumericTextDoesNotTranspose() {
        assertEquals(0, shift("31 eighth notes"));
    }

    @Test
    public void unparenthesizedNumericPhraseIsNotGuessed() {
        assertEquals(0, shift("31 8va"));
    }

    @Test
    public void uppercaseContinuationPreservesWholeNotesAndPrefixBoundary() {
        var g = OctaveMarkDetectorTest.page();
        OctaveMarkDetectorTest.dash(g, 120, 600, 55);
        var originalPixels = g.clone();
        var direction = OctaveMarkDetectorTest.word("127 (15MA).", 80, 40);
        var words = List.of(direction);
        var beforePrefix = OctaveMarkDetectorTest.note(80, 0);
        var covered = OctaveMarkDetectorTest.note(570, 0);
        var notes = List.of(beforePrefix, covered);
        assertEquals(
                List.of(beforePrefix, covered.withOctaveShift(2)),
                OctaveMarkDetectorTest.apply(g, words, notes));
        assertSame(direction, words.get(0));
        assertEquals("127 (15MA).", direction.text());
        assertSame(beforePrefix, notes.get(0));
        assertSame(covered, notes.get(1));
        assertArrayEquals(originalPixels, g);
        for (String text : List.of("12345 (15ma)", "١٢٧ (15ma)", "127\u00a0(15ma)", "127 15ma")) {
            assertEquals(
                    text,
                    notes,
                    OctaveMarkDetectorTest.apply(
                            g, List.of(OctaveMarkDetectorTest.word(text, 80, 40)), notes));
        }
    }
}
