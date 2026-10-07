// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original tapered shaft prediction whose pale connection survives in the raw image. */
public class AttachedShaftWedgeTest {
    int mark(boolean connected, boolean breakShaft, boolean semantic, int paper) {
        int w = 1280, h = 900, x = 640, cy = 260;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) paper);
        for (int y = 254; y <= 265; y++)
            for (int xx = x - 11; xx <= x + 11; xx++) {
                gray[y * w + xx] = 65;
                labels[y * w + xx] = OmrMeasurePostProcessor.NOTEHEAD;
            }
        if (connected)
            for (int y = 260; y < 282; y++) {
                gray[y * w + x - 10] = 95;
                labels[y * w + x - 10] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        for (int y = 280; y <= 292; y++) {
            int half = (y - 280) / 5;
            for (int xx = x - 10 - half; xx <= x - 10 + half; xx++) {
                gray[y * w + xx] = 65;
                if (semantic) labels[y * w + xx] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        }
        if (breakShaft) for (int y = 272; y <= 274; y++) gray[y * w + x - 10] = (byte) paper;
        var gc = gray.clone();
        var lc = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        w,
                        h,
                        List.of(new NoteArticulationDetector.Anchor(x, cy, 16, 0)))[0];
        assertArrayEquals(gc, gray);
        assertArrayEquals(lc, labels);
        return result;
    }

    @Test
    public void taperedStemPredictionCannotShortenItsOwnNote() {
        assertEquals(0, mark(true, false, true, 140));
    }

    @Test
    public void detachedMislabeledWedgeRetainsItsGate() {
        assertEquals(NoteArticulation.STACCATISSIMO, mark(false, false, true, 140));
    }

    @Test
    public void actualWhiteGapPreventsAttachedShaftProof() {
        assertEquals(NoteArticulation.STACCATISSIMO, mark(true, true, true, 140));
    }

    @Test
    public void detachedOrdinaryWedgeRetainsItsGate() {
        assertEquals(NoteArticulation.STACCATISSIMO, mark(false, false, false, 140));
    }
}
