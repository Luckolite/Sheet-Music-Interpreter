// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.*;
import java.util.*;

/** Original complete outline with a pale whole lower cap, never external score pixels. */
public class PartialBowlRestOriginalTest {
    static final int W = 400, H = 240;

    static byte[] base() {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int y : new int[] {10, 20, 30, 40, 50})
            for (int x = 10; x < 390; x++) r[y * W + x] = 0;
        for (int y = 40; y <= 48; y++)
            for (int x = 313; x <= 327; x++) {
                double q = (x - 320) * (x - 320) / 49.0 + (y - 44) * (y - 44) / 16.0;
                if (.4 <= q && q <= 1) r[y * W + x] = 0;
            }
        for (int y = 44; y <= 80; y++) r[y * W + 327] = 0;
        return r;
    }

    static byte[] bowl(boolean pale) {
        byte[] r = base();
        int[] right = {145, 146, 147, 147, 146, 145};
        for (int y = 90; y <= 95; y++)
            for (int x = 140; x <= right[y - 90]; x++)
                if (!(92 <= y && y <= 93 && 143 <= x && x <= 144))
                    r[y * W + x] = (byte) (pale && y >= 94 ? 190 : 0);
        for (int y = 96; y <= 110; y++) for (int x = 140; x <= 141; x++) r[y * W + x] = 0;
        return r;
    }

    static List<ScoreRestEvent> inspect(byte[] raw, int left, int right) throws Exception {
        var held =
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
        for (int y = 40; y <= 48; y++) {
            int first = -1;
            for (int x = 313; x <= 328; x++) {
                double q = (x - 320) * (x - 320) / 49.0 + (y - 44) * (y - 44) / 16.0;
                boolean ink = x <= 327 && .4 <= q && q <= 1;
                if (ink && first < 0) first = x;
                if (!ink && first >= 0) {
                    runs.add(new int[] {y, first, x - 1});
                    first = -1;
                }
            }
        }
        var origin = RestFigureOrigins.HeadOrigin.accepted(new Object(), raw, W, H, runs);
        assertNotNull(origin);
        var notes = RestFigureOrigins.bind(List.of(held), Map.of(held, origin));
        var result = new ArrayList<ScoreRestEvent>();
        Method m =
                Arrays.stream(SixteenthRestDetector.class.getDeclaredMethods())
                        .filter(x -> x.getName().equals("inspect"))
                        .findFirst()
                        .orElseThrow();
        m.setAccessible(true);
        m.invoke(
                null,
                raw,
                W,
                H,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                notes,
                new SixteenthRestDetector.Staff(80, 120, 10, 0, 1),
                80,
                120,
                new boolean[41],
                left,
                right,
                result,
                new ArrayList<SixteenthRestDetector.RestDot>(),
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                100f / H,
                false);
        return result;
    }

    @Test
    public void paleWholeLowerCapMustNotTurnClosedBowlIntoSilence() throws Exception {
        assertTrue(inspect(bowl(true), 140, 147).isEmpty());
    }

    @Test
    public void sameCompleteDarkBowlAlreadyCreatesNoSilence() throws Exception {
        assertTrue(inspect(bowl(false), 140, 147).isEmpty());
    }

    @Test
    public void genuineFilledBulbAndDescendingTailRemainEighth() throws Exception {
        byte[] r = base();
        for (int y = 89; y <= 95; y++)
            for (int x = 139; x <= 147; x++)
                if ((x - 143) * (x - 143) / 16.0 + (y - 92) * (y - 92) / 9.0 <= 1) r[y * W + x] = 0;
        for (int y = 90; y <= 110; y++) {
            int x = 148 - (y - 90) * 8 / 20;
            r[y * W + x] = 0;
            r[y * W + x + 1] = 0;
        }
        var rests = inspect(r, 139, 149);
        assertEquals(1, rests.size());
        assertEquals(.5f, rests.get(0).durationBeats(), 0f);
    }

    @Test
    public void diagnosticInspectionPreservesEveryOriginalPixel() throws Exception {
        byte[] r = bowl(true), before = r.clone();
        inspect(r, 140, 147);
        assertArrayEquals(before, r);
    }
}
