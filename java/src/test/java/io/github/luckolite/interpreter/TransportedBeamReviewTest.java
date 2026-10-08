// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** Independent original downward and reversed-endpoint control geometry. */
public class TransportedBeamReviewTest {
    private static TransportedBeamShaftTest.Drawing down(float slope, int beams) {
        var d = TransportedBeamShaftTest.page(145, slope, 1);
        var a = TransportedBeamShaftTest.shaft(d, 90, 185, 230, 1);
        var b = TransportedBeamShaftTest.shaft(d, 118, 189, 234, 1);
        for (int i = 0; i < beams; i++) TransportedBeamShaftTest.beam(d, a, b, -i * 8, 5);
        return d;
    }

    private static int count(TransportedBeamShaftTest.Drawing d, boolean reversed) {
        return reversed
                ? TransportedBeamShaftTest.count(d, 118, 189, 90, 185)
                : TransportedBeamShaftTest.count(d, 90, 185, 118, 189);
    }

    @Test
    public void downRisingDoubleIsOwned() {
        assertEquals(2, count(down(.1f, 2), false));
    }

    @Test
    public void downFallingDoubleIsOwned() {
        assertEquals(2, count(down(-.12f, 2), false));
    }

    @Test
    public void downRisingReversedDoubleIsOwned() {
        assertEquals(2, count(down(.1f, 2), true));
    }

    @Test
    public void downFallingReversedDoubleIsOwned() {
        assertEquals(2, count(down(-.12f, 2), true));
    }

    @Test
    public void downRisingSingleDoesNotPromote() {
        assertEquals(0, count(down(.1f, 1), false));
    }

    @Test
    public void downFallingSingleDoesNotPromote() {
        assertEquals(0, count(down(-.12f, 1), false));
    }

    @Test
    public void downRisingReversedSingleDoesNotPromote() {
        assertEquals(0, count(down(.1f, 1), true));
    }

    @Test
    public void downFallingReversedSingleDoesNotPromote() {
        assertEquals(0, count(down(-.12f, 1), true));
    }

    @Test
    public void upwardRisingReverseIsOwned() {
        assertEquals(
                2,
                TransportedBeamShaftTest.count(
                        TransportedBeamShaftTest.pair(145, .1f, 2, 1), 118, 194, 90, 190));
    }

    @Test
    public void upwardFallingReverseIsOwned() {
        assertEquals(
                2,
                TransportedBeamShaftTest.count(
                        TransportedBeamShaftTest.pair(145, -.12f, 2, 1), 118, 194, 90, 190));
    }
}
