// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TabAnnotationFrameOriginalTest {
    static final int W = 300, H = 300;

    static ScoreClockContextSource capture(
            PartProducerSourceOriginalTest.Page p, byte[] processed, byte[] original) {
        var parts =
                ScorePartProducerSource.capture(
                        p.source, p.gray, W, H, PartProducerSourceOriginalTest.rows);
        return ScoreClockContextCapture.capture(
                new byte[W * H],
                processed,
                W,
                H,
                new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of()),
                null,
                parts,
                null,
                original);
    }

    static byte[] suppressed(byte[] original) {
        return TablatureDecoder.withoutTabs(
                original,
                W,
                H,
                // A decoded fret confirms this synthetic TAB band; bare rules cannot suppress
                // notation.
                List.of(
                        new TablatureDecoder.Staff(
                                72,
                                8,
                                0,
                                List.of(new TablatureDecoder.Fret(150, 72, 0, 3)),
                                List.of())),
                true);
    }

    @Test
    public void actualTabSuppressionKeepsEveryOriginalWordNumberAndRival() throws Exception {
        var p = new PartProducerSourceOriginalTest.Page(true, false, false);
        var before = p.gray.clone();
        var processed = suppressed(p.gray);
        assertFalse(Arrays.equals(p.gray, processed));
        var normal = capture(p, p.gray, p.gray);
        var tab = capture(p, processed, p.gray);
        assertEquals(normal.partWords(), tab.partWords());
        assertEquals(normal.numbers(), tab.numbers());
        assertEquals(5, tab.partWords().size());
        assertEquals(2, tab.numbers().size());
        assertTrue(tab.partWords().stream().anyMatch(w -> w.canonicalPart().equals("violin")));
        assertEquals(ScoreClockContextCapture.digest(processed), tab.origin().rasterSha256());
        assertEquals(
                ScoreClockContextCapture.digest(before), tab.origin().annotationRasterSha256());
        assertNotEquals(tab.origin().rasterSha256(), tab.origin().annotationRasterSha256());
        assertArrayEquals(before, p.gray);
        assertEquals(2, ByteBuffer.wrap(ScoreClockContextWire.encode(tab), 8, 4).getInt());
        assertEquals(tab, ScoreClockContextWire.decode(ScoreClockContextWire.encode(tab), 0, 4, 0));
    }

    @Test
    public void foreignAnnotationPixelsCannotBeRelabelledAsTheOriginalObservations() {
        var p = new PartProducerSourceOriginalTest.Page(true, false, false);
        var foreign = p.gray.clone();
        foreign[0] = 0;
        var c = capture(p, suppressed(p.gray), foreign);
        assertTrue(c.partWords().isEmpty());
        assertTrue(c.numbers().isEmpty());
    }

    @Test
    public void staleAnnotationSnapshotCannotBeUsedAfterMutation() {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        var parts =
                ScorePartProducerSource.capture(
                        p.source, p.gray, W, H, PartProducerSourceOriginalTest.rows);
        p.gray[0] = 0;
        var c =
                ScoreClockContextCapture.capture(
                        new byte[W * H],
                        suppressed(p.gray),
                        W,
                        H,
                        new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of()),
                        null,
                        parts,
                        null,
                        p.gray);
        assertTrue(c.partWords().isEmpty());
        assertTrue(c.numbers().isEmpty());
    }

    @Test
    public void missingOrWrongSizedOriginalFrameRetainsUnknown() {
        for (byte[] original : new byte[][] {null, new byte[4]}) {
            var p = new PartProducerSourceOriginalTest.Page(false, false, false);
            var c = capture(p, suppressed(p.gray), original);
            assertTrue(c.partWords().isEmpty());
            assertTrue(c.numbers().isEmpty());
            assertEquals("", c.origin().annotationRasterSha256());
            assertTrue(
                    c.missing().stream()
                            .anyMatch(
                                    m ->
                                            m.requirement()
                                                    == ScoreClockContextSource.Requirement
                                                            .PART_CONTINUITY));
        }
    }

    @Test
    public void noTabUsesSchemaOneAndDefaultSourceFrame() throws Exception {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        assertSame(p.gray, TablatureDecoder.withoutTabs(p.gray, W, H, List.of(), true));
        var c = capture(p, p.gray, p.gray);
        assertEquals(c.origin().rasterSha256(), c.origin().annotationRasterSha256());
        assertEquals(1, ByteBuffer.wrap(ScoreClockContextWire.encode(c), 8, 4).getInt());
    }

    @Test
    public void documentNativeAndWireCopiesPreserveBothFrames() throws Exception {
        var p = new PartProducerSourceOriginalTest.Page(true, false, false);
        var c = capture(p, suppressed(p.gray), p.gray);
        var page =
                new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of())
                        .withClockContextSource(c);
        var bound =
                ScoreClockContextAssembly.bindDocument(page, "a".repeat(64), 1, 3)
                        .clockContextSource();
        assertEquals(c.origin().annotationRasterSha256(), bound.origin().annotationRasterSha256());
        var analysis =
                new OmrScoreInterpreter.Analysis(
                        List.of(),
                        List.of(),
                        List.of(),
                        ScoreTremoloSource.empty(0),
                        ScoreClockProducerSource.unavailable(0),
                        bound);
        var bytes = new ByteArrayOutputStream();
        NativeDecoderWire.writeAnalysis(new DataOutputStream(bytes), analysis);
        var read =
                NativeDecoderWire.readAnalysis(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 4);
        assertEquals(bound, read.clockContextSource());
    }

    public static void main(String[] args) throws Exception {
        var p = new PartProducerSourceOriginalTest.Page(true, false, false);
        var tab = capture(p, suppressed(p.gray), p.gray);
        java.nio.file.Files.write(
                java.nio.file.Path.of(args[0]), ScoreClockContextWire.encode(tab));
        java.nio.file.Files.writeString(
                java.nio.file.Path.of(args[1]), new com.google.gson.Gson().toJson(tab));
    }
}
