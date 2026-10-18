// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Distinct clearly shaded fixture; original uniform240 failure remains immutable. */
public class PhotometricShadedCompanionTest {
    private static final int W = ProjectionCallerScene.W, H = ProjectionCallerScene.H;

    private static ProjectionCallerScene scene() {
        var p = new ProjectionCallerScene();
        for (int i = 0; i < p.gray.length; i++)
            if ((p.gray[i] & 255) == 240) p.gray[i] = (byte) 220;
        return p;
    }

    private static List<ScoreNoteEvent> view(List<ScoreNoteEvent> notes, byte[] a, byte[] b)
            throws Exception {
        try {
            Method m =
                    RestFigureOrigins.class.getDeclaredMethod(
                            "photometricView",
                            List.class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class);
            return (List<ScoreNoteEvent>) m.invoke(null, notes, a, b, W, H);
        } catch (NoSuchMethodException legacyV6) {
            return notes;
        }
    }

    private static List<ScoreNoteEvent> map(
            ProjectionCallerScene p, List<ScoreNoteEvent> carrier, byte[] plane, byte[] flat) {
        float[] bottoms = new float[W];
        Arrays.fill(bottoms, 134);
        return RestFigureOrigins.rectified(
                carrier, List.copyOf(p.notes), p.notes, plane, W, H, flat, H, bottoms, 0, 134);
    }

    @Test
    public void shadedKnownTransformAndExactSamplerKeepTheFiniteOwner() throws Exception {
        var p = scene();
        var owners = p.acceptedOwners();
        byte[] derived = RestPaperTone.normalize(p.gray, W, H, 16);
        assertNotSame(p.gray, derived);
        byte[] flat = derived.clone();
        var mapped = map(p, view(owners, p.gray, derived), derived, flat);
        assertNotNull(RestFigureOrigins.origin(mapped, mapped.get(0), flat, W, H));
        var old = p.proposal(137);
        var proposal =
                new RestFigureOrigins.ProposalOrigin(
                        flat, flat, W, H, old.top, old.bottom, old.mask, 197, 218, 90, 137);
        assertEquals(
                FiniteBeamQuarterOwnership.ProposalResult.OWNED,
                FiniteBeamQuarterOwnership.proposalOwnership(
                        flat, W, H, p.REGION, mapped, 0, 0, 1, 102f / H, 16, 197, 218, 90, 137,
                        proposal));
    }

    @Test
    public void shadedCanonicalSamplerPreservesOriginalPlaneAndComponentIdentity()
            throws Exception {
        var p = scene();
        var owners = p.acceptedOwners();
        byte[] before = p.gray.clone();
        byte[] derived = RestPaperTone.normalize(p.gray, W, H, 16),
                afterTransform = derived.clone(),
                flat = derived.clone();
        assertNotSame(p.gray, derived);
        var mapped = map(p, view(owners, p.gray, derived), derived, flat);
        var origin = RestFigureOrigins.origin(mapped, mapped.get(0), flat, W, H);
        assertNotNull(origin);
        var original = RestFigureOrigins.origin(owners, owners.get(0), p.gray, W, H);
        assertSame(original.component, origin.component);
        Field f = origin.getClass().getDeclaredField("originalGray");
        f.setAccessible(true);
        assertArrayEquals(before, (byte[]) f.get(origin));
        assertArrayEquals(before, p.gray);
        assertArrayEquals(afterTransform, derived);
    }
}
