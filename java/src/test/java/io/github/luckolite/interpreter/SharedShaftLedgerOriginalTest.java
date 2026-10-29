// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic chord, short printed ledgers and a continuous common shaft. */
public class SharedShaftLedgerOriginalTest {
    static final int W = 240, H = 360;
    static final float GAP = 16;
    static final Class<?> C = type("Component"), S = type("Staff");
    static final Class<?> B = type("RawCourtesyBudget");

    static Class<?> type(String n) {
        try {
            return Class.forName(OmrScoreInterpreter.class.getName() + "$" + n);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static Object construct(Class<?> c, Object... v) throws Exception {
        var k = c.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return k.newInstance(v);
    }

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Object head, peer, staff;

        Page() throws Exception {
            Arrays.fill(g, (byte) 255);
            for (int y = 120; y <= 200; y += 16) for (int x = 94; x <= 126; x++) g[y * W + x] = 0;
            head = oval(110, 120);
            peer = oval(110, 168);
            for (int y = 78; y <= 168; y++) g[y * W + 120] = 0;
            staff = construct(S, 216f, 280f, 16f);
        }

        Object oval(int cx, int cy) throws Exception {
            int n = 0, minX = W, maxX = 0, minY = H, maxY = 0;
            long sx = 0, sy = 0;
            for (int y = cy - 9; y <= cy + 9; y++)
                for (int x = cx - 12; x <= cx + 12; x++)
                    if (Math.pow((x - cx) / 12., 2) + Math.pow((y - cy + .35 * (x - cx)) / 8., 2)
                            <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                        n++;
                        sx += x;
                        sy += y;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
            return construct(C, n, minX, maxX, minY, maxY, sx / (float) n, sy / (float) n);
        }

        boolean proof(List<?> roster, long[] work) throws Exception {
            Method m;
            try {
                m =
                        OmrScoreInterpreter.class.getDeclaredMethod(
                                "sharedLedgerBody",
                                byte[].class,
                                byte[].class,
                                int.class,
                                int.class,
                                C,
                                S,
                                List.class,
                                List.class,
                                IdentityHashMap.class,
                                B);
                var budget = construct(B, work[0], 16L * 1024 * 1024);
                m.setAccessible(true);
                var result =
                        (boolean)
                                m.invoke(
                                        null,
                                        l,
                                        g,
                                        W,
                                        H,
                                        head,
                                        staff,
                                        List.of(staff),
                                        roster,
                                        new IdentityHashMap<>(),
                                        budget);
                var used = B.getDeclaredField("workUsed");
                used.setAccessible(true);
                work[0] -= used.getLong(budget);
                return result;
            } catch (NoSuchMethodException old) {
                m =
                        OmrScoreInterpreter.class.getDeclaredMethod(
                                "hasHeadLedgerSupport",
                                byte[].class,
                                byte[].class,
                                int.class,
                                int.class,
                                C,
                                S,
                                boolean.class,
                                List.class);
                m.setAccessible(true);
                return (boolean) m.invoke(null, l, g, W, H, head, staff, false, List.of(staff));
            }
        }

        boolean proof() throws Exception {
            return proof(List.of(head, peer), new long[] {64_000_000});
        }
    }

    @Test
    public void upperChordOvalSharesItsShaftWithIndependentLowerHead() throws Exception {
        assertTrue(new Page().proof());
    }

    @Test
    public void absentPhysicalPeerCannotExplainShaftContinuation() throws Exception {
        var p = new Page();
        assertFalse(p.proof(List.of(p.head), new long[] {64_000_000}));
    }

    @Test
    public void brokenCommonShaftRefuses() throws Exception {
        var p = new Page();
        for (int y = 147; y <= 151; y++) p.g[y * W + 120] = (byte) 255;
        assertFalse(p.proof());
    }

    @Test
    public void missingInnerLedgerRefuses() throws Exception {
        var p = new Page();
        for (int x = 94; x <= 126; x++) p.g[152 * W + x] = (byte) 255;
        assertFalse(p.proof());
    }

    @Test
    public void rawInstructionStripOutsideOvalRefuses() throws Exception {
        var p = new Page();
        for (int x = 99; x <= 121; x++) p.g[111 * W + x] = 0;
        assertFalse(p.proof());
    }

    @Test
    public void semanticOnlyPeerHasNoRawBody() throws Exception {
        var p = new Page();
        for (int y = 160; y <= 176; y++)
            for (int x = 99; x <= 121; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(p.proof());
    }

    @Test
    public void twoLobeInstructionBoxCannotSupplyFilledOvals() throws Exception {
        var p = new Page();
        for (int y = 110; y <= 130; y++)
            for (int x = 98; x <= 122; x++) p.g[y * W + x] = (byte) 255;
        for (int y = 114; y <= 126; y++) for (int x = 101; x <= 119; x++) p.g[y * W + x] = 0;
        assertFalse(p.proof());
    }

    @Test
    public void beamCapWithoutACompleteOvalCannotOwnSemanticHead() throws Exception {
        var p = new Page();
        for (int y = 110; y <= 130; y++)
            for (int x = 98; x <= 122; x++) p.g[y * W + x] = (byte) 255;
        for (int y = 119; y <= 121; y++) for (int x = 94; x <= 126; x++) p.g[y * W + x] = 0;
        assertFalse(p.proof());
    }

    @Test
    public void shaftBorrowedOnlyFromPeerCannotReachUpperOval() throws Exception {
        var p = new Page();
        for (int y = 78; y <= 145; y++) p.g[y * W + 120] = (byte) 255;
        assertFalse(p.proof());
    }

    @Test
    public void incompatibleThirdBodyCannotExplainFreeCorner() throws Exception {
        var p = new Page();
        p.oval(96, 127);
        assertFalse(p.proof());
    }

    @Test
    public void eachExpectedInnerRuleRemainsMandatory() throws Exception {
        for (int y : new int[] {136, 152, 168, 184, 200}) {
            var p = new Page();
            for (int x = 94; x <= 126; x++) p.g[y * W + x] = (byte) 255;
            assertFalse("Missing rule " + y, p.proof());
        }
    }

    @Test
    public void oldStrictBodyIsStillUnsupportedBySharedShaftScene() throws Exception {
        var p = new Page();
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "completeUpperLedgerBody",
                        byte[].class,
                        int.class,
                        int.class,
                        C,
                        float.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, p.g, W, H, p.head, GAP));
    }

    static boolean seam(boolean recordedSameSplit) throws Exception {
        var p = new Page();
        p.oval(110, 136);
        for (int x = 99; x <= 121; x++) p.g[129 * W + x] = 0;
        var upper = construct(C, 250, 99, 121, 111, 129, 110f, 120f);
        var lower = construct(C, 250, 99, 121, 130, 145, 110f, 137f);
        try {
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "sharedBodyEnvelope",
                            byte[].class,
                            int.class,
                            int.class,
                            C,
                            C,
                            float.class,
                            int[].class,
                            boolean.class);
            m.setAccessible(true);
            return (boolean)
                    m.invoke(
                            null,
                            p.g,
                            W,
                            H,
                            upper,
                            lower,
                            16f,
                            new int[] {120, 78, -1},
                            recordedSameSplit);
        } catch (NoSuchMethodException old) {
            return false;
        }
    }

    @Test
    public void committedSiblingSeamCanExplainItsSingleOverlappingRawRow() throws Exception {
        assertTrue(seam(true));
    }

    @Test
    public void unregisteredAdjacentBoxCannotExplainTheSameRawSeam() throws Exception {
        assertFalse(seam(false));
    }

    static OmrScoreInterpreter.Analysis pipeline(boolean unavailableCapture) throws Exception {
        return (OmrScoreInterpreter.Analysis) pipelineWithQuota(unavailableCapture, -1)[0];
    }

    static Object[] pipelineWithQuota(boolean unavailableCapture, long storage) throws Exception {
        int w = 420, h = 600;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var p = new Page();
        for (int y = 0; y < H; y++) {
            System.arraycopy(p.g, y * W, g, y * w, W);
            System.arraycopy(p.l, y * W, l, y * w, W);
        }
        for (int y = 216; y <= 280; y += 16)
            for (int x = 20; x < 400; x++) {
                g[y * w + x] = 0;
                l[y * w + x] = 4;
            }
        for (int y = 240; y <= 256; y++)
            for (int x = 49; x <= 71; x++)
                if (Math.pow((x - 60) / 11., 2) + Math.pow((y - 248) / 8., 2) <= 1) {
                    g[y * w + x] = 0;
                    l[y * w + x] = 2;
                }
        for (int y = 204; y <= 248; y++) {
            g[y * w + 70] = 0;
            if (l[y * w + 70] != 2) l[y * w + 70] = 1;
        }
        // A single diagonally connected checkerboard has >11000 exact producer runs.
        // Its bounded observer capture refuses; ordinary recognition must continue.
        if (unavailableCapture)
            for (int y = 360; y < 540; y++)
                for (int x = 260; x < 392; x++)
                    if ((x + y) % 2 == 0) {
                        g[y * w + x] = 0;
                        l[y * w + x] = 2;
                    }
        var measures = List.of(new MeasureRegion(.04f, .96f, .12f, .52f));
        if (storage < 0)
            return new Object[] {OmrScoreInterpreter.analyze(l, g, w, h, measures), null};
        var budget = construct(B, 64_000_000L, storage);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "analyze", byte[].class, byte[].class, int.class, int.class, List.class, B);
        m.setAccessible(true);
        return new Object[] {m.invoke(null, l, g, w, h, measures, budget), budget};
    }

    @Test
    public void realPipelineRecoversSharedShaftHeadWithAuthenticatedProducer() throws Exception {
        var a = pipeline(false);
        assertTrue(a.notes().stream().anyMatch(n -> n.pageY() < .23f));
        assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .39f && n.pageY() < .44f));
    }

    @Test
    public void exhaustedProducerAbstainsOnlyFromNewSharedBodyAdmission() throws Exception {
        var a = pipeline(true);
        assertFalse(a.notes().stream().anyMatch(n -> n.pageY() < .23f));
        assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .39f && n.pageY() < .44f));
        assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .26f && n.pageY() < .31f));
    }

    @Test
    public void optionalMapStorageIsPrepaidAndRefusalPreservesOrdinaryNotes() throws Exception {
        for (long bytes : new long[] {4095, 4096}) {
            var run = pipelineWithQuota(false, bytes);
            var a = (OmrScoreInterpreter.Analysis) run[0];
            assertFalse(a.notes().stream().anyMatch(n -> n.pageY() < .23f));
            assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .39f && n.pageY() < .44f));
            assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .26f && n.pageY() < .31f));
            var f = B.getDeclaredField("bytesUsed");
            f.setAccessible(true);
            assertEquals(bytes == 4095 ? 0 : 4096, f.getLong(run[1]));
        }
    }

    @Test
    public void decliningOptionalMapsKeepsAffordableLegacyCourtesyWorkAvailable() throws Exception {
        var run = pipelineWithQuota(false, 4095);
        var budget = run[1];
        var exhausted = B.getDeclaredField("exhausted");
        exhausted.setAccessible(true);
        assertFalse(exhausted.getBoolean(budget));
        var legacy =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "prepayRawNaturalPart", B, int.class, int.class, float.class);
        legacy.setAccessible(true);
        assertTrue((boolean) legacy.invoke(null, budget, 8, 12, 8f));
        var used = B.getDeclaredField("bytesUsed");
        used.setAccessible(true);
        assertEquals(800, used.getLong(budget));
        var a = (OmrScoreInterpreter.Analysis) run[0];
        assertTrue(a.notes().stream().anyMatch(n -> n.pageY() > .39f && n.pageY() < .44f));
    }

    @Test
    public void noBudgetAbstains() throws Exception {
        var p = new Page();
        assertFalse(p.proof(List.of(p.head, p.peer), new long[] {0}));
    }

    @Test
    public void insufficientBudgetAbstainsWithoutOverdraft() throws Exception {
        var p = new Page();
        long[] w = {4};
        assertFalse(p.proof(List.of(p.head, p.peer), w));
        assertTrue(w[0] >= 0);
    }

    @Test
    public void certificateDoesNotMutateInputRasters() throws Exception {
        var p = new Page();
        byte[] g = p.g.clone(), l = p.l.clone();
        p.proof();
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }
}
