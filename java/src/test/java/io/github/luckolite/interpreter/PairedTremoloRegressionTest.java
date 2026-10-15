// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated rasters and musical controls, without score-derived geometry. */
public class PairedTremoloRegressionTest {
    static byte[] raster(int strokes, boolean connected, boolean secondStem, boolean flip) {
        int w = 180, h = 200;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y = 50; y <= 160; y++) for (int x = 59; x <= 61; x++) gray[y * w + x] = 0;
        if (secondStem)
            for (int y = 40; y <= 140; y++) for (int x = 98; x <= 100; x++) gray[y * w + x] = 0;
        for (int center : new int[] {59, 72, 85}) {
            if (strokes-- <= 0) break;
            for (int x = connected ? 60 : 70; x < (connected ? 100 : 91); x++) {
                int cy = (int) Math.rint(center - .35 * (x - 80));
                for (int y = cy - 3; y <= cy + 3; y++) gray[y * w + x] = 0;
            }
        }
        if (flip) {
            byte[] flipped = new byte[gray.length];
            for (int y = 0; y < h; y++) System.arraycopy(gray, y * w, flipped, (h - y - 1) * w, w);
            gray = flipped;
        }
        return gray;
    }

    @Test
    public void floatingRailsRequireTwoStemsAndRejectConnectedBeams() {
        for (int test = 0; test < 6; test++) {
            boolean flip = test == 1;
            byte[] gray = raster(test == 3 ? 2 : test == 4 ? 1 : 3, test == 2, test != 5, flip);
            var left = new PairedTremoloInk.Stem(60, flip ? 39 : 160, flip ? 149 : 50);
            var right = new PairedTremoloInk.Stem(99, flip ? 59 : 140, flip ? 159 : 40);
            assertEquals(
                    "generated control " + test,
                    test < 2,
                    !PairedTremoloInk.find(gray, 180, 200, left, right, 17).isEmpty());
        }
        assertTrue(PairedTremoloInk.find(null, 1, 1, null, null, 17).isEmpty());
    }

    @Test
    public void writtenQuartersKeepTheirTypeWhilePairAdvancesOneBeat() throws Exception {
        var first =
                new ScoreNoteEvent(
                                0,
                                .4f,
                                0,
                                0,
                                1,
                                .2f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1)
                        .withLeadingRest(3);
        var second =
                new ScoreNoteEvent(
                        0, .6f, 4, 0, 1, .2f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 1);
        var pair =
                new ScoreTremoloSource.Pair(
                        "synthetic-pair",
                        List.of(new ScoreTremoloSource.Anchor(0, 0)),
                        List.of(new ScoreTremoloSource.Anchor(0, 1)),
                        3,
                        List.of(new ScoreTremoloSource.Evidence(0, .4f, .2f, .6f, .3f)));
        var page =
                new ScorePageInterpretation(
                                List.of(new MeasureRegion(.1f, .9f, .1f, .4f)),
                                List.of(first, second),
                                1)
                        .withTremoloSource(ScoreTremoloSource.capture(0, 2, List.of(pair)));
        var remapped =
                page.tremoloSource()
                        .retainFinalHeads(page.notes(), List.of(second.withClef(22), first));
        assertEquals(
                new ScoreTremoloSource.Anchor(0, 1), remapped.noteOrigins().get(0).orElseThrow());
        assertEquals(1, remapped.resolve().resolved().size());
        assertEquals(
                1,
                page.tremoloSource()
                        .retainFinalHeads(page.notes(), List.of(second))
                        .resolve()
                        .unresolved()
                        .size());
        assertEquals(
                1,
                page.tremoloSource()
                        .retainFinalHeads(page.notes(), List.of(first, first, second))
                        .resolve()
                        .unresolved()
                        .size());
        assertEquals(
                1,
                ScoreTremoloSource.empty(2)
                        .retainFinalHeads(page.notes(), List.of(second))
                        .noteOrigins()
                        .size());
        var output = new java.io.ByteArrayOutputStream();
        var analysis =
                new OmrScoreInterpreter.Analysis(
                        page.notes(), List.of(), List.of(), page.tremoloSource());
        NativeDecoderWire.writeAnalysis(new java.io.DataOutputStream(output), analysis);
        byte[] encoded = output.toByteArray();
        assertEquals(-26, java.nio.ByteBuffer.wrap(encoded).getInt());
        var decoded =
                NativeDecoderWire.readAnalysis(
                        new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded)), 1);
        assertEquals(page.tremoloSource(), decoded.tremoloSource());
        assertThrows(
                java.io.IOException.class,
                () ->
                        NativeDecoderWire.writeAnalysis(
                                new java.io.DataOutputStream(new java.io.ByteArrayOutputStream()),
                                analysis,
                                -25));
        try (var scope = ScoreNoteTiming.beginTimingSession(page)) {
            assertEquals(1, ScoreNoteTiming.writtenDurationBeats(first), 1e-7);
            assertEquals(
                    .5, ScoreNoteTiming.resolvedWrittenDurationBeats(first, page.notes(), 4), 1e-7);
            assertEquals(
                    .5,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(second, page.notes(), 4),
                    1e-7);
            try (var nested = ScoreNoteTiming.beginTimingSession()) {
                assertEquals(
                        .5,
                        ScoreNoteTiming.resolvedWrittenDurationBeats(second, page.notes(), 4),
                        1e-7);
            }
        }
        assertEquals(1, ScoreNoteTiming.writtenDurationBeats(second), 1e-7);
    }
}
