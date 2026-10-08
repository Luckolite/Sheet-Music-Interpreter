// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original blurred dots on shaded paper; printed curves, ovals and rests retain ownership. */
public final class ShadedSustainedDotTest {
    static final int W = 180, H = 180, X = 80, Y = 80, G = 12, L = 73, R = 87;

    byte[] page(boolean shaft, boolean hollow, float slope) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 122);
        for (int y = 74; y <= 86; y++)
            for (int x = L; x <= R; x++) {
                double e = Math.pow((x - X) / 7., 2) + Math.pow((y - Y) / 5., 2);
                if (e <= 1 && (!hollow || e > .5)) p[y * W + x] = 42;
            }
        if (shaft)
            for (int n = 0; n <= 38; n++) {
                int x = Math.round(L - slope * n);
                for (int q = -1; q <= 1; q++) p[(Y + n) * W + x + q] = 42;
            }
        return p;
    }

    void dot(byte[] p, int x, int y, int tone) {
        for (int yy = y - 2; yy <= y + 2; yy++)
            for (int xx = x - 2; xx <= x + 2; xx++)
                if ((xx - x) * (xx - x) + (yy - y) * (yy - y) <= 4) p[yy * W + xx] = (byte) tone;
    }

    Object component(int l, int r, int t, int b, float x, float y) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> k = c.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return k.newInstance(120, l, r, t, b, x, y);
    }

    int count(byte[] p, boolean hollow, float slope, List<Object> excluded, List<Object> neighbors)
            throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        c,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class,
                        float.class);
        m.setAccessible(true);
        return (int)
                m.invoke(
                        null,
                        List.of(),
                        component(L, R, 74, 86, X, Y),
                        12f,
                        p,
                        W,
                        H,
                        hollow,
                        excluded,
                        neighbors,
                        slope);
    }

    int count(byte[] p, boolean hollow, float slope) throws Exception {
        return count(p, hollow, slope, List.of(), List.of());
    }

    int tracked(byte[] p, StaffPitchTrack track) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        c,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class,
                        float.class,
                        StaffPitchTrack.class);
        m.setAccessible(true);
        return (int)
                m.invoke(
                        null,
                        List.of(),
                        component(L, R, 74, 86, X, Y),
                        12f,
                        p,
                        W,
                        H,
                        true,
                        List.of(),
                        List.of(),
                        0f,
                        track);
    }

    StaffPitchTrack verified() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {10, 136, 12}, new float[] {170, 136, 12}));
    }

    byte[] ruleDot(boolean thick, boolean allRules) {
        byte[] p = page(true, true, 0);
        for (int x = 10; x <= 170; x++)
            for (int rail = 0; rail < (allRules ? 5 : 1); rail++)
                for (int d = -2; d <= 2; d++) p[(88 + rail * 12 + d) * W + x] = 87;
        for (int y = 80; y <= 118; y++) for (int x = L - 1; x <= L + 1; x++) p[y * W + x] = 42;
        for (int yy = 77; yy <= 83; yy++)
            for (int xx = 99; xx <= 105; xx++)
                if ((xx - 102) * (xx - 102) + (yy - 80) * (yy - 80) <= 9) p[yy * W + xx] = 85;
        for (int y = 84; y <= 85; y++) p[y * W + 102] = 95;
        if (thick) for (int x = 88; x <= 130; x++) for (int y = 84; y <= 92; y++) p[y * W + x] = 87;
        return p;
    }

    @Test
    public void printedDotOnDarkPaperRequiresProvedHalfAndShaft() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, true, Float.NaN));
        assertEquals(1, count(p, true, 0));
    }

    @Test
    public void twoSeparateDotsRemainDoubleDot() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 100, 80, 85);
        dot(p, 110, 80, 85);
        assertEquals(2, count(p, true, 0));
    }

    @Test
    public void trueLineToSpaceEngravingOffsetIsRetained() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 74, 85);
        assertEquals(1, count(p, true, 0));
    }

    @Test
    public void inclinedHalfRetainsSameDotSlot() throws Exception {
        byte[] p = page(true, true, .12f);
        dot(p, 102, 80, 85);
        assertEquals(1, count(p, true, .12f));
    }

    @Test
    public void paperGrainCannotCreateDurationDot() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 80, 110);
        assertEquals(0, count(p, true, 0));
    }

    @Test
    public void stemlessWholeDoesNotEnterNewSustainedPath() throws Exception {
        byte[] p = page(false, true, 0);
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, true, 0));
    }

    @Test
    public void filledHeadDoesNotEnterNewSustainedPath() throws Exception {
        byte[] p = page(true, false, 0);
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, false, 0));
    }

    @Test
    public void aboveHeadMarkDoesNotLengthenNote() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 65, 85);
        assertEquals(0, count(p, true, 0));
    }

    @Test
    public void farNotationDoesNotLengthenNote() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 119, 80, 85);
        assertEquals(0, count(p, true, 0));
    }

    @Test
    public void completeFadedCurveOwnsItsDarkerIsland() throws Exception {
        byte[] p = page(true, true, 0);
        for (int x = 88; x <= 125; x++)
            p[(80 + Math.round((float) Math.pow((x - 102) / 15., 2))) * W + x] = 95;
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, true, 0));
    }

    @Test
    public void realDotBesideSeparateFadedCurveRemains() throws Exception {
        byte[] p = page(true, true, 0);
        for (int x = 89; x <= 125; x++) p[87 * W + x] = 95;
        dot(p, 102, 80, 85);
        assertEquals(1, count(p, true, 0));
    }

    @Test
    public void completeNeighborOvalOwnsRawBody() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, true, 0, List.of(), List.of(component(99, 106, 77, 84, 102, 80))));
    }

    @Test
    public void accidentalBodyKeepsOwnership() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 80, 85);
        assertEquals(0, count(p, true, 0, List.of(component(99, 106, 77, 84, 102, 80)), List.of()));
    }

    @Test
    public void nextHeadStaccatoStillBelongsToNextAttack() throws Exception {
        byte[] p = page(true, true, 0);
        dot(p, 102, 86, 85);
        assertEquals(0, count(p, true, 0, List.of(), List.of(component(98, 107, 74, 83, 102, 77))));
    }

    @Test
    public void grayAndCandidateOwnershipRemainUnchanged() throws Exception {
        byte[] p = page(true, true, 0), copy = p.clone();
        dot(p, 102, 80, 85);
        copy = p.clone();
        count(p, true, 0);
        assertArrayEquals(copy, p);
    }

    @Test
    public void completeThinRulesDoNotStealPrintedDurationDot() throws Exception {
        byte[] p = ruleDot(false, true);
        assertTrue(
                "independent raw thin rail",
                PrintedRuleInkOwnership.matchesWithNarrowReference(
                        p, W, H, 115, 87, 89, 88, 12, 0, verified()));
        assertEquals("unverified owner sees connected line", 0, tracked(p, null));
        assertEquals("verified rules retain the dot", 1, tracked(p, verified()));
    }

    @Test
    public void thickCurveOnRulePhaseRetainsItsInkOwner() throws Exception {
        assertEquals(0, tracked(ruleDot(true, true), verified()));
    }

    @Test
    public void numericFrameWithoutActualRulesCannotEraseCurve() throws Exception {
        assertEquals(0, tracked(ruleDot(true, false), verified()));
    }

    @Test
    public void verifiedRuleTrackAndPixelsRemainUnchanged() throws Exception {
        byte[] p = ruleDot(false, true), copy = p.clone();
        StaffPitchTrack t = verified();
        float[] before = t.at(100);
        tracked(p, t);
        assertArrayEquals(copy, p);
        assertArrayEquals(before, t.at(100), 0);
    }
}
