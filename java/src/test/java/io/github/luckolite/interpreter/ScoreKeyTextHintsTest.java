// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class ScoreKeyTextHintsTest {
    @Test
    public void articleAndNonterminalPitchWordsAreNotShorthand() {
        assertEquals("", ScoreKeyTextHints.printedKey("Lost in a Dream"));
        assertEquals("", ScoreKeyTextHints.printedKey("Lost in A Dream"));
        assertEquals("", ScoreKeyTextHints.printedKey("Play in a quiet room"));
        assertEquals("", ScoreKeyTextHints.printedKey("Play in A quiet room"));
        assertEquals("", ScoreKeyTextHints.printedKey("in d"));
        assertEquals("", ScoreKeyTextHints.printedKey("key signature: 2 sharps"));
    }

    @Test
    public void terminalPitchAndExplicitModeRetainMusicalMeaning() {
        assertEquals("A Major", ScoreKeyTextHints.printedKey("Theme in A"));
        assertEquals("D Major", ScoreKeyTextHints.printedKey("Theme in D\nViolin"));
        assertEquals("F# Major", ScoreKeyTextHints.printedKey("Theme in F#."));
        assertEquals("a minor", ScoreKeyTextHints.printedKey("Theme in a minor"));
        assertEquals("C sharp minor", ScoreKeyTextHints.printedKey("In the key of C sharp minor"));
        assertEquals("E♭ Major", ScoreKeyTextHints.printedKey("E♭ Major"));
    }

    @Test
    public void actualPrintedLabelHasPriorityOverTitleGuess() {
        assertEquals(
                "C Major", ScoreKeyTextHints.preferPrinted("Theme in D\nKey: C Major", "E Minor"));
        assertEquals("D Major", ScoreKeyTextHints.preferPrinted("Allegro", "D Major"));
        assertEquals("", ScoreKeyTextHints.preferPrinted(null, null));
    }
}
