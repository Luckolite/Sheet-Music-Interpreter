// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale whole-note contours, occluded outer ledger and a separate neighboring glyph. */
public class ContaminatedPaleLedgerHeadTest {
    static final int W = 500, H = 300;

    void preserve(float sigma) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int i = 0; i < 5; i++)
            for (int x = 40; x <= 460; x++) {
                gray[(100 + i * 16) * W + x] = 0;
                labels[(100 + i * 16) * W + x] = 4;
            }
        for (int y = 172; y <= 240; y++)
            for (int x = 145; x <= 255; x++) gray[y * W + x] = (byte) 220;
        for (int y = 199; y <= 225; y++)
            for (int x = 180; x <= 216; x++) {
                double r = Math.sqrt((x - 200) * (x - 200) / 144.0 + (y - 212) * (y - 212) / 64.0);
                int oval =
                        (int)
                                Math.round(
                                        220
                                                - 70
                                                        * Math.exp(
                                                                -(r - .70)
                                                                        * (r - .70)
                                                                        / (2 * sigma * sigma)));
                int neighbor =
                        (int)
                                Math.round(
                                        220
                                                - 125
                                                        * Math.exp(
                                                                -((x - 185) * (x - 185)
                                                                                + (y - 212)
                                                                                        * (y - 212))
                                                                        / 32.0));
                gray[y * W + x] = (byte) Math.min(oval, neighbor);
            }
        for (int y = 207; y <= 217; y++)
            for (int x = 192; x <= 208; x++)
                if ((x - 200) * (x - 200) / 64.0 + (y - 212) * (y - 212) / 25.0 <= 1)
                    labels[y * W + x] = 2;
        for (int y : new int[] {180, 196})
            for (int x = 180; x <= 220; x++) gray[y * W + x] = (byte) 155;
        // One printed protrusion remains; the neighboring glyph covers its inner section.
        for (int x = 180; x < 192; x++)
            gray[212 * W + x] = (byte) Math.min(155, gray[212 * W + x] & 255);
        byte[] beforeGray = gray.clone(), beforeLabels = labels.clone();
        var notes =
                OmrScoreInterpreter.extract(
                        labels, gray, W, H, List.of(new MeasureRegion(.08f, .92f, .28f, .62f)));
        assertEquals(notes.toString(), 1, notes.size());
        assertEquals(4, ScoreNoteTiming.writtenDurationBeats(notes.get(0)), .00001);
        assertEquals(0, notes.get(0).stemDirection());
        assertArrayEquals(beforeGray, gray);
        assertArrayEquals(beforeLabels, labels);
    }

    @Test
    public void paleWholeWithNeighborAndNarrowSoftOutline() {
        preserve(.22f);
    }

    @Test
    public void paleWholeWithNeighborAndSoftOutline() {
        preserve(.28f);
    }

    @Test
    public void paleWholeWithNeighborAndBroaderSoftOutline() {
        preserve(.35f);
    }
}
