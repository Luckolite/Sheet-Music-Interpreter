// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original short printed bow with real blank terminal pixels beside a thin rail. */
public class PrintedSlurRailCapTest {
    @Test
    public void subpixelRailReferenceCannotEraseActualBlankTermination() {
        int w = 400, h = 260;
        var gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 0; x < w; x++)
            for (int line = 0; line < 5; line++)
                gray[Math.round(210.5f - line * 13.75f) * w + x] = 60;
        for (int x = 200; x <= 230; x++) {
            double t = 2.0 * (x - 200) / 30 - 1;
            gray[(int) Math.round(200 + 4.3 * (1 - t * t)) * w + x] = 0;
        }
        for (float bottom : new float[] {210, 210.5f}) {
            var track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, 13.75f},
                                    new float[] {w - 1, bottom, 13.75f}));
            var found = PrintedSlurContours.find(gray, null, w, h, 190, 190, 240, 215, track);
            assertEquals("raw cap at staff phase " + bottom, 1, found.bows().size());
        }
    }
}
