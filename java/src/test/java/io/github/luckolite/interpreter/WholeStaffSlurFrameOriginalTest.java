// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original faint full staff with a short local exterior rail; no score assets. */
public final class WholeStaffSlurFrameOriginalTest {
    static final int W = 720, H = 240;
    static final float GAP = 16;

    static byte[] fixture(float sixth, int missing) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 5; i++)
            if (i != missing) for (int x = 0; x < W; x++) g[(60 + i * 16) * W + x] = (byte) 200;
        for (int x = 0; x < Math.round(W * sixth); x++) g[140 * W + x] = 100;
        return g;
    }

    static PrintedSlurFrames.Result<PrintedSlurOwners.GeometryHead> prepare(byte[] g) {
        var note =
                new ScoreNoteEvent(
                        0, .5f, 0, 0, 1, 124f / H, false, 0, 0, 0, 1, 1, 0, 0, 30, false);
        return PrintedSlurFrames.prepare(
                g,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(60, 124, GAP, 0, 1)),
                List.of(note),
                List.of(new PrintedSlurOwners.GeometryHead(0, 500, 124, 0, 1)));
    }

    @Test
    public void fullRawFiveRuleProofSurvivesLocalStripRailAmbiguity() {
        byte[] g = fixture(.4f, -1);
        assertNull(StaffPitchTrack.forPrintedSlurs(g, W, H, 60, 124, GAP));
        var note =
                new ScoreNoteEvent(
                        0, .5f, 0, 0, 1, 124f / H, false, 0, 0, 0, 1, 1, 0, 0, 30, false);
        var score =
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
        var f =
                PrintedSlurRecognition.captureObserved(
                                score,
                                0,
                                g,
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(60, 124, GAP, 0, 1)))
                        .orElseThrow();
        assertEquals(0, f.unprovedStaffs());
        assertTrue(f.unboundOwners().isEmpty());
    }

    @Test
    public void completeSixthRuleRemainsAmbiguous() {
        assertEquals(0, prepare(fixture(1, -1)).frames().size());
    }

    @Test
    public void missingRuleRemainsUnproved() {
        for (int i = 0; i < 5; i++) assertEquals(0, prepare(fixture(.4f, i)).frames().size());
    }

    @Test
    public void clearLegacyStaffIsUnchanged() {
        var f = prepare(fixture(0, -1));
        assertEquals(1, f.frames().size());
        assertTrue(f.unboundOwners().isEmpty());
    }
}
