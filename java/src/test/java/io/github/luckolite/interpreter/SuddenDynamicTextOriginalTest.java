// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Complete original dynamic captions and conservative lexical counterexamples. */
public final class SuddenDynamicTextOriginalTest {
    @Test
    public void suffixPianoKeepsPrintedModifier() {
        var m = SuddenDynamicText.parse("p sub.").orElseThrow();
        assertEquals("p", m.level());
        assertEquals("sub.", m.qualifier());
        assertFalse(m.before());
        assertEquals(-8, ScoreDynamicsDetector.level("p sub."), 0);
        assertTrue(ScoreDynamicsDetector.isSuddenLevel("p sub."));
    }

    @Test
    public void prefixCompatibilityRemains() {
        var m = SuddenDynamicText.parse("Sub. ff").orElseThrow();
        assertEquals("ff", m.level());
        assertEquals("Sub.", m.qualifier());
        assertTrue(m.before());
        assertEquals(6, ScoreDynamicsDetector.level("Sub. ff"), 0);
    }

    @Test
    public void everyStandardLevelHasTheSameSuffixLaw() {
        for (var s : List.of("ppp", "pp", "p", "mp", "mf", "fff", "ff", "f")) {
            assertEquals(
                    ScoreDynamicsDetector.level(s), ScoreDynamicsDetector.level(s + " subito"), 0);
            assertEquals(s, ScoreDynamicsDetector.glyphLevelText(s + " subito"));
        }
    }

    @Test
    public void arbitraryProseAndBareModifierRemainUnresolved() {
        for (var s :
                List.of(
                        "sub.",
                        "please p sub.",
                        "p sub. later",
                        "p submarine",
                        "subway f",
                        "p sub. mf")) assertTrue(SuddenDynamicText.parse(s).isEmpty());
    }

    @Test
    public void suffixBecomesOneOwnedNeutralDirection() {
        var ds = ExpressiveDirectionText.parse("p sub.");
        assertEquals(1, ds.size());
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, ds.get(0).kind());
        assertEquals("p sub.", ds.get(0).printedText());
        assertEquals("", ds.get(0).qualifierText());
    }

    @Test
    public void glyphRefinementDoesNotEraseSuffixText() {
        var literal = new PlayingTechniqueDetector.Word("p sub.", .2f, .3f, .3f, .4f);
        var box = new PlayingTechniqueDetector.Word("p", .2f, .3f, .23f, .4f);
        assertEquals(
                "p sub.", ScoreDynamicsDetector.recognizedWord("p", box, List.of(literal)).text());
    }
}
