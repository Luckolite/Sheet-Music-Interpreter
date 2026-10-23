// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.Method;
import java.util.stream.IntStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Instrumented head-access and legacy-equivalence controls for the linear preflight. */
public class AttachedApproachWorkBoundOriginalsTest {
    private static final int W = 200, H = 400, N = 32;

    private static final class CountedHeads extends AbstractList<NoteSlideDetector.Head> {
        final int count;
        long gets;

        CountedHeads(int count) {
            this.count = count;
        }

        public int size() {
            return count;
        }

        public NoteSlideDetector.Head get(int i) {
            if (i < 0 || i >= count) throw new IndexOutOfBoundsException();
            gets++;
            return new NoteSlideDetector.Head(115, 60, 64, 0, 0);
        }
    }

    private static int[] component() {
        int[] a = new int[N];
        int i = 0;
        for (int y = 110; y < 118; y++) for (int x = 50; x < 54; x++) a[i++] = y * W + x;
        return a;
    }

    private static List<Integer> indices(int owners) {
        return IntStream.range(0, owners).boxed().toList();
    }

    private static long measuredEnvelope(int pixels, int owners) {
        long n = 0;
        for (int p = 0; p < pixels; p++) {
            n++;
            for (int h = 0; h < owners; h++) n++;
        }
        for (int h = 0; h < owners; h++) {
            n++;
            for (int p = 0; p < pixels; p++) n++;
        }
        return n;
    }

    private static boolean preflight(
            int[] pixels, List<NoteSlideDetector.Head> heads, List<Integer> local)
            throws Exception {
        Method m =
                AttachedApproachBranches.class.getDeclaredMethod(
                        "possibleFreeApproach",
                        int[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, pixels, pixels.length, W, heads, local);
    }

    private static List<AttachedApproachBranches.Part> run(
            CountedHeads heads, AttachedApproachBranches.Budget budget) {
        int[] pixels = component();
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        for (int p : pixels) raw[p] = 0;
        return AttachedApproachBranches.find(
                raw,
                W,
                H,
                pixels,
                pixels.length,
                List.of(new NoteSlideDetector.Staff(40, 296, 64)),
                heads,
                new HashMap<>(),
                budget);
    }

    // Previous nested algorithm retained as the behavior reference, not the new cached loop.
    private static boolean legacy(
            int[] pixels, List<NoteSlideDetector.Head> heads, List<Integer> local) {
        for (int owner : local) {
            var target = heads.get(owner);
            float gap = target.gap();
            int free = 0, left = W, right = 0;
            for (int at : pixels) {
                int x = at % W, y = at / W;
                if (x > target.x() - gap * .65f
                        || x < target.x() - gap * 3.5f
                        || y < target.y() - gap * 2.5f
                        || y > target.y() + gap * 2.5f) continue;
                boolean blocked = false;
                for (int h : local) {
                    var n = heads.get(h);
                    float g = n.gap();
                    if (Math.abs(x - n.x()) <= g * .85f && Math.abs(y - n.y()) <= g * .65f
                            || Math.abs(x - (n.x() - g * .6f)) <= g * .2f
                            || Math.abs(x - (n.x() + g * .6f)) <= g * .2f) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) {
                    free++;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (free >= Math.max(8, Math.ceil(gap * .8f)) && right - left >= gap * .65f)
                return true;
        }
        return false;
    }

    @Test
    public void maximumAndInvalidDimensionsCannotUnderchargeOrOverflow() {
        assertEquals(1_064_992L, AttachedApproachBranches.freeApproachWorkBound(16384, 32));
        for (int[] c :
                new int[][] {
                    {-1, 1}, {16385, 32}, {32, 0}, {32, 33}, {Integer.MAX_VALUE, Integer.MAX_VALUE}
                })
            assertEquals(
                    Long.MAX_VALUE, AttachedApproachBranches.freeApproachWorkBound(c[0], c[1]));
    }

    @Test
    public void actualHeadAccessesAreLinearRatherThanQuadratic() throws Exception {
        var old = new CountedHeads(32);
        var current = new CountedHeads(32);
        assertFalse(legacy(component(), old, indices(32)));
        assertFalse(preflight(component(), current, indices(32)));
        assertEquals(32L + N * 32L * 32, old.gets);
        assertEquals(32L + N * 32L, current.gets);
        assertTrue(current.gets < old.gets / 16);
    }

    @Test
    public void singletonAndEmptyEnvelopesCountEveryLoopVisit() {
        for (int[] c : new int[][] {{0, 32}, {1, 1}, {32, 1}, {37, 8}})
            assertEquals(
                    measuredEnvelope(c[0], c[1]),
                    AttachedApproachBranches.freeApproachWorkBound(c[0], c[1]));
    }

    @Test
    public void originalFiniteGeometriesMatchPreviousPredicate() throws Exception {
        var random = new Random(116115);
        int positives = 0, negatives = 0;
        for (int sample = 0; sample < 160; sample++) {
            int owners = 1 + sample % 32;
            var heads = new ArrayList<NoteSlideDetector.Head>();
            for (int i = 0; i < owners; i++)
                heads.add(
                        new NoteSlideDetector.Head(
                                30 + random.nextInt(150),
                                30 + random.nextInt(100),
                                6 + random.nextInt(59),
                                0,
                                0));
            int[] pixels = new int[16 + sample % 97];
            for (int i = 0; i < pixels.length; i++)
                pixels[i] = random.nextInt(160) * W + random.nextInt(W);
            boolean expected = legacy(pixels, heads, indices(owners));
            assertEquals(expected, preflight(pixels, heads, indices(owners)));
            if (expected) positives++;
            else negatives++;
        }
        assertTrue(positives > 0);
        assertTrue(negatives > 0);
    }

    @Test
    public void underfundedPreflightRefusesBeforeNestedHeadScan() {
        var heads = new CountedHeads(32);
        var b = new AttachedApproachBranches.Budget(measuredEnvelope(N, 32) - 1);
        assertTrue(run(heads, b).isEmpty());
        assertTrue("Preflight must reject before nested head scan", b.refused);
        assertEquals(32, heads.gets);
    }

    @Test
    public void exactBudgetPaysLinearEnvelope() {
        var heads = new CountedHeads(32);
        var b = new AttachedApproachBranches.Budget(measuredEnvelope(N, 32));
        assertTrue(run(heads, b).isEmpty());
        assertFalse(b.refused);
        assertEquals(0, b.left);
        assertEquals(64L + N * 32L, heads.gets);
    }

    @Test
    public void positivePreflightFitsExactBound() throws Exception {
        int[] pixels = new int[88];
        int i = 0;
        for (int y = 110; y < 112; y++) for (int x = 20; x < 64; x++) pixels[i++] = y * W + x;
        var heads = new CountedHeads(32);
        var b = new AttachedApproachBranches.Budget(measuredEnvelope(88, 32));
        assertTrue(b.take(AttachedApproachBranches.freeApproachWorkBound(88, 32)));
        assertTrue(preflight(pixels, heads, indices(32)));
        assertEquals(0, b.left);
    }

    @Test
    public void ownerPermutationPreservesResult() throws Exception {
        var heads =
                List.of(
                        new NoteSlideDetector.Head(115, 60, 64, 0, 0),
                        new NoteSlideDetector.Head(165, 100, 14, 0, 0),
                        new NoteSlideDetector.Head(80, 80, 24, 0, 0));
        for (int shift = 0; shift < 12; shift++) {
            int[] pixels = component();
            for (int i = 0; i < pixels.length; i++) pixels[i] += shift;
            boolean expected = preflight(pixels, heads, List.of(0, 1, 2));
            assertEquals(expected, preflight(pixels, heads, List.of(2, 0, 1)));
            assertEquals(expected, legacy(pixels, heads, List.of(1, 2, 0)));
        }
    }
}
