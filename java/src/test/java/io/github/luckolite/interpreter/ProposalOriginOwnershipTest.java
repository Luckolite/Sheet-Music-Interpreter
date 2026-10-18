// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Optional caller provenance controls; originals remain separately byte-exact. */
public final class ProposalOriginOwnershipTest {
    @Test
    public void completeFiniteCornerUsesActuallyCreatedSemanticOwners() throws Exception {
        var p = new ProjectionCallerScene();
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), p.proposal(137), 90, 137));
    }

    @Test
    public void visibleQuarterInsideSameProposalRemainsResidual() throws Exception {
        var p = new ProjectionCallerScene();
        p.branch();
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), p.proposal(137), 90, 137));
    }

    @Test
    public void independentNearbyGlyphDoesNotInventProposalOwnership() throws Exception {
        var p = new ProjectionCallerScene();
        p.unrelated();
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), p.proposal(137), 90, 137));
    }

    @Test
    public void ordinaryUnknownLegacyNotesCannotSupplyHeadOrigin() {
        var p = new ProjectionCallerScene();
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.UNKNOWN_HEAD_ORIGIN,
                p.result(p.notes, p.proposal(137), 90, 137));
    }

    @Test
    public void partialOrForeignSemanticOwnerCannotBorrowRawOval() throws Exception {
        var p = new ProjectionCallerScene();
        // The real floodfill accepts this one-column fragment, but it has no 2-D own-shaft port.
        for (int y = 0; y < p.H; y++)
            for (int x = 0; x < p.W; x++)
                if (p.labels[y * p.W + x] == 2 && x != 202 && x < 230) p.labels[y * p.W + x] = 0;
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), p.proposal(137), 90, 137));
    }

    @Test
    public void noOriginSurvivesAPlaneIdentitySubstitution() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        byte[] other = p.gray.clone();
        assertNull(RestFigureOrigins.origin(owners, owners.get(0), other, p.W, p.H));
    }

    @Test
    public void removedOrUnboundOwnerRemainsUnknown() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
        map.put(owners.get(0), RestFigureOrigins.origin(owners, owners.get(0), p.gray, p.W, p.H));
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.UNKNOWN_HEAD_ORIGIN,
                p.result(RestFigureOrigins.bind(p.notes, map), p.proposal(137), 90, 137));
    }

    @Test
    public void trimCannotHideAVisibleUnownedLowerContinuation() throws Exception {
        var p = new ProjectionCallerScene();
        p.stroke(212, 137, 212, 146);
        boolean[] line = new boolean[61];
        var proposal =
                new RestFigureOrigins.ProposalOrigin(
                        p.gray, p.gray, p.W, p.H, 90, 150, line, 197, 218, 90, 146);
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), proposal, 90, 137));
    }

    @Test
    public void isolatedPlaneAndInvalidMaskCannotClaimDirectProjection() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        var isolated =
                new RestFigureOrigins.ProposalOrigin(
                        p.gray.clone(),
                        p.gray,
                        p.W,
                        p.H,
                        90,
                        137,
                        new boolean[48],
                        197,
                        218,
                        90,
                        137);
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.INVALID_PROJECTION,
                p.result(owners, isolated, 90, 137));
        var truncatedMask =
                new RestFigureOrigins.ProposalOrigin(
                        p.gray, p.gray, p.W, p.H, 90, 137, new boolean[47], 197, 218, 90, 137);
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.INVALID_PROJECTION,
                p.result(owners, truncatedMask, 90, 137));
    }

    @Test
    public void anUnsupportedOwnShaftExtensionIsNotAStaffRule() throws Exception {
        var p = new ProjectionCallerScene();
        for (int y = 136; y <= 140; y++) for (int x = 199; x <= 202; x++) p.ink(x, y, 1);
        assertNotEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                p.result(p.acceptedOwners(), p.proposal(137), 90, 137));
    }

    @Test
    public void actualProjectionCallerKeepsGenuineQuarterGlyph() {
        int w = 260, h = 210;
        float gap = 16;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int k = 0; k < 5; k++)
            for (int x = 20; x < w - 20; x++) g[Math.round(60 + k * gap) * w + x] = 20;
        double[] t = {0, .04, .18, .24, .42, .48, .57, .62, .72, .78, .86, .96, 1};
        double[] l = {.4, .4, .46, .46, .2, .2, .45, .45, 0, 0, .08, .3, .3};
        double[] r = {.48, .48, .96, .96, .68, .68, .6, .6, .95, .95, .32, .42, .42};
        int height = Math.round(2.9f * gap);
        for (int dy = 0; dy <= height; dy++) {
            double f = dy / (double) height;
            int k = 0;
            while (k + 1 < t.length - 1 && f > t[k + 1]) k++;
            double a = (f - t[k]) / (t[k + 1] - t[k]);
            int left = 130 + (int) Math.round(gap * (l[k] + a * (l[k + 1] - l[k])));
            int right = 130 + (int) Math.round(gap * (r[k] + a * (r[k + 1] - r[k])));
            int y = 60 + Math.round(.6f * gap) + dy;
            for (int x = left; x <= right; x++) g[y * w + x] = (byte) (dy < 2 ? 185 : 20);
        }
        var rests =
                SixteenthRestDetector.detect(
                        g,
                        w,
                        h,
                        List.of(new MeasureRegion(.05f, .95f, .1f, .95f)),
                        List.of(new SixteenthRestDetector.Staff(60, 124, gap, 0, 1)),
                        List.of());
        assertEquals(1, rests.size());
        assertEquals(1, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void rectifierRetainsExactExistingSampleMembershipOnly() throws Exception {
        var p = new ProjectionCallerScene();
        var source = p.acceptedOwners();
        float[] bottoms = new float[p.W];
        Arrays.fill(bottoms, 134);
        byte[] flat = p.gray.clone();
        var mapped = List.copyOf(p.notes);
        var bound =
                RestFigureOrigins.rectified(
                        source, mapped, p.notes, p.gray, p.W, p.H, flat, p.H, bottoms, 0, 134);
        var first = RestFigureOrigins.origin(bound, mapped.get(0), flat, p.W, p.H);
        assertNotNull(first);
        assertTrue(first.contains(210, 78));
        assertFalse(first.contains(210, 60));
        var missing =
                RestFigureOrigins.rectified(
                        source, mapped, p.notes, p.gray, p.W, p.H, flat, p.H, null, 0, 134);
        assertNull(RestFigureOrigins.origin(missing, mapped.get(0), flat, p.W, p.H));
    }
}
