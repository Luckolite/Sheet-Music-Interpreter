// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class RuleCrossedPaleEighthTest {
    static List<ScoreNoteEvent> notes(int fill, int mode, int transform) {
        boolean hollow = (mode & 1) != 0, broken = (mode & 2) != 0;
        int w = 320, h = 220;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 250);
        for (int y = 85; y <= 141; y += 14)
            for (int d = -2; d <= 2; d++)
                for (int x = 20; x < 300; x++) {
                    g[(y + d) * w + x] = 90;
                    l[(y + d) * w + x] = 4;
                }
        for (int cx : new int[] {100, 156}) {
            for (int dy = -8; dy <= 8; dy++)
                for (int dx = -10; dx <= 10; dx++) {
                    double z = dx * dx / 100.0 + dy * dy / 64.0;
                    if (z > 1) continue;
                    int i = (127 + dy) * w + cx + dx;
                    g[i] =
                            (byte)
                                    (z > .72
                                            ? 65
                                            : cx == 156
                                                    ? ((mode & 256) != 0 ? 250 : 65)
                                                    : hollow ? 250 : fill);
                    l[i] = 2;
                }
            for (int y = 76; y <= 127; y++)
                for (int x = cx + 9; x <= cx + 11; x++) {
                    g[y * w + x] = 65;
                    l[y * w + x] = 1;
                }
        }
        for (int x = 110; x <= 167; x++)
            for (int y = 76; y <= 84; y++) {
                if (broken && x > 129 && x < 136) continue;
                g[y * w + x] = (byte) (y == 76 || y == 84 ? 45 : 115);
                l[y * w + x] = 5;
            }
        // The printed rule crossing retains its independently measured five-pixel body.
        for (int d = -2; d <= 2; d++)
            for (int x = 20; x < 300; x++) {
                g[(85 + d) * w + x] = 90;
                l[(85 + d) * w + x] = 4;
            }
        for (int d = -2; d <= 2; d++) for (int x = 90; x <= 110; x++) g[(127 + d) * w + x] = 90;
        if ((mode & 4) != 0)
            for (int y = 70; y < 83; y++) for (int x = 113; x < 163; x++) g[y * w + x] = (byte) 250;
        if ((mode & 8) != 0)
            for (int y = 97; y <= 144; y++)
                for (int x = 20; x < 300; x++) if (Math.abs(y - 127) > 9) g[y * w + x] = (byte) 250;
        if ((mode & 16) != 0)
            for (int y = 91; y <= 115; y++)
                for (int x = 165; x <= 167; x++) g[y * w + x] = (byte) 250;
        if ((mode & 32) != 0)
            for (int y = 70; y <= 99; y++) for (int x = 110; x <= 167; x++) g[y * w + x] = 85;
        if ((mode & 64) != 0)
            for (int y = 93; y <= 100; y++)
                for (int x = 110; x <= 167; x++) {
                    g[y * w + x] = 55;
                    l[y * w + x] = 5;
                }
        if ((mode & 128) != 0) {
            for (int y = 76; y <= 126; y++)
                for (int x = 165; x <= 167; x++) {
                    g[y * w + x] = (byte) 250;
                    l[y * w + x] = 0;
                }
            for (int y = 127; y <= 181; y++)
                for (int x = 145; x <= 147; x++) {
                    g[y * w + x] = 65;
                    l[y * w + x] = 1;
                }
        }
        if ((mode & 512) != 0)
            for (int y = 93; y <= 100; y++)
                for (int x = 110; x <= 167; x++) {
                    g[y * w + x] = (byte) 230;
                    l[y * w + x] = 5;
                }
        if ((transform & 1) != 0)
            for (int i = 0; i < g.length / 2; i++) {
                int j = g.length - 1 - i;
                byte z = g[i];
                g[i] = g[j];
                g[j] = z;
                z = l[i];
                l[i] = l[j];
                l[j] = z;
            }
        if ((transform & 2) != 0) {
            byte[] gg = new byte[g.length * 4], ll = new byte[l.length * 4];
            for (int y = 0; y < h * 2; y++)
                for (int x = 0; x < w * 2; x++) {
                    gg[y * w * 2 + x] = g[y / 2 * w + x / 2];
                    ll[y * w * 2 + x] = l[y / 2 * w + x / 2];
                }
            g = gg;
            l = ll;
            w *= 2;
            h *= 2;
        }
        return OmrScoreInterpreter.extract(
                l, g, w, h, List.of(new MeasureRegion(.05f, .95f, .25f, .75f)));
    }

    static ScoreNoteEvent target(List<ScoreNoteEvent> n, int transform) {
        org.junit.Assert.assertEquals(2, n.size());
        return n.get((transform & 1) == 0 ? 0 : 1);
    }

    static void eighth(int transform) {
        var n = target(notes(175, 0, transform), transform);
        org.junit.Assert.assertEquals(1, n.beamCount());
        org.junit.Assert.assertEquals(0, n.unbeamedDurationBeats(), .001f);
    }

    static void half(int mode) {
        var n = target(notes(175, mode, 0), 0);
        org.junit.Assert.assertEquals(0, n.beamCount());
        org.junit.Assert.assertEquals(2, n.unbeamedDurationBeats(), .001f);
    }

    @org.junit.Test
    public void filledCoreKeepsOwnedEighth() {
        eighth(0);
    }

    @org.junit.Test
    public void downwardOwnedEighth() {
        eighth(1);
    }

    @org.junit.Test
    public void doubleScaleOwnedEighth() {
        eighth(2);
    }

    @org.junit.Test
    public void doubleScaleDownwardOwnedEighth() {
        eighth(3);
    }

    @org.junit.Test
    public void genuineWhiteCounterStaysHalf() {
        half(1);
    }

    @org.junit.Test
    public void brokenRailCannotOverrideCounter() {
        half(2);
    }

    @org.junit.Test
    public void printedRuleAloneCannotBecomeBeam() {
        half(4);
    }

    @org.junit.Test
    public void missingRulesPreservesExistingEighth() {
        org.junit.Assert.assertEquals(1, target(notes(175, 8, 0), 0).beamCount());
    }

    @org.junit.Test
    public void detachedCompanionCannotProveRail() {
        half(16);
    }

    @org.junit.Test
    public void broadGrayPreservesExistingEighth() {
        org.junit.Assert.assertEquals(1, target(notes(175, 32, 0), 0).beamCount());
    }

    @org.junit.Test
    public void twoActualRailsStaySixteenth() {
        var n = target(notes(175, 64, 0), 0);
        org.junit.Assert.assertEquals(2, n.beamCount());
        org.junit.Assert.assertEquals(0, n.unbeamedDurationBeats(), .001f);
    }

    @org.junit.Test
    public void opposedStemCannotProveCompanion() {
        half(128);
    }

    @org.junit.Test
    public void hollowCompanionCannotProveRhythm() {
        half(256);
    }

    @org.junit.Test
    public void retainedPaleSecondaryRailCannotBecomeEighth() {
        half(512);
    }
}
