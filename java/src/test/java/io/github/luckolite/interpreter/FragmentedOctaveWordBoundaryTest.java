// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original arrangements of licensed rendered words and procedural disconnected lettering. */
public class FragmentedOctaveWordBoundaryTest {
    static List<PlayingTechniqueDetector.Word> read(int glyph, float scale, int mode)
            throws Exception {
        var field = PrintedOctaveWordTest.class.getDeclaredField("G" + glyph);
        field.setAccessible(true);
        Object g = field.get(null);
        var pm = g.getClass().getDeclaredMethod("pixels");
        pm.setAccessible(true);
        var wm = g.getClass().getDeclaredMethod("width");
        wm.setAccessible(true);
        var hm = g.getClass().getDeclaredMethod("height");
        hm.setAccessible(true);
        byte[] ink = (byte[]) pm.invoke(g);
        int gw = (int) wm.invoke(g), gh = (int) hm.invoke(g);
        int w = Math.round(500 * scale), h = Math.round(280 * scale);
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        int top = glyph == 1 || glyph == 3 ? 200 : 40;
        for (int y = 0; y < gh; y++)
            for (int x = 0; x < gw; x++)
                fill(gray, w, 70 + x, top + y, 70 + x, top + y, scale, ink[y * gw + x]);
        int l = 70 + gw + 1;
        if ((mode & 1) != 0) {
            fill(gray, w, l + 2, top + 4, l + 10, top + 6, scale, (byte) 0);
            fill(gray, w, l, top + 13, l + 8, top + 15, scale, (byte) 0);
        }
        if ((mode & 2) != 0)
            for (int y = top + 3; y <= top + 16; y++)
                for (int x = l + 12; x <= l + 22; x++) {
                    double z = Math.pow((x - l - 17) / 5d, 2) + Math.pow((y - top - 9.5) / 6.5, 2);
                    if (z <= 1.1 && z >= .4) fill(gray, w, x, y, x, y, scale, (byte) 0);
                }
        if ((mode & 4) != 0)
            for (int x = l; x < l + 120; x += 14)
                fill(gray, w, x, top + 6, x + 6, top + 7, scale, (byte) 0);
        if ((mode & 8) != 0) {
            fill(gray, w, l + 2, top + 4, l + 10, top + 6, scale, (byte) 0);
        }
        byte[] copy = gray.clone();
        var words =
                OctaveMarkDetector.printedWords(
                        gray,
                        w,
                        h,
                        List.of(
                                new PlayingTechniqueDetector.Staff(
                                        100 * scale, 164 * scale, 16 * scale, 0, 1)));
        assertArrayEquals(copy, gray);
        return words;
    }

    static void fill(byte[] g, int w, int l, int t, int r, int b, float s, byte v) {
        for (int y = Math.round(t * s); y < Math.round((b + 1) * s); y++)
            for (int x = Math.round(l * s); x < Math.round((r + 1) * s); x++) g[y * w + x] = v;
    }

    static boolean has(List<PlayingTechniqueDetector.Word> w, int glyph) {
        String[] t = {"8va", "8vb", "15ma", "15mb"};
        return w.stream().anyMatch(z -> z.text().equals(t[glyph]));
    }

    @Test
    public void matchedPrefixInsideLongerFragmentedLetteringIsRejected() throws Exception {
        assertFalse(has(read(0, 1, 3), 0));
    }

    @Test
    public void scaledMatchedPrefixInsideLongerLetteringIsRejected() throws Exception {
        for (float s : new float[] {.75f, 1.5f}) assertFalse("scale=" + s, has(read(0, s, 3), 0));
    }

    @Test
    public void twoOctavePrefixInsideLongerLetteringIsRejected() throws Exception {
        assertFalse(has(read(2, 1, 3), 2));
    }

    @Test
    public void lowerOctavePrefixInsideLongerLetteringIsRejected() throws Exception {
        assertFalse(has(read(1, 1, 3), 1));
    }

    @Test
    public void genuineCompleteWordsRemain() throws Exception {
        for (int g = 0; g < 4; g++) assertTrue(has(read(g, 1, 0), g));
    }

    @Test
    public void dashOnlyIsNotLetterContinuation() throws Exception {
        for (int g = 0; g < 4; g++) assertTrue(has(read(g, 1, 4), g));
    }

    @Test
    public void fragmentPairWithoutFollowingLetterIsInsufficient() throws Exception {
        assertTrue(has(read(0, 1, 1), 0));
    }

    @Test
    public void singleFragmentAndLetterAreInsufficient() throws Exception {
        assertTrue(has(read(0, 1, 10), 0));
    }
}
