// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** A retained component is not identical to the singleton-column contour selected for fitting. */
public final class TerminalBranchRawOwnershipTest {
    @Test
    public void acceptedTerminalForkRemainsProvenanceWithoutCurveOwnership() {
        var s = new OwnedCurveMezzoScene(true, false);
        // An independently drawn short offshoot joins through a finite narrow junction.
        // Existing curve, glyph and both actual head bodies are otherwise unchanged.
        for (int x = 362; x <= 364; x++) s.gray[196 * s.W + x] = 20;
        for (int y = 190; y <= 196; y++) s.gray[y * s.W + 365] = 20;
        var raw =
                PrintedSlurContours.findWithRawEvidence(
                        s.gray,
                        null,
                        s.W,
                        s.H,
                        300,
                        140,
                        550,
                        250,
                        s.track(),
                        null,
                        PrintedSlurOwners.headEvidence(s.heads(), s.gray, s.W, s.H, s.track()));
        assertFalse(
                "Fixture must retain an originally accepted bow", raw.result().bows().isEmpty());
        boolean retained = false, selected = false;
        for (var bow : raw.result().bows()) {
            for (var point : bow.points()) if (point.x() == 363) selected = true;
            for (var column : raw.witnesses().get(bow))
                if (column.x() == 363)
                    for (var band : column.members())
                        if (band.first() <= 196 && band.last() >= 196) retained = true;
        }
        System.out.println(
                "TERMINAL_BRANCH admitted="
                        + raw.result().bows().size()
                        + " retained="
                        + retained
                        + " selectedColumn="
                        + selected);
        assertTrue("Full component evidence must retain the literal offshoot", retained);
        assertFalse("The ambiguous multi-run column is excluded from the fitted contour", selected);
        var context =
                DynamicNotationRawContext.capture(s.gray, s.W, s.H, s.staffs, s.measures, s.notes);
        assertFalse(
                "A fitted selected-column sample retains exact curve ownership",
                context.curves().isEmpty());
        assertEquals(
                Integer.valueOf(20),
                context.ownedPixelValues(s.gray, s.W, s.H).get(202 * s.W + 415));
        assertFalse(
                "Excluded terminal offshoot is UNKNOWN, never owned fitted curve ink",
                context.ownedPixelValues(s.gray, s.W, s.H).containsKey(196 * s.W + 363));
    }
}
