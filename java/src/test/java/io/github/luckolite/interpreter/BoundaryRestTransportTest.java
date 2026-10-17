// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.*;
import java.util.*;

/** Uses existing original polygon/ellipse fixtures to verify unchanged rest transport. */
public class BoundaryRestTransportTest {
    static SixteenthRestDetector.Detection curved(byte[] g) {
        return SixteenthRestDetector.detectWithDots(
                g,
                400,
                240,
                List.of(new MeasureRegion(0, 1, .1f, .8f)),
                List.of(
                        new SixteenthRestDetector.Staff(
                                80, 144, 16, 0, 1, StaffPitchTrack.linear(400, 144, 16, 0))),
                List.of());
    }

    static void parent(SixteenthRestDetector.Detection d, double beats) {
        assertEquals(1, d.rests().size());
        assertEquals(beats, d.rests().get(0).durationBeats(), .0001);
        assertEquals(1, d.dots().size());
        assertEquals(d.rests().get(0), d.dots().get(0).rest());
    }

    static byte[] existing(String name, Class<?>[] types, Object... a) throws Exception {
        var m = DottedRestTest.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, a);
    }

    static void dot(byte[] g, int x, int y) throws Exception {
        var m =
                DottedRestTest.class.getDeclaredMethod(
                        "dot", byte[].class, int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(null, g, x, y, 3);
    }

    @Test
    public void curvedDottedQuarterKeepsMappedParent() {
        var p = new CurvedRestRecognitionTest.Page(false, 0, false, 1);
        parent(p.detect(List.of()), 1.5);
    }

    @Test
    public void reversedCurveKeepsDottedQuarterAssociation() {
        var p = new CurvedRestRecognitionTest.Page(true, 0, false, 1);
        parent(p.detect(List.of()), 1.5);
    }

    @Test
    public void rectifiedDottedEighthKeepsMappedParent() throws Exception {
        byte[] g = existing("flaggedRest", new Class<?>[] {boolean.class}, false);
        dot(g, 197, 104);
        var d = curved(g);
        assertEquals(1, d.rests().size());
        assertEquals(.75, d.rests().get(0).durationBeats(), .0001);
        // The unchanged baseline reports this same dot through three acquisition views.
        // Preserve every association rather than normalizing unrelated output here.
        assertEquals(3, d.dots().size());
        for (var dot : d.dots()) {
            assertEquals(d.rests().get(0), dot.rest());
            assertEquals(197, dot.x(), 0);
            assertEquals(104, dot.y(), 0);
        }
    }

    @Test
    public void rectifiedDottedHalfKeepsMappedParent() {
        var p = new HalfRestRecognitionTest.Page();
        p.half();
        p.ellipse(134, 104, 3, 3, false);
        parent(curved(p.gray), 3);
    }

    @Test
    public void optionalBoundsDoNotChangeRecognitionOrPixels() throws Exception {
        byte[] g = existing("page", new Class<?>[] {}), saved = g.clone();
        var measures = List.of(new MeasureRegion(0, 1, .1f, .8f));
        var staffs = List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1));
        Method ordinary = null, tracked = null;
        for (var m : SixteenthRestDetector.class.getDeclaredMethods())
            if (m.getName().equals("detectWithDots")) {
                if (m.getParameterCount() == 8) ordinary = m;
                if (m.getParameterCount() == 9) tracked = m;
            }
        assertNotNull(ordinary);
        assertNotNull(tracked);
        ordinary.setAccessible(true);
        tracked.setAccessible(true);
        var bounds = new HashMap<ScoreRestEvent, int[]>();
        Object a = ordinary.invoke(null, g, 400, 240, measures, staffs, List.of(), false, false);
        Object b =
                tracked.invoke(
                        null, g, 400, 240, measures, staffs, List.of(), false, false, bounds);
        assertEquals(a, b);
        assertFalse(bounds.isEmpty());
        assertArrayEquals(saved, g);
    }

    static void realQuarterByBoundary(int boundary) throws Exception {
        byte[] g = existing("page", new Class<?>[] {});
        var ordinary =
                SixteenthRestDetector.detect(
                        g,
                        400,
                        240,
                        List.of(new MeasureRegion(0, 1, .1f, .8f)),
                        List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1)),
                        List.of());
        assertEquals(1, ordinary.size());
        assertEquals(1, ordinary.get(0).durationBeats(), 0);
        for (int y = 80; y <= 144; y++)
            for (int x = boundary; x <= boundary + 1; x++) g[y * 400 + x] = 0;
        var region = new MeasureRegion(0, boundary / 400f, .1f, .8f);
        var rest = new ScoreRestEvent(0, 176.5f / boundary, 113f / 240, 47f / 240, 0, 1, 1);
        assertFalse(PrintedBoundaryRestInk.owns(g, 400, 240, region, rest, 16, 170, 183, 90, 136));
    }

    @Test
    public void recognizedQuarterBesideBoundaryKeepsIndependentGlyph() throws Exception {
        realQuarterByBoundary(188);
    }

    @Test
    public void recognizedQuarterCrossingBoundaryKeepsIndependentGlyph() throws Exception {
        realQuarterByBoundary(180);
    }

    @Test
    public void invalidTransportExtremesCannotAcquireProof() {
        var p = new PrintedBoundaryRestInkTest.Page();
        assertFalse(
                PrintedBoundaryRestInk.owns(
                        p.gray, 400, 260, p.region, p.rest, 14, -1, 353, 78, 120));
        assertFalse(
                PrintedBoundaryRestInk.owns(
                        p.gray, 400, 260, p.region, p.rest, 14, 334, 400, 78, 120));
        assertFalse(
                PrintedBoundaryRestInk.owns(
                        p.gray,
                        400,
                        260,
                        p.region,
                        p.rest,
                        14,
                        334,
                        353,
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE));
        assertFalse(
                PrintedBoundaryRestInk.owns(
                        p.gray, 400, 260, p.region, p.rest, Float.NaN, 334, 353, 78, 120));
    }
}
