// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CanonicalSelectedPairTest {
    @Test
    public void canonicalViewContainsExactlyTheAlreadySelectedIdentities() throws Exception {
        var p = new ProjectionCallerScene();
        p.ellipse(330, 94);
        p.notes.add(ProjectionCallerScene.note(330, 94));
        var owners = p.acceptedOwners();
        byte[] derived = p.gray.clone();
        var bound = RestFigureOrigins.photometricView(owners, p.gray, derived, p.W, p.H);
        var a = RestFigureOrigins.origin(bound, bound.get(0), derived, p.W, p.H);
        var b = RestFigureOrigins.origin(bound, bound.get(1), derived, p.W, p.H);
        var pair =
                RestFigureOrigins.originalPair(
                        bound, bound.get(0), a, bound.get(1), b, derived, p.W, p.H);
        assertNotNull(pair);
        assertSame(p.gray, pair.gray());
        assertEquals(2, pair.notes().size());
        assertSame(bound.get(0), pair.notes().get(0));
        assertSame(bound.get(1), pair.notes().get(1));
        assertSame(
                a.component,
                RestFigureOrigins.origin(pair.notes(), pair.notes().get(0), p.gray, p.W, p.H)
                        .component);
    }

    @Test
    public void wrongOriginEqualCloneAndUnknownCanvasCannotBorrowCanonicalPair() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        byte[] derived = p.gray.clone();
        var bound = RestFigureOrigins.photometricView(owners, p.gray, derived, p.W, p.H);
        var na = bound.get(0);
        var nb = bound.get(1);
        var a = RestFigureOrigins.origin(bound, na, derived, p.W, p.H);
        var b = RestFigureOrigins.origin(bound, nb, derived, p.W, p.H);
        assertNull(RestFigureOrigins.originalPair(bound, na, b, nb, a, derived, p.W, p.H));
        var clone = ProjectionCallerScene.note(210, 78);
        assertEquals(na, clone);
        assertNotSame(na, clone);
        assertNull(RestFigureOrigins.originalPair(bound, clone, a, nb, b, derived, p.W, p.H));
        assertNull(RestFigureOrigins.originalPair(bound, na, a, nb, b, derived.clone(), p.W, p.H));
    }

    @Test
    public void originalInkBeyondDerivedLastRowStillNeedsOwnership() throws Exception {
        var p = new ProjectionCallerScene();
        for (int i = 0; i < p.gray.length; i++)
            if ((p.gray[i] & 255) == 240) p.gray[i] = (byte) 220;
        byte[] old = p.gray.clone();
        p.stroke(208, 140, 215, 144);
        p.stroke(215, 144, 209, 149);
        for (int i = 0; i < p.gray.length; i++)
            if ((old[i] & 255) >= 170 && (p.gray[i] & 255) < 170) p.gray[i] = (byte) 120;
        var owners = p.acceptedOwners();
        byte[] derived = RestPaperTone.normalize(p.gray, p.W, p.H, 16);
        int removed = 0;
        for (int y = 138; y <= 153; y++)
            for (int x = 197; x <= 218; x++)
                if ((p.gray[y * p.W + x] & 255) < 170 && (derived[y * p.W + x] & 255) >= 170)
                    removed++;
        assertTrue(removed > 0);
        var bound = RestFigureOrigins.photometricView(owners, p.gray, derived, p.W, p.H);
        var proposal =
                new RestFigureOrigins.ProposalOrigin(
                        derived, derived, p.W, p.H, 90, 153, new boolean[64], 197, 218, 90, 137);
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                FiniteBeamQuarterOwnership.proposalOwnership(
                        derived, p.W, p.H, p.REGION, bound, 0, 0, 1, 102f / p.H, 16, 197, 218, 90,
                        137, proposal));
    }
}
