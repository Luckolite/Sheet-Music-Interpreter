// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two straight engraved arms, including their complete gray raster fringes. */
public class HairpinApertureOriginalTest {
    static class Scene {
        final int w = 950, h = 350;
        final byte[] gray = new byte[w * h];
        final List<PlayingTechniqueDetector.Staff> staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 136, 14, 0, 2),
                        new PlayingTechniqueDetector.Staff(246, 302, 14, 1, 2));

        Scene(int direction, boolean bar, boolean solid) {
            Arrays.fill(gray, (byte) 255);
            for (var s : staffs)
                for (int r = 0; r < 5; r++)
                    for (int x = 10; x < w - 10; x++)
                        gray[Math.round(s.top() + r * s.gap()) * w + x] = 0;
            for (int y = 150; y <= 190; y++)
                for (int x = 100; x <= 850; x++) {
                    double sum = 0;
                    for (int sy = 0; sy < 4; sy++)
                        for (int sx = 0; sx < 4; sx++) {
                            double xx = x + (sx + .5) / 4 - .5,
                                    yy = y + (sy + .5) / 4 - .5,
                                    t = direction > 0 ? xx - 100 : 850 - xx;
                            if (t < 0 || t > 750) {
                                sum += 255;
                                continue;
                            }
                            double opening = .012 * t,
                                    dy = Math.abs(yy - 170),
                                    distance = Math.abs(dy - opening);
                            sum +=
                                    solid && dy <= opening + .8
                                            ? 0
                                            : distance <= .8 ? 0 : distance <= 2.1 ? 180 : 255;
                        }
                    gray[y * w + x] = (byte) Math.round(sum / 16.);
                }
            if (bar)
                for (int y = 80; y <= 302; y++)
                    for (int x = 449; x <= 450; x++) gray[y * w + x] = 0;
        }

        List<OpenHairpinPairs.Shape> found() {
            byte[] copy = gray.clone();
            var r = OpenHairpinPairs.detect(gray, w, h, staffs);
            assertArrayEquals(copy, gray);
            return r;
        }

        void positive(int direction) {
            var r = found();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(0, r.get(0).staff().index());
            assertEquals(direction, r.get(0).direction());
            assertTrue(r.get(0).left() >= 98 && r.get(0).left() <= 104);
            assertTrue(r.get(0).right() >= 846 && r.get(0).right() <= 852);
        }
    }

    @Test
    public void realCrescendoApertureSurvivesItsMergedRasterTipAndBar() {
        new Scene(1, true, false).positive(1);
    }

    @Test
    public void realDiminuendoApertureSurvivesItsMergedRasterTipAndBar() {
        new Scene(-1, true, false).positive(-1);
    }

    @Test
    public void aMergedRasterTipWithoutABarIsStillTheSameOpenWedge() {
        new Scene(1, false, false).positive(1);
    }

    @Test
    public void aSolidTriangleDoesNotBecomeAnOpenHairpin() {
        assertTrue(new Scene(1, false, true).found().isEmpty());
    }

    @Test
    public void unexplainedWideInteriorInkCannotBeDiscardedAsAperture() {
        var s = new Scene(1, true, false);
        for (int y = 167; y <= 173; y++) for (int x = 600; x <= 820; x++) s.gray[y * s.w + x] = 0;
        assertTrue(s.found().isEmpty());
    }

    @Test
    public void missingUpperArmCannotBorrowTheLowerArmAsPaperProof() {
        var s = new Scene(1, false, false);
        for (int x = 740; x <= 850; x++)
            for (int y = 154; y <= 166; y++) s.gray[y * s.w + x] = (byte) 255;
        assertTrue(s.found().isEmpty());
    }
}
