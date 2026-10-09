// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the copied real analysis writer/readers, including actual unchanged legacy-writer bytes. */
public class RestKindAnalysisWireTest {
    static ScoreNoteEvent note() {
        return new ScoreNoteEvent(
                        0, .375f, -4, 1, 2, .75f, true, 2, 3, -1, .5f, 3, .25f, 12, 23, true, .125f,
                        true, -2)
                .withBoundaryTies(10)
                .withTupletRatio(5, 3)
                .withStemDirection(-1);
    }

    static ScoreRestEvent literal() {
        return new ScoreRestEvent(0, .5f, .2f, .03f, 1, 2, 4);
    }

    static ScoreRestEvent full() {
        return ScoreRestEvent.fullMeasure(0, .5f, .2f, .03f, 1, 2);
    }

    static OmrScoreInterpreter.Analysis score(boolean typed) {
        return new OmrScoreInterpreter.Analysis(
                List.of(typed ? note().withKind(ScoreNoteEvent.Kind.UNPITCHED) : note()),
                List.of(new ScoreKeyChange(0, -5)),
                List.of(typed ? full() : literal(), literal()));
    }

    static byte[] encoded(OmrScoreInterpreter.Analysis s, int marker) throws Exception {
        var out = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(out), s, marker);
        return out.toByteArray();
    }

    static byte[] encoded(OmrScoreInterpreter.Analysis s) throws Exception {
        var out = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(out), s);
        return out.toByteArray();
    }

    static byte[] oracle(OmrScoreInterpreter.Analysis s) throws Exception {
        var out = new ByteArrayOutputStream();
        LegacyNativeDecoderWire.writeAnalysis(new DataOutputStream(out), s);
        return out.toByteArray();
    }

    static OmrScoreInterpreter.Analysis read(byte[] b) throws Exception {
        return NativeDecoderWire.readAnalysis(new DataInputStream(new ByteArrayInputStream(b)), 1);
    }

    static void invalid(byte[] b) {
        assertThrows(IOException.class, () -> read(b));
    }

    @Test
    public void allActualNoteKeyRestFieldsAndKindsRoundTrip() throws Exception {
        assertEquals(score(true), read(encoded(score(true))));
    }

    @Test
    public void newMarkerAndActualThirtyThreeByteRestRecords() throws Exception {
        var old = oracle(score(false));
        var n = encoded(score(false));
        assertEquals(-24, ByteBuffer.wrap(n).getInt());
        assertEquals(old.length + 2, n.length);
        assertArrayEquals(Arrays.copyOfRange(old, 4, 104), Arrays.copyOfRange(n, 4, 104));
        for (int i = 0; i < 2; i++) {
            assertArrayEquals(
                    Arrays.copyOfRange(old, 104 + i * 32, 136 + i * 32),
                    Arrays.copyOfRange(n, 104 + i * 33, 136 + i * 33));
            assertEquals(0, n[136 + i * 33]);
        }
    }

    @Test
    public void explicitLegacyLiteralOutputIsByteExactActualOracle() throws Exception {
        assertArrayEquals(oracle(score(false)), encoded(score(false), -23));
    }

    @Test
    public void oldMinus23RecordsDefaultLiteralAndRetainEveryOtherField() throws Exception {
        assertEquals(score(false), read(oracle(score(false))));
    }

    @Test
    public void minus22StemFormatDefaultsLiteralRestAndPitchedNote() throws Exception {
        var old = oracle(score(false));
        var out = new ByteArrayOutputStream();
        out.write(old, 0, 87);
        out.write(old, 88, old.length - 88);
        var b = out.toByteArray();
        ByteBuffer.wrap(b).putInt(-22);
        assertEquals(score(false), read(b));
    }

    @Test
    public void oldestCountFormatDefaultsUnknownStemAndLiteralRest() throws Exception {
        var old = oracle(score(false));
        var out = new ByteArrayOutputStream();
        out.write(old, 4, 79);
        out.write(old, 88, old.length - 88);
        var b = out.toByteArray();
        var expected =
                new OmrScoreInterpreter.Analysis(
                        List.of(note().withStemDirection(0)),
                        score(false).keyChanges(),
                        score(false).rests());
        assertEquals(expected, read(b));
    }

    @Test
    public void explicitFullKindCannotBeDroppedByLegacyWriterTarget() {
        assertThrows(IOException.class, () -> encoded(score(true), -23));
    }

    @Test
    public void unsupportedAnalysisWriterTargetRejects() {
        assertThrows(IOException.class, () -> encoded(score(false), -22));
    }

    @Test
    public void unknownRestKindRejectsInsteadOfBecomingLiteral() throws Exception {
        var b = encoded(score(true));
        b[136] = (byte) 255;
        invalid(b);
    }

    @Test
    public void explicitFullKindRequiresUndottedWholeBase() throws Exception {
        var b = encoded(score(true));
        ByteBuffer.wrap(b).putDouble(128, 3);
        invalid(b);
    }

    @Test
    public void everyTruncatedTypedPrefixRejects() throws Exception {
        var b = encoded(score(true));
        for (int end = 0; end < b.length; end++) invalid(Arrays.copyOf(b, end));
    }

    @Test
    public void literalNonfiniteAndNonpositiveDurationRejects() throws Exception {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, -1}) {
            var b = encoded(score(false));
            ByteBuffer.wrap(b).putDouble(128, value);
            invalid(b);
        }
    }

    @Test
    public void unsupportedFutureAnalysisMarkerRejects() throws Exception {
        var b = encoded(score(false));
        ByteBuffer.wrap(b).putInt(-25);
        invalid(b);
    }

    @Test
    public void negativeRestCountRejects() throws Exception {
        var b = encoded(score(false));
        ByteBuffer.wrap(b).putInt(100, -1);
        invalid(b);
    }

    @Test
    public void trailingBytesRejectEvenAfterValidTypedRest() throws Exception {
        var b = encoded(score(true));
        invalid(Arrays.copyOf(b, b.length + 1));
    }

    @Test
    public void equalGeometryFullAndLiteralRemainSeparate() throws Exception {
        var s = read(encoded(score(true)));
        assertEquals(2, s.rests().size());
        assertTrue(s.rests().get(0).isFullMeasure());
        assertFalse(s.rests().get(1).isFullMeasure());
        assertNotEquals(s.rests().get(0), s.rests().get(1));
    }
}
