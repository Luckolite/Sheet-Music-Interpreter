// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Complete lexical directions; no tempo, articulation or performance policy is inferred. */
public class LegatoDirectionOriginalTest {
    private void admitted(String text) {
        var ds = ExpressiveDirectionText.parse(text);
        assertEquals(1, ds.size());
        assertEquals(Kind.UNRESOLVED_DIRECTION, ds.get(0).kind());
        assertEquals(text, ds.get(0).printedText());
    }

    @Test
    public void standaloneInstructionIsRetained() {
        admitted("legato");
    }

    @Test
    public void completeDynamicCompoundIsRetained() {
        admitted("mf legato");
    }

    @Test
    public void casePunctuationPreservesCompleteEvidence() {
        admitted("  LEGATO.  ");
    }

    @Test
    public void completeExistingQualifierIsRetained() {
        admitted("p sempre legato");
    }

    @Test
    public void negationIsNotAffirmativeLegato() {
        assertTrue(ExpressiveDirectionText.parse("non legato").isEmpty());
    }

    @Test
    public void proseIsNotACompleteDirection() {
        assertTrue(ExpressiveDirectionText.parse("please play legato here").isEmpty());
    }

    @Test
    public void secondTechniqueIsNotSilentlyDiscarded() {
        assertTrue(ExpressiveDirectionText.parse("legato e staccato").isEmpty());
    }

    @Test
    public void unrelatedWordAndInvalidDynamicDoNotMatch() {
        assertTrue(ExpressiveDirectionText.parse("allegato").isEmpty());
        assertTrue(ExpressiveDirectionText.parse("mmm legato").isEmpty());
    }

    @Test
    public void existingRitardandoRemainsDistinct() {
        var ds = ExpressiveDirectionText.parse("rit. legato");
        assertEquals(1, ds.size());
        assertEquals(Kind.RITARDANDO, ds.get(0).kind());
    }
}
