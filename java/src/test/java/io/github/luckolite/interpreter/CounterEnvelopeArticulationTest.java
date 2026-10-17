// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original boundary/transform controls; explicit admission isolates optical ownership only. */
public class CounterEnvelopeArticulationTest {
    static final int W = 1000, H = 1000;
    static final float G = 12;

    static List<PlayingTechniqueDetector.Word> word(float l, float t, float r, float b) {
        return List.of(
                new PlayingTechniqueDetector.Word(
                        "flowing",
                        l,
                        t,
                        r,
                        b,
                        PlayingTechniqueDetector.WordAdmission.CONTEXT_QUALIFIED));
    }

    static int mark(
            byte[][] s,
            int w,
            int h,
            float x,
            float y,
            float gap,
            List<PlayingTechniqueDetector.Word> words,
            boolean filtered) {
        var a = List.of(new NoteArticulationDetector.Anchor(x, y, gap, 0));
        return filtered
                ? NoteArticulationDetector.detectWithWordBodies(
                        s[1],
                        s[0],
                        w,
                        h,
                        a,
                        ExpressiveWordBodyInk.detect(words, s[1], s[0], w, h, a))[0]
                : NoteArticulationDetector.detect(s[1], s[0], w, h, a)[0];
    }

    static void put(byte[][] s, int x, int y, int v) {
        s[0][y * W + x] = (byte) v;
        s[1][y * W + x] = 5;
    }

    @Test
    public void unclassifiedEnglishCannotOwnWordInk() {
        var s = ConnectedArticulationTest.scene(true, false);
        assertTrue(
                ExpressiveWordBodyInk.detect(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "flowing", .149f, .111f, .265f, .163f)),
                                s[1],
                                s[0],
                                W,
                                H,
                                List.of(new NoteArticulationDetector.Anchor(211, 179, G, 0)))
                        .isEmpty());
    }

    @Test
    public void tangentStrongTenutoDoesNotBecomeALetter() {
        var s = ConnectedArticulationTest.scene(false, false);
        for (int y = 143; y <= 160; y++)
            for (int x = 205; x <= 217; x++) {
                s[0][y * W + x] = (byte) 205;
                s[1][y * W + x] = 0;
            }
        for (int y = 143; y <= 145; y++) put(s, 211, y, 155);
        for (int y = 144; y <= 145; y++) for (int x = 205; x <= 217; x++) put(s, x, y, 35);
        assertEquals(
                NoteArticulation.TENUTO,
                mark(s, W, H, 211, 179, G, word(.149f, .111f, .265f, .163f), false));
        assertEquals(
                NoteArticulation.TENUTO,
                mark(s, W, H, 211, 179, G, word(.149f, .111f, .265f, .163f), true));
    }

    @Test
    public void connectedMarkInsideTextHeightButOutsideLetterBodiesStaysIndependent() {
        var s = ConnectedArticulationTest.scene(false, false);
        for (int y = 143; y <= 160; y++)
            for (int x = 205; x <= 217; x++) {
                s[0][y * W + x] = (byte) 205;
                s[1][y * W + x] = 0;
            }
        for (int x = 250; x <= 260; x++) put(s, x, 132, 155);
        for (int y = 130; y <= 134; y++) for (int x = 258; x <= 262; x++) put(s, x, y, 35);
        assertEquals(
                NoteArticulation.STACCATO,
                mark(s, W, H, 260, 158, G, word(.149f, .111f, .265f, .163f), false));
        assertEquals(
                NoteArticulation.STACCATO,
                mark(s, W, H, 260, 158, G, word(.149f, .111f, .265f, .163f), true));
    }

    @Test
    public void noCounterCombDoesNotOwnInk() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 205);
        var l = new byte[W * H];
        var s = new byte[][] {g, l};
        for (int x = 160; x <= 251; x++) for (int y = 140; y <= 142; y++) put(s, x, y, 155);
        for (int x = 162; x <= 246; x += 14)
            for (int y = 121; y <= 142; y++) for (int xx = x; xx <= x + 2; xx++) put(s, xx, y, 155);
        assertTrue(
                ExpressiveWordBodyInk.detect(
                                word(.149f, .111f, .265f, .163f),
                                l,
                                g,
                                W,
                                H,
                                List.of(new NoteArticulationDetector.Anchor(211, 179, G, 0)))
                        .isEmpty());
    }

    @Test
    public void translatedConnectedDotStaysIndependent() {
        var s = ConnectedArticulationTest.scene(true, false);
        byte[][] d = {new byte[W * H], new byte[W * H]};
        Arrays.fill(d[0], (byte) 205);
        for (int y = 0; y < H - 30; y++)
            for (int x = 0; x < W - 50; x++) {
                d[0][(y + 30) * W + x + 50] = s[0][y * W + x];
                d[1][(y + 30) * W + x + 50] = s[1][y * W + x];
            }
        var words = word(.199f, .141f, .315f, .193f);
        assertEquals(NoteArticulation.STACCATO, mark(d, W, H, 261, 209, G, words, true));
    }

    @Test
    public void mirroredConnectedDotStaysIndependent() {
        var s = ConnectedArticulationTest.scene(true, false);
        byte[][] d = {new byte[W * H], new byte[W * H]};
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                d[0][(H - 1 - y) * W + x] = s[0][y * W + x];
                d[1][(H - 1 - y) * W + x] = s[1][y * W + x];
            }
        var words = word(.149f, .836f, .265f, .888f);
        assertEquals(NoteArticulation.STACCATO, mark(d, W, H, 211, 820, G, words, false));
        assertEquals(NoteArticulation.STACCATO, mark(d, W, H, 211, 820, G, words, true));
    }

    @Test
    public void twiceScaledConnectedDotStaysIndependent() {
        var s = ConnectedArticulationTest.scene(true, false);
        int w = W * 2, h = H * 2;
        byte[][] d = {new byte[w * h], new byte[w * h]};
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                d[0][y * w + x] = s[0][(y / 2) * W + x / 2];
                d[1][y * w + x] = s[1][(y / 2) * W + x / 2];
            }
        var words = word(.149f, .111f, .265f, .163f);
        assertEquals(NoteArticulation.STACCATO, mark(d, w, h, 422, 358, G * 2, words, false));
        assertEquals(NoteArticulation.STACCATO, mark(d, w, h, 422, 358, G * 2, words, true));
    }
}
