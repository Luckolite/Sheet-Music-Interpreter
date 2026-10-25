// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Original source-free page projections; no score scans or recognition outputs. */
public final class TremoloPageJoinOriginalTest {
    private static ScoreTremoloSource.Pair pair(String id, int a, int b) {
        return new ScoreTremoloSource.Pair(
                id,
                List.of(new ScoreTremoloSource.Anchor(0, a)),
                List.of(new ScoreTremoloSource.Anchor(0, b)),
                2,
                List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f)),
                ScoreTremoloSource.ClockPolicy.INDEPENDENT_ENDPOINT_DURATIONS);
    }

    @Test
    public void splitEndpointsRejoinTheSamePrintedPair() {
        var source = ScoreTremoloSource.capture(0, 4, List.of(pair("pair", 0, 3))).onPage(2);
        var first = source.edited(List.of(0, 1));
        var second = source.edited(List.of(2, 3));
        assertEquals(1, first.resolve().unresolved().size());
        assertEquals(1, second.resolve().unresolved().size());
        var joined = ScoreTremoloSource.join(List.of(first, second));
        assertEquals(source, joined);
        assertEquals(1, joined.resolve().resolved().size());
        assertEquals(List.of(0), joined.resolve().resolved().get(0).first());
        assertEquals(List.of(3), joined.resolve().resolved().get(0).second());
    }

    @Test
    public void unrelatedPageCopiesRetainUnresolvedFactsAndInsertedNotes() {
        var source =
                ScoreTremoloSource.capture(
                        0, 4, List.of(pair("resolved", 0, 1), pair("retained", 2, 3)));
        var joined =
                ScoreTremoloSource.join(
                        List.of(
                                source.edited(List.of(0)),
                                source.edited(List.of(1)),
                                source.edited(List.of(-1))));
        assertEquals(source.pairs(), joined.pairs());
        assertEquals(3, joined.noteOrigins().size());
        assertTrue(joined.noteOrigins().get(2).isEmpty());
        assertEquals(1, joined.resolve().resolved().size());
        assertEquals(1, joined.resolve().unresolved().size());
        assertEquals(source.pairs().get(1), joined.resolve().unresolved().get(0));
    }

    @Test
    public void independentPagesKeepFirstOccurrenceOrderAndPhysicalNamespaces() {
        var local = ScoreTremoloSource.capture(0, 2, List.of(pair("local", 0, 1)));
        var one = local.onPage(0);
        var two = local.onPage(1);
        var joined = ScoreTremoloSource.join(List.of(two, one));
        assertEquals(List.of(two.pairs().get(0), one.pairs().get(0)), joined.pairs());
        assertEquals(2, joined.resolve().resolved().size());
        assertEquals(two.noteOrigins().get(0), joined.noteOrigins().get(0));
        assertEquals(one.noteOrigins().get(0), joined.noteOrigins().get(2));
        assertEquals(one, ScoreTremoloSource.join(List.of(one)));
    }

    @Test
    public void sameIdentityWithAnyChangedSourcePayloadIsStillRejected() {
        var original = pair("identity", 0, 1);
        var changed =
                List.of(
                        new ScoreTremoloSource.Pair(
                                original.id(),
                                original.first(),
                                original.second(),
                                3,
                                original.evidence(),
                                original.clockPolicy()),
                        new ScoreTremoloSource.Pair(
                                original.id(),
                                original.second(),
                                original.first(),
                                original.strokes(),
                                original.evidence(),
                                original.clockPolicy()),
                        new ScoreTremoloSource.Pair(
                                original.id(),
                                original.first(),
                                original.second(),
                                original.strokes(),
                                List.of(new ScoreTremoloSource.Evidence(0, .2f, .2f, .4f, .4f)),
                                original.clockPolicy()),
                        new ScoreTremoloSource.Pair(
                                original.id(),
                                original.first(),
                                original.second(),
                                original.strokes(),
                                original.evidence(),
                                ScoreTremoloSource.ClockPolicy.UNRESOLVED));
        for (var conflict : changed) {
            var a = new ScoreTremoloSource(List.of(original), List.of());
            var b = new ScoreTremoloSource(List.of(conflict), List.of());
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ScoreTremoloSource.join(List.of(a, b)));
            assertEquals("Conflicting tremolo identity", error.getMessage());
        }
    }

    @Test
    public void constructorAndJoinedOwnershipGuardsRemainStrict() {
        var p = pair("a", 0, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreTremoloSource(List.of(p, p), List.of()));
        var a = new ScoreTremoloSource(List.of(p), List.of());
        var b = new ScoreTremoloSource(List.of(pair("b", 0, 1)), List.of());
        assertThrows(IllegalArgumentException.class, () -> ScoreTremoloSource.join(List.of(a, b)));
        var origin = Optional.of(new ScoreTremoloSource.Anchor(0, 0));
        var single = new ScoreTremoloSource(List.of(), List.of(origin));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreTremoloSource.join(List.of(single, single)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreTremoloSource(List.of(), List.of(origin, origin)));
    }

    @Test
    public void joinedNoteLimitIsCheckedBeforeCopyingTheNextPage() {
        var large = ScoreTremoloSource.empty(500_001);
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ScoreTremoloSource.join(List.of(large, large)));
        assertEquals("Too many tremolo note owners", error.getMessage());
        assertEquals(ScoreTremoloSource.empty(0), ScoreTremoloSource.join(List.of()));
    }
}
