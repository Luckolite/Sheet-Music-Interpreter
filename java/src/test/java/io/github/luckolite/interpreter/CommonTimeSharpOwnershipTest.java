// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric clefs, key accidentals and time signs; no source score pixels. */
public class CommonTimeSharpOwnershipTest {
    static final class Page {
        final int w = 480, h = 240, gap = 16, first = 80;
        final byte[] gray = new byte[w * h], labels = new byte[w * h];

        Page() {
            Arrays.fill(gray, (byte) 255);
            staff();
            for (int y = 48; y <= 172; y++) for (int x = 35; x <= 58; x++) put(x, y, 3);
        }

        void put(int x, int y, int label) {
            gray[y * w + x] = 0;
            labels[y * w + x] = (byte) label;
        }

        void staff() {
            for (int y = 80; y <= 144; y += 16) for (int x = 20; x < 460; x++) put(x, y, 4);
        }

        void sharp(int x, int y, int thickness, int rise) {
            for (int xx : new int[] {x - 5, x + 3})
                for (int dx = 0; dx < thickness; dx++)
                    for (int yy = y - 22; yy <= y + 22; yy++) put(xx + dx, yy, 3);
            for (int cy : new int[] {y - 7, y + 7})
                for (int xx = x - 9; xx <= x + 8; xx++) {
                    int yy = cy - Math.round(rise * (xx - x + 9) / 17f);
                    for (int dy = 0; dy < thickness + 1; dy++) put(xx, yy + dy, 3);
                }
            staff();
        }

        void flat(int x, int y) {
            for (int yy = y - 24; yy <= y + 12; yy++) put(x, yy, 3);
            for (int yy = y - 5; yy <= y + 12; yy++)
                for (int xx = x; xx <= x + 10; xx++) {
                    double outer = Math.pow((xx - x - 3) / 7d, 2) + Math.pow((yy - y - 3) / 9d, 2);
                    double inner = Math.pow((xx - x - 3) / 4d, 2) + Math.pow((yy - y - 3) / 6d, 2);
                    if (outer <= 1 && inner >= 1) put(xx, yy, 3);
                }
            staff();
        }

        void sign(boolean cut) {
            for (int y = 88; y <= 136; y++)
                for (int x = 230; x <= 262; x++) {
                    double outer = Math.pow((x - 246) / 16d, 2) + Math.pow((y - 112) / 24d, 2);
                    double inner = Math.pow((x - 246) / 10d, 2) + Math.pow((y - 112) / 18d, 2);
                    if (outer <= 1 && inner >= 1 && !(x > 246 && Math.abs(y - 112) < 9))
                        put(x, y, 5);
                }
            if (cut) for (int y = 84; y <= 140; y++) for (int x = 245; x <= 247; x++) put(x, y, 5);
            staff();
        }

        List<ScoreMeterChange> read() {
            return MeterChangeDetector.commonTimeReadings(
                    labels,
                    gray,
                    w,
                    h,
                    List.of(new MeasureRegion(.1f, .96f, .15f, .85f)),
                    List.of());
        }
    }

    @Test
    public void twoShaftsAndTwoCrossbarsCannotBecomeCutTime() {
        for (int center : new int[] {96, 104, 112, 120, 128})
            for (int thickness : new int[] {1, 2})
                for (int rise : new int[] {0, 3, 5}) {
                    Page p = new Page();
                    p.sharp(126, center, thickness, rise);
                    assertEquals(
                            "center=" + center + " thickness=" + thickness + " rise=" + rise,
                            List.of(),
                            p.read());
                }
    }

    @Test
    public void realCommonAndCutTimeSurviveSharpKeys() {
        for (boolean cut : new boolean[] {false, true})
            for (int count = 0; count <= 3; count++) {
                Page p = new Page();
                for (int i = 0; i < count; i++)
                    p.sharp(82 + 26 * i, 80 + new int[] {0, 24, -8}[i], 2, 3);
                p.sign(cut);
                assertEquals(
                        "cut=" + cut + " sharps=" + count,
                        List.of(new ScoreMeterChange(0, cut ? 2 : 4, cut ? 2 : 4)),
                        p.read());
            }
    }

    @Test
    public void realCommonAndCutTimeSurviveFlatKeys() {
        for (boolean cut : new boolean[] {false, true})
            for (int count = 1; count <= 3; count++) {
                Page p = new Page();
                for (int i = 0; i < count; i++) p.flat(82 + 26 * i, 112 + new int[] {0, -24, 8}[i]);
                p.sign(cut);
                assertEquals(
                        "cut=" + cut + " flats=" + count,
                        List.of(new ScoreMeterChange(0, cut ? 2 : 4, cut ? 2 : 4)),
                        p.read());
            }
    }

    @Test
    public void analysisDoesNotChangeTheKeyOrSourceInk() {
        Page p = new Page();
        p.sharp(126, 112, 2, 3);
        var labels = p.labels.clone();
        var gray = p.gray.clone();
        p.read();
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
    }

    /** Independently generated narrow signs, including a straight left bowl. */
    static final class NarrowPage {
        final int w, h, gap, radius;
        final float scale, slope;
        final byte[] gray;

        NarrowPage(int gap, float slope, int radius, int style, boolean cut) {
            this.gap = gap;
            this.scale = gap / 16f;
            this.slope = slope;
            this.radius = radius;
            w = Math.round(480 * scale);
            h = Math.round(240 * scale);
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int y = 80; y <= 144; y += 16) for (int x = 20; x < 460; x++) dot(x, y);
            for (int y = 88; y <= 136; y++)
                for (int x = 246 - radius; x <= 246 + radius; x++) {
                    boolean dark;
                    if (style == 2) dark = x <= 246 - radius + 5 || y <= 96 || y >= 128;
                    else {
                        double outer =
                                Math.pow((x - 246) / (double) radius, 2)
                                        + Math.pow((y - 112) / 24d, 2);
                        double inner =
                                Math.pow((x - 246) / (double) (radius - 6), 2)
                                        + Math.pow((y - 112) / 18d, 2);
                        dark = outer <= 1 && inner >= 1;
                    }
                    if (dark && !(x > 246 && Math.abs(y - 112) < 9)) dot(x, y);
                }
            if (style == 1)
                for (int y = 101; y <= 111; y++)
                    for (int x = 246 + radius - 8; x <= 246 + radius; x++)
                        if (Math.pow(x - (246 + radius - 4), 2) + Math.pow(y - 106, 2) <= 25)
                            dot(x, y);
            if (cut) for (int y = 84; y <= 140; y++) for (int x = 245; x <= 247; x++) dot(x, y);
        }

        void dot(int x, int y) {
            for (int xx = Math.round(x * scale); xx < Math.round((x + 1) * scale); xx++)
                for (int yy = Math.round(y * scale); yy < Math.round((y + 1) * scale); yy++) {
                    int row = yy + Math.round(slope * (xx - w * .5f));
                    if (xx >= 0 && xx < w && row >= 0 && row < h) gray[row * w + xx] = 0;
                }
        }

        int read() {
            return CommonTimeMeter.read(
                    gray,
                    w,
                    h,
                    Math.round((246 - radius) * scale),
                    Math.round((247 + radius) * scale) - 1,
                    Math.round(80 * scale),
                    gap,
                    slope);
        }
    }

    @Test
    public void narrowCurvedCommonAndCutTimeRemainValid() {
        for (boolean cut : new boolean[] {false, true})
            for (int radius : new int[] {11, 12, 13}) {
                assertTrue(2 * radius + 1 <= 28);
                assertEquals(
                        "cut=" + cut + " radius=" + radius,
                        cut ? 2 : 4,
                        new NarrowPage(16, 0, radius, 0, cut).read());
            }
    }

    @Test
    public void narrowAsymmetricCommonAndCutTimeRemainValid() {
        for (boolean cut : new boolean[] {false, true})
            for (int radius : new int[] {12, 13})
                assertEquals(
                        "cut=" + cut + " radius=" + radius,
                        cut ? 2 : 4,
                        new NarrowPage(16, 0, radius, 1, cut).read());
    }

    @Test
    public void narrowStraightBowlCommonAndCutTimeRemainValid() {
        for (boolean cut : new boolean[] {false, true})
            for (int radius : new int[] {12, 13})
                assertEquals(
                        "cut=" + cut + " radius=" + radius,
                        cut ? 2 : 4,
                        new NarrowPage(16, 0, radius, 2, cut).read());
    }

    @Test
    public void narrowSignsRemainValidAcrossScaleAndShallowStaffSlope() {
        for (int gap : new int[] {8, 12, 24})
            for (float slope : new float[] {0, .015f})
                assertEquals(
                        "common gap=" + gap + " slope=" + slope,
                        4,
                        new NarrowPage(gap, slope, 12, 0, false).read());
        for (int gap : new int[] {8, 16})
            for (float slope : new float[] {0, .015f})
                assertEquals(
                        "cut gap=" + gap + " slope=" + slope,
                        2,
                        new NarrowPage(gap, slope, 12, 0, true).read());
    }
}
