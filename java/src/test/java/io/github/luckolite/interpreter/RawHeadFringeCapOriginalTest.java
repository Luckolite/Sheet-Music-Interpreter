// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster-neighbor tests: only real fringe ink next to proved body can close a cap. */
public class RawHeadFringeCapOriginalTest {
    static final int W = 240, H = 220;
    static final float GAP = 14;

    static byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    static byte[] head() {
        byte[] g = paper();
        for (int y = 92; y <= 108; y++)
            for (int x = 90; x <= 110; x++)
                if ((x - 100) * (x - 100) / 100d + (y - 100) * (y - 100) / 64d <= 1)
                    g[y * W + x] = 0;
        return g;
    }

    static PrintedSlurContours.HeadEvidence evidence(byte[] g) {
        var t =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 180, GAP}, new float[] {W - 1, 180, GAP}));
        return PrintedSlurOwners.headEvidence(
                List.of(new PrintedSlurOwners.GeometryHead(0, 100, 100, 0, 1)), g, W, H, t);
    }

    static boolean closes(byte[] g, int x, int y) {
        return evidence(g).closes(new PrintedSlurContours.Point(96, 84), x, y, false);
    }

    @Test
    public void onePixelOfRealFilledHeadFringeClosesItsOwnBowCap() {
        byte[] g = head();
        assertFalse(evidence(g).contains(100, 92));
        assertTrue(closes(g, 100, 92));
    }

    @Test
    public void unchangedEllipseCoreStillClosesItsOwnCap() {
        assertTrue(closes(head(), 100, 94));
    }

    @Test
    public void whiteFringeCannotBeInventedByTheMetadataEllipse() {
        byte[] g = head();
        g[92 * W + 100] = (byte) 255;
        assertFalse(closes(g, 100, 92));
    }

    @Test
    public void twoPixelDetachedMarkCannotBorrowTheBody() {
        byte[] g = head();
        g[91 * W + 100] = 0;
        assertFalse(closes(g, 100, 91));
    }

    @Test
    public void aBareRailWithMetadataCannotProveAnyHeadFringe() {
        byte[] g = paper();
        for (int x = 0; x < W; x++) g[92 * W + x] = 0;
        assertFalse(closes(g, 100, 92));
    }

    @Test
    public void aWhiteBodyCannotSupportADarkFringeMark() {
        byte[] g = paper();
        g[92 * W + 100] = 0;
        assertFalse(closes(g, 100, 92));
    }
}
