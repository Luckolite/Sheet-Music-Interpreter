// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedStemWireTest {
    // Captured from the released 21-field writer using only this original synthetic note.
    private static final byte[] LEGACY =
            Base64.getDecoder()
                    .decode(
                            "AAAAAQAAAAA+wAAA/////AAAAAEAAAACP0AAAAEAAAACAAAAA/////8/AAAAAAAABT6AAAAAAAAMAAAAEgE+AAAAAf////4AAAAKAAAAAwAAAAAAAAAA");

    private static ScoreNoteEvent note() {
        return new ScoreNoteEvent(
                        0, .375f, -4, 1, 2, .75f, true, 2, 3, -1, .5f, 3, .25f, 12, 18, true, .125f,
                        true, -2)
                .withBoundaryTies(10)
                .withTupletRatio(5, 3);
    }

    private static byte[] bytes(ScoreNoteEvent note) throws IOException {
        var out = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(
                new DataOutputStream(out),
                new OmrScoreInterpreter.Analysis(List.of(note), List.of(), List.of()));
        return out.toByteArray();
    }

    private static OmrScoreInterpreter.Analysis read(byte[] bytes) throws IOException {
        return NativeDecoderWire.readAnalysis(
                new DataInputStream(new ByteArrayInputStream(bytes)), 1);
    }

    // Historical21 bytes provide the unchanged note fields independently of the typed writer.
    private static byte[] legacyStemBytes(int direction) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(-22);
        out.writeInt(1);
        out.write(LEGACY, Integer.BYTES, LEGACY.length - 3 * Integer.BYTES);
        out.writeInt(direction);
        out.write(LEGACY, LEGACY.length - 2 * Integer.BYTES, 2 * Integer.BYTES);
        return bytes.toByteArray();
    }

    private static int kindOffset(byte[] typed) {
        // The two empty key/rest counts follow the new kind byte.
        return typed.length - 2 * Integer.BYTES - 1;
    }

    private static int stemOffset(byte[] typed) {
        return kindOffset(typed) - Integer.BYTES;
    }

    @Test
    public void legacyReleased21FieldRecordKeepsExactMetadataAndUnknownStem() throws Exception {
        assertEquals(List.of(note()), read(LEGACY).notes());
        assertEquals(0, read(LEGACY).notes().get(0).stemDirection());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, read(LEGACY).notes().get(0).kind());
    }

    @Test
    public void bothStemDirectionsAndUnknownRoundTripWithCclefs() throws Exception {
        for (int direction : new int[] {-1, 0, 1})
            for (int clef : new int[] {22, 24})
                for (var kind : ScoreNoteEvent.Kind.values()) {
                    var n = note().withClef(clef).withStemDirection(direction).withKind(kind);
                    var encoded = bytes(n);
                    assertEquals(direction, ByteBuffer.wrap(encoded).getInt(stemOffset(encoded)));
                    assertEquals(
                            kind == ScoreNoteEvent.Kind.PITCHED ? 0 : 1,
                            Byte.toUnsignedInt(encoded[kindOffset(encoded)]));
                    assertEquals(List.of(n), read(encoded).notes());
                }
    }

    @Test
    public void explicitNewFrameDoesNotRelabelLegacyBytes() throws Exception {
        var newBytes = bytes(note());
        var oldStemBytes = legacyStemBytes(0);
        assertEquals(96, newBytes.length);
        assertEquals(95, oldStemBytes.length);
        assertEquals(LEGACY.length + 9, newBytes.length);
        assertEquals(-24, new DataInputStream(new ByteArrayInputStream(newBytes)).readInt());
        assertEquals(-22, new DataInputStream(new ByteArrayInputStream(oldStemBytes)).readInt());
        assertEquals(1, new DataInputStream(new ByteArrayInputStream(LEGACY)).readInt());
        assertEquals(83, stemOffset(newBytes));
        assertEquals(87, kindOffset(newBytes));
        assertEquals(0, Byte.toUnsignedInt(newBytes[kindOffset(newBytes)]));
        assertEquals(
                1,
                Byte.toUnsignedInt(
                        bytes(note().withKind(ScoreNoteEvent.Kind.UNPITCHED))[
                                kindOffset(newBytes)]));
        assertArrayEquals(
                Arrays.copyOfRange(oldStemBytes, 8, oldStemBytes.length - 8),
                Arrays.copyOfRange(newBytes, 8, kindOffset(newBytes)));
        assertArrayEquals(
                Arrays.copyOfRange(oldStemBytes, oldStemBytes.length - 8, oldStemBytes.length),
                Arrays.copyOfRange(newBytes, kindOffset(newBytes) + 1, newBytes.length));
    }

    @Test
    public void unsupportedAnalysisMarkerAndOutOfRangeStemAreRejected() throws Exception {
        var bytes = bytes(note());
        ByteBuffer.wrap(bytes).putInt(-25);
        assertThrows(IOException.class, () -> read(bytes));
        for (int direction : new int[] {-2, 2}) {
            var bad = bytes(note());
            ByteBuffer.wrap(bad).putInt(stemOffset(bad), direction);
            assertEquals(0, Byte.toUnsignedInt(bad[kindOffset(bad)]));
            assertEquals(
                    "Decoder stem direction",
                    assertThrows(IOException.class, () -> read(bad)).getMessage());
        }
    }

    @Test
    public void truncatedStemAndExtraBytesCannotBecomeValidLegacyRecords() throws Exception {
        var typed = bytes(note());
        assertThrows(IOException.class, () -> read(Arrays.copyOf(typed, stemOffset(typed) + 3)));
        assertThrows(IOException.class, () -> read(Arrays.copyOf(typed, kindOffset(typed))));
        assertThrows(IOException.class, () -> read(Arrays.copyOf(typed, typed.length + 1)));
        var legacy = legacyStemBytes(0);
        assertThrows(IOException.class, () -> read(Arrays.copyOf(legacy, legacy.length - 9)));
        assertThrows(IOException.class, () -> read(Arrays.copyOf(legacy, legacy.length + 1)));
    }

    @Test
    public void explicitLegacy22FrameRetainsStemAndDefaultsOnlyKind() throws Exception {
        for (int direction : new int[] {-1, 0, 1}) {
            var legacy = legacyStemBytes(direction);
            assertEquals(95, legacy.length);
            assertEquals(direction, ByteBuffer.wrap(legacy).getInt(legacy.length - 12));
            var decoded = read(legacy).notes().get(0);
            assertEquals(note().withStemDirection(direction), decoded);
            assertEquals(ScoreNoteEvent.Kind.PITCHED, decoded.kind());
        }
    }

    @Test
    public void unknownKindCannotMasqueradeAsMalformedStem() throws Exception {
        for (int kind : new int[] {2, 255}) {
            var bad = bytes(note().withStemDirection(-1));
            bad[kindOffset(bad)] = (byte) kind;
            assertEquals(-1, ByteBuffer.wrap(bad).getInt(stemOffset(bad)));
            assertEquals(
                    "Decoder note kind",
                    assertThrows(IOException.class, () -> read(bad)).getMessage());
        }
        var legacy = legacyStemBytes(2);
        assertEquals(
                "Decoder stem direction",
                assertThrows(IOException.class, () -> read(legacy)).getMessage());
    }
}
