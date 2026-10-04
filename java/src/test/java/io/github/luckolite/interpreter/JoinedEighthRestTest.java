// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original quarter oval occluding an eighth-rest bulb, with independently attached beams. */
public class JoinedEighthRestTest {
    private List<ScoreRestEvent> read(
            int tail, boolean shaft, boolean numeral, boolean beam, boolean quarter) {
        return read(tail, shaft, numeral, beam, quarter, false);
    }

    private List<ScoreRestEvent> read(
            int tail,
            boolean shaft,
            boolean numeral,
            boolean beam,
            boolean quarter,
            boolean displaced) {
        int w = 400, h = 260;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 240);
        for (int y = 112; y <= 176; y += 16) for (int x = 20; x < 380; x++) gray[y * w + x] = 0;
        for (int y = 97; y <= 111; y++)
            for (int x = 131; x <= 149; x++)
                if ((x - 140) * (x - 140) / 81. + (y - 104) * (y - 104) / 49. <= 1)
                    gray[y * w + x] = 0;
        if (shaft)
            for (int y = 104; y <= 180; y++) for (int x = 130; x <= 132; x++) gray[y * w + x] = 0;
        if (tail != 1)
            for (int y = 100; y <= (tail == 3 ? 144 : 126); y++) {
                int x = tail == 2 ? 141 : 146 - Math.round((y - 100) * .32f);
                for (int dx = -1; dx <= 1; dx++) gray[y * w + x + dx] = 0;
            }
        for (int x : new int[] {215, 290}) {
            int cy = x == 215 ? 120 : 136;
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int dx = -8; dx <= 8; dx++)
                    if (dx * dx / 64. + (y - cy) * (y - cy) / 36. <= 1) gray[y * w + x + dx] = 0;
            for (int y = 72; y <= cy; y++)
                for (int dx = 8; dx <= 10; dx++) gray[y * w + x + dx] = 0;
        }
        if (beam)
            for (int y = 70; y <= 75; y++) for (int x = 223; x <= 300; x++) gray[y * w + x] = 0;
        if (numeral) {
            byte[] original = RestTripletTest.ink(false);
            for (int y = 0; y < 22; y++)
                for (int x = 0; x < 12; x++)
                    if ((original[(145 + y) * 400 + 119 + x] & 255) < 170)
                        gray[(40 + y) * w + 218 + x] = 0;
        }
        var owner =
                new ScoreNoteEvent(
                        0,
                        (displaced ? 146f : 140f) / w,
                        5,
                        0,
                        1,
                        104f / h,
                        false,
                        0,
                        0,
                        2,
                        quarter ? 1 : 0,
                        1);
        var a = new ScoreNoteEvent(0, 215f / w, 3, 0, 1, 120f / h, false, 0, 1, 2, 0, 1);
        var b = new ScoreNoteEvent(0, 290f / w, 1, 0, 1, 136f / h, false, 0, 1, 2, 0, 1);
        return SixteenthRestDetector.detect(
                gray,
                w,
                h,
                List.of(new MeasureRegion(0, 1, .1f, .1f + 128f / h)),
                List.of(new SixteenthRestDetector.Staff(112, 176, 16, 0, 1)),
                List.of(owner, a, b));
    }

    private boolean recovered(List<ScoreRestEvent> rests) {
        return rests.stream()
                .anyMatch(
                        r ->
                                r.durationBeats() == .5
                                        && r.positionInMeasure() > .3
                                        && r.positionInMeasure() < .4);
    }

    @Test
    public void occludedBulbWithSeparateTailAndOwnedGroupIsRecovered() {
        assertTrue(recovered(read(0, true, true, true, true)));
    }

    @Test
    public void aDisplacedDecodedQuarterColumnStillKeepsTheRest() {
        assertTrue(recovered(read(0, true, true, true, true, true)));
    }

    @Test
    public void absentTailCannotInventARest() {
        assertFalse(recovered(read(1, true, true, true, true)));
    }

    @Test
    public void verticalShaftIsNotARestTail() {
        assertFalse(recovered(read(2, true, true, true, true)));
    }

    @Test
    public void continuedDiagonalIsNotATerminatingRest() {
        assertFalse(recovered(read(3, true, true, true, true)));
    }

    @Test
    public void missingIndependentQuarterShaftCannotAuthorizeRecovery() {
        assertFalse(recovered(read(0, false, true, true, true)));
    }

    @Test
    public void missingNumeralCannotAuthorizeRecovery() {
        assertFalse(recovered(read(0, true, false, true, true)));
    }

    @Test
    public void disconnectedBeamCannotAuthorizeRecovery() {
        assertFalse(recovered(read(0, true, true, false, true)));
    }

    @Test
    public void anUnprovedQuarterValueCannotAuthorizeRecovery() {
        assertFalse(recovered(read(0, true, true, true, false)));
    }
}
