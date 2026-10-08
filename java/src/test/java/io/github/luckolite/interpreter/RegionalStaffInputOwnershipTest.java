// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original input-shape, finite-frame and ownership controls for regional geometry. */
public class RegionalStaffInputOwnershipTest {
    @Test
    public void malformedDimensionsAndPacketsCannotGenerateGeometry() {
        byte[] labels = new byte[20], gray = new byte[20];
        for (int[] dimensions :
                new int[][] {
                    {0, 4}, {5, 0}, {-5, 4}, {5, -4}, {Integer.MAX_VALUE, Integer.MAX_VALUE}
                }) {
            assertTrue(
                    RegionalStaffSeeds.detect(labels, gray, dimensions[0], dimensions[1])
                            .isEmpty());
            assertNull(
                    RegionalStaffSeeds.track(
                            labels,
                            gray,
                            dimensions[0],
                            dimensions[1],
                            new RegionalStaffSeeds.Seed(1, 13, 3, 0)));
        }
        assertNull(
                RegionalStaffSeeds.track(
                        null, gray, 5, 4, new RegionalStaffSeeds.Seed(1, 13, 3, 0)));
        assertNull(
                RegionalStaffSeeds.track(
                        labels, new byte[19], 5, 4, new RegionalStaffSeeds.Seed(1, 13, 3, 0)));
    }

    @Test
    public void missingAndNonfiniteSeedsCannotGenerateATrack() {
        byte[] labels = new byte[20], gray = new byte[20];
        assertNull(RegionalStaffSeeds.track(labels, gray, 5, 4, null));
        for (var seed :
                List.of(
                        new RegionalStaffSeeds.Seed(Float.NaN, 13, 3, 0),
                        new RegionalStaffSeeds.Seed(1, Float.POSITIVE_INFINITY, 3, 0),
                        new RegionalStaffSeeds.Seed(1, 13, Float.NaN, 0),
                        new RegionalStaffSeeds.Seed(1, 13, 3, Float.NEGATIVE_INFINITY),
                        new RegionalStaffSeeds.Seed(1, 13, 2.9f, 0)))
            assertNull(RegionalStaffSeeds.track(labels, gray, 5, 4, seed));
    }

    private static void rejected(List<float[]> samples) {
        try {
            StaffPitchTrack.fromVerifiedSamples(samples);
            fail("Invalid samples accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void verifiedSampleFactoryRejectsMalformedOrUnorderedFrames() {
        rejected(null);
        rejected(List.of());
        rejected(List.of(new float[] {10, 80, 10}));
        var tooMany = new ArrayList<float[]>();
        for (int i = 0; i < 14; i++) tooMany.add(new float[] {i, 80, 10});
        rejected(tooMany);
        var withNull = new ArrayList<float[]>();
        withNull.add(new float[] {10, 80, 10});
        withNull.add(null);
        rejected(withNull);
        rejected(List.of(new float[] {10, 80, 10}, new float[] {20, 90}));
        rejected(List.of(new float[] {10, 80, 10}, new float[] {10, 90, 10}));
        rejected(List.of(new float[] {20, 80, 10}, new float[] {10, 90, 10}));
        rejected(List.of(new float[] {10, 80, 10}, new float[] {20, Float.NaN, 10}));
        rejected(List.of(new float[] {10, 80, 10}, new float[] {20, 90, 2.9f}));
    }

    @Test
    public void detectingAndTrackingFiveRulesDoNotMutateSourcePackets() {
        int width = 520, height = 360;
        byte[] labels = new byte[width * height], gray = new byte[labels.length];
        Arrays.fill(gray, (byte) 180);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < width - 35; x++) {
                int y = Math.round(150 + .035f * (x - width * .5f) + line * 10);
                labels[y * width + x] = OmrMeasurePostProcessor.STAFF;
                gray[y * width + x] = 40;
            }
        byte[] labelsBefore = labels.clone(), grayBefore = gray.clone();
        var seeds = RegionalStaffSeeds.detect(labels, gray, width, height);
        assertEquals(1, seeds.size());
        assertNotNull(RegionalStaffSeeds.track(labels, gray, width, height, seeds.get(0)));
        assertArrayEquals(labelsBefore, labels);
        assertArrayEquals(grayBefore, gray);
    }
}
