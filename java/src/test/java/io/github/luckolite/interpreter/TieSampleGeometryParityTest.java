// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Original synthetic geometry only. No score images or phone data. */
public final class TieSampleGeometryParityTest {
    record Fixture(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float center,
            float gap,
            int ink,
            int side,
            boolean strict,
            boolean flat) {}

    static Method detector;

    static boolean detect(Fixture f) throws Exception {
        return (boolean)
                detector.invoke(
                        null, f.labels, f.gray, f.width, f.height, f.left, f.right, f.center, f.gap,
                        null, f.ink, f.side, f.strict, f.flat);
    }

    static Fixture fixture(int index) {
        int width = 420, height = 250, left = 50;
        float gap = 8 + (index % 4) * 4 + (index % 3) * .125f, center = 120 + (index % 5) * .25f;
        int span = Math.round(gap * (2 + index % 12)), right = Math.min(width - 30, left + span);
        byte[] gray = new byte[width * height], labels = new byte[gray.length];
        Arrays.fill(gray, (byte) 255);
        int mode = index % 8, side = (index / 8) % 2 == 0 ? -1 : 1;
        if (mode >= 2)
            for (int k = -2; k <= 2; k++)
                for (int x = 0; x < width; x++) {
                    int y = Math.round(center + k * gap + .5f * gap);
                    gray[y * width + x] = 0;
                    labels[y * width + x] = 4;
                }
        if (mode != 0 && mode != 2)
            for (int x = left; x <= right; x++) {
                float t = (x - left) / (float) (right - left);
                if (mode == 4 && t > .55f || mode == 5 && t > .42f && t < .58f) continue;
                float bend = mode == 6 ? t * gap : 4 * t * (1 - t) * gap;
                int y = Math.round(center + side * (gap * .9f + bend));
                int thickness = mode == 7 ? 4 : 0;
                for (int dy = -thickness; dy <= thickness; dy++) {
                    gray[(y + dy) * width + x] = (byte) (mode == 3 ? 200 : 0);
                    if (labels[(y + dy) * width + x] != 4) labels[(y + dy) * width + x] = 5;
                }
            }
        if (index >= 64) {
            Random rng = new Random(771901L + index);
            for (int i = 0; i < 300; i++) {
                int x = left + rng.nextInt(right - left + 1), y = 60 + rng.nextInt(120);
                gray[y * width + x] = (byte) rng.nextInt(210);
                labels[y * width + x] = (byte) (rng.nextBoolean() ? 1 : 5);
            }
        }
        return new Fixture(
                labels,
                gray,
                width,
                height,
                left,
                right,
                center,
                gap,
                (index / 16) % 2 == 0 ? 165 : 205,
                (index / 32) % 3 - 1,
                (index & 16) != 0,
                (index & 32) != 0);
    }

    @org.junit.Test
    public void originalArcsRetainBaselineDecisionsAndCallerPixels() throws Exception {
        Class<?> owner = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter");
        Class<?> component = Class.forName(owner.getName() + "$Component");
        detector =
                owner.getDeclaredMethod(
                        "hasContinuousTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        component,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class);
        detector.setAccessible(true);
        // Reviewed change: fixture253 deliberately deletes the middle of the arc.
        // Raw staff/noise pixels do not reconnect its separated returning halves.
        org.junit.Assert.assertFalse(
                "Disconnected middle remains a negative", detect(fixture(253)));
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        int positives = 0;
        for (int index = 0; index < 512; index++) {
            Fixture value = fixture(index);
            byte[] gray = value.gray.clone(), labels = value.labels.clone();
            boolean found = detect(value);
            hash.update((byte) (found ? 1 : 0));
            if (found) positives++;
            org.junit.Assert.assertArrayEquals(gray, value.gray);
            org.junit.Assert.assertArrayEquals(labels, value.labels);
        }
        org.junit.Assert.assertEquals(86, positives);
        org.junit.Assert.assertEquals(
                "eb29f6fbdb52965a8d31e7a2dfa5980b32cd567795c3dc4a108112234b6a2da0",
                HexFormat.of().formatHex(hash.digest()));
    }
}
