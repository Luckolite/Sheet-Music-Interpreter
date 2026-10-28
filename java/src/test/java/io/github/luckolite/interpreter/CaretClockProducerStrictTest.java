// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
import org.junit.Test;

/** Original raster derived from AndroidLocalManagedCaptureOriginalTest's Apache fixture. */
public final class CaretClockProducerStrictTest {
    private static final int W = 600, H = 240;

    private static final class Scene {
        final byte[] labels = new byte[W * H], raw = new byte[W * H];
        final List<MeasureRegion> measures = List.of(new MeasureRegion(.03f, .97f, .2f, .8f));

        Scene() {
            Arrays.fill(raw, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int x = 20; x < 580; x++) {
                    labels[(80 + line * 16) * W + x] = 4;
                    raw[(80 + line * 16) * W + x] = (byte) 120;
                }
            for (int cx : new int[] {180, 360}) {
                int cy = cx == 180 ? 128 : 112;
                for (int y = cy - 7; y <= cy + 7; y++)
                    for (int x = cx - 11; x <= cx + 11; x++)
                        if ((x - cx) * (x - cx) / 100.0 + (y - cy) * (y - cy) / 36.0 <= 1) {
                            labels[y * W + x] = 2;
                            raw[y * W + x] = 0;
                        }
                for (int y = cy - 52; y <= cy; y++)
                    for (int x = cx + 9; x <= cx + 10; x++) {
                        labels[y * W + x] = 5;
                        raw[y * W + x] = 0;
                    }
            }
        }

        ScoreClockContextSource capture(
                List<ScoreNoteEvent> notes, OmrScoreInterpreter.Analysis a) {
            var p =
                    new ScorePageInterpretation(
                            measures,
                            notes,
                            0,
                            a.keyChanges(),
                            List.of(),
                            List.of(),
                            a.rests(),
                            List.of(),
                            List.of());
            return ScoreClockContextCapture.capture(labels, raw, W, H, p, a, null, null);
        }
    }

    private static void strict(Scene s, OmrScoreInterpreter.Analysis a) {
        assertEquals(2, a.notes().size());
        var baseline = s.capture(a.notes(), a);
        assertEquals(2, baseline.printedValues().size());
        assertFalse(baseline.glyphLedgers().isEmpty());
        var changed = new ArrayList<>(a.notes());
        changed.set(0, changed.get(0).withArticulations(NoteArticulation.MARCATO));
        assertEquals(
                List.of(1),
                s.capture(changed, a).printedValues().stream()
                        .map(ScoreClockContextSource.PrintedValue::noteIndex)
                        .toList());
        changed.set(0, a.notes().get(0).withVisualSize(ScoreNoteEvent.VisualSize.CUE));
        assertEquals(
                List.of(1),
                s.capture(changed, a).printedValues().stream()
                        .map(ScoreClockContextSource.PrintedValue::noteIndex)
                        .toList());
        assertTrue(
                s.capture(List.of(a.notes().get(0), a.notes().get(0)), a)
                        .printedValues()
                        .isEmpty());
        var duplicateProducer =
                new OmrScoreInterpreter.Analysis(
                        List.of(a.notes().get(0), a.notes().get(0)),
                        a.keyChanges(),
                        a.rests(),
                        a.tremoloSource(),
                        a.clockProducerSource(),
                        a.clockContextSource());
        assertTrue(s.capture(a.notes(), duplicateProducer).printedValues().isEmpty());
        s.raw[0] = 0;
        assertTrue(s.capture(a.notes(), a).printedValues().isEmpty());
    }

    @Test
    public void liveManagedIssuerStillRejectsArbitraryEditsDuplicatesAndForeignFrame() {
        var s = new Scene();
        byte[] raw = s.raw.clone(), labels = s.labels.clone();
        var a = OmrScoreInterpreter.analyze(s.labels, s.raw, W, H, s.measures);
        assertFalse(a.clockProducerSource().emissions().isEmpty());
        assertArrayEquals(raw, s.raw);
        assertArrayEquals(labels, s.labels);
        strict(s, a);
    }

    @Test
    public void wireTransportedIssuerKeepsTheSameStrictBoundaries() throws Exception {
        var s = new Scene();
        var a = OmrScoreInterpreter.analyze(s.labels, s.raw, W, H, s.measures);
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(bytes), a);
        var remote =
                NativeDecoderWire.readAnalysis(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 1);
        assertTrue(remote.clockProducerSource().emissions().isEmpty());
        assertFalse(remote.clockContextSource().printedValues().isEmpty());
        strict(s, remote);
    }
}
