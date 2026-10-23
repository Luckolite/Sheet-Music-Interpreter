// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original aperture certificate, refusal and analytic density controls. */
public class HairpinApertureSafetyOriginalTest {
    static Optional<CompleteHairpinAperture.Proof> proof(
            HairpinApertureOriginalTest.Scene s,
            boolean[] bars,
            CompleteHairpinAperture.Budget budget) {
        byte[] before = s.gray.clone();
        var r =
                CompleteHairpinAperture.prove(
                        s.gray,
                        s.w,
                        s.h,
                        100,
                        850,
                        new double[] {-.012, 169.2},
                        new double[] {.012, 170.8},
                        bars,
                        14,
                        budget);
        assertArrayEquals(before, s.gray);
        return r;
    }

    @Test
    public void completeRawColumnsAccountForEveryMergedTipAndArm() {
        var s = FringedHairpinApertureOriginalTest.scene(1, false);
        var p =
                proof(s, new boolean[751], new CompleteHairpinAperture.Budget(4_000_000))
                        .orElseThrow();
        assertEquals(751, p.columns().size());
        assertTrue(
                p.columns().stream()
                        .anyMatch(c -> c.role() == CompleteHairpinAperture.Role.MERGED_TIP));
        assertTrue(
                p.columns().stream().anyMatch(c -> c.role() == CompleteHairpinAperture.Role.ARMS));
    }

    @Test
    public void zeroWorkCannotPublishAPartialApertureCertificate() {
        var b = new CompleteHairpinAperture.Budget(0);
        assertTrue(
                proof(FringedHairpinApertureOriginalTest.scene(1, false), new boolean[751], b)
                        .isEmpty());
        assertTrue(b.refused);
    }

    @Test
    public void aMidProofWorkLimitAlsoAbstainsReadOnly() {
        var b = new CompleteHairpinAperture.Budget(40_000);
        assertTrue(
                proof(FringedHairpinApertureOriginalTest.scene(1, false), new boolean[751], b)
                        .isEmpty());
        assertTrue(b.refused);
        assertTrue(b.remaining >= 0);
    }

    @Test
    public void aClaimedBarNeedsItsOwnLongRawColumn() {
        boolean[] bars = new boolean[751];
        bars[350] = true;
        assertTrue(
                proof(
                                FringedHairpinApertureOriginalTest.scene(1, false),
                                bars,
                                new CompleteHairpinAperture.Budget(4_000_000))
                        .isEmpty());
    }

    @Test
    public void aCroppedUnknownVerticalBranchCannotBeAnArm() {
        var s = FringedHairpinApertureOriginalTest.scene(1, false);
        for (int y = 150; y <= 190; y++) s.gray[y * s.w + 600] = 0;
        assertTrue(
                proof(s, new boolean[751], new CompleteHairpinAperture.Budget(4_000_000))
                        .isEmpty());
    }

    static void scaled(int scale, int direction) {
        int w = 950 * scale, h = 350 * scale;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(
                                80 * scale, 136 * scale, 14 * scale, 0, 2),
                        new PlayingTechniqueDetector.Staff(
                                246 * scale, 302 * scale, 14 * scale, 1, 2));
        for (var s : staffs)
            for (int r = 0; r < 5; r++)
                for (int y = Math.round(s.top() + r * s.gap()) - scale / 2;
                        y <= Math.round(s.top() + r * s.gap()) + scale / 2;
                        y++) for (int x = 10 * scale; x < w - 10 * scale; x++) g[y * w + x] = 0;
        for (int y = 150 * scale; y <= 190 * scale; y++)
            for (int x = 100 * scale; x <= 850 * scale; x++) {
                double sum = 0;
                for (int sy = 0; sy < 4; sy++)
                    for (int sx = 0; sx < 4; sx++) {
                        double xx = (x + (sx + .5) / 4 - .5) / scale,
                                yy = (y + (sy + .5) / 4 - .5) / scale,
                                t = direction > 0 ? xx - 100 : 850 - xx,
                                d = Math.abs(Math.abs(yy - 170) - .012 * t);
                        sum +=
                                t < 0 || t > 750
                                        ? 255
                                        : d <= .8 ? 0 : d <= 2.1 ? 180 : d <= 2.6 ? 190 : 255;
                    }
                g[y * w + x] = (byte) Math.round(sum / 16.);
            }
        for (int y = 80 * scale; y <= 302 * scale; y++)
            for (int x = 449 * scale; x <= 450 * scale; x++) g[y * w + x] = 0;
        var copy = g.clone();
        var found = OpenHairpinPairs.detect(g, w, h, staffs);
        assertArrayEquals(copy, g);
        assertEquals(found.toString(), 1, found.size());
        assertEquals(direction, found.get(0).direction());
    }

    @Test
    public void doubledAnalyticDensityPreservesTheCompleteAperture() {
        scaled(2, 1);
    }

    @Test
    public void tripledAnalyticDensityPreservesTheReflectedAperture() {
        scaled(3, -1);
    }

    @Test
    public void lateGlobalRefusalPreservesLegacyShapesAndDropsAllNewRecoveries() {
        int slots = 41, w = 950 * slots, h = 350;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < slots; i++) {
            var s =
                    i == 0
                            ? new HairpinApertureOriginalTest.Scene(1, true, false)
                            : FringedHairpinApertureOriginalTest.scene(1, true);
            for (int y = 0; y < h; y++) System.arraycopy(s.gray, y * s.w, g, y * w + i * s.w, s.w);
        }
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 136, 14, 0, 2),
                        new PlayingTechniqueDetector.Staff(246, 302, 14, 1, 2));
        var before = g.clone();
        var r = OpenHairpinPairs.detect(g, w, h, staffs);
        assertArrayEquals(before, g);
        assertEquals(r.toString(), 1, r.size());
        assertTrue(r.get(0).right() < 950);
    }
}
