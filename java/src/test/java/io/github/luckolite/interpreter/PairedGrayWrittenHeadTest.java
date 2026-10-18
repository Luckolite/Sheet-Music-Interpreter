// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic paired ovals: gray fill is not a paper counter. */
public final class PairedGrayWrittenHeadTest {
    static List<ScoreNoteEvent> draw(
            boolean hollow, int paper, boolean broken, boolean up, int mode) {
        int w = 440, h = 300;
        byte[] g = new byte[w * h], a = new byte[w * h];
        Arrays.fill(g, (byte) paper);
        for (int y = 90; y <= 170; y += 20)
            for (int x = 20; x < 420; x++) {
                g[y * w + x] = 45;
                a[y * w + x] = 4;
            }
        for (int cx : new int[] {150, 210}) {
            for (int dy = -10; dy <= 10; dy++)
                for (int dx = -14; dx <= 14; dx++) {
                    double z = dx * dx / 196.0 + dy * dy / 100.0;
                    if (z > 1) continue;
                    int i = (130 + dy) * w + cx + dx;
                    g[i] = (byte) (z > .68 ? 45 : hollow ? paper : 180);
                    a[i] = 2;
                }
            for (int y = 130; y <= 207; y++)
                for (int x = cx - 14; x <= cx - 12; x++) {
                    g[y * w + x] = 45;
                    a[y * w + x] = 1;
                }
        }
        for (int t :
                (mode == 3 ? new int[] {} : mode == 1 ? new int[] {187} : new int[] {187, 200}))
            for (int y = t; y < t + 8; y++)
                for (int x = (mode == 2 ? 260 : 136); x <= (mode == 2 ? 322 : 198); x++) {
                    if (broken && x >= 164 && x <= 170) continue;
                    g[y * w + x] = 45;
                    a[y * w + x] = 5;
                }
        if (up)
            for (int i = 0; i < g.length / 2; i++) {
                int j = g.length - 1 - i;
                byte t = g[i];
                g[i] = g[j];
                g[j] = t;
                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }
        byte[] gg = g.clone(), aa = a.clone();
        var notes =
                OmrScoreInterpreter.extract(
                        a, g, w, h, List.of(new MeasureRegion(.02f, .98f, .1f, .95f)));
        if (!Arrays.equals(gg, g) || !Arrays.equals(aa, a))
            throw new AssertionError("source mutation");
        return notes;
    }

    static void beamed(boolean up) {
        var n = draw(false, 250, false, up, 0);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(2, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), 0);
        }
    }

    static void retainedHalf(int paper, boolean broken, int mode) {
        var n = draw(true, paper, broken, false, mode);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(0, e.beamCount());
            assertEquals(2, e.unbeamedDurationBeats(), 0);
        }
    }

    static void unprovedPair(int mode, boolean broken) {
        var n = draw(false, 250, broken, false, mode);
        assertEquals(2, n.size());
        for (var e : n)
            assertFalse("unproved rail pair", e.beamCount() >= 2 && e.unbeamedDurationBeats() == 0);
    }

    @Test
    public void grayFilledPairKeepsDownwardBeams() {
        beamed(false);
    }

    @Test
    public void grayFilledPairKeepsUpwardBeams() {
        beamed(true);
    }

    // Contradictory connected-beam ink must not defeat direct genuine paper-counter evidence.
    @Test
    public void paperCounterRetainsSustainedPriority() {
        retainedHalf(250, false, 0);
    }

    @Test
    public void shadedCounterRetainsSustainedPriority() {
        retainedHalf(195, false, 0);
    }

    @Test
    public void brokenRailsCannotDefeatPaperCounter() {
        retainedHalf(250, true, 0);
    }

    @Test
    public void genuineHalfWithoutRailsStaysHalf() {
        retainedHalf(250, false, 3);
    }

    @Test
    public void brokenGrayPairIsNotPromoted() {
        unprovedPair(0, true);
    }

    @Test
    public void singleGrayRailIsNotPromoted() {
        unprovedPair(1, false);
    }

    @Test
    public void foreignGrayRailsAreNotPromoted() {
        unprovedPair(2, false);
    }
}
