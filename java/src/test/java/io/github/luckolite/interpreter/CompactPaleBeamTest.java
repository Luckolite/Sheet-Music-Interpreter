// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired stems and compact filled or hollow ovals. */
public class CompactPaleBeamTest {
    List<ScoreNoteEvent> notes(boolean hollow, boolean broken) {
        return notes(hollow, broken, 0);
    }

    List<ScoreNoteEvent> notes(boolean hollow, boolean broken, int transform) {
        int w = 340, h = 260;
        byte[] g = new byte[w * h], a = new byte[w * h];
        Arrays.fill(g, (byte) 250);
        for (int y = 80; y <= 160; y += 20)
            for (int x = 20; x < 320; x++) {
                g[y * w + x] = 40;
                a[y * w + x] = 4;
            }
        for (int cx : new int[] {110, 170}) {
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -11; dx <= 11; dx++) {
                    double z = dx * dx / 121.0 + dy * dy / 36.0;
                    if (z > 1) continue;
                    int i = (110 + dy) * w + cx + dx;
                    g[i] = (byte) (z > .72 ? 65 : hollow ? 250 : 195);
                    a[i] = 2;
                }
            for (int y = 110; y <= 185; y++)
                for (int x = cx - 11; x <= cx - 9; x++) {
                    g[y * w + x] = 65;
                    a[y * w + x] = 1;
                }
        }
        for (int top : new int[] {167, 179})
            for (int y = top; y < top + 7; y++)
                for (int x = 99; x <= 161; x++) {
                    if (broken && x >= 129 && x <= 135) continue;
                    g[y * w + x] = 65;
                    a[y * w + x] = 5;
                }
        if ((transform & 1) != 0) {
            for (int i = 0; i < g.length / 2; i++) {
                int j = g.length - 1 - i;
                byte t = g[i];
                g[i] = g[j];
                g[j] = t;
                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }
        }
        if ((transform & 2) != 0) {
            byte[] gg = new byte[g.length * 4], aa = new byte[a.length * 4];
            for (int y = 0; y < h * 2; y++)
                for (int x = 0; x < w * 2; x++) {
                    gg[y * w * 2 + x] = g[(y / 2) * w + x / 2];
                    aa[y * w * 2 + x] = a[(y / 2) * w + x / 2];
                }
            g = gg;
            a = aa;
            w *= 2;
            h *= 2;
        }
        return OmrScoreInterpreter.extract(
                a, g, w, h, List.of(new MeasureRegion(.05f, .95f, .2f, .85f)));
    }

    @Test
    public void compactGrayHeadsKeepTheirTwoBeams() {
        var n = notes(false, false);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(2, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void genuineHollowHeadsStayHalf() {
        var n = notes(true, false);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(0, e.beamCount());
            assertEquals(2, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void brokenRailsCannotOverrideHollow() {
        var n = notes(true, true);
        assertEquals(2, n.size());
        for (var e : n) assertEquals(2, e.unbeamedDurationBeats(), .001f);
    }

    void beamed(int transform) {
        var n = notes(false, false, transform);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(2, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void upwardStems() {
        beamed(1);
    }

    @Test
    public void doubleScale() {
        beamed(2);
    }

    @Test
    public void doubleScaleUpward() {
        beamed(3);
    }
}
