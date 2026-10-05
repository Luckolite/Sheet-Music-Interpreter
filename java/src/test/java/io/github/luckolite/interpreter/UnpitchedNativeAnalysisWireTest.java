// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original typed records and explicit historical layouts; no score or device data. */
public final class UnpitchedNativeAnalysisWireTest {
    private static ScoreNoteEvent note(ScoreNoteEvent.Kind kind, int stem) {
        return new ScoreNoteEvent(
                0, .375f, -4, 1, 2, .75f, true, 2, 3, -1, .5f, 5, .25f, 12, 23, true, .125f, true,
                -2, 10, 3, stem, kind);
    }

    private static OmrScoreInterpreter.Analysis score(ScoreNoteEvent... notes) {
        return new OmrScoreInterpreter.Analysis(
                List.of(notes),
                List.of(new ScoreKeyChange(0, -5)),
                List.of(new ScoreRestEvent(0, .5f, .2f, .07f, 1, 2, .125)));
    }

    private static byte[] encode(OmrScoreInterpreter.Analysis score) throws IOException {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(bytes), score);
        return bytes.toByteArray();
    }

    private static OmrScoreInterpreter.Analysis decode(byte[] bytes) throws IOException {
        return NativeDecoderWire.readAnalysis(
                new DataInputStream(new ByteArrayInputStream(bytes)), 1);
    }

    private static byte[] legacyNote(ScoreNoteEvent n, boolean stems) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(n.measureIndex());
        out.writeFloat(n.positionInMeasure());
        out.writeInt(n.staffStep());
        out.writeInt(n.staffIndex());
        out.writeInt(n.staffCount());
        out.writeFloat(n.pageY());
        out.writeBoolean(n.tiedFromPrevious());
        out.writeInt(n.augmentationDots());
        out.writeInt(n.beamCount());
        out.writeInt(n.writtenAccidental());
        out.writeFloat(n.unbeamedDurationBeats());
        out.writeInt(n.tupletDivisor());
        out.writeFloat(n.followingRestBeats());
        out.writeInt(n.articulations());
        out.writeInt(n.clefBottomDiatonic());
        out.writeBoolean(n.crossStaffBeam());
        out.writeFloat(n.leadingRestBeats());
        out.writeBoolean(n.compactOpening());
        out.writeInt(n.octaveShift());
        out.writeInt(n.boundaryTies());
        out.writeInt(n.tupletNormalNotes());
        if (stems) out.writeInt(n.stemDirection());
        return bytes.toByteArray();
    }

    private static byte[] legacy(boolean stems, ScoreNoteEvent... notes) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        if (stems) out.writeInt(-22);
        out.writeInt(notes.length);
        for (var n : notes) out.write(legacyNote(n, stems));
        out.writeInt(1);
        out.writeInt(0);
        out.writeInt(-5);
        out.writeInt(1);
        out.writeInt(0);
        out.writeFloat(.5f);
        out.writeFloat(.2f);
        out.writeFloat(.07f);
        out.writeInt(1);
        out.writeInt(2);
        out.writeDouble(.125);
        return bytes.toByteArray();
    }

    private static int kindOffset() throws IOException {
        return 8 + legacyNote(note(ScoreNoteEvent.Kind.PITCHED, 1), true).length;
    }

    @Test
    public void all23FieldsAndIdenticalPositionAttackKindsRoundTrip() throws Exception {
        var p = note(ScoreNoteEvent.Kind.PITCHED, 1);
        var u = note(ScoreNoteEvent.Kind.UNPITCHED, -1);
        var input = score(p, u, u, p);
        var actual = decode(encode(input));
        assertEquals(input, actual);
        assertEquals(4, actual.notes().size());
        assertEquals(List.of(p, u, u, p), actual.notes());
        assertEquals(3, actual.notes().get(1).tupletNormalNotes());
        assertEquals(-1, actual.notes().get(1).stemDirection());
    }

    @Test
    public void allThreeStemDirectionsRetainType() throws Exception {
        for (int direction = -1; direction <= 1; direction++)
            for (var kind : ScoreNoteEvent.Kind.values()) {
                var input = score(note(kind, direction));
                assertEquals(input, decode(encode(input)));
            }
    }

    @Test
    public void compressedTransportRetainsMixedTypesAndEveryField() throws Exception {
        var input =
                score(
                        note(ScoreNoteEvent.Kind.PITCHED, 1),
                        note(ScoreNoteEvent.Kind.UNPITCHED, -1));
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.packet(
                new DataOutputStream(bytes), out -> NativeDecoderWire.writeAnalysis(out, input));
        try (var in =
                NativeDecoderWire.packet(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))) {
            assertEquals(input, NativeDecoderWire.readAnalysis(in, 1));
        }
    }

    @Test
    public void typedWriterUsesDistinctMinus23Marker() throws Exception {
        var in =
                new DataInputStream(
                        new ByteArrayInputStream(
                                encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)))));
        assertEquals(-23, in.readInt());
        assertEquals(1, in.readInt());
        assertEquals(0x4d534431, NativeDecoderWire.MAGIC);
    }

    @Test
    public void legacyPositive21CountDefaultsOnlyKindAndMissingStem() throws Exception {
        var a = note(ScoreNoteEvent.Kind.PITCHED, 1);
        var b = note(ScoreNoteEvent.Kind.PITCHED, -1);
        assertEquals(
                score(a.withStemDirection(0), b.withStemDirection(0)), decode(legacy(false, a, b)));
    }

    @Test
    public void legacyMinus22RetainsCurrentRatioAndStemDefaultsKind() throws Exception {
        var a = note(ScoreNoteEvent.Kind.PITCHED, 1);
        var b = note(ScoreNoteEvent.Kind.PITCHED, -1);
        assertEquals(score(a, b), decode(legacy(true, a, b)));
    }

    @Test
    public void legacyZeroNotesAndEmptyTypedRecordsAreAccepted() throws Exception {
        assertEquals(score(), decode(legacy(false)));
        assertEquals(score(), decode(legacy(true)));
        assertEquals(score(), decode(encode(score())));
    }

    @Test
    public void unknownSignedMarkersFailClosed() throws Exception {
        for (int marker : new int[] {-1, -21, -24, Integer.MIN_VALUE}) {
            byte[] body = encode(score());
            ByteBuffer.wrap(body).putInt(marker);
            assertThrows(IOException.class, () -> decode(body));
        }
    }

    @Test
    public void unknownKindByteTwoAndUnsigned255AreRejected() throws Exception {
        for (int kind : new int[] {2, 255}) {
            byte[] body = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
            body[kindOffset()] = (byte) kind;
            assertThrows(IOException.class, () -> decode(body));
        }
    }

    @Test
    public void missingKindCannotBorrowSectionCountOrBecomePitched() throws Exception {
        byte[] full = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
        int offset = kindOffset();
        byte[] body = new byte[full.length - 1];
        System.arraycopy(full, 0, body, 0, offset);
        System.arraycopy(full, offset + 1, body, offset, full.length - offset - 1);
        assertThrows(IOException.class, () -> decode(body));
    }

    @Test
    public void everyTruncatedTypedPrefixIsRejectedAsIoFailure() throws Exception {
        byte[] full = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
        for (int length = 0; length < full.length; length++) {
            byte[] body = Arrays.copyOf(full, length);
            assertThrows("prefix " + length, IOException.class, () -> decode(body));
        }
    }

    @Test
    public void trailingBodyByteIsRejected() throws Exception {
        byte[] full = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
        assertThrows(IOException.class, () -> decode(Arrays.copyOf(full, full.length + 1)));
    }

    @Test
    public void compressedTrailingAnalysisByteIsRejected() throws Exception {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.packet(
                new DataOutputStream(bytes),
                out -> {
                    NativeDecoderWire.writeAnalysis(
                            out, score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
                    out.writeByte(0);
                });
        try (var in =
                NativeDecoderWire.packet(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))) {
            assertThrows(IOException.class, () -> NativeDecoderWire.readAnalysis(in, 1));
        }
    }

    @Test
    public void currentStemAndNormalRatioValidationAreNotWeakened() throws Exception {
        for (int direction : new int[] {-2, 2}) {
            byte[] body = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
            ByteBuffer.wrap(body).putInt(kindOffset() - 4, direction);
            assertThrows(IOException.class, () -> decode(body));
        }
        for (int normal : new int[] {0, 17}) {
            byte[] body = encode(score(note(ScoreNoteEvent.Kind.UNPITCHED, 1)));
            ByteBuffer.wrap(body).putInt(kindOffset() - 8, normal);
            assertThrows(IOException.class, () -> decode(body));
        }
    }
}
