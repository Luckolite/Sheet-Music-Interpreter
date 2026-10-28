// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class CompleteFaintHairpinArmsTest {
    static final int W = 480, H = 260;
    static final float GAP = 14;

    static byte[] draw(int tone, boolean reverse, float phase, String mode) {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int x = 100; x <= 212; x++) {
            int xx = reverse ? 312 - x : x;
            int a = (int) Math.round(170 - .085 * (x - 100) + phase),
                    b = (int) Math.round(170 + .085 * (x - 100) + phase);
            if (mode.equals("parallel")) {
                a = 160;
                b = 178;
            }
            for (int y = a - 1; y <= a + 1; y++) r[y * W + xx] = (byte) tone;
            for (int y = b - 1; y <= b + 1; y++)
                r[y * W + xx] = (byte) (mode.equals("oneArm") ? 238 : tone);
            if (mode.equals("solid")) for (int y = a; y <= b; y++) r[y * W + xx] = (byte) tone;
        }
        if (mode.equals("gap")) for (int y = 140; y < 200; y++) r[y * W + 143] = (byte) 255;
        if (mode.equals("III")) {
            for (int x : new int[] {92, 100, 108})
                for (int y = 151; y <= 188; y++)
                    for (int d = -1; d <= 1; d++) r[y * W + x + d] = 80;
            for (int x = 89; x <= 112; x++) for (int y : new int[] {151, 188}) r[y * W + x] = 80;
        }
        return r;
    }

    static List<ScoreDynamicChange> detect(byte[] r) {
        return ScoreDynamicsDetector.detect(
                List.of(),
                List.of(new PlayingTechniqueDetector.Staff(70, 126, GAP, 0, 1)),
                List.of(new MeasureRegion(.08f, .92f, .2f, .55f)),
                List.of(),
                r,
                W,
                H);
    }

    static byte[] page(String mode, boolean reverse, float phase) {
        byte[] r = draw(210, reverse, phase, mode);
        for (int x = 175; x <= 198; x++) {
            int xx = reverse ? 312 - x : x;
            int y = (int) Math.round(170 - .085 * (x - 100) + phase);
            for (int d = -1; d <= 1; d++) r[(y + d) * 480 + xx] = 80;
        }
        if (mode.equals("gap")) for (int y = 140; y < 200; y++) r[y * 480 + 143] = (byte) 255;
        return r;
    }

    @Test
    public void oneRawFragmentCanSeedIndependentlyCompleteFaintArms() {
        var a = detect(page("normal", false, 0));
        assertEquals(a.toString(), 1, a.size());
        assertEquals(1, a.get(0).direction());
    }

    @Test
    public void mirrorsAndPhasesRetainTheirActualDirection() {
        for (boolean reverse : new boolean[] {false, true})
            for (float phase : new float[] {0, .25f, .5f, .75f}) {
                var a = detect(page("normal", reverse, phase));
                assertEquals(a.toString(), 1, a.size());
                assertEquals(reverse ? -1 : 1, a.get(0).direction());
            }
    }

    @Test
    public void aWhiteColumnIsNeverFilled() {
        assertTrue(detect(page("gap", false, 0)).isEmpty());
    }

    @Test
    public void filledWedgeCannotBorrowAnInterior() {
        assertTrue(detect(page("solid", false, 0)).isEmpty());
    }

    @Test
    public void missingSecondArmRefuses() {
        assertTrue(detect(page("oneArm", false, 0)).isEmpty());
    }

    @Test
    public void joinedRomanInkIsRetained() {
        assertTrue(detect(page("III", false, 0)).isEmpty());
    }

    @Test
    public void entirelyFaintWithoutOriginalSeedRemainsUnproposed() {
        assertTrue(detect(draw(210, false, 0, "normal")).isEmpty());
    }

    static void seed(CompleteFaintHairpinArms.Page p, byte[] r) {
        seed(p, r, new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1));
    }

    static void seed(
            CompleteFaintHairpinArms.Page p, byte[] r, PlayingTechniqueDetector.Staff staff) {
        int[] q = new int[72];
        int n = 0, top = 260, bottom = 0;
        for (int y = 140; y < 200; y++)
            for (int x = 175; x <= 198; x++)
                if ((r[y * 480 + x] & 255) < 145) {
                    q[n++] = y * 480 + x;
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
        p.candidate(staff, q, n, 175, 198, top, bottom);
    }

    @Test
    public void failedOptionalFundingKeepsNoPartialSet() {
        byte[] r = page("normal", false, 0);
        var staff = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1);
        var p = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(staff));
        p.work = 0;
        seed(p, r);
        assertTrue(p.finish().isEmpty());
        assertTrue(p.failed);
    }

    @Test
    public void exactAndOneUnderWholeOptionalFunding() {
        byte[] r = page("normal", false, 0);
        var staff = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1);
        var p = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(staff));
        seed(p, r);
        assertEquals(1, p.finish().size());
        long used = 2_000_000 - p.work, bytes = 1_000_000 - p.bytes;
        var exact = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(staff));
        exact.work = used - 36;
        exact.bytes = bytes - 256;
        seed(exact, r);
        assertEquals(1, exact.finish().size());
        var under = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(staff));
        under.work = used - 37;
        under.bytes = bytes - 256;
        seed(under, r);
        assertTrue(under.finish().isEmpty());
        assertTrue(under.failed);
    }

    @Test
    public void brightGuardMatchesExistingPrimitiveForEveryCenterAndPaperTone() throws Exception {
        byte[] raw = new byte[11 * 32];
        var p =
                new CompleteFaintHairpinArms.Page(
                        raw, 11, 32, List.of(new PlayingTechniqueDetector.Staff(0, 28, 14, 0, 1)));
        var section =
                PrintedBeamRawContrast.class.getDeclaredMethod(
                        "enclosingSection",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        double.class,
                        boolean.class,
                        boolean.class,
                        java.util.function.LongConsumer.class);
        section.setAccessible(true);
        java.util.function.LongConsumer charge = n -> {};
        for (int value = 145; value <= 255; value++)
            for (int paper = 0; paper <= 255; paper++) {
                Arrays.fill(raw, (byte) paper);
                raw[16 * 11 + 5] = (byte) value;
                p.work = 2000;
                boolean expected =
                        (Boolean)
                                section.invoke(
                                        null, raw, 11, 32, 5, 16, 11, 14d, false, false, charge);
                assertEquals(value + "/" + paper, expected, p.foreground(5, 16, 14));
                if (value > 225) assertEquals(1999, p.work);
            }
    }

    @Test
    public void inputLinearAllowanceHasSafeOptionalCapacity() {
        var staff = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1);
        var at =
                new CompleteFaintHairpinArms.Page(new byte[20_000_000], 5000, 4000, List.of(staff));
        assertFalse(at.failed);
        assertEquals(40_000_000, at.workLimit);
        assertNull(at.stamp);
        var above =
                new CompleteFaintHairpinArms.Page(
                        new byte[20_000_001], 6_666_667, 3, List.of(staff));
        assertTrue(above.failed);
        assertTrue(above.finish().isEmpty());
        assertNull(above.stamp);
        var overflow =
                new CompleteFaintHairpinArms.Page(
                        new byte[9], Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(staff));
        assertTrue(overflow.failed);
        assertTrue(overflow.finish().isEmpty());
    }

    @Test
    public void optionalStorageFailureOccursBeforeWorkspaceAllocation() {
        byte[] r = page("normal", false, 0);
        var p =
                new CompleteFaintHairpinArms.Page(
                        r,
                        480,
                        260,
                        List.of(new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1)));
        p.bytes = 0;
        seed(p, r);
        assertTrue(p.failed);
        assertNull(p.stamp);
        assertNull(p.queue);
        assertTrue(p.finish().isEmpty());
    }

    @Test
    public void lateFaintExtensionCannotReplaceExistingFaintEndpoint() {
        for (float phase : new float[] {0, .25f, .5f, .75f}) {
            byte[] r = new byte[480 * 260];
            Arrays.fill(r, (byte) 255);
            for (int x = 100; x <= 224; x++) {
                int a = (int) Math.round(170 - .085 * (x - 100) + phase),
                        b = (int) Math.round(170 + .085 * (x - 100) + phase),
                        ink = x >= 176 && x <= 212 ? 80 : 210;
                for (int y = a - 1; y <= a + 1; y++) r[y * 480 + x] = (byte) ink;
                for (int y = b - 1; y <= b + 1; y++) r[y * 480 + x] = (byte) ink;
            }
            var result = detect(r);
            assertEquals(result.toString(), 1, result.size());
            assertEquals(
                    (212 / 480f - .08f) / (.92f - .08f), result.get(0).endPosition(), .000001f);
        }
    }

    @Test
    public void parallelFaintArmsDoNotAcquireDirection() {
        byte[] r = draw(210, false, 0, "parallel");
        for (int x = 175; x <= 198; x++) for (int y = 159; y <= 161; y++) r[y * 480 + x] = 80;
        assertTrue(detect(r).isEmpty());
    }

    @Test
    public void aThirdLiteralArmIsNotErased() {
        byte[] r = page("normal", false, 0);
        for (int x = 100; x <= 212; x++) r[170 * 480 + x] = (byte) 210;
        assertTrue(detect(r).isEmpty());
    }

    @Test
    public void unequalLastArmEndpointsAreNotClipped() {
        byte[] r = page("normal", false, 0);
        int y = (int) Math.round(170 - .085 * 112);
        for (int d = -1; d <= 1; d++) r[(y + d) * 480 + 212] = (byte) 255;
        assertTrue(detect(r).isEmpty());
    }

    @Test
    public void completedComponentMembershipDoesNotCrossStaffOwners() {
        byte[] r = page("normal", false, 0);
        var a = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 2);
        var b = new PlayingTechniqueDetector.Staff(70, 126, 14, 1, 2);
        var p = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(a, b));
        seed(p, r, a);
        seed(p, r, b);
        var shapes = p.finish();
        assertEquals(2, shapes.size());
        assertEquals(a, shapes.get(0).staff());
        assertEquals(b, shapes.get(1).staff());
    }

    @Test
    public void completedComponentMembershipDoesNotCrossGapFrames() {
        byte[] r = page("normal", false, 0);
        var a = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1);
        var b = new PlayingTechniqueDetector.Staff(70, 127, 14.25f, 0, 1);
        var p = new CompleteFaintHairpinArms.Page(r, 480, 260, List.of(a, b));
        seed(p, r, a);
        seed(p, r, b);
        var shapes = p.finish();
        assertEquals(2, shapes.size());
        assertEquals(a, shapes.get(0).staff());
        assertEquals(b, shapes.get(1).staff());
    }
}
