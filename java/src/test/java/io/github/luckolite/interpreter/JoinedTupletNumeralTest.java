// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original licensed glyph rasterizations plus procedural curves and foreign continuations. */
public class JoinedTupletNumeralTest {
    private record Scene(
            int style,
            int size,
            int digit,
            int lift,
            int shape,
            int l,
            int t,
            int r,
            int b,
            int rl,
            int rt,
            int rr,
            int rb,
            byte[] raw,
            BitSet curve) {}

    private static List<Scene> scenes() throws IOException {
        return scenes("/joined-numerals/contexts.bin.gz", 480);
    }

    private static List<Scene> scenes(String resource, int expected) throws IOException {
        var stream = JoinedTupletNumeralTest.class.getResourceAsStream(resource);
        assertNotNull("licensed procedural fixtures", stream);
        var result = new ArrayList<Scene>();
        try (var in = new DataInputStream(new GZIPInputStream(stream))) {
            assertEquals(0x4a444e31, in.readInt());
            assertEquals(expected, in.readInt());
            for (int i = 0; i < expected; i++) {
                int[] q = new int[13];
                for (int k = 0; k < q.length; k++) q[k] = in.readInt();
                byte[] raw = new byte[16384];
                in.readFully(raw);
                int length = in.readInt();
                assertTrue(length >= 0 && length <= 2048);
                byte[] mask = new byte[length];
                in.readFully(mask);
                result.add(
                        new Scene(
                                q[0],
                                q[1],
                                q[2],
                                q[3],
                                q[4],
                                q[5],
                                q[6],
                                q[7],
                                q[8],
                                q[9],
                                q[10],
                                q[11],
                                q[12],
                                raw,
                                BitSet.valueOf(mask)));
            }
            assertEquals(-1, in.read());
        }
        return result;
    }

    private static boolean positive(Scene s) {
        return s.style == 2 && s.size == 28 && s.digit == 3 && (s.shape == 0 || s.lift == 4);
    }

    private static final class Limit extends RuntimeException {}

    private static boolean accepts(Scene s, int variant) {
        byte[] raw = s.raw.clone();
        BitSet curve = (BitSet) s.curve.clone();
        int l = s.rl, t = s.rt, r = s.rr, b = s.rb;
        if (variant == 1 || variant == 2) {
            int y = s.t + Math.round((s.b - s.t) * .48f), anchor = -1;
            for (int x = s.l; x <= s.r; x++) if ((raw[y * 128 + x] & 255) <= 165) anchor = x;
            assertTrue(anchor >= 0);
            int xx = s.r + (variant == 1 ? 10 : 12),
                    yy = y - (variant == 1 ? 7 : 8),
                    n = Math.max(Math.abs(xx - anchor), Math.abs(yy - y)) * 4;
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                int px = Math.round(anchor + (xx - anchor) * f),
                        py = Math.round(y + (yy - y) * f),
                        p = py * 128 + px;
                raw[p] = (byte) Math.min(raw[p] & 255, variant == 1 ? 30 : 230);
            }
            // The entire new core branch is retained in the proposal, never clipped to the old box.
            l = 128;
            t = 128;
            r = -1;
            b = -1;
            for (int py = 0; py < 128; py++)
                for (int px = 0; px < 128; px++)
                    if ((raw[py * 128 + px] & 255) <= 165 && !curve.get(py * 128 + px)) {
                        l = Math.min(l, px);
                        r = Math.max(r, px);
                        t = Math.min(t, py);
                        b = Math.max(b, py);
                    }
        }
        byte[] frozen = raw.clone();
        long[] work = {0};
        boolean accepted = false;
        ObservedFringeContinuation.Charge charge =
                () -> {
                    if (++work[0] > 8_000_000) throw new Limit();
                };
        try {
            var outcome =
                    JointTerminalThree.inspect(
                            raw,
                            128,
                            128,
                            l,
                            t,
                            r,
                            b,
                            0,
                            variant == 3 ? t : 0,
                            127,
                            127,
                            curve,
                            16f,
                            charge,
                            () -> {});
            if (outcome.supported()) {
                var g = outcome.glyphExtent();
                var fringe =
                        ObservedFringeContinuation.inspect(
                                raw,
                                128,
                                128,
                                curve,
                                g == null ? l : g.l(),
                                g == null ? t : g.t(),
                                g == null ? r : g.r(),
                                g == null ? b : g.b(),
                                0,
                                0,
                                127,
                                127,
                                16f,
                                charge);
                accepted = fringe.kind() == ObservedFringeContinuation.Kind.CLEAR;
            }
        } catch (Limit exhausted) {
            /* Resource exhaustion is unknown, never a numeral proof. */
        }
        assertArrayEquals(frozen, raw);
        assertEquals(s.curve, curve);
        return accepted;
    }

    @Test
    public void crossingCurvesKeepFourOriginalThrees() throws Exception {
        int count = 0;
        for (var s : scenes())
            if (positive(s)) {
                assertTrue(accepts(s, 0));
                count++;
            }
        assertEquals(4, count);
    }

    @Test
    public void allOtherOriginalDigitsRefuseThree() throws Exception {
        int count = 0;
        for (var s : scenes())
            if (s.digit != 3) {
                assertFalse(
                        "digit=" + s.digit + " style=" + s.style + " size=" + s.size,
                        accepts(s, 0));
                count++;
            }
        assertEquals(432, count);
    }

    @Test
    public void completeNonThreeReadingsKeepTheirObservedClasses() throws Exception {
        int rivals = 0, twos = 0;
        for (var s : scenes()) {
            byte[] raw = s.raw.clone();
            BitSet curve = (BitSet) s.curve.clone();
            long[] work = {0};
            var value =
                    JointTerminalThree.inspect(
                            raw,
                            128,
                            128,
                            s.rl,
                            s.rt,
                            s.rr,
                            s.rb,
                            0,
                            0,
                            127,
                            127,
                            curve,
                            16f,
                            () -> {
                                if (++work[0] > 8_000_000) throw new Limit();
                            },
                            () -> {});
            if (!value.supported() && value.classifiedDigit() >= 0) {
                assertEquals(s.digit, value.classifiedDigit());
                assertNotEquals(3, value.classifiedDigit());
                rivals++;
                if (s.digit == 2) twos++;
            }
            assertArrayEquals(s.raw, raw);
            assertEquals(s.curve, curve);
        }
        assertEquals(19, rivals);
        assertEquals(1, twos);
    }

    @Test
    public void independentOpticalSizesKeepAllRivalDigitsConservative() throws Exception {
        int rivals = 0;
        for (var s : scenes("/joined-numerals/certainty-grid.bin.gz", 2880)) {
            if (s.digit != 3) {
                assertFalse("digit=" + s.digit + " size=" + s.size, accepts(s, 0));
                rivals++;
            }
        }
        assertEquals(2592, rivals);
    }

    @Test
    public void connectedForeignCoreIsNotClippedAway() throws Exception {
        for (var s : scenes()) if (positive(s)) assertFalse(accepts(s, 1));
    }

    @Test
    public void weakContinuationRemainsPartOfTheOriginalFringe() throws Exception {
        for (var s : scenes()) if (positive(s)) assertFalse(accepts(s, 2));
    }

    @Test
    public void clippedFramesDoNotSupplyMissingPaper() throws Exception {
        for (var s : scenes()) if (positive(s)) assertFalse(accepts(s, 3));
    }

    @Test
    public void glyphCacheIncludesCoveredCapAndExtentDependencies() {
        var box = new JoinedTupletNumeral.Glyph(8, 8, 15, 25);
        BitSet base = new BitSet();
        base.set(10 * 40 + 12);
        var saved = JoinedTupletNumeral.terminalFootprint(box, base, 40, 40, 16f, () -> {});
        for (int[] sample : new int[][] {{3, 8}, {20, 8}, {8, 3}, {8, 30}, {12, 10}}) {
            var other = (BitSet) base.clone();
            other.flip(sample[1] * 40 + sample[0]);
            assertNotEquals(
                    saved,
                    JoinedTupletNumeral.terminalFootprint(box, other, 40, 40, 16f, () -> {}));
        }
        var outside = (BitSet) base.clone();
        outside.set(39 * 40 + 39);
        assertEquals(
                saved, JoinedTupletNumeral.terminalFootprint(box, outside, 40, 40, 16f, () -> {}));
        base.clear();
        assertFalse(saved.allCurveSamples().isEmpty());
    }

    @Test
    public void exhaustedAndMalformedCallsCannotSupplyATuplet() {
        assertEquals(
                JoinedTupletNumeral.Status.RESOURCE_UNKNOWN,
                JoinedTupletNumeral.inspect(null, 0, 0, List.of(), List.of(), 0, 1, 2, 0).status());
        assertEquals(
                JoinedTupletNumeral.Status.UNKNOWN,
                JoinedTupletNumeral.inspect(new byte[4], 2, 2, List.of(), List.of(), 0, 1, 2, 100)
                        .status());
        assertFalse(
                TripletRhythmDetector.hasJoinedBracketCandidate(
                        new byte[4], 2, 2, 0, 1, 1, Float.NaN));
    }
}
