// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoldSlurWorkLimitOriginalTest {
    @Test
    public void unsearchedWideThickComponentPropagatesUnknown() {
        int w = 9000, h = 310;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 170, 20}, new float[] {w - 1, 170, 20}));
        var heads = new ArrayList<PrintedSlurOwners.GeometryHead>();
        for (int k = 0; k < 2; k++) {
            int cx = k == 0 ? 58 : 8412, cy = 180;
            heads.add(new PrintedSlurOwners.GeometryHead(k, cx, cy, 0, 1));
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = cx - 9; x <= cx + 9; x++)
                    if ((x - cx) * (x - cx) / 81d + (y - cy) * (y - cy) / 25d <= 1)
                        g[y * w + x] = 0;
        }
        for (int x = 70; x <= 8400; x++) {
            double t = 2d * (x - 70) / 8330 - 1, y = 225 - 30 * t * t, a = 1 + 5 * (1 - t * t);
            for (int yy = (int) Math.ceil(y - a); yy <= Math.floor(y + a); yy++) g[yy * w + x] = 0;
        }
        var hints = PrintedSlurOwners.headEvidence(heads, g, w, h, track);
        assertTrue(
                PrintedSlurContours.find(g, null, w, h, 0, 10, w - 1, h - 1, track, null, hints)
                        .limitReached());
    }
}
