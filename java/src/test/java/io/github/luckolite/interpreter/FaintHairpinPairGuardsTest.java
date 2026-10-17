// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FaintHairpinPairGuardsTest {
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 1);

    static List<ShadedHairpinFragments.Rail> cores(
            PlayingTechniqueDetector.Staff a, PlayingTechniqueDetector.Staff b) {
        return List.of(
                new ShadedHairpinFragments.Rail(a, 177, 212, -.085, 178.5),
                new ShadedHairpinFragments.Rail(b, 177, 212, .085, 161.5));
    }

    static List<FaintHairpinPair.Shape> read(
            FaintHairpinPairTest.Page p, List<ShadedHairpinFragments.Rail> c) {
        byte[] before = p.gray.clone();
        var r = FaintHairpinPair.recover(c, p.gray, 480, 260);
        assertArrayEquals(before, p.gray);
        return r;
    }

    @Test
    public void actualInkJoinsBothIndependentlyDarkCores() {
        assertEquals(
                1,
                read(new FaintHairpinPairTest.Page("normal", false, 0), cores(STAFF, STAFF))
                        .size());
    }

    @Test
    public void filledTriangleCannotSupplyOpenInterior() {
        assertTrue(
                read(new FaintHairpinPairTest.Page("solid", false, 0), cores(STAFF, STAFF))
                        .isEmpty());
    }

    @Test
    public void twoDistinctPartsCannotShareOnePair() {
        var a = new PlayingTechniqueDetector.Staff(70, 126, 14, 0, 2);
        var b = new PlayingTechniqueDetector.Staff(70, 126, 14, 1, 2);
        assertTrue(read(new FaintHairpinPairTest.Page("normal", false, 0), cores(a, b)).isEmpty());
    }

    @Test
    public void neighboringPhysicalRowsCannotShareOnePair() {
        var b = new PlayingTechniqueDetector.Staff(71, 127, 14, 0, 1);
        assertTrue(
                read(new FaintHairpinPairTest.Page("normal", false, 0), cores(STAFF, b)).isEmpty());
    }

    @Test
    public void staffInteriorBeamsCannotBecomeDirections() {
        var b = new PlayingTechniqueDetector.Staff(140, 196, 14, 0, 1);
        assertTrue(read(new FaintHairpinPairTest.Page("normal", false, 0), cores(b, b)).isEmpty());
    }

    @Test
    public void longThirdStrokeContradictsOpenInterior() {
        var p = new FaintHairpinPairTest.Page("normal", false, 0);
        for (int x = 100; x <= 212; x++) p.stroke(x, 170, 80);
        assertTrue(read(p, cores(STAFF, STAFF)).isEmpty());
    }

    @Test
    public void inconsistentPaperFlanksCannotSupplyContrast() {
        var p = new FaintHairpinPairTest.Page("normal", false, 0);
        for (int x = 95; x <= 215; x++)
            for (int y = 145; y <= 163; y++)
                if ((p.gray[y * 480 + x] & 255) > 240) p.gray[y * 480 + x] = (byte) 230;
        assertTrue(read(p, cores(STAFF, STAFF)).isEmpty());
    }

    @Test
    public void onlyFaintCoresCannotProveTheirOwnFit() {
        assertTrue(
                read(new FaintHairpinPairTest.Page("allFaint", false, 0), cores(STAFF, STAFF))
                        .isEmpty());
    }

    @Test
    public void oneCoreCannotInventMissingArm() {
        assertTrue(
                read(new FaintHairpinPairTest.Page("oneCore", false, 0), cores(STAFF, STAFF))
                        .isEmpty());
    }

    @Test
    public void duplicateProposalOrderKeepsOnePhysicalPair() {
        var c = new ArrayList<>(cores(STAFF, STAFF));
        c.addAll(cores(STAFF, STAFF));
        Collections.reverse(c);
        assertEquals(1, read(new FaintHairpinPairTest.Page("normal", false, 0), c).size());
    }

    @Test
    public void invalidDimensionsAndNullInputsAbstain() {
        assertTrue(FaintHairpinPair.recover(null, new byte[1], 1, 1).isEmpty());
        assertTrue(FaintHairpinPair.recover(cores(STAFF, STAFF), null, 480, 260).isEmpty());
        assertTrue(
                FaintHairpinPair.recover(
                                cores(STAFF, STAFF),
                                new byte[1],
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE)
                        .isEmpty());
    }

    @Test
    public void imageEdgeFramesAndFittedEndpointsNeverReadOutside() {
        for (int w : new int[] {8, 16, 40})
            for (int h : new int[] {8, 16, 40}) {
                byte[] p = new byte[w * h];
                Arrays.fill(p, (byte) 255);
                var s = new PlayingTechniqueDetector.Staff(0, 7, 6, 0, 1);
                assertTrue(FaintHairpinPair.recover(cores(s, s), p, w, h).isEmpty());
            }
    }

    @Test
    public void extremeFittedCoordinatesConservativelyAbstain() {
        for (double y :
                new double[] {
                    Double.MAX_VALUE,
                    -Double.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MIN_VALUE,
                    Double.NaN,
                    Double.POSITIVE_INFINITY
                }) {
            var c =
                    List.of(
                            new ShadedHairpinFragments.Rail(STAFF, 177, 212, -.085, y),
                            new ShadedHairpinFragments.Rail(STAFF, 177, 212, .085, y + 20));
            assertTrue(read(new FaintHairpinPairTest.Page("normal", false, 0), c).isEmpty());
        }
    }
}
