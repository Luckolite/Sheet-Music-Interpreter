// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ClockContextCaptureOriginalTest {
    private static final String SHA = "a".repeat(64);

    private static ScorePageInterpretation score() {
        return new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of());
    }

    private static ScorePageInterpretation captured(boolean rival) {
        var page = new PartProducerSourceOriginalTest.Page(rival, false, false);
        var parts =
                ScorePartProducerSource.capture(
                        page.source, page.gray, 300, 300, PartProducerSourceOriginalTest.rows);
        var score = score();
        return score.withClockContextSource(
                ScoreClockContextCapture.capture(
                        new byte[90000], page.gray, 300, 300, score, null, parts, null));
    }

    @Test
    public void realReaderFactsSurviveTypedWireWithoutClaimingPartOrClockCompleteness()
            throws Exception {
        var source = captured(false).clockContextSource();
        assertEquals(4, source.partWords().size());
        assertEquals(2, source.numbers().size());
        assertTrue(source.partWords().stream().anyMatch(p -> p.canonicalPart().equals("piano")));
        assertTrue(
                source.partWords().stream()
                        .noneMatch(
                                p -> p.status() == ScoreClockContextSource.Status.SOURCE_PREMISE));
        assertEquals(
                source,
                ScoreClockContextWire.decode(ScoreClockContextWire.encode(source), 0, 4, 0));
        assertTrue(
                source.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .PART_CONTINUITY));
        assertTrue(
                source.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .VOICE_OWNERSHIP));
    }

    @Test
    public void everyRivalWordRemainsVisible() {
        var source = captured(true).clockContextSource();
        assertEquals(5, source.partWords().size());
        assertTrue(source.partWords().stream().anyMatch(p -> p.canonicalPart().equals("violin")));
        assertTrue(
                source.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .PART_CONTINUITY));
    }

    @Test
    public void documentBindingPreservesTheProducerRasterAndEveryFact() {
        var score = captured(false);
        var old = score.clockContextSource();
        var bound = ScoreClockContextAssembly.bindDocument(score, SHA, 1, 2).clockContextSource();
        assertTrue(bound.origin().boundDocument());
        assertEquals(old.origin().rasterSha256(), bound.origin().rasterSha256());
        assertEquals(old.partWords(), bound.partWords());
        assertEquals(old.numbers(), bound.numbers());
        assertFalse(
                bound.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .DOCUMENT_ORIGIN));
        assertTrue(
                bound.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .METER_SCOPE));
    }

    @Test
    public void nakedHostMetadataDoesNotCreateRasterAuthority() {
        var bound = ScoreClockContextAssembly.bindDocument(score(), SHA, 0, 1);
        assertFalse(bound.clockContextSource().origin().boundDocument());
        assertTrue(
                bound.clockContextSource().missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .DOCUMENT_ORIGIN));
    }

    @Test
    public void foreignDocumentPageCountOrOrderRefusesInsteadOfRelabelling() {
        var bound = ScoreClockContextAssembly.bindDocument(captured(false), SHA, 0, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreClockContextAssembly.bindDocument(bound, "b".repeat(64), 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreClockContextAssembly.bindDocument(bound, SHA, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreClockContextAssembly.bindDocument(bound, SHA, 0, 3));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreClockContextAssembly.intake(SHA, 2, List.of(score(), bound)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreClockContextAssembly.intake(SHA, 1, List.of(bound)));
    }

    @Test
    public void absentPhysicalPageRemainsExplicitAtAssembly() {
        var bound = ScoreClockContextAssembly.bindDocument(captured(false), SHA, 1, 2);
        var intake = ScoreClockContextAssembly.intake(SHA, 2, List.of(score(), bound));
        assertEquals(2, intake.pages().size());
        assertFalse(intake.pages().get(0).boundDocument());
        assertTrue(intake.pages().get(1).boundDocument());
        assertEquals(bound.clockContextSource(), intake.pages().get(1).context());
        assertThrows(UnsupportedOperationException.class, () -> intake.pages().clear());
    }

    @Test
    public void cacheEnvelopeRejectsForeignFactsAndDoesNotPromoteUnknownOrigin() {
        var unknown = score();
        assertTrue(ScoreClockContextAssembly.matchesPage(unknown, SHA, 0, 9));
        assertFalse(unknown.clockContextSource().origin().boundDocument());
        var known = ScoreClockContextAssembly.bindDocument(captured(false), SHA, 1, 9);
        assertTrue(ScoreClockContextAssembly.matchesPage(known, SHA, 1, 9));
        assertFalse(ScoreClockContextAssembly.matchesPage(known, "b".repeat(64), 1, 9));
        assertFalse(ScoreClockContextAssembly.matchesPage(known, SHA, 2, 9));
        assertFalse(ScoreClockContextAssembly.matchesPage(known, SHA, 1, 8));
        assertTrue(ScoreClockContextAssembly.matchesPage(known, SHA, 1, 0));
    }

    @Test
    public void unsupportedScoreEditInvalidatesContextRatherThanBorrowingOldOwners() {
        var source = captured(false);
        assertEquals(
                source.clockContextSource(),
                source.withBeamSource(source.beamSource()).clockContextSource());
        assertFalse(source.withNotes(List.of(), List.of()).clockContextSource().hasFacts());
    }
}
