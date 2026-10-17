// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original analytic rasters; no score pixels, traced outlines, or source coordinates. */
public class CorroboratedFaintBeamTest {
    static final int W = 300, H = 240;
    static final float GAP = 14;

    static final class Page {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final int[] own = {80, 147, 1}, a = {132, 150, 1}, b = {182, 153, 1};

        Page() {
            Arrays.fill(g, (byte) 250);
        }

        void pixel(int x, int y, int ink, int label) {
            if (x >= 0 && x < W && y >= 0 && y < H) {
                g[y * W + x] = (byte) ink;
                l[y * W + x] = (byte) label;
            }
        }

        void oval(int cx, int cy, boolean hollow) {
            float rx = GAP * .69f, ry = GAP * .4f;
            for (int dy = -12; dy <= 12; dy++)
                for (int dx = -13; dx <= 13; dx++) {
                    float u = dx * dx / (rx * rx) + (dy + .4f * dx) * (dy + .4f * dx) / (ry * ry);
                    if (u <= 1) pixel(cx + dx, cy + dy, 80, 2);
                    if (hollow
                            && dx * dx / (5.8f * 5.8f)
                                            + (dy + .4f * dx) * (dy + .4f * dx) / (2.8f * 2.8f)
                                    <= 1) pixel(cx + dx, cy + dy, 250, 0);
                }
        }

        void stem(int x, int top, int bottom) {
            for (int y = top; y <= bottom; y++)
                for (int xx = x; xx <= x + 1; xx++) pixel(xx, y, y > 130 && x == 80 ? 215 : 80, 1);
        }

        void rail(int x1, int y1, int x2, int y2, int ink, int thick) {
            for (int x = x1; x <= x2; x++) {
                int y = Math.round(y1 + (y2 - y1) * (x - x1) / (float) (x2 - x1));
                for (int yy = y - thick + 1; yy <= y; yy++) pixel(x, yy, ink, 5);
            }
        }

        void clearLeftRail() {
            for (int x = 82; x <= 131; x++) for (int y = 139; y <= 153; y++) pixel(x, y, 250, 0);
        }
    }

    static Page page(boolean hollow) {
        Page p = new Page();
        for (int y = 95; y <= 151; y += 14) for (int x = 20; x < 230; x++) p.pixel(x, y, 160, 4);
        p.oval(90, 70, hollow);
        p.oval(142, 84, false);
        p.oval(192, 105, false);
        p.stem(80, 70, 147);
        p.stem(132, 84, 150);
        p.stem(182, 105, 153);
        p.rail(80, 147, 132, 150, 215, 7);
        p.rail(132, 150, 182, 153, 80, 7);
        return p;
    }

    static SymbolStemHeadRecovery.Head head() {
        return new SymbolStemHeadRecovery.Head(80, 61, 100, 75, 160, 90, 68);
    }

    static int[] pale(Page p, SymbolStemHeadRecovery.Head head) throws Exception {
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component"),
                s = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
        var hc = c.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        Object h =
                hc.newInstance(
                        head.area(),
                        head.left(),
                        head.right(),
                        head.top(),
                        head.bottom(),
                        head.x(),
                        head.y());
        var sc = s.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(95f, 151f, GAP);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "paleStemEndpoint", byte[].class, byte[].class, int.class, int.class, c, s);
        m.setAccessible(true);
        return (int[]) m.invoke(null, p.l, p.g, W, H, h, staff);
    }

    static boolean recover(Page p) throws Exception {
        var h = CorroboratedFaintBeam.refine(p.g, W, H, head(), GAP);
        if (h == null) return false;
        int[] own = pale(p, h);
        return CorroboratedFaintBeam.connects(p.l, p.g, W, H, own, p.a, p.b, GAP);
    }

    @Test
    public void fadedExtensionWithCoarseHeadPhaseIsRecovered() throws Exception {
        Page p = page(false);
        assertFalse(FilledSparseBeamHead.matches(p.g, W, H, 90, 68, GAP));
        var h = CorroboratedFaintBeam.refine(p.g, W, H, head(), GAP);
        assertNotNull(h);
        assertEquals(70f, h.y(), 0);
        assertNotNull(pale(p, h));
        assertTrue(PrintedSingleBeamPath.connects(p.g, W, H, p.a, p.b, GAP));
        assertFalse(PrintedSingleBeamPath.connects(p.g, W, H, p.own, p.a, GAP));
        assertTrue(recover(p));
    }

    @Test
    public void solidBeamIsPreserved() throws Exception {
        Page p = page(false);
        p.rail(80, 147, 132, 150, 80, 7);
        assertTrue(PrintedSingleBeamPath.connects(p.g, W, H, p.own, p.a, GAP));
        assertTrue(recover(p));
    }

    @Test
    public void hollowHalfCounterCannotBeRefinedIntoFilledHead() throws Exception {
        Page p = page(true);
        assertNull(CorroboratedFaintBeam.refine(p.g, W, H, head(), GAP));
        assertFalse(recover(p));
    }

    @Test
    public void bareQuarterCannotBorrowLowerBeam() throws Exception {
        Page p = page(false);
        for (int y = 117; y <= 147; y++) for (int x = 79; x <= 81; x++) p.pixel(x, y, 250, 0);
        assertFalse(recover(p));
    }

    @Test
    public void ordinaryStaffRuleCannotSupplyExtension() throws Exception {
        Page p = page(false);
        p.clearLeftRail();
        for (int x = 80; x <= 132; x++) p.pixel(x, 148, 100, 4);
        assertFalse(recover(p));
    }

    @Test
    public void curvedThinSlurCannotSupplyExtension() throws Exception {
        Page p = page(false);
        p.clearLeftRail();
        for (int x = 82; x <= 131; x++) {
            double t = (x - 80) / 52.0;
            int y = (int) Math.round(147 + 3 * t - 8 * Math.sin(Math.PI * t));
            for (int d = 0; d < 3; d++) p.pixel(x, y - d, 140, 5);
        }
        assertFalse(recover(p));
    }

    @Test
    public void blankCapFailsDespiteCompleteBody() throws Exception {
        Page p = page(false);
        for (int x = 82; x <= 83; x++) for (int y = 138; y <= 154; y++) p.pixel(x, y, 250, 0);
        assertFalse(recover(p));
    }

    @Test
    public void isolatedInteriorGapFailsDespiteBothEndCaps() throws Exception {
        Page p = page(false);
        for (int y = 138; y <= 154; y++) p.pixel(110, y, 250, 0);
        assertFalse(recover(p));
    }

    @Test
    public void detachedShaftFailsExistingPaleProof() throws Exception {
        Page p = page(false);
        for (int y = 111; y <= 115; y++) for (int x = 79; x <= 82; x++) p.pixel(x, y, 250, 0);
        assertFalse(recover(p));
    }

    @Test
    public void opposedVoiceShaftCannotJoinDonorBeam() {
        Page p = page(false);
        assertFalse(
                CorroboratedFaintBeam.connects(
                        p.l, p.g, W, H, new int[] {80, 147, -1}, p.a, p.b, GAP));
    }

    @Test
    public void missingIndependentDonorRailFails() {
        Page p = page(false);
        for (int y = 140; y <= 157; y++) p.pixel(158, y, 250, 0);
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void grayOnlyDonorCannotReplaceSemanticCaps() {
        Page p = page(false);
        Arrays.fill(p.l, (byte) 0);
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void broadGrayCannotReplaceBilateralPaper() {
        Page p = page(false);
        for (int x = 84; x <= 128; x++) for (int y = 130; y <= 165; y++) p.pixel(x, y, 220, 5);
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void weakPaperStreakWithoutStrongContrastMajorityFails() {
        Page p = page(false);
        p.rail(80, 147, 132, 150, 239, 7);
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void tooWideStrokeCannotReplaceBeam() {
        Page p = page(false);
        p.rail(80, 147, 132, 150, 215, 15);
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void extrapolationIntoExistingDonorSpanIsRejected() {
        Page p = page(false);
        assertFalse(
                CorroboratedFaintBeam.connects(
                        p.l, p.g, W, H, new int[] {157, 152, 1}, p.a, p.b, GAP));
    }

    @Test
    public void missingOwnHeadInkFailsRefinement() throws Exception {
        Page p = page(false);
        for (int y = 56; y <= 82; y++) for (int x = 77; x <= 104; x++) p.pixel(x, y, 250, 0);
        assertFalse(recover(p));
    }

    @Test
    public void malformedInputsFailClosed() {
        Page p = page(false);
        assertFalse(CorroboratedFaintBeam.connects(null, p.g, W, H, p.own, p.a, p.b, GAP));
        assertNull(CorroboratedFaintBeam.refine(p.g, W, H, head(), Float.NaN));
    }

    @Test
    public void strongDeadEndBranchesCannotPoolVotesIntoWeakCompletePath() {
        Page p = page(false);
        p.clearLeftRail();
        p.rail(80, 147, 132, 150, 230, 7);
        for (int x = 84; x <= 128; x++) {
            for (int y = 138; y <= 154; y++) p.pixel(x, y, 250, 0);
            int e = Math.round(150 + 3 * (x - 132) / 50f);
            for (int y = e - 3; y <= e; y++) p.pixel(x, y, x < 106 ? 230 : 100, 5);
            if (x < 106) for (int y = e - 8; y <= e - 5; y++) p.pixel(x, y, 100, 5);
        }
        assertFalse(CorroboratedFaintBeam.connects(p.l, p.g, W, H, p.own, p.a, p.b, GAP));
    }

    @Test
    public void analysisIsReadOnly() throws Exception {
        Page p = page(false);
        byte[] g = p.g.clone(), l = p.l.clone();
        recover(p);
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }

    @Test
    public void outerStripProposalsRemainBoundedAndDeduplicated() {
        Page p = page(false);
        var heads = CorroboratedFaintBeam.outerSparse(p.l, p.g, W, H, GAP, 60, 180);
        assertEquals(heads.size(), new HashSet<>(heads).size());
        assertTrue(
                heads.stream()
                        .allMatch(a -> a.y() >= 60 && a.y() <= 180 && (a.y() < 80 || a.y() > 160)));
    }

    @Test
    public void invalidEndpointsFailBeforePathAllocation() {
        Page p = page(false);
        assertFalse(
                CorroboratedFaintBeam.connects(
                        p.l, p.g, W, H, new int[] {Integer.MAX_VALUE, 147, 1}, p.a, p.b, GAP));
        assertFalse(
                CorroboratedFaintBeam.connects(
                        p.l, p.g, W, H, p.own, new int[] {132, -1, 1}, p.b, GAP));
    }
}
