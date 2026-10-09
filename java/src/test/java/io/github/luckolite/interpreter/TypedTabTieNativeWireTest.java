// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scalar records prove layout gates independently of optical recognition. */
public final class TypedTabTieNativeWireTest {
    private static ScoreNoteEvent note(int flags) {
        return new ScoreNoteEvent(
                0,
                .5f,
                0,
                0,
                1,
                .5f,
                false,
                0,
                0,
                2,
                4,
                1,
                0,
                0,
                18,
                false,
                0,
                false,
                0,
                flags,
                1,
                0,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private static OmrScoreInterpreter.Analysis score(ScoreNoteEvent note) {
        return new OmrScoreInterpreter.Analysis(
                List.of(note), List.of(), List.of(new ScoreRestEvent(0, .5f, .5f, .05f, 0, 1, 4)));
    }

    private static byte[] encode(ScoreNoteEvent note, int marker) throws IOException {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(bytes), score(note), marker);
        return bytes.toByteArray();
    }

    private static OmrScoreInterpreter.Analysis decode(byte[] bytes) throws IOException {
        return NativeDecoderWire.readAnalysis(
                new DataInputStream(new ByteArrayInputStream(bytes)), 1);
    }

    @Test
    public void typedCurrentRoundTripPreservesEveryExistingFieldAndRecordSize() throws Exception {
        int flags = TabTieIdentity.encode(8, 6, 7, 36, 91);
        var expected = note(flags);
        byte[] typed = encode(expected, -25), legacy = encode(note(0), -24);
        assertEquals(score(expected), decode(typed));
        assertEquals(-25, ByteBuffer.wrap(typed).getInt());
        assertEquals(flags, ByteBuffer.wrap(typed).getInt(8 + 67));
        assertEquals(8 + 80 + 4 + 4 + 33, typed.length);
        assertEquals(legacy.length, typed.length);
        assertArrayEquals(
                java.util.Arrays.copyOfRange(legacy, 8, 8 + 67),
                java.util.Arrays.copyOfRange(typed, 8, 8 + 67));
        assertArrayEquals(
                java.util.Arrays.copyOfRange(legacy, 8 + 71, legacy.length),
                java.util.Arrays.copyOfRange(typed, 8 + 71, typed.length));
    }

    @Test
    public void currentDefaultWriterUsesMinus25() throws Exception {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(bytes), score(note(0)));
        assertEquals(-25, ByteBuffer.wrap(bytes.toByteArray()).getInt());
    }

    @Test
    public void historicalWritersAndReadersRejectTypedFlags() throws Exception {
        var n = note(TabTieIdentity.encode(8, 0, 6, 5, 55));
        for (int marker : new int[] {-24, -23})
            assertThrows(IOException.class, () -> encode(n, marker));
        // -24 has exactly the same physical layout: rejection must be semantic.
        byte[] data = encode(n, -25);
        ByteBuffer.wrap(data).putInt(-24);
        assertThrows(IOException.class, () -> decode(data));
    }

    @Test
    public void everyLegacyDirectionRetainsHistoricalMinus24RoundTrip() throws Exception {
        for (int flags = 0; flags <= 15; flags++) {
            var n = note(flags);
            assertEquals(score(n), decode(encode(n, -24)));
        }
    }

    @Test
    public void malformedPackedIdentityRejectsAtReaderBoundary() throws Exception {
        int valid = TabTieIdentity.encode(8, 0, 6, 5, 55);
        for (int flags :
                new int[] {
                    -1,
                    16,
                    40,
                    valid | 1 << 22,
                    8 | 16 | 6 << 5,
                    8 | 16 | 37 << 9,
                    8 | 16 | 36 << 9 | 92 << 15
                }) {
            byte[] data = encode(note(0), -25);
            ByteBuffer.wrap(data).putInt(8 + 67, flags);
            assertThrows(IOException.class, () -> decode(data));
        }
    }

    @Test
    public void unpitchedTypedIdentityRejectsOnWriteAndRead() throws Exception {
        var n = note(TabTieIdentity.encode(8, 0, 6, 5, 55));
        assertThrows(
                IOException.class, () -> encode(n.withKind(ScoreNoteEvent.Kind.UNPITCHED), -25));
        byte[] data = encode(n, -25);
        data[8 + 79] = 1;
        assertThrows(IOException.class, () -> decode(data));
    }

    private static byte[] historical(byte[] current, int marker) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        if (marker != 0) out.writeInt(marker);
        out.writeInt(1);
        out.write(current, 8, marker == -23 ? 80 : marker == -22 ? 79 : 75);
        out.writeInt(0); // Keys.
        out.writeInt(1); // Rests.
        out.write(current, 96, 32);
        return bytes.toByteArray();
    }

    @Test
    public void everyHistoricalAnalysisLayoutRejectsTypedIdentity() throws Exception {
        var typed = note(TabTieIdentity.encode(8, 0, 6, 5, 55));
        for (int marker : new int[] {-23, -22, 0}) {
            byte[] old = historical(encode(typed, -25), marker);
            assertThrows(IOException.class, () -> decode(old));
            var legacy = note(8);
            assertEquals(score(legacy), decode(historical(encode(legacy, -25), marker)));
        }
    }
}
