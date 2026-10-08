// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scaled and sloping ruled-paper controls for the decoder. */
public class PolyphonicHalfRestsGeometryTest {
    static List<ScoreNoteEvent> notes(float firstY, float secondY) {
        return List.of(
                new ScoreNoteEvent(0, 310f / 640, 7, 0, 1, firstY, false, 0, 1)
                        .withStemDirection(1),
                new ScoreNoteEvent(0, 392f / 640, 5, 0, 1, secondY, false, 0, 1)
                        .withStemDirection(1));
    }

    static List<MeasureRegion> measures() {
        return List.of(new MeasureRegion(0, 1, .1f, .9f));
    }

    static List<ScoreRestEvent> target(SixteenthRestDetector.Detection d) {
        return d.rests().stream()
                .filter(
                        r ->
                                Math.abs(r.positionInMeasure() - 310f / 640) < .035f
                                        && Math.abs(r.pageY() - 96f / 400) < .045f)
                .toList();
    }

    static void scaled(float scale) {
        var p = new PolyphonicHalfRestsTest.Page(true, true);
        int w = Math.round(640 * scale), h = Math.round(400 * scale);
        byte[] g = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                g[y * w + x] =
                        p.gray[
                                Math.min(399, (int) (y / scale)) * 640
                                        + Math.min(639, (int) (x / scale))];
        var r =
                target(
                        SixteenthRestDetector.detectWithDots(
                                g,
                                w,
                                h,
                                measures(),
                                List.of(
                                        new SixteenthRestDetector.Staff(
                                                100 * scale, 164 * scale, 16 * scale, 0, 1)),
                                notes(108f / 400, 124f / 400)));
        assertEquals("scale=" + scale + " " + r, 1, r.size());
        assertEquals(2, r.get(0).durationBeats(), 0);
    }

    static SixteenthRestDetector.Detection curved(float slope, boolean dot) {
        var p = new PolyphonicHalfRestsTest.Page(true, true);
        if (dot) p.oval(336, 94, 3, 3);
        byte[] warped = new byte[640 * 400];
        Arrays.fill(warped, (byte) 245);
        for (int x = 0; x < 640; x++)
            for (int y = 0; y < 400; y++) {
                int ny = y + Math.round(slope * (x - 320));
                if (ny >= 0 && ny < 400) warped[ny * 640 + x] = p.gray[y * 640 + x];
            }
        var before = warped.clone();
        var d =
                SixteenthRestDetector.detectWithDots(
                        warped,
                        640,
                        400,
                        measures(),
                        List.of(
                                new SixteenthRestDetector.Staff(
                                        100,
                                        164,
                                        16,
                                        0,
                                        1,
                                        StaffPitchTrack.linear(640, 164, 16, slope))),
                        notes(
                                (108 + slope * (310 - 320)) / 400,
                                (124 + slope * (392 - 320)) / 400));
        assertArrayEquals(before, warped);
        return d;
    }

    @Test
    public void smallerStaffScaleRetainsTheIndependentRectangle() {
        scaled(.5f);
        scaled(.75f);
    }

    @Test
    public void largerStaffScaleRetainsTheIndependentRectangle() {
        scaled(1.5f);
        scaled(2);
    }

    @Test
    public void upwardSlopingStaffRetainsIndependentHalfRest() {
        var r = target(curved(.075f, false));
        assertEquals(r.toString(), 1, r.size());
        assertEquals(2, r.get(0).durationBeats(), 0);
    }

    @Test
    public void downwardSlopingStaffRetainsIndependentHalfRest() {
        var r = target(curved(-.075f, false));
        assertEquals(r.toString(), 1, r.size());
        assertEquals(2, r.get(0).durationBeats(), 0);
    }

    @Test
    public void curvedDottedHalfRetainsDurationAndOriginalDotCoordinates() {
        var d = curved(.075f, true);
        var r = target(d);
        assertEquals(r.toString(), 1, r.size());
        assertEquals(3, r.get(0).durationBeats(), 0);
        assertEquals(1, d.dots().size());
        assertEquals(336, d.dots().get(0).x(), 1);
        assertEquals(94 + .075f * 16, d.dots().get(0).y(), 1);
    }

    @Test
    public void malformedRasterDoesNotCreateCandidates() {
        assertTrue(
                PolyphonicHalfRests.detectWithDots(null, 640, 400, measures(), List.of(), List.of())
                        .rests()
                        .isEmpty());
        assertTrue(
                PolyphonicHalfRests.detectWithDots(
                                new byte[1], 640, 400, measures(), List.of(), List.of())
                        .rests()
                        .isEmpty());
        assertTrue(
                PolyphonicHalfRests.detectWithDots(
                                new byte[640 * 400], 640, 400, null, List.of(), List.of())
                        .rests()
                        .isEmpty());
    }
}
