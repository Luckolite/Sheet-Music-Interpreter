// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural page-view lifecycle controls; no score pixels or commercial data. */
public class ScoreBeamSourceJoinProjectionTest {
    static ScoreBeamSource.Anchor a(int p, int n) {
        return new ScoreBeamSource.Anchor(p, n);
    }

    static ScoreBeamSource.Column c(int p, int... ns) {
        var r = new ArrayList<ScoreBeamSource.Anchor>();
        for (int n : ns) r.add(a(p, n));
        return new ScoreBeamSource.Column(r);
    }

    static ScoreBeamSource master() {
        return ScoreBeamSource.capture(
                2,
                5,
                List.of(
                        new ScoreBeamSource.Group(
                                "original-id",
                                List.of(c(2, 0, 1), c(2, 2), c(2, 3)),
                                List.of(5, 1),
                                List.of(
                                        new ScoreBeamSource.Hook(
                                                2, 2, ScoreBeamSource.Direction.BACKWARD)))),
                List.of(c(2, 4)));
    }

    static void rejects(Runnable r) {
        try {
            r.run();
            fail("must reject conflicting source facts");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void threeProjectedViewsReuniteOneMaster() {
        var s = master();
        var j =
                ScoreBeamSource.join(
                        List.of(
                                s.edited(List.of(0, 1)),
                                s.edited(List.of(2)),
                                s.edited(List.of(3, 4))));
        assertEquals(s, j);
        assertEquals(5, j.resolve().groups().get(0).bridgeMask(0));
        assertEquals(1, j.resolve().groups().get(0).bridgeMask(1));
    }

    @Test
    public void detachedFactsAcrossViewsRetainedOnce() {
        var s = ScoreBeamSource.capture(1, 2, List.of(), List.of(c(1, 0), c(1, 1)));
        var j = ScoreBeamSource.join(List.of(s.edited(List.of(0)), s.edited(List.of(1))));
        assertEquals(s, j);
        assertEquals(2, j.resolve().detachedColumns().size());
    }

    @Test
    public void reorderedViewsKeepLiteralPositionsAndHooks() {
        var s = master();
        var j = ScoreBeamSource.join(List.of(s.edited(List.of(3, 0, 1)), s.edited(List.of(2, 4))));
        assertEquals(s.groups(), j.groups());
        assertEquals(
                List.of(
                        Optional.of(a(2, 3)),
                        Optional.of(a(2, 0)),
                        Optional.of(a(2, 1)),
                        Optional.of(a(2, 2)),
                        Optional.of(a(2, 4))),
                j.noteOrigins());
        var r = j.resolve().groups().get(0);
        assertEquals(List.of(1, 2), r.columns().get(0).owners());
        assertEquals(List.of(3), r.columns().get(1).owners());
        assertEquals(List.of(0), r.columns().get(2).owners());
        assertEquals(5, r.bridgeMask(0));
        assertEquals(1, r.bridgeMask(1));
        assertEquals(s.groups().get(0).hooks(), r.engravingHooks());
    }

    @Test
    public void repeatedCurrentOriginRemainsAmbiguous() {
        var s = master();
        var j =
                ScoreBeamSource.join(
                        List.of(s.edited(List.of(0, 1, 2)), s.edited(List.of(0, 3, 4))));
        assertEquals(6, j.noteOrigins().size());
        assertEquals(j.noteOrigins().get(0), j.noteOrigins().get(3));
        var r = j.resolve().groups().get(0);
        assertTrue(r.columns().get(0).ambiguous());
        assertFalse(r.columns().get(0).usable());
        assertEquals(0, r.bridgeMask(0));
        assertEquals(1, r.bridgeMask(1));
    }

    @Test
    public void emptyOwnedViewDoesNotDuplicateFacts() {
        var s = master();
        assertEquals(s, ScoreBeamSource.join(List.of(s.edited(List.of()), s)));
    }

    @Test
    public void duplicateWholeViewsRetainOriginsButCannotEngrave() {
        var s = master();
        var j = ScoreBeamSource.join(List.of(s, s));
        assertEquals(s.groups(), j.groups());
        assertEquals(s.detachedColumns(), j.detachedColumns());
        assertEquals(10, j.noteOrigins().size());
        for (var col : j.resolve().groups().get(0).columns()) {
            assertTrue(col.ambiguous());
            assertFalse(col.usable());
        }
        assertEquals(0, j.resolve().groups().get(0).bridgeMask(0));
        assertEquals(0, j.resolve().groups().get(0).bridgeMask(1));
        assertTrue(j.resolve().groups().get(0).engravingHooks().isEmpty());
        assertTrue(j.resolve().detachedColumns().get(0).ambiguous());
    }

    @Test
    public void conflictingSameIdPayloadStillRejects() {
        var s = master();
        var g = s.groups().get(0);
        var changed = new ScoreBeamSource.Group(g.id(), g.columns(), List.of(1, 1), g.hooks());
        var other = new ScoreBeamSource(List.of(changed), s.detachedColumns(), s.noteOrigins());
        rejects(() -> ScoreBeamSource.join(List.of(s, other)));
    }

    @Test
    public void sameIdDisjointPhysicalPagesDoesNotSilentlyRename() {
        var s = master();
        var g =
                new ScoreBeamSource.Group(
                        "original-id", List.of(c(3, 0), c(3, 1)), List.of(1), List.of());
        var other = ScoreBeamSource.capture(3, 2, List.of(g), List.of());
        rejects(() -> ScoreBeamSource.join(List.of(s, other)));
    }

    @Test
    public void differentIdOverlappingOwnershipStillRejects() {
        var s = master();
        var g = s.groups().get(0);
        var other =
                new ScoreBeamSource(
                        List.of(
                                new ScoreBeamSource.Group(
                                        "different-id", g.columns(), g.bridges(), g.hooks())),
                        List.of(),
                        s.noteOrigins());
        rejects(() -> ScoreBeamSource.join(List.of(s, other)));
    }

    @Test
    public void groupDetachedConflictStillRejects() {
        var s = master();
        var other = new ScoreBeamSource(List.of(), List.of(c(2, 0, 1)), s.noteOrigins());
        rejects(() -> ScoreBeamSource.join(List.of(s, other)));
    }

    @Test
    public void canonicalDuplicateIdsRemainInvalid() {
        var s = master();
        rejects(
                () ->
                        new ScoreBeamSource(
                                List.of(s.groups().get(0), s.groups().get(0)),
                                List.of(),
                                s.noteOrigins()));
    }

    @Test
    public void canonicalDuplicateDetachedOwnershipRemainsInvalid() {
        var s = master();
        rejects(() -> new ScoreBeamSource(List.of(), List.of(c(2, 4), c(2, 4)), s.noteOrigins()));
    }

    @Test
    public void independentLocalPagesStillUseExplicitOnPageIdentity() {
        var local =
                ScoreBeamSource.capture(
                        0,
                        2,
                        List.of(
                                new ScoreBeamSource.Group(
                                        "same-local-name",
                                        List.of(c(0, 0), c(0, 1)),
                                        List.of(1),
                                        List.of())),
                        List.of());
        var j = ScoreBeamSource.join(List.of(local.onPage(0), local.onPage(1)));
        assertEquals(2, j.groups().size());
        assertEquals(
                List.of(
                        Optional.of(a(0, 0)),
                        Optional.of(a(0, 1)),
                        Optional.of(a(1, 0)),
                        Optional.of(a(1, 1))),
                j.noteOrigins());
    }

    @Test
    public void originalColumnsNotReconnectedAcrossOmission() {
        var r = master().edited(List.of(0, 1, 3, 4)).resolve().groups().get(0);
        assertEquals(0, r.bridgeMask(0));
        assertEquals(0, r.bridgeMask(1));
        assertEquals(3, r.columns().size());
        assertTrue(r.columns().get(1).owners().isEmpty());
    }
}
