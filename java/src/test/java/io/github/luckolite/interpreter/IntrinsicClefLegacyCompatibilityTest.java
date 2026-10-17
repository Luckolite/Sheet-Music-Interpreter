// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original event/paired-tremolo records compared with the actual published legacy codec. */
public class IntrinsicClefLegacyCompatibilityTest {
    private OmrScoreInterpreter.Analysis score(int clef) {
        var a = new ScoreTremoloSource.Anchor(0, 0);
        var b = new ScoreTremoloSource.Anchor(0, 1);
        var p =
                new ScoreTremoloSource.Pair(
                        "original-pair",
                        List.of(a),
                        List.of(b),
                        2,
                        List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f)));
        var s = new ScoreTremoloSource(List.of(p), List.of(Optional.of(a), Optional.of(b)));
        var n = new ScoreNoteEvent(0, .2f, 0, 0, 1, .4f, false).withClef(clef);
        return new OmrScoreInterpreter.Analysis(
                List.of(n, new ScoreNoteEvent(0, .7f, 2, 0, 1, .4f, false).withClef(clef)),
                List.of(),
                List.of(),
                s);
    }

    private byte[] bytes(int clef, int marker, boolean legacy) throws Exception {
        var b = new ByteArrayOutputStream();
        if (legacy)
            IntrinsicClefLegacyWireFixture.writeAnalysis(
                    new DataOutputStream(b), score(clef), marker);
        else NativeDecoderWire.writeAnalysis(new DataOutputStream(b), score(clef), marker);
        return b.toByteArray();
    }

    @Test
    public void publishedOldReaderRejectsNewMarker() throws Exception {
        byte[] b = bytes(32, -27, false);
        assertThrows(
                IOException.class,
                () ->
                        IntrinsicClefLegacyWireFixture.readAnalysis(
                                new DataInputStream(new ByteArrayInputStream(b)), 1));
    }

    @Test
    public void oldClefPairedRecordsKeepByteIdentity() throws Exception {
        for (int clef : new int[] {18, 25, 30, 37})
            assertArrayEquals(bytes(clef, -26, true), bytes(clef, -26, false));
    }

    @Test
    public void newMarkerPreservesActualNonemptyPairedPayload() throws Exception {
        var expected = score(32);
        byte[] b = bytes(32, -27, false);
        var actual =
                NativeDecoderWire.readAnalysis(new DataInputStream(new ByteArrayInputStream(b)), 1);
        assertEquals(expected.notes(), actual.notes());
        assertEquals(expected.tremoloSource(), actual.tremoloSource());
        byte[] old = bytes(25, -26, false), fresh = bytes(25, -27, false);
        assertArrayEquals(
                Arrays.copyOfRange(old, 4, old.length), Arrays.copyOfRange(fresh, 4, fresh.length));
    }
}
