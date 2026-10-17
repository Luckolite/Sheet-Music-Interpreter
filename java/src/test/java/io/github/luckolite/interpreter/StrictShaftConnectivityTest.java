// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** No amount of horizontal rule ink can fill a raw paper separation between voices. */
public class StrictShaftConnectivityTest {
    static TallCommonShaftDiagnostic separate(int above, int below) throws Exception {
        var d = TallChordAdversarialTest.independentVoices(false);
        for (int y = 179; y <= 185; y++) for (int x = 100; x <= 101; x++) d.pixel(x, y, 1);
        for (int y = 181 - above; y <= 180; y++)
            for (int x = 100; x <= 101; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        for (int y = 184; y <= 183 + below; y++)
            for (int x = 100; x <= 101; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        return d;
    }

    @Test
    public void evenSingleWhiteRowsPreventVoiceDonation() throws Exception {
        for (int above : new int[] {1, 2})
            for (int below : new int[] {1, 2}) {
                var d = separate(above, below);
                assertFalse(ShortWhiteGapProbe.connected(d.gray, 100, 178, 100, 186));
                assertEquals("paper=" + above + "+" + below, 0, d.run().get(0).beamCount());
            }
    }

    @Test
    public void mirroredSingleWhiteRowsPreventVoiceDonation() throws Exception {
        var d = MirroredShaftBridgeTest.mirror(separate(1, 1));
        assertFalse(ShortWhiteGapProbe.connected(d.gray, 100, 181, 100, 173));
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void distantRailNetworkCannotCompleteCommonShaft() throws Exception {
        var d = separate(1, 1);
        for (int y : new int[] {174, 188}) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        for (int y = 174; y <= 188; y++) d.pixel(120, y, 5);
        assertTrue(
                "deliberate distant ink connection",
                ShortWhiteGapProbe.connected(d.gray, 100, 178, 100, 186));
        assertEquals(0, d.run().get(0).beamCount());
    }
}
