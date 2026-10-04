// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffPitchTrackTest {
    private static float bottom(int x, int width) {
        return 210f - 45f * x * x / (width * (float) width);
    }

    @Test
    public void followsFivePrintedRulesThroughSmoothPageCurvature() {
        int width = 1200, height = 350;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 230);
        for (int x = 30; x < width - 30; x++)
            for (int line = 0; line < 5; line++) {
                int row = Math.round(bottom(x, width) - line * 10);
                gray[row * width + x] = 50;
                gray[(row + 1) * width + x] = 50;
            }
        var track = StaffPitchTrack.detect(gray, width, height, 155, 195, 10);
        assertNotNull(track);
        for (int x : new int[] {190, 440, 720, 970}) {
            assertEquals(bottom(x, width) + .5f, track.at(x)[0], 2f);
            assertEquals(10, track.at(x)[1], .6f);
        }
    }

    @Test
    public void parallelStraightStaffsDoNotCreateAnArtificialWarp() {
        int width = 1200, height = 350;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 230);
        for (int bottom : new int[] {140, 210})
            for (int x = 30; x < width - 30; x++)
                for (int line = 0; line < 5; line++) gray[(bottom - line * 10) * width + x] = 50;
        assertNull(StaffPitchTrack.detect(gray, width, height, 100, 140, 10));
        assertNull(StaffPitchTrack.detect(gray, width, height, 170, 210, 10));
    }

    @Test
    public void isolatedLedgerGroupsCannotEstablishAStaffTrack() {
        int width = 1200, height = 350;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 230);
        for (int strip = 0; strip < 7; strip++) {
            int center = Math.round(width * (.15f + strip * .12f));
            for (int x = center - 20; x < center + 20; x++)
                for (int line = 0; line < 5; line++)
                    gray[(Math.round(bottom(center, width)) - line * 10) * width + x] = 50;
        }
        assertNull(StaffPitchTrack.detect(gray, width, height, 155, 195, 10));
    }

    @Test
    public void topLineNotesKeepTheirWrittenPitchAcrossACurvedPage() {
        int width = 1200, height = 350;
        byte[] gray = new byte[width * height], labels = new byte[width * height];
        Arrays.fill(gray, (byte) 230);
        for (int x = 30; x < width - 30; x++)
            for (int line = 0; line < 5; line++) {
                int row = Math.round(bottom(x, width) - line * 10);
                for (int y = row; y <= row + 1; y++) {
                    gray[y * width + x] = 50;
                    labels[y * width + x] = 4;
                }
            }
        for (int center : new int[] {200, 600, 1000}) {
            int cy = Math.round(bottom(center, width) - 40);
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = center - 6; x <= center + 6; x++)
                    if ((x - center) * (x - center) / 36f + (y - cy) * (y - cy) / 16f <= 1) {
                        gray[y * width + x] = 0;
                        labels[y * width + x] = 2;
                    }
            for (int y = cy; y < cy + 30; y++) {
                gray[y * width + center - 6] = 0;
                labels[y * width + center - 6] = 1;
            }
        }
        var score =
                OmrScoreInterpreter.analyze(
                        labels,
                        gray,
                        width,
                        height,
                        java.util.List.of(new MeasureRegion(.02f, .98f, .2f, .8f)));
        assertEquals(3, score.notes().size());
        assertTrue(score.notes().stream().allMatch(n -> n.staffStep() == 8));
    }

    @Test
    public void shadedPaperDoesNotOverrideTheLocalFiveRuleAnchor() throws Exception {
        int width = 400, height = 280;
        byte[] gray = new byte[width * height], labels = new byte[width * height];
        Arrays.fill(gray, (byte) 150);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < 380; x++) {
                gray[(80 + line * 16) * width + x] = 80;
                labels[(80 + line * 16) * width + x] = 4;
            }
        var staffType = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var constructor = staffType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object staff = constructor.newInstance(84f, 148f, 16f);
        var headType = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var hc = headType.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        Object head = hc.newInstance(200, 170, 190, 49, 63, 180f, 56f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "localStaffPitch",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        staffType,
                        headType);
        method.setAccessible(true);
        float[] pitch = (float[]) method.invoke(null, labels, gray, width, height, staff, head);
        assertEquals(144, pitch[0], .01);
        assertEquals(16, pitch[1], .01);
    }

    @Test
    public void strongerUnlabelledBeamCannotHideTheFiveStaffRules() {
        int width = 400, height = 280;
        byte[] gray = new byte[width * height], labels = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 110; x <= 245; x++) {
                if (line == 0 && x > 215) continue;
                gray[(80 + line * 16) * width + x] = 0;
                labels[(80 + line * 16) * width + x] = 4;
            }
        for (int x = 90; x < 270; x++) gray[160 * width + x] = 0;
        var pitch = StaffPitchTrack.localRules(labels, gray, width, height, 180, 170, 190, 152, 16);
        assertNotNull(pitch);
        assertEquals(144, pitch[0], .01);
        assertEquals(16, pitch[1], .01);
    }

    @Test
    public void slopingRulesAtCurledPageEdgeKeepTheSpacePitch() {
        int width = 400, height = 280;
        byte[] gray = new byte[width * height], labels = new byte[width * height];
        Arrays.fill(gray, (byte) 150);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < 380; x++) {
                int row = 80 + line * 16 + Math.round((x - 180) * .12f);
                gray[row * width + x] = 70;
                labels[row * width + x] = 4;
            }
        assertNull(StaffPitchTrack.localRules(labels, gray, width, height, 180, 170, 190, 140, 16));
        var pitch =
                StaffPitchTrack.localRules(
                        labels, gray, width, height, 180, 170, 190, 140, 16, true);
        assertNotNull(pitch);
        assertEquals(144, pitch[0], 1);
        assertEquals(16, pitch[1], .5);
        // A4 is the space three diatonic steps above the treble bottom rule.
        assertEquals(3, Math.round((pitch[0] - 120) * 2 / pitch[1]));
    }

    @Test
    public void beamsBesideFadedOuterRulesCannotMakeAFlatStaffCurve() {
        int width = 1200, height = 280;
        byte[] gray = new byte[width * height], labels = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int x = 30; x < width - 30; x++)
            for (int line = 0; line < 5; line++) {
                int row = 100 + line * 10;
                labels[row * width + x] = 4;
                gray[row * width + x] =
                        (byte) ((x < 390 && line == 0 || x > 850 && line == 4) ? 200 : 60);
            }
        for (int x = 30; x < 390; x++) {
            gray[150 * width + x] = 0;
            labels[150 * width + x] = 5;
        }
        for (int x = 850; x < width - 30; x++) {
            gray[90 * width + x] = 0;
            labels[90 * width + x] = 5;
        }
        assertNull(
                "All five faded straight rules outweigh nearby darker beams",
                StaffPitchTrack.detect(gray, width, height, 100, 140, 10));
    }

    @Test
    public void successiveStripWindowsPreserveVaryingPaperAndCallerPixels() {
        int width = 1200, height = 350;
        byte[] gray = new byte[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                gray[y * width + x] = (byte) ((x / 90 & 1) == 0 ? 215 : 250);
        for (int x = 30; x < width - 30; x++)
            for (int line = 0; line < 5; line++) {
                int row = Math.round(bottom(x, width) - line * 10);
                byte ink = (byte) (50 + x / 120 % 4 * 10);
                gray[row * width + x] = ink;
                gray[(row + 1) * width + x] = ink;
            }
        byte[] original = gray.clone();
        var track = StaffPitchTrack.detect(gray, width, height, 155, 195, 10);
        assertNotNull(track);
        for (int x : new int[] {190, 440, 720, 970}) {
            assertEquals(bottom(x, width) + .5f, track.at(x)[0], 2f);
            assertEquals(10, track.at(x)[1], .6f);
        }
        assertArrayEquals(original, gray);
    }
}
