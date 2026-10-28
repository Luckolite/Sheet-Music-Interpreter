// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-root, raw-family rivals and optional-resource controls on original rasters. */
public final class WholeStaffSlurRuleControlsTest {
    @Test
    public void validLegacyLargePageKeepsItsOriginalScoreAndStatus() {
        int width = 5000, height = 4001;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        var note =
                new ScoreNoteEvent(
                        0, .5f, 0, 0, 1, 124f / height, false, 0, 0, 0, 1, 1, 0, 0, 30, false);
        var score =
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
        var result =
                PrintedSlurRecognition.captureObserved(
                                score, 0, gray, null, width, height, List.of(staff()))
                        .orElseThrow();
        assertSame(score, result.score());
        assertEquals(1, result.unprovedStaffs());
        assertFalse(result.limitReached());
        assertTrue(result.score().slurSource().spans().isEmpty());
        assertTrue(result.score().slurSource().candidates().isEmpty());
    }

    @Test
    public void optionalCapacityRefusesWithoutNarrowingValidRasterDomain() {
        int width = 5000, height = 4001;
        byte[] gray = new byte[width * height];
        var page = new WholeStaffSlurRules.Page(gray, width, height);
        assertTrue(page.failed);
        assertNull(page.prove(gray, width, height, staff()));
        assertFalse(page.finish());
        assertEquals(0, page.projections);
        assertEquals(0, page.workUsed);
    }

    static final int W = 720, H = 240;

    static PlayingTechniqueDetector.Staff staff() {
        return new PlayingTechniqueDetector.Staff(60, 124, 16, 0, 1);
    }

    static byte[] raw() {
        return WholeStaffSlurFrameOriginalTest.fixture(.4f, -1);
    }

    @Test
    public void exactSourceFrameProjectionIsReused() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H);
        var first = p.prove(g, W, H, staff());
        assertNotNull(first);
        assertSame(first, p.prove(g, W, H, staff()));
        assertEquals(1, p.projections);
        assertTrue(p.finish());
        assertTrue(p.workUsed <= p.workLimit);
        assertTrue(p.bytesUsed <= p.byteLimit);
    }

    @Test
    public void anotherRawArrayCannotBorrowProof() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g.clone(), W, H, staff()));
        assertFalse(p.finish());
    }

    @Test
    public void rawMutationDiscardsEveryNewProof() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNotNull(p.prove(g, W, H, staff()));
        g[0] = 0;
        assertFalse(p.finish());
        assertNull(p.prove(g, W, H, staff()));
    }

    @Test
    public void zeroWorkRefusesBeforeProjection() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H, 0, 100000);
        assertNull(p.prove(g, W, H, staff()));
        assertTrue(p.failed);
        assertEquals(0, p.projections);
        assertFalse(p.finish());
    }

    @Test
    public void zeroStorageRefusesBeforeProjection() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H, 10000000, 0);
        assertNull(p.prove(g, W, H, staff()));
        assertTrue(p.failed);
        assertEquals(0, p.projections);
    }

    @Test
    public void completeSixthRuleCannotBeResolvedByNominalSeed() {
        var g = WholeStaffSlurFrameOriginalTest.fixture(1, -1);
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, staff()));
        assertTrue(p.finish());
    }

    @Test
    public void exactExistingSixthCoverageBoundaryRefuses() {
        var g = WholeStaffSlurFrameOriginalTest.fixture(.6f, -1);
        for (int x = 0; x < (int) Math.ceil(W * .6f); x++) g[140 * W + x] = 100;
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, staff()));
    }

    @Test
    public void independentCloseFiveRuleFamiliesStayAmbiguous() {
        var g = raw();
        for (int y : new int[] {62, 78, 94, 110, 126})
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) 200;
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, staff()));
    }

    @Test
    public void phaseOutsideExistingLawIsNotCorrectedByNearestGroup() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, new PlayingTechniqueDetector.Staff(76, 140, 16, 0, 1)));
    }

    @Test
    public void scaleOutsideExistingLawIsNotRecalibrated() {
        var g = raw();
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, new PlayingTechniqueDetector.Staff(76, 124, 12, 0, 1)));
    }

    @Test
    public void darkPaperIsNotRuleInk() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 200);
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, staff()));
    }

    @Test
    public void fiveReturningBowsDoNotSupplyFlatStaffAuthority() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 50; x < 670; x++) {
                int y = 60 + line * 16 + Math.round(8 * (float) Math.sin(Math.PI * (x - 50) / 619));
                g[y * W + x] = (byte) 200;
            }
        var p = new WholeStaffSlurRules.Page(g, W, H);
        assertNull(p.prove(g, W, H, staff()));
    }

    @Test
    public void existingLegacyFrameConsumersKeepTheirContract() {
        var g = raw();
        assertTrue(WholeStaffSlurFrameOriginalTest.prepare(g).frames().isEmpty());
        assertNotNull(new WholeStaffSlurRules.Page(g, W, H).prove(g, W, H, staff()));
    }

    @Test
    public void sourceSlotsAreRequiredForOptionalProof() {
        var g = raw();
        var n =
                new ScoreNoteEvent(
                        0, .5f, 0, 0, 1, 124f / H, false, 0, 0, 0, 1, 1, 0, 0, 30, false);
        var heads = List.of(new PrintedSlurOwners.GeometryHead(0, 500, 124, 0, 1));
        var unsupported =
                new PrintedSlurFrames.Result<PrintedSlurOwners.GeometryHead>(
                        List.of(), Set.of(0), 1);
        var result =
                PrintedSlurFrames.prepareSupplemental(
                        g, W, H, List.of(staff()), List.of(n), heads, unsupported);
        assertTrue(result.frames().isEmpty());
    }

    @Test
    public void refusedOptionalPagePreservesExistingSourceObject() {
        var g = raw();
        var n =
                new ScoreNoteEvent(
                        0, .5f, 0, 0, 1, 124f / H, false, 0, 0, 0, 1, 1, 0, 0, 30, false);
        var notes = List.of(n, n, n);
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        var span =
                new ScoreSlurPages.Span(
                        "existing-original",
                        new ScoreSlurPages.Anchor(0, 0),
                        new ScoreSlurPages.Anchor(0, 1),
                        ScoreSlurSpan.Placement.ABOVE,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(new ScoreSlurSpan.ArcEvidence(0, .1f, .4f, .2f, .4f)));
        var source = ScoreSlurSource.capture(List.of(span), List.of(new ScoreSlurPages.Page(0, 3)));
        var captured =
                new PrintedSlurPageCapture.Result(
                        source, Map.of(PrintedSlurOwners.Status.RESOLVED, 1), false);
        var staffs = new ArrayList<PlayingTechniqueDetector.Staff>();
        var slots = new HashSet<Integer>();
        for (int i = 0; i < 512; i++) {
            float delta = i * .0001f;
            staffs.add(new PlayingTechniqueDetector.Staff(60 + delta, 124 + delta, 16, 0, 1));
            slots.add(i);
        }
        var legacy =
                new PrintedSlurFrames.Result<PrintedSlurOwners.GeometryHead>(
                        List.of(), Set.of(2), 512, slots);
        var heads = List.of(new PrintedSlurOwners.GeometryHead(2, 500, 124, 0, 1));
        var optional = PrintedSlurFrames.prepareSupplemental(g, W, H, staffs, notes, heads, legacy);
        assertEquals(512, optional.unprovedStaffs());
        assertTrue(optional.frames().isEmpty());
        var result =
                WholeStaffSlurRules.append(
                        0, score, g, null, W, H, staffs, heads, legacy, captured, Set.of(), true);
        assertSame(captured, result.captured());
        assertSame(source, result.captured().source());
        assertSame(span, result.captured().source().spans().get(0));
    }
}
