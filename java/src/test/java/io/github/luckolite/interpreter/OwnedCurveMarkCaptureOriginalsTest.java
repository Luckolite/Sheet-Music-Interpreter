// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Raw producer and diagnostic modes must agree on the actual complete mark witness. */
public class OwnedCurveMarkCaptureOriginalsTest {
    private static PrintedSlurPageCapture.Result capture(
            OwnedCurveMarkOriginalsTest.Field f, boolean typed) {
        var frame =
                new PrintedSlurPageCapture.Frame<>(
                        0, 30 * f.scale, f.w - 1, 280 * f.scale, f.track, f.heads);
        return PrintedSlurPageCapture.captureGeometry(
                3, 3, f.gray, null, f.w, f.h, List.of(frame), typed ? f.notes : null);
    }

    private static void recovers(boolean above, int scale, float phase) {
        var f = new OwnedCurveMarkOriginalsTest.Field(above, scale, phase);
        var before = capture(f, false);
        var after = capture(f, true);
        assertFalse(before.limitReached());
        assertFalse(after.limitReached());
        assertTrue(before.source().candidates().isEmpty());
        assertEquals(1, after.source().candidates().size());
        var c = after.source().candidates().get(0);
        assertEquals(0, c.start().noteIndex());
        assertEquals(1, c.end().noteIndex());
        assertTrue(c.id().contains("-mark-arc-"));
    }

    @Test
    public void upperRawProducerRecoversOnlyTheIndependentCurve() {
        recovers(true, 1, 0);
    }

    @Test
    public void lowerRawProducerRecoversOnlyTheIndependentCurve() {
        recovers(false, 1, 0);
    }

    @Test
    public void doubleDensityProducerKeepsLiteralRoles() {
        recovers(true, 2, 0);
    }

    @Test
    public void fractionalPhaseProducerKeepsLiteralRoles() {
        recovers(false, 1, .5f);
    }

    @Test
    public void missingTypedRoleKeepsTheLegacyCapture() {
        var f = new OwnedCurveMarkOriginalsTest.Field(true, 1, 0);
        f.notes.set(2, f.note(2, 0));
        assertEquals(capture(f, false), capture(f, true));
    }

    @Test
    public void rawAndDiagnosticWitnessModesAgree() {
        var f = new OwnedCurveMarkOriginalsTest.Field(true, 1, 0);
        var roles = new PrintedCurveMarkRoles(f.notes, f.gray, f.w, f.h);
        var hints =
                roles.wrap(
                        PrintedSlurOwners.headEvidence(f.heads, f.gray, f.w, f.h, f.track),
                        f.heads,
                        f.track);
        var raw =
                PrintedSlurContours.find(
                        f.gray, null, f.w, f.h, 0, 30, f.w - 1, 280, f.track, null, hints);
        var other = new PrintedCurveMarkRoles(f.notes, f.gray, f.w, f.h);
        var h =
                other.wrap(
                        PrintedSlurOwners.headEvidence(f.heads, f.gray, f.w, f.h, f.track),
                        f.heads,
                        f.track);
        var diagnostic =
                PrintedSlurContours.findWithRawEvidence(
                        f.gray, null, f.w, f.h, 0, 30, f.w - 1, 280, f.track, null, h);
        assertEquals(raw, diagnostic.result());
        assertEquals(1, raw.bows().size());
    }
}
