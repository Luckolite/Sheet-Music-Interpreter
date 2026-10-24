// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeClockContextWireOriginalTest {
    static OmrScoreInterpreter.Analysis source() {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        var page = new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of());
        var parts = ScorePartProducerSource.capture(p.source, p.gray, 300, 300, page.measures());
        var context =
                ScoreClockContextCapture.capture(
                        new byte[90000], p.gray, 300, 300, page, null, parts, null);
        return new OmrScoreInterpreter.Analysis(
                List.of(),
                List.of(),
                List.of(),
                ScoreTremoloSource.empty(0),
                ScoreClockProducerSource.unavailable(0),
                context);
    }

    static byte[] write(OmrScoreInterpreter.Analysis source, Integer marker) throws Exception {
        var b = new ByteArrayOutputStream();
        var out = new DataOutputStream(b);
        if (marker == null) NativeDecoderWire.writeAnalysis(out, source);
        else NativeDecoderWire.writeAnalysis(out, source, marker);
        return b.toByteArray();
    }

    @Test
    public void genuineProducerContextSurvivesManagedAnalysisRoundtrip() throws Exception {
        var source = source();
        var bytes = write(source, null);
        assertEquals(-28, new DataInputStream(new ByteArrayInputStream(bytes)).readInt());
        var restored =
                NativeDecoderWire.readAnalysis(
                        new DataInputStream(new ByteArrayInputStream(bytes)), 4);
        assertEquals(source.clockContextSource(), restored.clockContextSource());
        assertEquals(4, restored.clockContextSource().partWords().size());
        assertFalse(restored.clockContextSource().origin().boundDocument());
    }

    @Test
    public void oldMarkersCannotSilentlyDropTheNewProducerContext() {
        for (int marker : new int[] {-23, -24, -25, -26, -27})
            assertThrows(IOException.class, () -> write(source(), marker));
    }

    @Test
    public void wrongCountsTruncatedAndTrailingAnalysisRefuse() throws Exception {
        var bytes = write(source(), null);
        assertThrows(
                IOException.class,
                () ->
                        NativeDecoderWire.readAnalysis(
                                new DataInputStream(new ByteArrayInputStream(bytes)), 3));
        assertThrows(
                IOException.class,
                () ->
                        NativeDecoderWire.readAnalysis(
                                new DataInputStream(
                                        new ByteArrayInputStream(
                                                Arrays.copyOf(bytes, bytes.length - 1))),
                                4));
        assertThrows(
                IOException.class,
                () ->
                        NativeDecoderWire.readAnalysis(
                                new DataInputStream(
                                        new ByteArrayInputStream(
                                                Arrays.copyOf(bytes, bytes.length + 1))),
                                4));
    }

    @Test
    public void transportedContextRequiresItsOriginalRaster() {
        var source = source();
        var page = new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of());
        var foreign =
                ScoreClockContextCapture.capture(
                        new byte[90000], new byte[90000], 300, 300, page, source, null, null);
        assertTrue(foreign.printedValues().isEmpty());
        assertTrue(foreign.glyphLedgers().isEmpty());
        assertFalse(foreign.missing().isEmpty());
    }
}
