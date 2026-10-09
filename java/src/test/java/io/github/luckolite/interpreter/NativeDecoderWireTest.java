// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class NativeDecoderWireTest {
    private static byte[] packet(NativeDecoderWire.Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.packet(new DataOutputStream(bytes), writer);
        return bytes.toByteArray();
    }

    private static DataInputStream unpack(byte[] bytes) throws Exception {
        return NativeDecoderWire.packet(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void everyNoteRestAndKeyFieldRoundTripsExactly() throws Exception {
        var note =
                new ScoreNoteEvent(
                                0, .375f, -4, 1, 2, .75f, true, 2, 3, -1, .5f, 3, .25f, 12, 23,
                                true, .125f, true, -2)
                        .withBoundaryTies(10)
                        .withTupletRatio(5, 3);
        var rest = new ScoreRestEvent(0, .5f, .2f, .07f, 1, 2, .125);
        var score =
                new OmrScoreInterpreter.Analysis(
                        List.of(note), List.of(new ScoreKeyChange(0, -5)), List.of(rest));
        try (var in = unpack(packet(out -> NativeDecoderWire.writeAnalysis(out, score)))) {
            assertEquals(score, NativeDecoderWire.readAnalysis(in, 1));
        }
    }

    @Test
    public void requestPreservesPixelsAndMeasureGeometry() throws Exception {
        byte[] labels = {0, 1, 2, 3, 4, 5}, gray = {0, 1, 127, (byte) 128, (byte) 254, (byte) 255};
        var request =
                new NativeDecoderWire.Request(
                        labels, gray, 3, 2, List.of(new MeasureRegion(.1f, .9f, .2f, .8f)));
        try (var in = unpack(packet(out -> NativeDecoderWire.writeRequest(out, request)))) {
            var result = NativeDecoderWire.readRequest(in);
            assertArrayEquals(labels, result.labels());
            assertArrayEquals(gray, result.gray());
            assertEquals(request.measures(), result.measures());
        }
    }

    @Test
    public void malformedAndTruncatedRecordsAreRejected() throws Exception {
        assertThrows(
                IOException.class,
                () ->
                        NativeDecoderWire.packet(
                                new DataInputStream(
                                        new ByteArrayInputStream(
                                                new byte[] {127, 127, 127, 127}))));
        assertThrows(
                IOException.class,
                () ->
                        NativeDecoderWire.writeRequest(
                                new DataOutputStream(new ByteArrayOutputStream()),
                                new NativeDecoderWire.Request(
                                        new byte[1],
                                        new byte[1],
                                        Integer.MAX_VALUE,
                                        2,
                                        List.of())));
        byte[] invalid =
                packet(
                        out -> {
                            out.writeInt(NativeDecoderWire.ANALYZE);
                            out.writeInt(1);
                            out.writeInt(1);
                            out.writeByte(9);
                            out.writeByte(0);
                            out.writeInt(0);
                        });
        try (var in = unpack(invalid)) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readRequest(in));
        }
        byte[] truncated = packet(out -> out.writeInt(1));
        try (var in = unpack(truncated)) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readAnalysis(in, 1));
        }
    }

    @Test
    public void trailingBytesAndUnknownMeasureAreRejected() throws Exception {
        var empty = new OmrScoreInterpreter.Analysis(List.of(), List.of());
        try (var in =
                unpack(
                        packet(
                                out -> {
                                    NativeDecoderWire.writeAnalysis(out, empty);
                                    out.writeByte(1);
                                }))) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readAnalysis(in, 1));
        }
        var wrong = new OmrScoreInterpreter.Analysis(List.of(), List.of(new ScoreKeyChange(3, 0)));
        try (var in = unpack(packet(out -> NativeDecoderWire.writeAnalysis(out, wrong)))) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readAnalysis(in, 1));
        }
    }

    @Test
    public void geometryRoundTripsAndRejectsInvalidResponses() throws Exception {
        var geometry =
                new NativeDecoderStages.Geometry(
                        new byte[] {0, 1, 2, 3, 4, 5},
                        List.of(new MeasureRegion(.1f, .9f, .2f, .8f)));
        byte[] encoded = packet(out -> NativeDecoderWire.writeGeometry(out, geometry));
        try (var in = unpack(encoded)) {
            var result = NativeDecoderWire.readGeometry(in, 3, 2);
            assertArrayEquals(geometry.labels(), result.labels());
            assertEquals(geometry.measures(), result.measures());
        }
        try (var in = unpack(encoded)) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readGeometry(in, 1, 1));
        }
        try (var in =
                unpack(
                        packet(
                                out -> {
                                    NativeDecoderWire.writeGeometry(out, geometry);
                                    out.writeByte(1);
                                }))) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readGeometry(in, 3, 2));
        }
        try (var in =
                unpack(
                        packet(
                                out -> {
                                    out.writeInt(1);
                                    out.writeByte(6);
                                    out.writeInt(0);
                                }))) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readGeometry(in, 1, 1));
        }
    }

    @Test
    public void geometryOperationSurvivesRequestRoundTrip() throws Exception {
        var request =
                new NativeDecoderWire.Request(
                        new byte[4], new byte[4], 2, 2, List.of(), NativeDecoderWire.GEOMETRY);
        try (var in = unpack(packet(out -> NativeDecoderWire.writeRequest(out, request)))) {
            assertEquals(NativeDecoderWire.GEOMETRY, NativeDecoderWire.readRequest(in).operation());
        }
    }
}
