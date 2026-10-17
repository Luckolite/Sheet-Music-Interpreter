// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntrinsicBassClefWireTest {
    private ScoreNoteEvent note(int clef) {
        return new ScoreNoteEvent(0, .4f, 3, 1, 2, .5f, false).withClef(clef);
    }

    private byte[] encode(ScoreNoteEvent n, int marker) throws Exception {
        var b = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(
                new DataOutputStream(b),
                new OmrScoreInterpreter.Analysis(List.of(n), List.of()),
                marker);
        return b.toByteArray();
    }

    private ScoreNoteEvent decode(byte[] b) throws Exception {
        return NativeDecoderWire.readAnalysis(new DataInputStream(new ByteArrayInputStream(b)), 1)
                .notes()
                .get(0);
    }

    @Test
    public void newIntrinsicValueSurvivesCopiesWithoutDirection() {
        var n = note(32);
        assertEquals(32, n.clefBottomDiatonic());
        assertEquals(0, n.octaveShift());
        for (var copy :
                List.of(
                        n.withKind(ScoreNoteEvent.Kind.PITCHED),
                        n.withStemDirection(-1),
                        n.withTupletRatio(3, 2),
                        n.withBoundaryTies(1),
                        n.withOctaveShift(-1),
                        n.withCompactOpening(),
                        n.withLeadingRest(.25f),
                        n.withCrossStaffBeam(),
                        n.withArticulations(NoteArticulation.STACCATO)))
            assertEquals(32, copy.clefBottomDiatonic());
        assertEquals(35, n.diatonicPitchIdentity());
    }

    @Test
    public void newMarkerRoundTripsIntrinsicValueAndPayload() throws Exception {
        var n = note(32);
        assertEquals(n, decode(encode(n, -27)));
        assertEquals(encode(note(25), -26).length, encode(n, -27).length);
    }

    @Test
    public void legacyWritersRefuseIntrinsicValue() throws Exception {
        for (int marker : new int[] {-23, -24, -25, -26})
            try {
                encode(note(32), marker);
                fail("legacy write32");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("two-octave"));
            }
    }

    @Test
    public void relabelingNewRecordAsLegacyFailsClosed() throws Exception {
        for (int marker : new int[] {-23, -24, -25, -26}) {
            byte[] b = encode(note(32), -27);
            java.nio.ByteBuffer.wrap(b).putInt(marker);
            try {
                decode(b);
                fail("legacy read32");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("two-octave"));
            }
        }
    }

    @Test
    public void compatibleOldClefsRetainCurrentRoundTrips() throws Exception {
        for (int clef : new int[] {-1, 18, 22, 24, 25, 30, 37})
            assertEquals(note(clef), decode(encode(note(clef), -26)));
    }

    @Test
    public void ordinaryBassRetainsOlderRoundTrip() throws Exception {
        assertEquals(note(18), decode(encode(note(18), -25)));
    }

    @Test
    public void defaultMarkerChangesOnlyForNewIntrinsicValue() throws Exception {
        for (int clef : new int[] {18, 25, 30, 32, 37}) {
            var b = new ByteArrayOutputStream();
            NativeDecoderWire.writeAnalysis(
                    new DataOutputStream(b),
                    new OmrScoreInterpreter.Analysis(List.of(note(clef)), List.of()));
            assertEquals(
                    clef == 32 ? -27 : -26,
                    new DataInputStream(new ByteArrayInputStream(b.toByteArray())).readInt());
        }
    }
}
