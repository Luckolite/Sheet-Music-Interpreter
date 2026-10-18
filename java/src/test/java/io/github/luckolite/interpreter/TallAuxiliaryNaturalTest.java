// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original line-drawn symbols only; no fonts, score crops or private recordings. */
public final class TallAuxiliaryNaturalTest {
    static final int W = 360, H = 280, GW = 25, GH = 9, NW = 9;

    static byte[] white(int n) {
        byte[] a = new byte[n];
        Arrays.fill(a, (byte) 255);
        return a;
    }

    static byte[] mordent() {
        byte[] a = white(GW * GH);
        for (int x = 0; x < GW; x++) {
            int y = 1 + Math.abs(x % 12 - 6);
            for (int d = -1; d <= 1; d++) a[(y + d) * GW + x] = 0;
        }
        return a;
    }

    static byte[] natural(int h) {
        byte[] a = white(NW * h);
        for (int y = 0; y < h * 3 / 4; y++) for (int x = 0; x < 2; x++) a[y * NW + x] = 0;
        for (int y = h / 4; y < h; y++) for (int x = 7; x < 9; x++) a[y * NW + x] = 0;
        for (int x = 0; x < 9; x++)
            for (int d = 0; d < 2; d++) {
                a[(h / 3 - x / 4 + d) * NW + x] = 0;
                a[(h * 2 / 3 - x / 4 + d) * NW + x] = 0;
            }
        return a;
    }

    static void paste(byte[] p, byte[] g, int w, int x, int y) {
        for (int row = 0; row < g.length / w; row++)
            System.arraycopy(g, row * w, p, (y + row) * W + x, w);
    }

    static int marks(int h, int x, int y) throws Exception {
        byte[] p = white(W * H);
        paste(p, mordent(), GW, 168, 100);
        paste(p, natural(h), NW, x, y);
        byte[] before = p.clone();
        var recognizer = new PortableOrnamentGlyphs();
        recognizer.add(mordent(), GW, GH, NoteOrnament.MORDENT, false);
        recognizer.add(natural(h), NW, h, 2, true);
        assertTrue(
                "complete natural passes unchanged classifier",
                recognizer
                        .accidental(p, W, new PortableNoteOrnaments.Bounds(x, y, x + NW, y + h))
                        .accepted());
        var found =
                PortableNoteOrnaments.detect(
                        recognizer,
                        p,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(150, 214, 16, 0, 1)),
                        List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0)));
        assertArrayEquals("recognition preserves input pixels", before, p);
        assertEquals(1, found.size());
        assertEquals(NoteOrnament.MORDENT, NoteOrnament.type(found.get(0).marks()));
        return found.get(0).marks();
    }

    @Test
    public void tallCompleteUpperNaturalWithinInventoryIsRetained() throws Exception {
        assertEquals(0, NoteOrnament.accidental(marks(41, 176, 39), true));
    }

    @Test
    public void ordinaryShortUpperNaturalStillWorks() throws Exception {
        assertEquals(0, NoteOrnament.accidental(marks(31, 176, 49), true));
    }

    @Test
    public void foreignHorizontalNaturalIsNotBorrowed() throws Exception {
        assertEquals(
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                NoteOrnament.accidental(marks(41, 218, 39), true));
    }

    @Test
    public void overheightNaturalRemainsUnattached() throws Exception {
        assertEquals(
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                NoteOrnament.accidental(marks(47, 176, 33), true));
    }

    @Test
    public void lowerNaturalDoesNotSetUpperAccidental() throws Exception {
        assertEquals(
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                NoteOrnament.accidental(marks(31, 176, 114), true));
    }
}
