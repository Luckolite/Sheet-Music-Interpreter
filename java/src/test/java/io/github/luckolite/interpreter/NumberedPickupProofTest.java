// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumberedPickupProofTest {
    static List<MeasureRegion> page() {
        var out = new ArrayList<MeasureRegion>();
        int[] counts = {3, 4, 4, 3};
        for (int r = 0; r < counts.length; r++)
            for (int i = 0; i < counts[r]; i++)
                out.add(
                        new MeasureRegion(
                                .1f + .8f * i / counts[r],
                                .1f + .8f * (i + 1) / counts[r] - .004f,
                                .1f + r * .12f,
                                .18f + r * .12f));
        return out;
    }

    static MeasureNumberReconciler.NumberToken n(int value, int row) {
        float y = .1f + row * .12f;
        return new MeasureNumberReconciler.NumberToken(value, .09f, y - .005f, .11f, y + .012f);
    }

    static boolean proof(MeasureNumberReconciler.NumberToken... ns) {
        return MeasureNumberReconciler.provesOpeningPickup(page(), List.of(ns));
    }

    @Test
    public void twoIndependentSystemsProvePickup() {
        assertTrue(proof(n(3, 1), n(7, 2)));
    }

    @Test
    public void threeIndependentSystemsProvePickup() {
        assertTrue(proof(n(3, 1), n(7, 2), n(11, 3)));
    }

    @Test
    public void ordinaryMeasureOneIsNotPickup() {
        assertFalse(proof(n(4, 1), n(8, 2)));
    }

    @Test
    public void continuationIsNotPickup() {
        assertFalse(proof(n(13, 1), n(17, 2)));
    }

    @Test
    public void oneAnchorIsInsufficient() {
        assertFalse(proof(n(3, 1)));
    }

    @Test
    public void duplicateAnchorIsNotIndependent() {
        assertFalse(proof(n(3, 1), n(3, 1)));
    }

    @Test
    public void contradictoryAnchorRejects() {
        assertFalse(proof(n(3, 1), n(7, 2), n(12, 3)));
    }

    @Test
    public void emptyInputRejects() {
        assertFalse(MeasureNumberReconciler.provesOpeningPickup(null, null));
    }

    @Test
    public void footerIsNotIndependent() {
        assertFalse(
                proof(n(3, 1), new MeasureNumberReconciler.NumberToken(7, .09f, .97f, .11f, .98f)));
    }
}
