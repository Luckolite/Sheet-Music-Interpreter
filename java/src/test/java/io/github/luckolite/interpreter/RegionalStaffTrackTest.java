// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original synthetic five-line frames with independent line spacing and page slope. */
public final class RegionalStaffTrackTest {
    private record Page(int width, int height, byte[] labels, byte[] gray) {}

    private static Page fan(float top, float gap, float slope, float gapSlope) {
        int width = 520, height = 480;
        byte[] labels = new byte[width * height], gray = new byte[labels.length];
        Arrays.fill(gray, (byte) 180);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < width - 35; x++) {
                float dx = x - width * .5f;
                int y = Math.round(top + slope * dx + line * (gap + gapSlope * dx));
                labels[y * width + x] = 4;
                gray[y * width + x] = 40;
            }
        return new Page(width, height, labels, gray);
    }

    private static void assertFan(float slope, float gapSlope) {
        float top = 180, gap = 10;
        Page p = fan(top, gap, slope, gapSlope);
        var seed = new RegionalStaffSeeds.Seed(top, top + 4 * gap, gap, slope + 2 * gapSlope);
        var track = RegionalStaffSeeds.track(p.labels, p.gray, p.width, p.height, seed);
        assertNotNull(track);
        for (float x : new float[] {80, 170, 260, 350, 450}) {
            float dx = x - p.width * .5f, expectedGap = gap + gapSlope * dx;
            var at = track.at(x);
            assertEquals(top + slope * dx + 4 * expectedGap, at[0], .8f);
            assertEquals(expectedGap, at[1], .22f);
        }
    }

    @Test
    public void increasingGapTracksSubpixelPitchFrameOnDarkPaper() {
        assertFan(.034f, .004f);
    }

    @Test
    public void negativeSlopeAndDecreasingGapKeepIndependentPhysicalRules() {
        assertFan(-.076f, -.003f);
    }

    @Test
    public void wholeGapPhaseShiftCannotBecomeAnotherPitchTrack() {
        Page p = fan(180, 10, .04f, .002f);
        assertNull(
                RegionalStaffSeeds.track(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        new RegionalStaffSeeds.Seed(170, 210, 10, .044f)));
    }

    @Test
    public void parallelBeamBandsCannotSubstituteForTheStaffScale() {
        Page p = fan(180, 4, .04f, 0);
        assertNull(
                RegionalStaffSeeds.track(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        new RegionalStaffSeeds.Seed(180, 220, 10, .04f)));
    }

    @Test
    public void shortExtraBeamCannotChangeTheEstablishedFiveLinePhase() {
        Page p = fan(180, 10, .03f, .003f);
        for (int x = 225; x < 280; x++) {
            int y = Math.round(230 + .03f * (x - 260));
            p.labels[y * p.width + x] = 4;
            p.gray[y * p.width + x] = 40;
        }
        var track =
                RegionalStaffSeeds.track(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        new RegionalStaffSeeds.Seed(180, 220, 10, .036f));
        assertNotNull(track);
        assertEquals(220, track.at(260)[0], .8f);
        assertEquals(10, track.at(260)[1], .22f);
    }

    @Test
    public void broadPaintAndNoRawRulesCannotProduceATrack() {
        byte[] labels = new byte[520 * 480], gray = new byte[labels.length];
        Arrays.fill(labels, (byte) 4);
        Arrays.fill(gray, (byte) 70);
        assertNull(
                RegionalStaffSeeds.track(
                        labels, gray, 520, 480, new RegionalStaffSeeds.Seed(180, 220, 10, .03f)));
    }

    @Test
    public void fourRawRulesCannotProveAChangingGapTrack() {
        Page p = fan(180, 10, .03f, .003f);
        for (int x = 45; x < p.width - 35; x++) {
            int y = Math.round(180 + .03f * (x - 260) + 4 * (10 + .003f * (x - 260)));
            p.gray[y * p.width + x] = (byte) 180;
        }
        assertNull(
                RegionalStaffSeeds.track(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        new RegionalStaffSeeds.Seed(180, 220, 10, .036f)));
    }

    @Test
    public void aShortFiveRulePocketCannotAnchorTheWholePage() {
        Page p = fan(180, 10, .03f, .003f);
        for (int y = 0; y < p.height; y++)
            for (int x = 0; x < p.width; x++)
                if (x < 100 || x > 170) p.gray[y * p.width + x] = (byte) 180;
        assertNull(
                RegionalStaffSeeds.track(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        new RegionalStaffSeeds.Seed(180, 220, 10, .036f)));
    }

    @Test
    public void sampledTrackOwnsItsArrays() {
        float[] a = {20, 180, 10}, b = {200, 188, 11};
        var samples = new ArrayList<float[]>();
        samples.add(a);
        samples.add(b);
        var track = StaffPitchTrack.fromVerifiedSamples(samples);
        var before = track.at(110);
        a[1] = 900;
        b[2] = 200;
        samples.clear();
        assertArrayEquals(before, track.at(110), 0);
    }
}
