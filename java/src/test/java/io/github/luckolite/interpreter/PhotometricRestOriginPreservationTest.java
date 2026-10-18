// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source-free known-view/actual-sampler controls, not a commercial rest repro. */
public class PhotometricRestOriginPreservationTest {
    private static final int W = ProjectionCallerScene.W, H = ProjectionCallerScene.H;

    private static List<SixteenthRestDetector.Staff> staffs() {
        return List.of(new SixteenthRestDetector.Staff(70, 134, 16, 0, 1));
    }

    private static List<ScoreNoteEvent> view(List<ScoreNoteEvent> notes, byte[] src, byte[] dst)
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
            return (List<ScoreNoteEvent>) m.invoke(null, notes, src, dst, W, H);
        } catch (NoSuchMethodException oldV6) {
            // Exact legacy call-site semantics: transformed image reused the original notes
            // carrier.
            return notes;
        }
    }

    private static byte[] contrast(byte[] src) throws Exception {
        Method m =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "contrastedRestInk",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        int.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, src, W, H, staffs(), 185);
    }

    private static List<ScoreNoteEvent> rectify(
            List<ScoreNoteEvent> carrier, List<ScoreNoteEvent> source, byte[] plane, byte[] flat) {
        float[] bottom = new float[W];
        Arrays.fill(bottom, 134);
        return RestFigureOrigins.rectified(
                carrier, List.copyOf(source), source, plane, W, H, flat, H, bottom, 0, 134);
    }

    private static FiniteBeamQuarterOwnership.ProposalResult result(
            byte[] g, List<ScoreNoteEvent> notes) {
        var p = new ProjectionCallerScene().proposal(137);
        var proposal =
                new RestFigureOrigins.ProposalOrigin(
                        g, g, W, H, p.top, p.bottom, p.mask, 197, 218, 90, 137);
        return FiniteBeamQuarterOwnership.proposalOwnership(
                g,
                W,
                H,
                ProjectionCallerScene.REGION,
                notes,
                0,
                0,
                1,
                102f / H,
                16,
                197,
                218,
                90,
                137,
                proposal);
    }

    @Test
    public void unissuedEqualCanvasCannotBorrowOrigin() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        byte[] copy = p.gray.clone();
        assertNull(RestFigureOrigins.origin(owners, owners.get(0), copy, W, H));
    }

    @Test
    public void unknownSourceCannotIssuePhotometricProvenance() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        byte[] wrong = p.gray.clone(), derived = wrong.clone();
        var candidate = view(owners, wrong, derived);
        assertNull(RestFigureOrigins.origin(candidate, candidate.get(0), derived, W, H));
    }

    @Test
    public void equalDistinctNoteCannotBorrowViewOrigin() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        var clone = ProjectionCallerScene.note(210, 78);
        assertEquals(owners.get(0), clone);
        assertNotSame(owners.get(0), clone);
        var origin = RestFigureOrigins.origin(owners, owners.get(0), p.gray, W, H);
        var impostor = RestFigureOrigins.bind(List.of(clone), Map.of(owners.get(0), origin));
        byte[] derived = p.gray.clone();
        var candidate = view(impostor, p.gray, derived);
        assertNull(RestFigureOrigins.origin(candidate, clone, derived, W, H));
    }

    @Test
    public void hollowSourceCannotGainFilledOwnershipFromToneView() throws Exception {
        var p = new ProjectionCallerScene();
        for (int i = 0; i < p.gray.length; i++)
            if ((p.gray[i] & 255) == 240) p.gray[i] = (byte) 238;
        for (int dy = -8; dy <= 8; dy++)
            for (int dx = -10; dx <= 10; dx++) {
                double tilted = dy + .4 * dx;
                if (dx * dx / 100.0 + tilted * tilted / 36.0 <= .55)
                    p.gray[(78 + dy) * W + 210 + dx] = (byte) 220;
            }
        var owners = p.acceptedOwners();
        assertFalse(FilledSparseBeamHead.matches(p.gray, W, H, 210, 78, 16));
        byte[] derived = RestPaperTone.normalizeOrdinaryRestInk(p.gray, W, H, 16, 220);
        assertNotSame(p.gray, derived);
        assertTrue(FilledSparseBeamHead.matches(derived, W, H, 210, 78, 16));
        var bound = view(owners, p.gray, derived);
        assertNotEquals(FiniteBeamQuarterOwnership.ProposalResult.OWNED, result(derived, bound));
    }

    @Test
    public void newlyVisibleQuarterBranchIsNotLostBySourceAuthority() throws Exception {
        var p = new ProjectionCallerScene();
        byte[] before = p.gray.clone();
        p.branch();
        for (int i = 0; i < before.length; i++)
            if ((before[i] & 255) >= 170 && (p.gray[i] & 255) < 170) p.gray[i] = (byte) 180;
        var owners = p.acceptedOwners();
        byte[] derived = contrast(p.gray);
        int visible = 0;
        for (int i = 0; i < before.length; i++)
            if ((p.gray[i] & 255) >= 170 && (derived[i] & 255) < 170) visible++;
        assertTrue(visible > 0);
        var bound = view(owners, p.gray, derived);
        assertNotEquals(FiniteBeamQuarterOwnership.ProposalResult.OWNED, result(derived, bound));
    }

    @Test
    public void publicMultiStageProjectionKeepsCleanSceneSilent() throws Exception {
        var p = new ProjectionCallerScene();
        assertTrue(p.acquire(p.acceptedOwners()).isEmpty());
        // This caller has always returned zero; it is preservation, NOT actual false-rest
        // acquisition proof.
    }

    @Test
    public void canonicalSamplerAndAllInputPlanesStayExact() throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        byte[] raw = p.gray.clone();
        byte[] derived = RestPaperTone.normalize(p.gray, W, H, 16),
                before = derived.clone(),
                flat = derived.clone();
        var mapped = rectify(view(owners, p.gray, derived), p.notes, derived, flat);
        var origin = RestFigureOrigins.origin(mapped, mapped.get(0), flat, W, H);
        assertNotNull(origin);
        Field f = origin.getClass().getDeclaredField("originalGray");
        f.setAccessible(true);
        assertArrayEquals(raw, (byte[]) f.get(origin));
        assertArrayEquals(raw, p.gray);
        assertArrayEquals(before, derived);
    }
}
