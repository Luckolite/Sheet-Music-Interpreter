// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original printed rule and quarter-note equations. */
public class OpeningTempoEnvelopeTest {
    final int W = 480, H = 400;
    byte[] g = new byte[W * H];

    OpeningTempoEnvelopeTest(boolean unused) {
        Arrays.fill(g, (byte) 255);
    }

    public OpeningTempoEnvelopeTest() {
        this(false);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
    }

    List<ScoreTempoChange> read(boolean staff, boolean note, int top, int left) {
        if (staff) for (int y = 150; y <= 198; y += 12) box(30, y, 420, 1);
        box(left - 18, top + 6, 12, 2);
        box(left - 18, top + 12, 12, 2);
        box(left, top, 3, 18);
        box(left + 12, top, 3, 18);
        if (note) {
            box(left - 30, top - 12, 2, 28);
            for (int y = top + 9; y <= top + 16; y++)
                for (int x = left - 39; x <= left - 29; x++)
                    if (Math.pow((x - (left - 34)) / 5d, 2) + Math.pow((y - (top + 13)) / 4d, 2)
                            <= 1) g[y * W + x] = 0;
        }
        return TempoChangeDetector.detect(
                List.of(
                        new MeasureNumberReconciler.NumberToken(
                                96,
                                left / (float) W,
                                top / (float) H,
                                (left + 16f) / W,
                                (top + 18f) / H,
                                (left - 40f) / W)),
                g,
                W,
                H,
                List.of(new MeasureRegion(180f / W, 450f / W, 138f / H, 210f / H)));
    }

    @Test
    public void completeEquationLeftOfPlayableEdgeFindsOpening() {
        assertEquals(List.of(new ScoreTempoChange(0, 0, 96)), read(true, true, 88, 110));
    }

    @Test
    public void highCompleteEquationFindsVerifiedOpening() {
        assertEquals(List.of(new ScoreTempoChange(0, 0, 96)), read(true, true, 45, 220));
    }

    @Test
    public void missingNoteCannotUseExpandedEnvelope() {
        assertTrue(read(true, false, 88, 110).isEmpty());
    }

    @Test
    public void missingPrintedStaffCannotUseExpandedEnvelope() {
        assertTrue(read(false, true, 88, 110).isEmpty());
    }

    @Test
    public void remoteHeadingDoesNotAttach() {
        assertTrue(read(true, true, 15, 220).isEmpty());
    }

    @Test
    public void inStaffEquationRemainsRejected() {
        assertTrue(read(true, true, 158, 220).isEmpty());
    }
}
