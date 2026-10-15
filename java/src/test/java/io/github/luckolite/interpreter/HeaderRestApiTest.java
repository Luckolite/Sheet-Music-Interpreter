// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeaderRestApiTest {
    @Test
    public void headerTerminalCannotDistortMeasureBounds() {
        var p = new HeaderSymbolNormalizationTest.Page(true, true);
        p.restAndNextNote();
        // A staff fragment labelled as header ink extends beyond the C terminal.
        p.labels[80 * p.w + 145] = 3;
        for (int y = 95; y <= 103; y++)
            for (int x = 134; x <= 142; x++)
                if ((x - 138) * (x - 138) + (y - 99) * (y - 99) <= 16) {
                    p.labels[y * p.w + x] = 2;
                    p.gray[y * p.w + x] = 0;
                }
        byte[] expectedLabels = p.labels.clone();
        for (int y = 95; y <= 103; y++)
            for (int x = 134; x <= 142; x++)
                if (expectedLabels[y * p.w + x] == 2) expectedLabels[y * p.w + x] = 0;
        var expected = OmrMeasurePostProcessor.process(expectedLabels, p.gray, p.w, p.h);
        var original = p.measures();
        // The false header terminal distorts the raw extent; the public API must
        // normalize it to the independently cleaned measure boundaries.
        assertNotEquals(expected, original);
        var score = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h);
        assertEquals(expected, score.measures());
        assertEquals("Only the actual following note survives", 1, score.notes().size());
        assertEquals(1, score.notes().get(0).measureIndex());
    }

    @Test
    public void apiNormalizesHeadersBeforeRestReconciliation() {
        var p = new HeaderSymbolNormalizationTest.Page(true, true);
        p.restAndNextNote();
        var t = p.count();
        var count =
                new SheetInterpreter.NumberToken(
                        t.value(), t.left(), t.top(), t.right(), t.bottom(), t.left());
        var annotations =
                new SheetInterpreter.Annotations(
                        List.of(), List.of(), List.of(count), List.of(), List.of());
        var score = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, annotations);
        assertEquals(9, score.measures().size());
        assertEquals(1, score.notes().size());
        assertEquals(8, score.notes().get(0).measureIndex());
    }
}
