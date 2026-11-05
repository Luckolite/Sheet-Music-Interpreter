// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Original raster fixture for a dynamic with an attached curve. */
public class JoinedForteContourTest {
    @Test
    public void bothCrossbarAndReturningFootAreRequired() throws Exception {
        assertTrue(proved(true, true));
        assertFalse(proved(false, true));
        assertFalse(proved(true, false));
    }

    private static boolean proved(boolean crossbar, boolean foot) throws Exception {
        int width = 220, height = 220;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 205);
        var points = new ArrayList<PrintedSlurContours.Point>();
        for (int x = 72; x <= 128; x++) {
            points.add(new PrintedSlurContours.Point(x, 145));
            ink(gray, width, 145, x, x);
        }
        for (int y = 146; y <= 177; y++) {
            int left = 100 - (y - 146) / 5 - Math.max(0, y - 161) / 3 - (y >= 175 ? 1 : 0);
            ink(gray, width, y, left, left + 7);
            if (crossbar && y >= 153 && y <= 154)
                ink(gray, width, y, left - 2, left + 11);
            if (foot && y >= 168 && y <= 172)
                ink(gray, width, y, left - 7, left - 4);
            if (foot && y >= 173)
                ink(gray, width, y, left - (y >= 175 ? 6 : 7), left + 7);
        }
        var track = StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 135, 15}, new float[] {width - 1, 135, 15}));
        var bow = new PrintedSlurContours.Bow(points, false, .4f, .05f);
        var method = PrintedSlurRecognition.class.getDeclaredMethod("joinedForte",
                byte[].class, int.class, int.class, PrintedSlurContours.Bow.class,
                StaffPitchTrack.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, gray, width, height, bow, track);
    }

    private static void ink(byte[] gray, int width, int y, int left, int right) {
        Arrays.fill(gray, y * width + left, y * width + right + 1, (byte) 25);
    }
}
