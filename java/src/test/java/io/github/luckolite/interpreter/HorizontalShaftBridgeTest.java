// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Broad horizontal ink is not vertical continuity, regardless of semantic mask. */
public class HorizontalShaftBridgeTest {
    @Test
    public void unlabelledHorizontalInkCannotResetPaperGap() throws Exception {
        var d = TallChordAdversarialTest.independentVoices(false);
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 0);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void stemLabelledHorizontalInkCannotResetPaperGap() throws Exception {
        var d = TallChordAdversarialTest.independentVoices(false);
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 1);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void symbolLabelledHorizontalInkCannotResetPaperGap() throws Exception {
        var d = TallChordAdversarialTest.independentVoices(false);
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 5);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void continuousShaftCrossingStaffRemainsOneVoice() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        assertTrue(d.run().stream().allMatch(n -> n.beamCount() == 1));
    }

    @Test
    public void continuousShaftWithMissingSemanticPixelsAndStaffStillWorks() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 120; y <= 175; y++) for (int x = 100; x <= 101; x++) d.labels[y * d.W + x] = 0;
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        assertTrue(d.run().stream().allMatch(n -> n.beamCount() == 1));
    }
}
