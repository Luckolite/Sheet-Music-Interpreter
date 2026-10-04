// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
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

    @Test
    public void legacyReleased21FieldRecordKeepsExactMetadataAndUnknownStem() throws Exception {
        assertEquals(List.of(note()), read(LEGACY).notes());
        assertEquals(0, read(LEGACY).notes().get(0).stemDirection());
    }

    @Test
    public void bothStemDirectionsAndUnknownRoundTripWithCclefs() throws Exception {
        for (int direction : new int[] {-1, 0, 1})
            for (int clef : new int[] {22, 24}) {
                var n = note().withClef(clef).withStemDirection(direction);
                assertEquals(List.of(n), read(bytes(n)).notes());
            }
    }

    @Test
    public void explicitNewFrameDoesNotRelabelLegacyBytes() throws Exception {
        var newBytes = bytes(note());
        assertEquals(LEGACY.length + 8, newBytes.length);
        assertEquals(-22, new DataInputStream(new ByteArrayInputStream(newBytes)).readInt());
        assertEquals(1, new DataInputStream(new ByteArrayInputStream(LEGACY)).readInt());
    }

    @Test
    public void unsupportedAnalysisMarkerAndOutOfRangeStemAreRejected() throws Exception {
        var bytes = bytes(note());
        bytes[3] = (byte) -23;
        assertThrows(IOException.class, () -> read(bytes));
        var bad = bytes(note());
        bad[86] = 2;
        assertThrows(IOException.class, () -> read(bad));
    }

    @Test
    public void truncatedStemAndExtraBytesCannotBecomeValidLegacyRecords() throws Exception {
        assertThrows(IOException.class, () -> read(Arrays.copyOf(bytes(note()), 86)));
        assertThrows(
                IOException.class,
                () -> read(Arrays.copyOf(bytes(note()), bytes(note()).length + 1)));
    }
}
