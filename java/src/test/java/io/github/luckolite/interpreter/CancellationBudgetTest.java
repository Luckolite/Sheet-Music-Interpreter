// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes actual prepaid optional path/operation, not an accounting reimplementation. */
public final class CancellationBudgetTest {
    static final Class<?> B;

    static {
        try {
            B = Class.forName(OmrScoreInterpreter.class.getName() + "$RawCourtesyBudget");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Object budget(long work, long bytes) throws Exception {
        var c = B.getDeclaredConstructor(long.class, long.class);
        c.setAccessible(true);
        return c.newInstance(work, bytes);
    }

    static long used(Object b, String n) throws Exception {
        var f = B.getDeclaredField(n);
        f.setAccessible(true);
        return f.getLong(b);
    }

    static boolean exhausted(Object b) throws Exception {
        var f = B.getDeclaredField("exhausted");
        f.setAccessible(true);
        return f.getBoolean(b);
    }

    static Object part(String n, Object... a) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    static Object raw(Object operation, byte[] gray) throws Exception {
        Object seed = part("Component", 33, 513, 523, 127, 129, 518f, 128f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "rawCourtesyNaturalBody",
                        byte[].class,
                        int.class,
                        int.class,
                        seed.getClass(),
                        float.class,
                        B);
        m.setAccessible(true);
        return m.invoke(null, gray, 600, 250, seed, 16f, operation);
    }

    static OmrScoreInterpreter.Analysis analyze(JoinedSignatureSharpTest f, Object op)
            throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "analyze", byte[].class, byte[].class, int.class, int.class, List.class, B);
        m.setAccessible(true);
        return (OmrScoreInterpreter.Analysis)
                m.invoke(
                        null,
                        f.labels,
                        f.gray,
                        JoinedSignatureSharpTest.W,
                        JoinedSignatureSharpTest.H,
                        f.measures,
                        op);
    }

    static List<Integer> keys(OmrScoreInterpreter.Analysis a) {
        return a.keyChanges().stream().map(ScoreKeyChange::fifths).toList();
    }

    @Test
    public void workExhaustionRefusesBeforePoisonedRasterRead() throws Exception {
        Object b = budget(0, Long.MAX_VALUE);
        assertNull(raw(b, new byte[1]));
        assertTrue(exhausted(b));
        assertEquals(0, used(b, "workUsed"));
        assertEquals(0, used(b, "bytesUsed"));
    }

    @Test
    public void byteExhaustionRefusesBeforePoisonedRasterRead() throws Exception {
        Object b = budget(Long.MAX_VALUE, 0);
        assertNull(raw(b, new byte[1]));
        assertTrue(exhausted(b));
        assertEquals(0, used(b, "workUsed"));
        assertEquals(0, used(b, "bytesUsed"));
    }

    @Test
    public void sameOperationDoesNotResetForNextProof() throws Exception {
        var f = new NaturalCourtesyProofControls();
        f.rect(513, 111, 2, 30, (byte) 0);
        f.rect(522, 124, 2, 30, (byte) 0);
        f.rect(513, 127, 11, 3, (byte) 0);
        f.rect(513, 139, 11, 3, (byte) 0);
        Object first = budget(64_000_000, 16L * 1024 * 1024);
        assertNotNull(raw(first, f.gray));
        long w = used(first, "workUsed"), bytes = used(first, "bytesUsed");
        assertTrue(w > 0);
        assertTrue(bytes > 0);
        Object exact = budget(w, bytes);
        assertNotNull(raw(exact, f.gray));
        assertEquals(w, used(exact, "workUsed"));
        assertEquals(bytes, used(exact, "bytesUsed"));
        assertNull(raw(exact, new byte[1]));
        assertEquals(w, used(exact, "workUsed"));
        assertTrue(exhausted(exact));
    }

    @Test
    public void exhaustedFallbackPreservesSemanticCompleteCancellation() throws Exception {
        var f = CancellationAuthorityTest.fixture(3, false, false, 0, 24, -8);
        Object b = budget(0, 0);
        assertEquals(List.of(3, 0), keys(analyze(f, b)));
    }

    @Test
    public void earlyAndFinalUseOneActualPassedOperation() throws Exception {
        var f = CancellationAuthorityTest.fixture(1, false, true, 0);
        Object full = budget(64_000_000, 16L * 1024 * 1024);
        var a = analyze(f, full);
        assertEquals(List.of(1, 0), keys(a));
        long work = used(full, "workUsed"), bytes = used(full, "bytesUsed");
        assertTrue(work > 0 && bytes > 0);
        Object half = budget(work / 2, bytes / 2);
        var refused = analyze(f, half);
        assertEquals(List.of(1), keys(refused));
        assertTrue(exhausted(half));
        assertEquals(a.notes(), refused.notes());
        assertEquals(a.rests(), refused.rests());
        assertTrue(used(half, "workUsed") <= work / 2);
        assertTrue(used(half, "bytesUsed") <= bytes / 2);
    }

    @Test
    public void zeroAndUnknownPriorCannotMintCancellation() throws Exception {
        Object e = part("NaturalCancellation", List.of(3), 0, 1, true);
        var m = e.getClass().getDeclaredMethod("cancels", Integer.class);
        m.setAccessible(true);
        assertEquals(false, m.invoke(e, new Object[] {null}));
        assertEquals(false, m.invoke(e, 0));
        assertEquals(true, m.invoke(e, 1));
    }

    @Test
    public void unknownPriorSpendsNoOptionalRawWork() throws Exception {
        var f = CancellationAuthorityTest.fixture(0, false, true, 0);
        Object b = budget(64_000_000, 16L * 1024 * 1024);
        assertEquals(List.of(), keys(analyze(f, b)));
        assertEquals(0, used(b, "workUsed"));
        assertEquals(0, used(b, "bytesUsed"));
        assertFalse(exhausted(b));
    }

    @Test
    public void narrowFragmentCanAdmitWiderBodyAtOuterBarUnion() throws Exception {
        var f = new NaturalCourtesyProofControls();
        for (int y : new int[] {100, 116, 132, 148, 164}) f.rect(30, y, 540, 1, (byte) 0);
        f.rect(489, 100, 2, 65, (byte) 0);
        f.rect(513, 111, 2, 30, (byte) 0);
        f.rect(522, 124, 2, 30, (byte) 0);
        f.rect(513, 127, 11, 3, (byte) 0);
        f.rect(513, 139, 11, 3, (byte) 0);
        for (int y = 127; y <= 129; y++) for (int x = 516; x <= 520; x++) f.labels[y * 600 + x] = 3;
        Object seed = part("Component", 15, 516, 520, 127, 129, 518f, 128f),
                candidate = part("AccidentalCandidate", seed, (byte) 3),
                staff = part("Staff", 100f, 164f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "provedNaturalCourtesyBoundary",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        staff.getClass(),
                        List.class,
                        B);
        m.setAccessible(true);
        float boundary =
                (float)
                        m.invoke(
                                null,
                                f.labels,
                                f.gray,
                                600,
                                250,
                                500f,
                                staff,
                                List.of(candidate),
                                budget(64_000_000, 16L * 1024 * 1024));
        assertEquals(489.5f, boundary, 0);
    }
}
