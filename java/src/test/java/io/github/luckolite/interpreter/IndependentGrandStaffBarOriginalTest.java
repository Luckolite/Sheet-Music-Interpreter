// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired five-rule staffs, complete common bar and nearby independently stemmed ovals. */
public class IndependentGrandStaffBarOriginalTest {
    static final int W = 420, H = 420;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page(String mode) {
            Arrays.fill(g, (byte) 255);
            for (int top : new int[] {80, 210})
                for (int y = top; y <= top + 48; y += 12) rect(20, y, 380, 1, 4);
            rect(20, 79, 1, 181, 1);
            rect(400, 79, 1, 181, 1);
            rect(221, 79, 1, 181, 1);
            if (mode.equals("noSemantic")) for (int y = 79; y <= 259; y++) l[y * W + 221] = 0;
            oval(90, 104, true);
            rect(99, 60, 1, 45, 1);
            oval(320, 234, true);
            rect(329, 190, 1, 45, 1);
            if (!mode.equals("noNeighbor"))
                for (int cy : new int[] {80, 210}) {
                    oval(mode.equals("ownsAxis") ? 212 : 210, cy, true);
                    if (mode.equals("clippedOwnsAxis")) oval(212, cy, false);
                    if (!mode.equals("noOwnShaft")) rect(201, cy, 1, 41, 1);
                }
            if (mode.equals("unlabelledFilledOnAxis")) oval(223, 164, false);
            if (mode.equals("unlabelledHollowOnAxis")) {
                oval(223, 164, false);
                for (int y = 161; y <= 167; y++)
                    for (int x = 218; x <= 227; x++) if (x != 221) g[y * W + x] = (byte) 255;
            }
            if (mode.equals("extendedShaft")) rect(221, 55, 1, 24, 1);
            if (mode.equals("detachedOwnShaft"))
                for (int cy : new int[] {80, 210})
                    for (int y = cy + 5; y <= cy + 9; y++) {
                        g[y * W + 201] = (byte) 255;
                        l[y * W + 201] = 0;
                    }
            if (mode.equals("brokenAxis"))
                for (int y = 165; y <= 166; y++) {
                    g[y * W + 221] = (byte) 255;
                    l[y * W + 221] = 0;
                }
            if (mode.equals("singleStaff"))
                for (int y = 205; y <= 265; y++)
                    for (int x = 20; x <= 400; x++) {
                        g[y * W + x] = (byte) 255;
                        l[y * W + x] = 0;
                    }
        }

        void rect(int x, int y, int w, int h, int k) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    g[yy * W + xx] = 0;
                    if (l[yy * W + xx] != 2) l[yy * W + xx] = (byte) k;
                }
        }

        void oval(int cx, int cy, boolean semantic) {
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 9; x <= cx + 9; x++)
                    if (Math.pow((x - cx) / 9., 2) + Math.pow((y - cy + .3 * (x - cx)) / 6., 2)
                            <= 1) {
                        g[y * W + x] = 0;
                        if (semantic) l[y * W + x] = 2;
                    }
        }

        List<MeasureRegion> result() {
            return OmrMeasurePostProcessor.process(l, g, W, H);
        }
    }

    @Test
    public void independentGrandStaffBarSurvivesTwoNearbyOpposedShaftHeads() {
        assertEquals(2, new Page("valid").result().size());
    }

    @Test
    public void actualHeadOnAxisStillOwnsItsStem() {
        assertEquals(1, new Page("ownsAxis").result().size());
    }

    @Test
    public void clippedModelCannotDisownItsActualRawHeadOnAxis() {
        assertEquals(1, new Page("clippedOwnsAxis").result().size());
    }

    @Test
    public void missingIndependentOwnShaftCannotDisownLongVertical() {
        assertEquals(1, new Page("noOwnShaft").result().size());
    }

    @Test
    public void brokenTwoStaffAxisCannotBeRecovered() {
        assertEquals(1, new Page("brokenAxis").result().size());
    }

    @Test
    public void rawOnlyVerticalDoesNotCreateNewSemanticBar() {
        assertEquals(1, new Page("noSemantic").result().size());
    }

    @Test
    public void ordinaryUncontestedBarRemains() {
        assertEquals(2, new Page("noNeighbor").result().size());
    }

    @Test
    public void singleStaffCannotAcquireGrandStaffException() {
        assertEquals(1, new Page("singleStaff").result().size());
    }

    @Test
    public void sourcePlanesRemainUnchanged() {
        var p = new Page("valid");
        var g = p.g.clone();
        var l = p.l.clone();
        p.result();
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }

    @Test
    public void unlabelledFilledBulgeStillOwnsAxis() {
        assertEquals(1, new Page("unlabelledFilledOnAxis").result().size());
    }

    @Test
    public void unlabelledHollowBulgeStillOwnsAxis() {
        assertEquals(1, new Page("unlabelledHollowOnAxis").result().size());
    }

    @Test
    public void extendingLongStemCannotBorrowGrandStaffFrame() {
        assertEquals(1, new Page("extendedShaft").result().size());
    }

    @Test
    public void disconnectedOwnShaftCannotAuthenticateNeighbor() {
        assertEquals(1, new Page("detachedOwnShaft").result().size());
    }

    static void declinedRecoveryKeepsOrdinaryBoundaries(long operations, long bytes)
            throws Exception {
        Page page = new Page("valid");
        Class<?> owner = OmrMeasurePostProcessor.class;
        var find =
                owner.getDeclaredMethod(
                        "findStaffs", byte[].class, byte[].class, int.class, int.class);
        find.setAccessible(true);
        var merge =
                owner.getDeclaredMethod(
                        "mergeAlignedStaffs",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class);
        merge.setAccessible(true);
        var systems =
                (List<?>)
                        merge.invoke(
                                null,
                                find.invoke(null, page.l, page.g, W, H),
                                page.l,
                                page.g,
                                W,
                                H);
        Class<?> budget = Class.forName(owner.getName() + "$GrandBarWork");
        var ctor = budget.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        Object cap = ctor.newInstance(operations, bytes);
        var boundaries = systems.get(0).getClass().getDeclaredField("boundaries");
        boundaries.setAccessible(true);
        var before = new ArrayList<>((List<?>) boundaries.get(systems.get(0)));
        var recover =
                owner.getDeclaredMethod(
                        "recoverIndependentGrandStaffBars",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        budget);
        recover.setAccessible(true);
        recover.invoke(null, systems, page.l, page.g, W, H, cap);
        assertEquals(before, boundaries.get(systems.get(0)));
        assertEquals(2, before.size());
    }

    @Test
    public void zeroWorkDeclineRetainsOrdinaryBoundaries() throws Exception {
        declinedRecoveryKeepsOrdinaryBoundaries(0, 8000000);
    }

    @Test
    public void zeroStorageDeclineRetainsOrdinaryBoundaries() throws Exception {
        declinedRecoveryKeepsOrdinaryBoundaries(128000000, 0);
    }

    @Test
    public void insufficientFloodStorageDeclineRetainsOrdinaryBoundaries() throws Exception {
        declinedRecoveryKeepsOrdinaryBoundaries(128000000, 1024);
    }

    @Test
    public void ledgerFailureDoesNotDebitRemainingAllowance() throws Exception {
        Class<?> cap = Class.forName(OmrMeasurePostProcessor.class.getName() + "$GrandBarWork");
        var ctor = cap.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        Object work = ctor.newInstance(64L, 4096L);
        var pay = cap.getDeclaredMethod("pay", long.class, long.class);
        pay.setAccessible(true);
        assertEquals(false, pay.invoke(work, 65L, 4096L));
        assertEquals(true, pay.invoke(work, 64L, 4096L));
        assertEquals(false, pay.invoke(work, 1L, 0L));
    }
}
