// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric ribbons: width is authored in Euclidean normal space, not an axis. */
public class FiniteNormalRibbonTest {
    static final int W = 420, H = 360, L = 100, R = 300;
    static final double RADIUS = 2.8; // full width5.6 <= existing round(14*.42)=6

    // A finite returning arc. Its endpoint slope is1; no staff, text or head is authored.
    static double y(double x) {
        return 190 + (x - 200) * (x - 200) / 200;
    }

    static byte[] ribbon(double radius, boolean mirror, boolean branch) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        // Minkowski union of literal small disks around a fixed analytic finite centreline.
        // Every black pixel has a direct distance<=radius witness. No target pixels occur here.
        for (int i = 0; i <= 4000; i++) {
            double x = L + (R - L) * i / 4000.0, yy = y(x);
            if (mirror) yy = H - yy;
            for (int cy = (int) Math.floor(yy - radius); cy <= Math.ceil(yy + radius); cy++)
                for (int cx = (int) Math.floor(x - radius); cx <= Math.ceil(x + radius); cx++)
                    if ((cx - x) * (cx - x) + (cy - yy) * (cy - yy) <= radius * radius)
                        g[cy * W + cx] = 0;
        }
        if (branch)
            for (int x = 190; x <= 210; x++) {
                int yy = (int) Math.round(y(200)) + x - 190;
                if (mirror) yy = H - yy;
                g[yy * W + x] = 0;
            }
        return g;
    }

    static PrintedSlurContours.RawResult find(byte[] g) {
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 120, 14}, new float[] {W - 1, 120, 14}));
        return PrintedSlurContours.findWithRawEvidence(
                g, null, W, H, 70, 100, 330, 270, track, null, null);
    }

    static boolean complete(PrintedSlurContours.RawResult r) {
        return r.result().bows().stream()
                .anyMatch(b -> b.start().x() <= L + 3 && b.end().x() >= R - 3);
    }

    @Test
    public void normalThinReturningRibbonRetainsItsFiniteExtent() {
        for (boolean mirror : new boolean[] {false, true}) {
            byte[] g = ribbon(RADIUS, mirror, false);
            assertTrue(
                    "A complete smooth finite5.6px normal ribbon must not lose steep outer segments",
                    complete(find(g)));
        }
    }

    @Test
    public void narrowerSameCurveHasIdenticalMusicalRole() {
        for (boolean mirror : new boolean[] {false, true})
            assertTrue(complete(find(ribbon(1.25, mirror, false))));
    }

    @Test
    public void axisExtentsCanBothExceedTheExistingNormalBudget() throws Exception {
        byte[] g = ribbon(RADIUS, false, false);
        int x = 115, cy = (int) Math.round(y(x));
        int t = cy, b = cy, l = x, r = x;
        while ((g[(t - 1) * W + x] & 255) == 0) t--;
        while ((g[(b + 1) * W + x] & 255) == 0) b++;
        while ((g[cy * W + l - 1] & 255) == 0) l--;
        while ((g[cy * W + r + 1] & 255) == 0) r++;
        assertTrue("Euclidean5.6px ribbon vertical projection", b - t + 1 > 6);
        assertTrue("Euclidean5.6px ribbon horizontal projection", r - l + 1 > 6);
        var method =
                PrintedSlurContours.class.getDeclaredMethod(
                        "thinAcross",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        method.setAccessible(true);
        assertFalse(
                "Published fallback uses horizontal width, not the true normal width",
                (Boolean) method.invoke(null, g, W, H, x, (t + b) / 2, 6));
    }

    @Test
    public void independentBranchCannotAcquireTheCompleteRibbonRole() {
        assertFalse(
                "Visible interior fork keeps whole-component ownership unresolved",
                complete(find(ribbon(1.25, false, true))));
    }
}
