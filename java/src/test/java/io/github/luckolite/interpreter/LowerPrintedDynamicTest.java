// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LowerPrintedDynamicTest {
    static final int W = 600, H = 350;
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(144, 200, 14, 0, 1));
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(.2f, .9f, .35f, .65f));
    static final List<ScoreNoteEvent> NOTES =
            List.of(new ScoreNoteEvent(0, 100f / 420, 0, 0, 1, 200f / H, false, 0, 0, 2, 1));

    static PlayingTechniqueDetector.Word word(float top) {
        return new PlayingTechniqueDetector.Word("mf", 210f / W, top / H, 240f / W, (top + 15) / H);
    }

    static byte[] ink(int rails) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int l = 0; l < rails; l++)
            for (int x = 100; x < 580; x++) g[(200 - l * 14) * W + x] = 30;
        for (int y = 264; y < 280; y++) for (int x = 210; x < 240; x++) g[y * W + x] = 30;
        return g;
    }

    @Test
    public void recoversLiteralBelowNormalOwnershipRange() {
        var changes =
                ScoreDynamicsDetector.detect(List.of(word(264)), STAFFS, BARS, NOTES, ink(5), W, H);
        assertEquals(1, changes.size());
        assertEquals(0, changes.get(0).decibels(), .001);
        assertEquals(0, changes.get(0).measureIndex());
    }

    @Test
    public void missingRailRejected() {
        assertNull(
                ScoreDynamicsDetector.provedLowerWordOwner(
                        word(264), STAFFS, BARS, NOTES, ink(4), W, H));
    }

    @Test
    public void missingHeadRejected() {
        assertNull(
                ScoreDynamicsDetector.provedLowerWordOwner(
                        word(264), STAFFS, BARS, List.of(), ink(5), W, H));
    }

    @Test
    public void distantCaptionRejected() {
        assertNull(
                ScoreDynamicsDetector.provedLowerWordOwner(
                        word(272), STAFFS, BARS, NOTES, ink(5), W, H));
    }

    @Test
    public void ambiguousFrameRejected() {
        assertNull(
                ScoreDynamicsDetector.provedLowerWordOwner(
                        word(264),
                        List.of(STAFFS.get(0), STAFFS.get(0)),
                        BARS,
                        NOTES,
                        ink(5),
                        W,
                        H));
    }

    @Test
    public void absentPixelsRejected() {
        assertNull(
                ScoreDynamicsDetector.provedLowerWordOwner(
                        word(264), STAFFS, BARS, NOTES, null, W, H));
    }

    @Test
    public void sourceImmutable() {
        byte[] g = ink(5), copy = g.clone();
        ScoreDynamicsDetector.detect(List.of(word(264)), STAFFS, BARS, NOTES, g, W, H);
        assertArrayEquals(copy, g);
    }
}
