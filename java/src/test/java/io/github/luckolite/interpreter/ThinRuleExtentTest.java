// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original groundtruth: same unbroken dark wave; only thin staff raster varies. */
public final class ThinRuleExtentTest {
    static byte[] scene(boolean feathered) {
        var a = ExtentChallenge.finite();
        if (feathered)
            for (int y = 52; y <= 100; y += 12)
                for (int x = 10; x <= 220; x++)
                    ExtentChallenge.pixel(a, x, y, x % 8 < 2 ? 160 : 100);
        return a;
    }

    static int detect(byte[] a) {
        return ScoreArpeggioDetector.apply(
                        ExtentChallenge.page(),
                        a,
                        260,
                        250,
                        List.of(
                                new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2)),
                        0)
                .expressiveEvents()
                .size();
    }

    @Test
    public void sameFiniteDarkWaveWithoutRulesIsPositive() {
        assertEquals(1, detect(scene(false)));
    }

    @Test
    public void onePixelStaffRulesWithPaleSegmentsDoNotEraseFiniteWave() {
        assertEquals(1, detect(scene(true)));
    }

    public static void main(String[] args) throws Exception {
        java.nio.file.Files.write(java.nio.file.Path.of(args[0]), scene(true));
    }
}
