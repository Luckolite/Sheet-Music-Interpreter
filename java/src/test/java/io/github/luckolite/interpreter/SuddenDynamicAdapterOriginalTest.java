// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original acquisition boundary and late refinement; no source score or inference runtime. */
public final class SuddenDynamicAdapterOriginalTest {
    static final int W = 300, H = 200;

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final PlayingTechniqueDetector.Word whole = word(60, 70, 160, 100);
        final PlayingTechniqueDetector.Word child = word(125, 75, 155, 95);
        final PlayingTechniqueDetector.Word duplicate = word(125, 75, 155, 95);
        final OcrText.Line parent;
        final DirectionWordProvenance.Acquisition expression;

        Fixture() {
            Arrays.fill(raw, (byte) 255);
            for (int y = 80; y < 90; y++) {
                for (int x = 70; x < 90; x++) raw[y * W + x] = 0;
                for (int x = 130; x < 150; x++) raw[y * W + x] = 0;
            }
            var qualifier =
                    new OcrText.Element("sub.", new OcrText.Box(60, 70, 100, 100), List.of());
            var strength = new OcrText.Element("mp", new OcrText.Box(120, 70, 160, 100), List.of());
            parent =
                    new OcrText.Line(
                            "sub. mp",
                            new OcrText.Box(50, 60, 200, 110),
                            List.of(qualifier, strength));
            var read =
                    new OcrText(
                            "sub. mp",
                            List.of(new OcrText.Block("sub. mp", parent.box(), List.of(parent))),
                            OcrText.Grouping.DETECTED_LINES);
            var origins =
                    List.of(
                            new DirectionWordProvenance.Origin(
                                    whole, read, parent, qualifier, 0, 0, W, H, 1),
                            new DirectionWordProvenance.Origin(
                                    whole, read, parent, strength, 0, 0, W, H, 1));
            expression =
                    new DirectionWordProvenance.Acquisition(
                            List.of(whole),
                            origins,
                            new DirectionWordProvenance.Scope(raw, W, H, 0, List.of(whole)));
        }
    }

    static PlayingTechniqueDetector.Word word(int l, int t, int r, int b) {
        return new PlayingTechniqueDetector.Word(
                "sub. mp", (float) l / W, (float) t / H, (float) r / W, (float) b / H);
    }

    @Test
    public void finalRefinementUsesActualOriginsAndPreservesExpressionAcquisition()
            throws Exception {
        var f = new Fixture();
        byte[] before = f.raw.clone();
        int[] reads = {0};
        var acquired =
                MusicalOcr.SuddenDynamicAcquisition.acquire(
                        f.raw,
                        W,
                        H,
                        () -> {
                            reads[0]++;
                            return f.expression;
                        });
        // Glyph refinement and supplemental OCR can add child observations after acquisition.
        var finalWords = List.of(f.whole, f.child, f.duplicate);
        var normalized = acquired.normalize(finalWords, f.raw, W, H);
        assertEquals(1, reads[0]);
        assertEquals(List.of(f.child), normalized);
        assertSame(f.child, normalized.get(0));
        assertEquals("sub. mp", normalized.get(0).text());
        assertEquals("sub.", f.parent.elements().get(0).text());
        assertEquals(1, ExpressiveDirectionText.parse(normalized.get(0).text()).size());
        assertSame(f.expression, acquired.expressionAcquisition());
        assertEquals(List.of(f.whole), acquired.expressionAcquisition().words());
        assertSame(f.parent, acquired.expressionAcquisition().origins().get(0).parent());
        assertSame(f.parent, acquired.expressionAcquisition().origins().get(1).parent());
        var expressionInput = List.of(f.whole);
        assertSame(
                expressionInput,
                DirectionWordProvenance.reconcile(
                        expressionInput, acquired.expressionAcquisition(), null, f.raw, W, H));
        assertArrayEquals(before, f.raw);
    }

    @Test
    public void sourceRootMustPrecedeAcquisitionRatherThanFinalNormalization() throws Exception {
        var f = new Fixture();
        var acquired =
                MusicalOcr.SuddenDynamicAcquisition.acquire(
                        f.raw,
                        W,
                        H,
                        () -> {
                            f.raw[0] = 0;
                            return f.expression;
                        });
        var finalWords = List.of(f.whole, f.child);
        assertSame(finalWords, acquired.normalize(finalWords, f.raw, W, H));
        assertSame(f.expression, acquired.expressionAcquisition());
    }

    @Test
    public void callerAcquisitionFailureIsPropagatedWithoutAnotherRead() throws Exception {
        var f = new Fixture();
        var failure = new Exception("original acquisition failure");
        int[] reads = {0};
        try {
            MusicalOcr.SuddenDynamicAcquisition.acquire(
                    f.raw,
                    W,
                    H,
                    () -> {
                        reads[0]++;
                        throw failure;
                    });
            fail("Expected original acquisition exception");
        } catch (Exception observed) {
            assertSame(failure, observed);
        }
        assertEquals(1, reads[0]);
    }
}
