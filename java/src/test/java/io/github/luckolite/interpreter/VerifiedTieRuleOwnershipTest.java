// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original curved five-rule raster, measured in its own independently specified frame. */
public class VerifiedTieRuleOwnershipTest {
    static final int W = 180, H = 220, L = 50, R = 90;
    static final float GAP = 16, CY = 94;

    record Raster(byte[] labels, byte[] gray, StaffPitchTrack track) {}

    static Raster draw(boolean tie) {
        List<float[]> samples = new ArrayList<>();
        samples.add(new float[] {0, 166, GAP});
        for (int x = 40; x <= 100; x += 6) {
            float t = (x - 40) / 60f;
            samples.add(new float[] {x, 166 + 10 * 4 * t * (1 - t), GAP});
        }
        samples.add(new float[] {179, 166, GAP});
        var track = StaffPitchTrack.fromVerifiedSamples(samples);
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 210);
        for (int x = 0; x < W; x++)
            for (int rule = 0; rule < 5; rule++) {
                int y = Math.round(track.at(x)[0] - rule * GAP);
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    gray[yy * W + x] = 60;
                    labels[yy * W + x] = 4;
                }
            }
        if (tie)
            for (int x = L; x <= R; x++) {
                float t = (x - L) / (float) (R - L);
                int y = Math.round(CY - 7 - 6 * 4 * t * (1 - t));
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    gray[yy * W + x] = 40;
                    labels[yy * W + x] = 5;
                }
            }
        return new Raster(labels, gray, track);
    }

    static boolean detect(Raster r, boolean frame) throws Exception {
        Class<?> type = Class.forName(OmrScoreInterpreter.class.getName() + "$TieFrame");
        var ctor = type.getDeclaredConstructor(StaffPitchTrack.class);
        ctor.setAccessible(true);
        Object ownership = frame ? ctor.newInstance(r.track) : null;
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasContinuousTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        Class.forName(OmrScoreInterpreter.class.getName() + "$Component"),
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class,
                        type);
        method.setAccessible(true);
        return (boolean)
                method.invoke(
                        null, r.labels, r.gray, W, H, L, R, CY, GAP, null, 165, 0, false, false,
                        ownership);
    }

    @Test
    public void knownCurvedRuleCannotSupplyASecondContour() throws Exception {
        var r = draw(false);
        assertTrue(detect(r, false));
        assertFalse(detect(r, true));
    }

    @Test
    public void independentlyReturningContourSurvivesRuleOwnership() throws Exception {
        var r = draw(true);
        assertTrue(detect(r, true));
    }

    @Test
    public void ownershipChecksPreserveAllSourcePixels() throws Exception {
        var r = draw(false);
        byte[] l = r.labels.clone(), g = r.gray.clone();
        detect(r, true);
        assertArrayEquals(l, r.labels);
        assertArrayEquals(g, r.gray);
    }
}
