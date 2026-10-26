// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreTremoloSoundOwnershipTest {
    private ScoreTremoloSource original() {
        return ScoreTremoloSource.capture(
                0,
                8,
                List.of(
                        new ScoreTremoloSource.Pair(
                                "original-index-domain",
                                List.of(new ScoreTremoloSource.Anchor(0, 4)),
                                List.of(new ScoreTremoloSource.Anchor(0, 5)),
                                3,
                                List.of(new ScoreTremoloSource.Evidence(0, .2f, .3f, .6f, .4f)))));
    }

    @Test
    public void filteringAndRepeatsCannotReplaceTheOriginalDomain() {
        var source = original();
        for (int selected : List.of(5, 16))
            assertSame(
                    source,
                    ScoreTremoloSoundOwnership.forPerformedNotes(
                            ScoreTremoloSource.empty(selected), source));
    }

    @Test
    public void absentOriginalDomainPreservesLegacySelection() {
        var current = original();
        assertSame(current, ScoreTremoloSoundOwnership.forPerformedNotes(current, null));
    }

    @Test
    public void anOrdinaryPerformedRowNumberIsNotASourceEndpoint() {
        var pair = original().performanceResolution().resolved().get(0);
        assertFalse(ScoreTremoloSoundOwnership.containsEndpoint(pair, List.of(7)));
        assertTrue(ScoreTremoloSoundOwnership.containsEndpoint(pair, List.of(4)));
        assertTrue(ScoreTremoloSoundOwnership.containsEndpoint(pair, List.of(5)));
    }

    @Test
    public void membershipExaminesAllActualSoundAliases() {
        var pair = original().performanceResolution().resolved().get(0);
        assertTrue(ScoreTremoloSoundOwnership.containsEndpoint(pair, List.of(2, 4)));
        assertFalse(ScoreTremoloSoundOwnership.containsEndpoint(pair, List.of(2, 7)));
    }

    @Test
    public void aliasBoundsUseTheOriginalRoster() {
        var source = original();
        ScoreTremoloSoundOwnership.requireAlias(source, 7);
        for (int bad : List.of(-1, 8)) {
            try {
                ScoreTremoloSoundOwnership.requireAlias(source, bad);
                fail("Invalid original alias");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void selectionNeverRewritesTheRetainedOriginVector() {
        var source = original();
        var before = source.noteOrigins();
        var selected =
                ScoreTremoloSoundOwnership.forPerformedNotes(ScoreTremoloSource.empty(5), source);
        assertSame(before, selected.noteOrigins());
        assertEquals(8, selected.noteOrigins().size());
        assertEquals(List.of(4), selected.performanceResolution().resolved().get(0).first());
    }
}
