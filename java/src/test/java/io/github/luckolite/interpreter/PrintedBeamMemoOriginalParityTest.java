// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Fixed OLD actual-API expectations; test rasters are generated in memory. */
public final class PrintedBeamMemoOriginalParityTest {
    @Test
    public void originalGeometryMasksAndUnchangedRouteDebitsRemainExact() throws Exception {
        var actual = PrintedBeamMemoOriginals.corpus();
        assertEquals(624, actual.cases());
        assertEquals(223, actual.positives());
        assertEquals(85, actual.memoPositives());
        assertEquals(480, actual.defaultCases());
        assertEquals(PrintedBeamMemoOriginals.GEOMETRY_DIGEST, actual.geometry());
        assertEquals(PrintedBeamMemoOriginals.DEFAULT_DEBIT_DIGEST, actual.defaultDebits());
    }
}
