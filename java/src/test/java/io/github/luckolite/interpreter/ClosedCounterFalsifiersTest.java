// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.*;

/** The unchanged 420 connected-head and360 quaver variants, with every staff pixel retained. */
public class ClosedCounterFalsifiersTest {
    static final int W = 400, H = 220;

    static byte[] originalEighth() {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int y = 94; y <= 104; y++)
            for (int x = 137; x <= 149; x++)
                if ((x - 143) * (x - 143) / 36.0 + (y - 99) * (y - 99) / 25.0 <= 1)
                    r[y * W + x] = 0;
        for (int y = 96; y <= 123; y++) {
            int x = 151 - (y - 96) * 10 / 27;
            r[y * W + x] = 0;
            r[y * W + x + 1] = 0;
        }
        for (int y = 40; y <= 48; y++)
            for (int x = 313; x <= 327; x++) {
                double q = (x - 320) * (x - 320) / 49.0 + (y - 44) * (y - 44) / 16.0;
                if (.4 <= q && q <= 1) r[y * W + x] = 0;
            }
        for (int y = 44; y <= 80; y++) r[y * W + 327] = 0;
        return r;
    }

    static List<ScoreRestEvent> oldDeepLowered(byte[] r) throws Exception {
        Method method =
                Arrays.stream(SixteenthRestDetector.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("inspect"))
                        .findFirst()
                        .orElseThrow();
        method.setAccessible(true);
        var out = new ArrayList<ScoreRestEvent>();
        method.invoke(
                null,
                r,
                W,
                H,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                notes(r),
                new SixteenthRestDetector.Staff(80, 138, 14.5f, 0, 1),
                80,
                138,
                new boolean[59],
                137,
                152,
                out,
                new ArrayList<SixteenthRestDetector.RestDot>(),
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                109f / H,
                false);
        return out;
    }

    static List<ScoreNoteEvent> notes(byte[] r) {
        var note =
                new ScoreNoteEvent(
                        0,
                        .8f,
                        0,
                        0,
                        1,
                        44f / H,
                        true,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_NATURAL,
                        2f);
        var runs = new ArrayList<int[]>();
        for (int y = 40; y <= 48; y++)
            for (int x = 313; x <= 327; x++) {
                double q = (x - 320) * (x - 320) / 49.0 + (y - 44) * (y - 44) / 16.0;
                if (.4 <= q && q <= 1) runs.add(new int[] {y, x, x});
            }
        var origin = RestFigureOrigins.HeadOrigin.accepted(new Object(), r, W, H, runs);
        return RestFigureOrigins.bind(List.of(note), Map.of(note, origin));
    }

    static boolean proof(byte[] r, int l, int rr, int t, int b) {
        var payer = new ClosedCounterRestInk.Budget(10_000_000, 1_000_000);
        var n = notes(r);
        var frame = RestFigureOrigins.originalFrame(n, r, W, H, payer);
        var p =
                new RestFigureOrigins.ProposalOrigin(
                        r, r, W, H, 80, 138, new boolean[59], l, rr, t, b);
        return ClosedCounterRestInk.owns(frame, n, r, W, H, p, l, rr, t, b, 14.5f, payer);
    }

    static void rules(byte[] r) {
        for (int y : new int[] {10, 25, 39, 54, 68})
            for (int x = 10; x < 390; x++) r[y * W + x] = 0;
    }

    @Test
    public void all420ActualInkHeadContactsRemainUnknown() {
        int count = 0;
        for (int cy = 94; cy < 114; cy++)
            for (int cx = 145; cx < 166; cx++) {
                byte[] r = originalEighth();
                rules(r);
                for (int y = cy - 4; y <= cy + 4; y++)
                    for (int x = cx - 7; x <= cx + 7; x++) {
                        double q = (x - cx) * (x - cx) / 49.0 + (y - cy) * (y - cy) / 16.0;
                        if (.4 <= q && q <= 1) r[y * W + x] = 0;
                    }
                assertFalse("head contact " + cx + "," + cy, proof(r, 137, 152, 94, 123));
                count++;
            }
        assertEquals(420, count);
    }

    @Test
    public void all360FilledBulbAndSingleShaftVariantsRemainUnknown() {
        int count = 0;
        for (int sx = 150; sx < 154; sx++)
            for (int stroke = 1; stroke <= 3; stroke++)
                for (int ex = 139; ex < 145; ex++)
                    for (int sy = 94; sy < 99; sy++) {
                        byte[] r = originalEighth();
                        for (int y = 90; y < 130; y++)
                            for (int x = 125; x < 175; x++) r[y * W + x] = (byte) 255;
                        rules(r);
                        for (int y = 94; y <= 104; y++)
                            for (int x = 137; x <= 149; x++)
                                if ((x - 143) * (x - 143) / 36.0 + (y - 99) * (y - 99) / 25.0 <= 1)
                                    r[y * W + x] = 0;
                        for (int y = sy; y <= 123; y++) {
                            int x = sx - (y - sy) * (sx - ex) / (123 - sy);
                            for (int dx = 0; dx < stroke; dx++) r[y * W + x + dx] = 0;
                        }
                        int l = W, rr = -1, t = H, b = -1;
                        for (int y = 90; y < 130; y++)
                            for (int x = 125; x < 175; x++)
                                if ((r[y * W + x] & 255) < 170) {
                                    l = Math.min(l, x);
                                    rr = Math.max(rr, x);
                                    t = Math.min(t, y);
                                    b = Math.max(b, y);
                                }
                        assertFalse(
                                "quaver " + sx + "," + stroke + "," + ex + "," + sy,
                                proof(r, l, rr, t, b));
                        count++;
                    }
        assertEquals(360, count);
    }

    @Test
    public void genuineDeepLoweredEntryAndConnectedOpenheadKeepTheirRest() throws Exception {
        for (boolean connected : new boolean[] {false, true}) {
            byte[] r = originalEighth();
            if (connected) {
                int cx = 158, cy = 103;
                for (int y = cy - 4; y <= cy + 4; y++)
                    for (int x = cx - 7; x <= cx + 7; x++) {
                        double q = (x - cx) * (x - cx) / 49.0 + (y - cy) * (y - cy) / 16.0;
                        if (.4 <= q && q <= 1) r[y * W + x] = 0;
                    }
                for (int x = 149; x <= 153; x++) r[102 * W + x] = 0;
            }
            // leaf() invokes the real old entry with eighth geometry, lowered=true,
            // deepLowered=true and independently held-voice eligibility.
            var old = oldDeepLowered(r);
            assertEquals(1, old.size());
            assertEquals(.5f, old.get(0).durationBeats(), 0);
            assertFalse(proof(r, 137, 152, 94, 123));
        }
    }
}
