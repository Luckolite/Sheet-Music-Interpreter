// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale ledger rows and their absent-rule controls, mirrored above and below a staff. */
public class FadedLedgerSpaceTest {
    static final int W = 240, H = 320;
    byte[] g = new byte[W * H];

    int pitch(boolean upper, boolean outer, boolean pale) throws Exception {
        Arrays.fill(g, (byte) 250);
        for (int y = 208; y <= 224; y++)
            for (int x = 87; x <= 113; x++)
                if (Math.pow((x - 100) / 13., 2) + Math.pow((y - 216) / 8., 2) <= 1)
                    g[y * W + x] = (byte) (pale ? 185 : 80);
        for (int y : new int[] {180, 200})
            for (int x = 76; x <= 124; x++) g[y * W + x] = (byte) (pale ? 150 : 0);
        if (outer) for (int x = 76; x <= 124; x++) g[220 * W + x] = (byte) (pale ? 190 : 0);
        if (upper) {
            byte[] old = g.clone();
            for (int y = 0; y < H; y++) System.arraycopy(old, y * W, g, (H - 1 - y) * W, W);
        }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        int cy = upper ? H - 1 - 216 : 216;
        Object head = ct.newInstance(310, 87, 113, cy - 8, cy + 8, 100f, (float) cy);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "printedPitchStep",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        float.class);
        m.setAccessible(true);
        float bottom = upper ? H - 1 - 80 : 160;
        return (Integer) m.invoke(null, g, W, H, head, bottom, 20f);
    }

    @Test
    public void paleLowerLedgerRemainsLine() throws Exception {
        assertEquals(-6, pitch(false, true, true));
    }

    @Test
    public void paleUpperLedgerRemainsLine() throws Exception {
        assertEquals(14, pitch(true, true, true));
    }

    @Test
    public void paleLowerMissingLedgerRemainsSpace() throws Exception {
        assertEquals(-5, pitch(false, false, true));
    }

    @Test
    public void paleUpperMissingLedgerRemainsSpace() throws Exception {
        assertEquals(13, pitch(true, false, true));
    }

    @Test
    public void darkLowerLedgerRemainsLine() throws Exception {
        assertEquals(-6, pitch(false, true, false));
    }

    @Test
    public void darkUpperLedgerRemainsLine() throws Exception {
        assertEquals(14, pitch(true, true, false));
    }
}
