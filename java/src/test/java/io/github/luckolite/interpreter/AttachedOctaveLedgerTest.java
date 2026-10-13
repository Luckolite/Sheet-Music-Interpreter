// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ledger/note arrangements of the existing shareable rendered words. */
public class AttachedOctaveLedgerTest {
    private List<PlayingTechniqueDetector.Word> read(int glyph, float scale, int mode)
            throws Exception {
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
        int w = Math.round(400 * scale), h = Math.round(280 * scale);
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        boolean below = glyph == 1 || glyph == 3;
        int top = below ? 182 : 64;
        for (int y = 0; y < gh; y++)
            for (int x = 0; x < gw; x++)
                if (mode != 4 || x < 13)
                    fill(gray, w, 100 + x, top + y, 100 + x, top + y, scale, ink[y * gw + x]);
        int ledger = top + 15;
        // A preceding written head and its stem own the left side of a thin ledger.
        for (int y = ledger - 14; y <= ledger - 2; y++)
            for (int x = 64; x <= 86; x++) {
                double d = Math.pow((x - 75) / 11d, 2) + Math.pow((y - ledger + 8) / 6d, 2);
                if (d <= 1) fill(gray, w, x, y, x, y, scale, (byte) 0);
            }
        fill(gray, w, 64, below ? 169 : ledger - 8, 65, below ? ledger - 8 : 94, scale, (byte) 0);
        fill(gray, w, 62, ledger, 103, ledger + (mode == 3 ? 9 : 0), scale, (byte) 0);
        if (mode == 1 || mode == 2) {
            int bar = top + (below ? 14 : 6), end = bar + (below ? -9 : 9), start = 100 + gw + 5;
            if (mode == 2) fill(gray, w, start, top + 6, start + 13, top + 15, scale, (byte) 0);
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

    private void fill(byte[] gray, int w, int l, int t, int r, int b, float scale, byte value) {
        for (int y = Math.round(t * scale); y < Math.round((b + 1) * scale); y++)
            for (int x = Math.round(l * scale); x < Math.round((r + 1) * scale); x++)
                gray[y * w + x] = value;
    }

    @Test
    public void completeWordsSurviveThinLedgerAttachment() throws Exception {
        String[] expected = {"8va", "8vb", "15ma", "15mb"};
        for (int glyph : new int[] {0, 1, 2, 3})
            for (float scale : new float[] {.75f, 1, 1.5f}) {
                String text = expected[glyph];
                assertTrue(
                        "glyph=" + glyph + " scale=" + scale,
                        read(glyph, scale, 0).stream().anyMatch(w -> w.text().equals(text)));
            }
    }

    @Test
    public void closeProvedHooksPreserveLedgerOwnedWords() throws Exception {
        for (int glyph : new int[] {0, 1})
            assertTrue(
                    read(glyph, 1, 1).stream()
                            .anyMatch(w -> w.text().equals(glyph == 0 ? "8va" : "8vb")));
    }

    @Test
    public void arbitraryNearbyInkIsNotDiscardedAsAHook() throws Exception {
        assertTrue(read(0, 1, 2).isEmpty());
    }

    @Test
    public void aThickAttachedBlockDoesNotProveALedger() throws Exception {
        assertTrue(read(0, 1, 3).isEmpty());
    }

    @Test
    public void unrelatedBareNumbersWithLedgerInkRemainRejected() throws Exception {
        for (int glyph : new int[] {4, 5}) assertTrue(read(glyph, 1, 0).isEmpty());
    }

    @Test
    public void bareEightWithoutSuffixOrDashChainRemainsRejected() throws Exception {
        assertTrue(read(0, 1, 4).isEmpty());
    }

    @Test
    public void wordOwnsOnlyItsFalseHeadAndPreservesWrittenNeighbors() throws Exception {
        var words = read(0, 1, 0);
        assertFalse(words.isEmpty());
        assertTrue(OctaveMarkDetector.containsPrintedMark(words, 106, 79, 400, 280));
        assertFalse(OctaveMarkDetector.containsPrintedMark(words, 75, 71, 400, 280));
        assertFalse(OctaveMarkDetector.containsPrintedMark(words, 113, 100, 400, 280));
    }
}
