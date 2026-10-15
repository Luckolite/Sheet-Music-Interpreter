// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;

public class BassOctaveWireTest {
    static byte[] encode(ScoreNoteEvent n, int marker) throws Exception {
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(
                new DataOutputStream(bytes),
                new OmrScoreInterpreter.Analysis(List.of(n), List.of()),
                marker);
        return bytes.toByteArray();
    }

    static ScoreNoteEvent decode(byte[] b) throws Exception {
        return NativeDecoderWire.readAnalysis(new DataInputStream(new ByteArrayInputStream(b)), 1)
                .notes()
                .get(0);
    }

    @org.junit.Test
    public void versionedRoundtripAndDowngrade() throws Exception {
        var n =
                new ScoreNoteEvent(
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
                        1f,
                        1,
                        0f,
                        0,
                        25,
                        false,
                        0f,
                        false,
                        0,
                        0,
                        1,
                        0,
                        ScoreNoteEvent.Kind.PITCHED);
        byte[] paired = encode(n, -26);
        if (!decode(paired).equals(n)) throw new AssertionError("paired roundtrip");
        try {
            encode(n, -25);
            throw new AssertionError("downgrade write accepted");
        } catch (IOException e) {
            if (!e.getMessage().contains("octave-bass")) throw e;
        }
        java.nio.ByteBuffer.wrap(paired).putInt(-25);
        try {
            decode(paired);
            throw new AssertionError("legacy read accepted");
        } catch (IOException e) {
            if (!e.getMessage().contains("octave-bass")) throw e;
        }
        var plain = n.withClef(18);
        if (!decode(encode(plain, -25)).equals(plain))
            throw new AssertionError("plain bass changed");
        System.out.println(
                "PASS: paired octave-bass roundtrip, downgrade write rejection, legacy read rejection, plain bass legacy roundtrip");
    }
}
