// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RawCourtesyNaturalOwnershipTest {
    @Test
    public void fragmentedNaturalOwnsItsActualSemanticIsland() throws Exception {
        int m = 0;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(true, Float.isFinite(x));
    }

    @Test
    public void legacyCompleteNaturalIsRetained() throws Exception {
        int m = 1;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(true, Float.isFinite(x));
    }

    @Test
    public void absentBarRefuses() throws Exception {
        int m = 2;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void flatWithoutLowerRightNaturalSpineRefuses() throws Exception {
        int m = 3;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void playedNoteToRightRefuses() throws Exception {
        int m = 4;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void playedStemToRightRefuses() throws Exception {
        int m = 5;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void UnownedInkToRightRefuses() throws Exception {
        int m = 6;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void absentRawImageRefuses() throws Exception {
        int m = 7;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void isolatedWhiteCounterHeadLabelRefuses() throws Exception {
        int m = 8;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void containedIslandAntialiasFringeIsOwned() throws Exception {
        int m = 9;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(true, Float.isFinite(x));
    }

    @Test
    public void connectedForeignIslandOutsideBodyRefuses() throws Exception {
        int m = 10;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void producerPrintedPhaseUsesActualBarFrame() throws Exception {
        int m = 11;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(true, Float.isFinite(x));
    }

    @Test
    public void unprovedPitchFrameCannotMoveBar() throws Exception {
        int m = 12;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }

    @Test
    public void nonfinitePrintedFrameRefuses() throws Exception {
        int m = 13;
        float x =
                new NaturalCourtesyProofControls()
                        .run(
                                m == 1,
                                m != 2,
                                m != 3,
                                m == 4,
                                m == 5,
                                m == 6,
                                m == 7,
                                m == 8,
                                m == 9,
                                m == 10,
                                m >= 11 ? m - 10 : 0);
        assertEquals(false, Float.isFinite(x));
    }
}
