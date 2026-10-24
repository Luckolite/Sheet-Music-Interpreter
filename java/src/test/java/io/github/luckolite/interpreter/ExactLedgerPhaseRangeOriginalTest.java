// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original signed-range controls: exact phase arithmetic must never wrap. */
public final class ExactLedgerPhaseRangeOriginalTest {
    @Test
    public void signedIntEndpointsRemainRepresentable() {
        assertEquals(Integer.MIN_VALUE, ExactLedgerPhase.R.of(Integer.MIN_VALUE).floor());
        assertEquals(Integer.MAX_VALUE, ExactLedgerPhase.R.of(Integer.MAX_VALUE).floor());
    }

    @Test
    public void negativeFractionsRoundDownBeforeRangeChecks() {
        assertEquals(-1, ExactLedgerPhase.R.ratio(-1, 2).floor());
        assertEquals(
                Integer.MIN_VALUE, ExactLedgerPhase.R.ratio(2L * Integer.MIN_VALUE + 1, 2).floor());
        assertEquals(
                Integer.MAX_VALUE, ExactLedgerPhase.R.ratio(2L * Integer.MAX_VALUE + 1, 2).floor());
    }

    @Test
    public void unrepresentableFloorsThrowInsteadOfWrapping() {
        assertThrows(
                ArithmeticException.class,
                () -> ExactLedgerPhase.R.of((long) Integer.MAX_VALUE + 1).floor());
        assertThrows(
                ArithmeticException.class,
                () -> ExactLedgerPhase.R.of((long) Integer.MIN_VALUE - 1).floor());
        assertThrows(
                ArithmeticException.class,
                () -> ExactLedgerPhase.R.ratio(2L * Integer.MIN_VALUE - 1, 2).floor());
    }
}
