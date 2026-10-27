// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rasters: literal prefixes, retained strict evidence, real beam contacts and bounded work. */
public final class PrintedBeamConnectedPrefixTest {
    static final int W = 240, H = 240, HX = 120, HY = 80, G = 16, X = 110;

    static byte[] scene(boolean tail, boolean shortStem, boolean paper) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) (paper ? 180 : 245));
        if (paper) return raw;
        int end = shortStem ? 94 : 128;
        for (int y = HY + 3; y <= end; y++) raw[y * W + X] = 100;
        if (tail) raw[(end + 2) * W + X] = 100;
        return raw;
    }

    static List<PrintedBeamShafts.Shaft> scan(byte[] raw) {
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), raw, W, H, 0, n -> {}, true);
        return PrintedBeamShafts.atAll(
                raw, W, H, HX, HY, G, new PrintedBeamShafts.Budget(8_000_000), frame);
    }

    static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }

    @Test
    public void disconnectedSpeckCannotEraseLiteralFaintPrefix() {
        byte[] raw = scene(true, false, false), before = raw.clone();
        var a = scan(raw);
        assertEquals(1, a.size());
        var shaft = a.get(0);
        assertEquals(128, shaft.endY());
        assertTrue(shaft.pixels().has(X, 128));
        assertFalse(shaft.pixels().has(X, 129));
        assertFalse(shaft.pixels().has(X, 130));
        assertArrayEquals(before, raw);
        var strict =
                PrintedBeamShafts.atStrict(
                        raw,
                        W,
                        H,
                        HX,
                        HY,
                        G,
                        new PrintedBeamShafts.Budget(8_000_000),
                        shaft.pixels().frame);
        assertTrue(
                "strict scanner still requires its original complete observed cap",
                strict.isEmpty());
        assertTrue(scan(scene(true, true, false)).isEmpty());
        assertTrue(scan(scene(false, false, true)).isEmpty());
        var mirror = scene(true, false, false);
        for (int y = 0; y < H / 2; y++)
            for (int x = 0; x < W; x++) {
                byte v = mirror[y * W + x];
                mirror[y * W + x] = mirror[(H - 1 - y) * W + x];
                mirror[(H - 1 - y) * W + x] = v;
            }
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), mirror, W, H, 0, n -> {}, true);
        var reflected =
                PrintedBeamShafts.atAll(
                        mirror,
                        W,
                        H,
                        100,
                        H - 1 - HY,
                        G,
                        new PrintedBeamShafts.Budget(8_000_000),
                        frame);
        assertEquals(1, reflected.size());
        assertEquals(H - 1 - 128, reflected.get(0).endY());
    }

    static void pixel(byte[] a, int w, int x, int y) {
        if (x >= 0 && x < w && y >= 0 && y < a.length / w) a[y * w + x] = 0;
    }

    static byte[] raw(float scale, boolean body, boolean rail, boolean down) {
        int w = 120, h = 128;
        byte[] a = new byte[w * h];
        Arrays.fill(a, (byte) 255);
        for (int y : new int[] {40, 52, 64, 76, 88})
            for (int x = 4; x < 116; x++) pixel(a, w, x, y);
        float g = 12 * scale;
        for (int cx : new int[] {20, 88}) {
            if (body)
                for (int y = 80; y <= 96; y++)
                    for (int x = cx - 10; x <= cx + 10; x++)
                        if ((x - cx) * (x - cx) / (.6 * g * .6 * g)
                                        + (y - 88) * (y - 88) / (.4 * g * .4 * g)
                                <= 1) pixel(a, w, x, y);
            int sx = Math.round(cx + .6f * g);
            for (int y = 24; y <= 88; y++) pixel(a, w, sx, y);
            if (down) {
                int dx = Math.round(cx - .6f * g);
                for (int y = 88; y <= 117; y++) pixel(a, w, dx, y);
            }
        }
        if (rail)
            for (int y = 24; y < 24 + Math.max(3, Math.round(g * .45f)); y++)
                for (int x = Math.round(20 + .6f * g); x <= Math.round(88 + .6f * g); x++)
                    pixel(a, w, x, y);
        return a;
    }

    static ScorePageInterpretation score(int kind) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {20, 88}) {
            var n = new ScoreNoteEvent(0, x / 120f, 0, 0, 1, 88 / 128f, false, 0, 1);
            if (kind == 1) n = n.withVisualSize(ScoreNoteEvent.VisualSize.CUE);
            if (kind == 2) n = n.withArticulations(NoteOrnament.GRACE);
            notes.add(n);
        }
        return new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
    }

    static PrintedBeamSource.Capture capture(byte[] raw) {
        return PrintedBeamSource.capture(
                score(0),
                new byte[raw.length],
                raw,
                120,
                128,
                List.of(new PlayingTechniqueDetector.Staff(40, 88, 12, 0, 1)),
                0,
                8_000_000,
                256_000_000);
    }

    @Test
    public void paleShaftCellMustNotCreateAPreferredTruncatedStrictCore() {
        byte[] raw = raw(1, true, true, false);
        raw[50 * 120 + 27] = (byte) 185;
        var c = capture(raw);
        assertEquals(1, c.source().groups().size());
        assertEquals(0, c.diagnostics().get(0).get("rawStrongShafts"));
        assertEquals(1, c.diagnostics().get(0).get("rawFaintShafts"));
    }

    @Test
    public void prefixNeedsRealBeamContactAndCannotBorrowDistantRail() {
        assertEquals(1, capture(raw(1, true, true, false)).source().groups().size());
        for (int gapRow : new int[] {30, 34, 40, 60}) {
            byte[] raw = raw(1, true, true, false);
            raw[gapRow * 120 + 27] = (byte) 255;
            assertTrue("gap at" + gapRow, capture(raw).source().groups().isEmpty());
        }
        assertTrue(capture(raw(1, true, false, false)).source().groups().isEmpty());
        assertTrue(capture(raw(1, true, true, true)).source().groups().isEmpty());
    }

    @Test
    public void exactPaymentIdentityAndRefusalRemainEnforced() {
        byte[] raw = scene(true, false, false);
        Object source = new Object();
        long[] cost = {0};
        var frame = new PrintedBeamShaftPixels.Frame(source, raw, W, H, 0, n -> cost[0] += n, true);
        assertEquals(
                1,
                PrintedBeamShafts.atAll(
                                raw,
                                W,
                                H,
                                HX,
                                HY,
                                G,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame)
                        .size());
        long[] remaining = {cost[0]};
        var paid =
                new PrintedBeamShaftPixels.Frame(
                        source,
                        raw,
                        W,
                        H,
                        0,
                        n -> {
                            if (n < 0 || n > remaining[0])
                                throw new IllegalStateException("refused");
                            remaining[0] -= n;
                        },
                        true);
        assertEquals(
                1,
                PrintedBeamShafts.atAll(
                                raw, W, H, HX, HY, G, new PrintedBeamShafts.Budget(8_000_000), paid)
                        .size());
        assertEquals(0, remaining[0]);
        long[] under = {cost[0] - 1};
        try {
            var f =
                    new PrintedBeamShaftPixels.Frame(
                            source,
                            raw,
                            W,
                            H,
                            0,
                            n -> {
                                if (n < 0 || n > under[0])
                                    throw new IllegalStateException("refused");
                                under[0] -= n;
                            },
                            true);
            PrintedBeamShafts.atAll(
                    raw, W, H, HX, HY, G, new PrintedBeamShafts.Budget(8_000_000), f);
            fail("one under");
        } catch (IllegalStateException expected) {
            assertEquals("refused", expected.getMessage());
        }
        assertTrue(
                PrintedBeamShafts.atAll(
                                raw, W, H, HX, HY, G, new PrintedBeamShafts.Budget(0), frame)
                        .isEmpty());
        try {
            PrintedBeamShafts.atAll(
                    raw.clone(), W, H, HX, HY, G, new PrintedBeamShafts.Budget(8_000_000), frame);
            fail("foreign raster");
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
        }
    }
}
