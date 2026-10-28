// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original caller regression: no helper reflection or candidate-only entrypoint. */
public final class BeamShaftPaperExposureTest {
    static final int W = 240, H = 240, HX = 120, HY = 80, G = 16, X = 110;

    static byte[] scene(boolean shade, boolean shaft, boolean disconnected) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 245);
        if (shaft) for (int y = 83; y <= 128; y++) raw[y * W + X] = 100;
        // A finite exposure band: identical 40-level transverse contrast.
        // Eleven rows prevent mistaking the band itself for a thin orthogonal ink rule.
        for (int y = 94; y <= 104; y++) {
            Arrays.fill(raw, y * W, (y + 1) * W, (byte) (shade ? 205 : 225));
            if (shaft) raw[y * W + X] = (byte) (shade ? 165 : 185);
        }
        if (disconnected) raw[99 * W + X] = (byte) (shade ? 205 : 225);
        return raw;
    }

    static List<PrintedBeamShafts.Shaft> scan(byte[] raw, boolean reflected) {
        if (reflected)
            for (int y = 0; y < H / 2; y++)
                for (int x = 0; x < W; x++) {
                    byte v = raw[y * W + x];
                    raw[y * W + x] = raw[(H - 1 - y) * W + x];
                    raw[(H - 1 - y) * W + x] = v;
                }
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), raw, W, H, 0, n -> {}, true);
        return PrintedBeamShafts.atAll(
                raw,
                W,
                H,
                reflected ? 100 : HX,
                reflected ? H - 1 - HY : HY,
                G,
                new PrintedBeamShafts.Budget(8_000_000),
                frame);
    }

    @Test
    public void unchangedCallerRetainsConnectedShadedShaft() {
        for (boolean reflected : new boolean[] {false, true}) {
            var bright = scan(scene(false, true, false), reflected);
            assertEquals("unshaded control", 1, bright.size());
            var shaded = scan(scene(true, true, false), reflected);
            assertEquals("same literal connected shaft under bounded exposure", 1, shaded.size());
            assertEquals(reflected ? H - 1 - 128 : 128, shaded.get(0).endY());
            assertTrue(shaded.get(0).pixels().has(X, reflected ? H - 1 - 99 : 99));
        }
    }

    @Test
    public void exposureBandCannotInventOrBridgeAShaft() {
        for (boolean shade : new boolean[] {false, true}) {
            assertTrue(scan(scene(shade, false, false), false).isEmpty());
            assertTrue(scan(scene(shade, true, true), false).isEmpty());
        }
    }
}
