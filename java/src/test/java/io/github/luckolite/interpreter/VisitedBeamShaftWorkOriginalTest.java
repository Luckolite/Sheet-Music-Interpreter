// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural scanner-stage work accounting; not a synthetic full-score claim. */
public final class VisitedBeamShaftWorkOriginalTest {
    static List<PrintedBeamShafts.Shaft> call(
            PrintedBeamMemoOriginals.Input in,
            boolean all,
            PrintedBeamShafts.Budget budget,
            PrintedBeamShaftPixels.Frame frame)
            throws Exception {
        // The unchanged source can run the exact same original fixture to show the reservation bug.
        String name = all ? "atAllVisited" : "atStrictVisited";
        try {
            var m =
                    PrintedBeamShafts.class.getDeclaredMethod(
                            name,
                            byte[].class,
                            int.class,
                            int.class,
                            float.class,
                            float.class,
                            float.class,
                            PrintedBeamShafts.Budget.class,
                            PrintedBeamShaftPixels.Frame.class);
            m.setAccessible(true);
            try {
                return (List<PrintedBeamShafts.Shaft>)
                        m.invoke(
                                null, in.raw(), in.w(), in.h(), in.hx(), in.hy(), in.gap(), budget,
                                frame);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw e;
            }
        } catch (NoSuchMethodException absent) {
            return all
                    ? PrintedBeamShafts.atAll(
                            in.raw(), in.w(), in.h(), in.hx(), in.hy(), in.gap(), budget, frame)
                    : PrintedBeamShafts.atStrict(
                            in.raw(), in.w(), in.h(), in.hx(), in.hy(), in.gap(), budget, frame);
        }
    }

    static PrintedBeamShaftPixels.Frame frame(PrintedBeamMemoOriginals.Input in, boolean contrast) {
        return new PrintedBeamShaftPixels.Frame(
                new Object(), in.raw(), in.w(), in.h(), 0, n -> {}, contrast);
    }

    static PrintedBeamMemoOriginals.Input blank(float gap) {
        var in = PrintedBeamMemoOriginals.draw(gap, 164, 0, 1);
        Arrays.fill(in.raw(), (byte) 255);
        return in;
    }

    @Test
    public void blankStrictVisitsOnlyTwoWhiteRowsInEachColumn() throws Exception {
        var in = blank(16);
        var b = new PrintedBeamShafts.Budget(8_000_000);
        assertTrue(call(in, false, b, frame(in, false)).isEmpty());
        long expected = 0;
        for (int direction : new int[] {-1, 1}) {
            int edge = Math.round(in.hx() - direction * .6f * in.gap());
            int first = Math.max(1, Math.round(edge - in.gap() * .2f));
            int last = Math.min(in.w() - 2, (int) Math.floor(edge + in.gap() * .2f));
            expected += 2L * (last - first + 1);
        }
        assertEquals(expected, 8_000_000 - PrintedBeamMemoOriginals.remaining(b));
    }

    @Test
    public void blankContrastPaysLookupReadAndStoreBeforeEachVisitedCell() throws Exception {
        var in = blank(16);
        var b = new PrintedBeamShafts.Budget(8_000_000);
        assertTrue(call(in, true, b, frame(in, true)).isEmpty());
        long expected = 0;
        for (int direction : new int[] {-1, 1}) {
            int edge = Math.round(in.hx() - direction * .6f * in.gap());
            int first = Math.max(1, Math.round(edge - in.gap() * .2f));
            int last = Math.min(in.w() - 2, (int) Math.floor(edge + in.gap() * .2f));
            // Each white visit also retains the existing relative-paper value-read debit.
            expected += 8L * (last - first + 1);
        }
        assertEquals(expected, 8_000_000 - PrintedBeamMemoOriginals.remaining(b));
    }

    @Test
    public void unusedLongTailsCannotConsumeTheSharedEightMillionQuota() throws Exception {
        var in = PrintedBeamMemoOriginals.draw(64, 164, 0, 1);
        var b = new PrintedBeamShafts.Budget(8_000_000);
        var f = frame(in, true);
        for (int i = 0; i < 80; i++) {
            assertFalse(call(in, false, b, f).isEmpty());
            assertFalse(call(in, true, b, f).isEmpty());
            assertFalse(b.refused());
        }
        assertTrue(PrintedBeamMemoOriginals.remaining(b) > 0);
    }

    @Test
    public void memoLookupReadOrStoreExhaustionNeverPublishesPartialDirections() throws Exception {
        var in = blank(16);
        for (long n : new long[] {0, 1, 2, 3, 4, 5}) {
            var b = new PrintedBeamShafts.Budget(n);
            assertTrue(call(in, true, b, frame(in, true)).isEmpty());
            assertTrue(b.refused());
        }
    }

    @Test
    public void oneOperationShortRefusesTheWholeTwoDirectionResult() throws Exception {
        var in = PrintedBeamMemoOriginals.both();
        var paid = new PrintedBeamShafts.Budget(8_000_000);
        var answer = call(in, true, paid, frame(in, true));
        assertEquals(2, answer.size());
        long used = 8_000_000 - PrintedBeamMemoOriginals.remaining(paid);
        var shortBudget = new PrintedBeamShafts.Budget(used - 1);
        assertTrue(call(in, true, shortBudget, frame(in, true)).isEmpty());
        assertTrue(shortBudget.refused());
        var exact = new PrintedBeamShafts.Budget(used);
        assertEquals(
                PrintedBeamMemoOriginals.geometry(answer),
                PrintedBeamMemoOriginals.geometry(call(in, true, exact, frame(in, true))));
        assertFalse(exact.refused());
        assertEquals(0, PrintedBeamMemoOriginals.remaining(exact));
    }

    @Test
    public void denseActuallyVisitedWorkStillRefusesUnderRealExhaustion() throws Exception {
        var in = blank(64);
        Arrays.fill(in.raw(), (byte) 0);
        var b = new PrintedBeamShafts.Budget(64);
        assertTrue(call(in, false, b, frame(in, false)).isEmpty());
        assertTrue(b.refused());
    }

    @Test
    public void acceptedLegacyGeometrySavedPixelsAndFrameTruthStayExact() throws Exception {
        int compared = 0;
        for (float gap : new float[] {3, 14.25f, 64})
            for (int shade : new int[] {164, 165, 200, 230})
                for (int style = 0; style < 5; style++)
                    for (int direction : new int[] {-1, 1})
                        for (boolean all : new boolean[] {false, true}) {
                            var in = PrintedBeamMemoOriginals.draw(gap, shade, style, direction);
                            var oldBudget = new PrintedBeamShafts.Budget(8_000_000);
                            var newBudget = new PrintedBeamShafts.Budget(8_000_000);
                            var oldFrame = frame(in, true);
                            var newFrame = frame(in, true);
                            var old =
                                    all
                                            ? PrintedBeamShafts.atAll(
                                                    in.raw(), in.w(), in.h(), in.hx(), in.hy(), gap,
                                                    oldBudget, oldFrame)
                                            : PrintedBeamShafts.atStrict(
                                                    in.raw(), in.w(), in.h(), in.hx(), in.hy(), gap,
                                                    oldBudget, oldFrame);
                            var now = call(in, all, newBudget, newFrame);
                            assertFalse(oldBudget.refused());
                            assertFalse(newBudget.refused());
                            assertEquals(
                                    PrintedBeamMemoOriginals.geometry(old),
                                    PrintedBeamMemoOriginals.geometry(now));
                            for (var shaft : now) assertSame(newFrame, shaft.pixels().frame);
                            compared++;
                        }
        assertEquals(240, compared);
    }

    @Test
    public void foreignFrameCannotSupplyAnySavedCarrier() throws Exception {
        var in = blank(16);
        var foreign = frame(in, true);
        var copy =
                new PrintedBeamMemoOriginals.Input(
                        in.raw().clone(), in.w(), in.h(), in.hx(), in.hy(), in.gap());
        assertThrows(
                PrintedBeamShaftPixels.IdentityFault.class,
                () -> call(copy, true, new PrintedBeamShafts.Budget(8_000_000), foreign));
    }

    @Test
    public void pageAllocationReservationStillPrecedesAnyMemoConstruction() throws Exception {
        var in = blank(16);
        var page = new PrintedBeamMemoOriginals.Page(64);
        var f =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), in.raw(), in.w(), in.h(), 0, page, true);
        var b = new PrintedBeamShafts.Budget(8_000_000);
        assertThrows(PrintedBeamMemoOriginals.PageRefused.class, () -> call(in, true, b, f));
        assertTrue(page.refused);
        assertEquals(64, page.used);
    }

    @Test
    public void queryingDoesNotMutateCallerPixelsOrCacheAcrossInvocations() throws Exception {
        var in = PrintedBeamMemoOriginals.draw(16, 200, 0, 1);
        byte[] before = in.raw().clone();
        assertFalse(
                call(in, true, new PrintedBeamShafts.Budget(8_000_000), frame(in, true)).isEmpty());
        assertArrayEquals(before, in.raw());
        var blank = blank(16);
        assertTrue(
                call(blank, true, new PrintedBeamShafts.Budget(8_000_000), frame(blank, true))
                        .isEmpty());
        assertArrayEquals(before, in.raw());
    }
}
