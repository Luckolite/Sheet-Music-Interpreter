// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** New original arrangements of existing shareable rendered-word fixtures. */
public class PrintedOctaveShortHookTest {
    private List<PlayingTechniqueDetector.Word> read(
            int glyph, float scale, int mode, int separation) throws Exception {
        var field = PrintedOctaveWordTest.class.getDeclaredField("G" + glyph);
        field.setAccessible(true);
        Object g = field.get(null);
        var pixels = g.getClass().getDeclaredMethod("pixels");
        pixels.setAccessible(true);
        var getWidth = g.getClass().getDeclaredMethod("width");
        getWidth.setAccessible(true);
        var getHeight = g.getClass().getDeclaredMethod("height");
        getHeight.setAccessible(true);
        byte[] ink = (byte[]) pixels.invoke(g);
        int gw = (Integer) getWidth.invoke(g), gh = (Integer) getHeight.invoke(g);
        int w = Math.round(500 * scale), h = Math.round(280 * scale);
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        boolean below = glyph == 1 || glyph == 3;
        int top = below ? 200 : 40;
        for (int y = 0; y < gh; y++)
            for (int x = 0; x < gw; x++)
                fill(gray, w, 70 + x, top + y, 70 + x, top + y, scale, ink[y * gw + x]);
        int start = 70 + gw + separation;
        if (mode != 0) {
            boolean up = below ^ (mode == 2);
            int bar = top + (up ? 14 : 6), end = bar + (up ? -9 : 9);
            if (mode == 3) fill(gray, w, start, top + 6, start + 13, top + 15, scale, (byte) 0);
            else if (mode == 4)
                fill(gray, w, start + 12, top + 6, start + 13, top + 15, scale, (byte) 0);
            else {
                fill(gray, w, start, bar, start + 13, bar + 1, scale, (byte) 0);
                fill(
                        gray,
                        w,
                        start + 12,
                        Math.min(bar, end),
                        start + 13,
                        Math.max(bar, end),
                        scale,
                        (byte) 0);
            }
        }
        byte[] before = gray.clone();
        var words =
                OctaveMarkDetector.printedWords(
                        gray,
                        w,
                        h,
                        List.of(
                                new PlayingTechniqueDetector.Staff(
                                        100 * scale, 164 * scale, 16 * scale, 0, 1)));
        assertArrayEquals(before, gray);
        return words;
    }

    private void fill(byte[] gray, int w, int l, int t, int r, int b, float scale, byte color) {
        for (int y = Math.round(t * scale); y < Math.round((b + 1) * scale); y++)
            for (int x = Math.round(l * scale); x < Math.round((r + 1) * scale); x++)
                gray[y * w + x] = color;
    }

    @Test
    public void completeWordsSurviveCloseStaffFacingHooks() throws Exception {
        String[] expected = {"8va", "8vb", "15ma", "15mb"};
        for (int g = 0; g < 4; g++)
            for (float scale : new float[] {.75f, 1, 1.5f}) {
                final String text = expected[g];
                assertTrue(
                        "baseline glyph=" + g + " scale=" + scale,
                        read(g, scale, 0, 5).stream().anyMatch(w -> w.text().equals(text)));
                assertTrue(
                        "hook glyph=" + g + " scale=" + scale,
                        read(g, scale, 1, 5).stream().anyMatch(w -> w.text().equals(text)));
            }
    }

    @Test
    public void distantHooksPreserveExistingRecognition() throws Exception {
        for (int g = 0; g < 4; g++) assertFalse(read(g, 1, 1, 33).isEmpty());
    }

    @Test
    public void arbitraryNearbyInkDoesNotBecomeATerminalHook() throws Exception {
        for (int mode : new int[] {2, 3}) assertTrue("mode=" + mode, read(0, 1, mode, 5).isEmpty());
    }

    @Test
    public void aNearbyShaftPreservesTheExistingWordReading() throws Exception {
        assertTrue(read(0, 1, 4, 5).stream().anyMatch(w -> w.text().equals("8va")));
    }

    @Test
    public void bareNumbersRemainRejectedWithHooks() throws Exception {
        for (int glyph : new int[] {4, 5}) assertTrue(read(glyph, 1, 1, 5).isEmpty());
    }
}
