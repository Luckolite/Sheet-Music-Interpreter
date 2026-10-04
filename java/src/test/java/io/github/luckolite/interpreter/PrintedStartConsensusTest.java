// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PrintedStartConsensusTest {
    private static List<MeasureRegion> layout(int... counts) {
        var result = new ArrayList<MeasureRegion>();
        for (int row = 0; row < counts.length; row++) {
            float top = .12f + row * .14f;
            for (int bar = 0; bar < counts[row]; bar++)
                result.add(
                        new MeasureRegion(
                                .1f + .8f * bar / counts[row],
                                .1f + .8f * (bar + 1) / counts[row] - .004f,
                                top,
                                top + .08f));
        }
        return result;
    }

    private static List<MeasureNumberReconciler.NumberToken> anchors(int... values) {
        var result = new ArrayList<MeasureNumberReconciler.NumberToken>();
        for (int row = 0; row < values.length; row++) {
            float top = .12f + row * .14f;
            result.add(
                    new MeasureNumberReconciler.NumberToken(
                            values[row], .09f, top - .005f, .11f, top + .012f));
        }
        return result;
    }

    @Test
    public void laterSystemsRejectMisreadLeadingNumber() {
        assertEquals(
                1,
                MeasureNumberReconciler.firstMeasureNumber(
                        layout(3, 4, 2, 3), anchors(3, 4, 8, 10)));
    }

    @Test
    public void continuationPageUsesRepeatedIndependentOffset() {
        assertEquals(
                41,
                MeasureNumberReconciler.firstMeasureNumber(
                        layout(4, 3, 2, 4), anchors(43, 45, 48, 50)));
    }

    @Test
    public void unambiguousPrintedOpeningIsPreserved() {
        assertEquals(
                61,
                MeasureNumberReconciler.firstMeasureNumber(
                        layout(3, 2, 4, 3), anchors(61, 64, 66, 70)));
    }

    @Test
    public void equallySupportedOffsetsKeepExistingOpening() {
        assertEquals(
                21,
                MeasureNumberReconciler.firstMeasureNumber(
                        layout(3, 3, 3, 3), anchors(21, 24, 28, 31)));
    }

    @Test
    public void twoDisagreeingAnchorsCannotOutvoteOpening() {
        assertEquals(5, MeasureNumberReconciler.firstMeasureNumber(layout(3, 4), anchors(5, 9)));
    }
}
