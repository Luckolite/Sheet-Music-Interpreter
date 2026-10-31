// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original raw ink and full-caller fixtures. Primitive ink is not a stem-role oracle. */
public class ShaftInkEquivalenceOriginalTest {
    static final int W = 500, H = 330;

    byte[] slab(int lo, int hi, int tone) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 90; y <= 172; y++) for (int x = lo; x <= hi; x++) g[y * W + x] = (byte) tone;
        return g;
    }

    boolean ink(byte[] g, int raw, int traced, int head, int end) {
        return PairedTremoloInk.sameShaftInk(
                g,
                W,
                H,
                150,
                172,
                14,
                1,
                raw,
                head,
                end,
                new PairedTremoloInk.Stem(traced, head, end),
                30,
                470,
                50,
                297);
    }

    @Test
    public void identicalColumnNeedsNoRaster() {

        assertTrue(ink(null, 161, 161, 172, 90));
    }

    @Test
    public void adjacentAndWiderCommonShaftInk() {
        for (int raw : new int[] {161, 162, 163})
            assertTrue(ink(slab(160, raw, 180), raw, 160, 172, 90));
    }

    @Test
    public void separateParallelColumnsHaveNoCommonSlab() {
        byte[] g = slab(160, 160, 35);
        for (int y = 90; y <= 172; y++) g[y * W + 162] = 35;
        assertFalse(ink(g, 162, 160, 172, 90));
    }

    @Test
    public void headOnlyAndBeamOnlyBridgesRefuse() {
        for (int bridge : new int[] {90, 172}) {
            byte[] g = slab(160, 160, 35);
            for (int y = 90; y <= 172; y++) g[y * W + 162] = 35;
            g[bridge * W + 161] = 35;
            assertFalse(ink(g, 162, 160, 172, 90));
        }
    }

    @Test
    public void oneWhiteCellAnywhereRefusesIncludingEveryBoundary() {
        for (int[] p :
                new int[][] {
                    {160, 90}, {161, 90}, {160, 172}, {161, 172}, {160, 120}, {161, 121}
                }) {
            byte[] g = slab(160, 161, 35);
            g[p[1] * W + p[0]] = (byte) 255;
            assertFalse(ink(g, 161, 160, 172, 90));
        }
    }

    @Test
    public void existing185BoundaryIsExact() {
        assertTrue(ink(slab(160, 161, 184), 161, 160, 172, 90));
        assertFalse(ink(slab(160, 161, 185), 161, 160, 172, 90));
    }

    @Test
    public void endpointMismatchRefusesEvenAllBlack() {
        byte[] g = slab(160, 161, 35);
        for (var s :
                List.of(
                        new PairedTremoloInk.Stem(160, 171, 90),
                        new PairedTremoloInk.Stem(160, 172, 91)))
            assertFalse(
                    PairedTremoloInk.sameShaftInk(
                            g, W, H, 150, 172, 14, 1, 161, 172, 90, s, 30, 470, 50, 297));
    }

    @Test
    public void fullBlackRectangleIsTrueInkNotRole() {
        assertTrue(ink(slab(160, 163, 35), 163, 160, 172, 90));
    }

    @Test
    public void existingWindowAndCallerBoundsCannotBeExpanded() {
        assertFalse(ink(slab(150, 161, 35), 161, 150, 172, 90));
        assertFalse(
                PairedTremoloInk.sameShaftInk(
                        slab(160, 161, 35),
                        W,
                        H,
                        150,
                        172,
                        14,
                        1,
                        161,
                        172,
                        90,
                        new PairedTremoloInk.Stem(160, 172, 90),
                        161,
                        470,
                        50,
                        297));
    }

    @Test
    public void invalidOverflowInputsRefuseBeforeReads() {
        byte[] g = slab(160, 161, 35);
        for (double gap : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE})
            assertFalse(
                    PairedTremoloInk.sameShaftInk(
                            g,
                            W,
                            H,
                            150,
                            172,
                            gap,
                            1,
                            161,
                            172,
                            90,
                            new PairedTremoloInk.Stem(160, 172, 90),
                            30,
                            470,
                            50,
                            297));
        assertFalse(
                PairedTremoloInk.sameShaftInk(
                        g,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        150,
                        172,
                        14,
                        1,
                        161,
                        172,
                        90,
                        new PairedTremoloInk.Stem(160, 172, 90),
                        30,
                        470,
                        50,
                        297));
    }

    @Test
    public void threeOwnerAntialiasedShaftPreservesDetectorAndRecoversBeams() throws Exception {
        var a = ShaftEquivalenceOriginalScenes.analysis("positive");

        var n = a.notes().get(0);
        assertEquals(2, n.beamCount());
        assertEquals(0d, n.unbeamedDurationBeats(), 0);
        var v = a.clockProducerSource().printedValues().get(0);
        assertEquals("OPEN_WITH_STEM_HALF", v.detectorExit().toString());
        assertEquals(2d, v.detectorValue(), 0);
        assertTrue(v.admissions().contains("COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE"));
    }

    @Test
    public void oneMissingCommonShaftPixelKeepsSustainedValue() throws Exception {
        var a = ShaftEquivalenceOriginalScenes.analysis("gap");

        assertEquals(0, a.notes().get(0).beamCount());
        assertEquals(2d, a.notes().get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void blackAndPalePlatesAreCallerDomainControlsNotStripNegatives() {
        for (String mode : List.of("blackPlate", "palePlate")) {
            var a = ShaftEquivalenceOriginalScenes.analysis(mode);
            assertFalse(
                    a.clockProducerSource().printedValues().stream()
                            .anyMatch(
                                    v ->
                                            v.admissions()
                                                    .contains(
                                                            "COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE")));
        }
    }

    @Test
    public void foreignVoiceCannotBorrowTheOwnersInk() {
        var a = ShaftEquivalenceOriginalScenes.analysis("foreignVoice");

        assertFalse(
                a.clockProducerSource().printedValues().stream()
                        .anyMatch(
                                v ->
                                        v.admissions()
                                                .contains(
                                                        "COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE")));
    }

    @Test
    public void translatedAndFractionalOriginalInk() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 118; y <= 200; y++) for (int x = 260; x <= 261; x++) g[y * W + x] = (byte) 180;
        assertTrue(
                PairedTremoloInk.sameShaftInk(
                        g,
                        W,
                        H,
                        250.25,
                        200.1,
                        14,
                        1,
                        261,
                        200,
                        118,
                        new PairedTremoloInk.Stem(260, 200, 118),
                        30,
                        470,
                        50,
                        297));
    }

    @Test
    public void commonInkWithinExistingSearchWindowIsAccepted() {

        assertTrue(ink(slab(160, 161, 180), 161, 160, 172, 90));
    }

    @Test
    public void tracedExteriorCannotBorrowOutsideMeasure() {
        var a = ShaftEquivalenceOriginalScenes.marginAnalysis();

        assertFalse(
                a.clockProducerSource().printedValues().stream()
                        .anyMatch(
                                v ->
                                        v.admissions()
                                                .contains(
                                                        "COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE")));
    }
}
