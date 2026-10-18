// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** An original visible branch must not disappear from ownership when paper is brightened. */
public class OriginalPlaneBranchOwnershipTest {
    @Test
    public void brighteningCannotEraseOriginalProposalBranchAuthority() throws Exception {
        var p = new ProjectionCallerScene();
        for (int i = 0; i < p.gray.length; i++)
            if ((p.gray[i] & 255) == 240) p.gray[i] = (byte) 220;
        byte[] before = p.gray.clone();
        p.branch();
        for (int i = 0; i < p.gray.length; i++)
            if ((before[i] & 255) >= 170 && (p.gray[i] & 255) < 170) p.gray[i] = (byte) 120;
        var owners = p.acceptedOwners();
        byte[] derived = RestPaperTone.normalize(p.gray, p.W, p.H, 16);
        assertNotSame(p.gray, derived);
        int removed = 0;
        for (int y = 90; y <= 137; y++)
            for (int x = 197; x <= 218; x++)
                if ((p.gray[y * p.W + x] & 255) < 170 && (derived[y * p.W + x] & 255) >= 170)
                    removed++;
        assertTrue(removed > 0);
        var bound = RestFigureOrigins.photometricView(owners, p.gray, derived, p.W, p.H);
        var old = p.proposal(137);
        var proposal =
                new RestFigureOrigins.ProposalOrigin(
                        derived,
                        derived,
                        p.W,
                        p.H,
                        old.top,
                        old.bottom,
                        old.mask,
                        197,
                        218,
                        90,
                        137);
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                FiniteBeamQuarterOwnership.proposalOwnership(
                        derived, p.W, p.H, p.REGION, bound, 0, 0, 1, 102f / p.H, 16, 197, 218, 90,
                        137, proposal));
    }
}
