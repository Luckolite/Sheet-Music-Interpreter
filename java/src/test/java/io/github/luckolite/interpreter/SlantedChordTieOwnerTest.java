// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlantedChordTieOwnerTest {
    private PrintedSlurOwners.Binding bind(boolean body, boolean above) {
        int w = 240, h = 220;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        float y = 120, cap = above ? 125 : 115;
        if (body)
            for (int yy = 116; yy <= 124; yy++) for (int x = 34; x <= 46; x++) gray[yy * w + x] = 0;
        for (int yy = 116; yy <= 124; yy++) for (int x = 184; x <= 196; x++) gray[yy * w + x] = 0;
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 150, 12}, new float[] {239, 150, 12}));
        var bow =
                new PrintedSlurContours.Bow(
                        List.of(
                                new PrintedSlurContours.Point(46, cap),
                                new PrintedSlurContours.Point(184, above ? 115 : 125)),
                        above,
                        1,
                        0);
        var heads =
                List.of(
                        new PrintedSlurOwners.GeometryHead(0, 40, y, 0, 1),
                        new PrintedSlurOwners.GeometryHead(1, 40, above ? 144 : 96, 0, 1),
                        new PrintedSlurOwners.GeometryHead(2, 190, y, 0, 1));
        return PrintedSlurOwners.bind("original-slanted-rim", 0, bow, heads, gray, w, h, track);
    }

    @Test
    public void lowerBowOwnsItsSlantedRim() {
        assertEquals(0, bind(true, false).span().orElseThrow().start().noteIndex());
    }

    @Test
    public void upperBowOwnsItsSlantedRim() {
        assertEquals(0, bind(true, true).span().orElseThrow().start().noteIndex());
    }

    @Test
    public void noBodyCannotProveCrossCenterAttachment() {
        var r = bind(false, false);
        assertTrue(r.span().isEmpty() || r.span().get().start().noteIndex() != 0);
    }
}
