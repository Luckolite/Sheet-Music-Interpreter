// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original line-drawn symbols exercise the shared backend without fonts or score scans. */
public final class PortableNoteOrnamentsTest {
    private static final int W = 360, H = 280, GW = 25, GH = 9;

    private byte[] glyph() {
        byte[] a = new byte[GW * GH];
        Arrays.fill(a, (byte) 255);
        for (int x = 0; x < GW; x++) {
            int y = 1 + Math.abs(x % 12 - 6);
            for (int d = -1; d <= 1; d++) a[(y + d) * GW + x] = 0;
        }
        return a;
    }

    private byte[] page(int x, int y) {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 255);
        byte[] g = glyph();
        for (int row = 0; row < GH; row++) System.arraycopy(g, row * GW, a, (y + row) * W + x, GW);
        return a;
    }

    private PortableOrnamentGlyphs recognizer() {
        var r = new PortableOrnamentGlyphs();
        r.add(glyph(), GW, GH, NoteOrnament.TURN, false);
        return r;
    }

    private List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(new PlayingTechniqueDetector.Staff(150, 214, 16, 0, 1));
    }

    private List<PortableNoteOrnaments.Found> detect(
            byte[] p, List<PortableNoteOrnaments.Anchor> notes) {
        return PortableNoteOrnaments.detect(recognizer(), p, W, H, staffs(), notes);
    }

    @Test
    public void sharedTemplateMatchesIdenticalRaster() {
        var m = recognizer().match(glyph(), GW, new PortableNoteOrnaments.Bounds(0, 0, GW, GH));
        assertEquals(NoteOrnament.TURN, m.kind());
        assertTrue(m.accepted());
    }

    @Test
    public void invalidCropIsRejected() {
        assertFalse(
                recognizer()
                        .match(glyph(), GW, new PortableNoteOrnaments.Bounds(-1, 0, 20, 13))
                        .accepted());
    }

    @Test
    public void blankRasterIsRejected() {
        byte[] p = new byte[GW * GH];
        Arrays.fill(p, (byte) 255);
        assertFalse(
                recognizer()
                        .match(p, GW, new PortableNoteOrnaments.Bounds(0, 0, GW, GH))
                        .accepted());
    }

    @Test
    public void duplicateSameKindDoesNotReduceMargin() {
        var r = recognizer();
        r.add(glyph(), GW, GH, NoteOrnament.TURN, false);
        assertTrue(r.match(glyph(), GW, new PortableNoteOrnaments.Bounds(0, 0, GW, GH)).accepted());
    }

    @Test
    public void aboveStaffOrnamentBelongsToNearestNote() {
        var f =
                detect(
                        page(168, 110),
                        List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0)));
        assertEquals(1, f.size());
        assertEquals(0, f.get(0).noteIndex());
        assertEquals(NoteOrnament.TURN, NoteOrnament.type(f.get(0).marks()));
    }

    @Test
    public void belowStaffGlyphIsNotAnOrnament() {
        assertTrue(
                detect(
                                page(168, 240),
                                List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0)))
                        .isEmpty());
    }

    @Test
    public void noStaffOwnerIsRejected() {
        assertTrue(
                detect(
                                page(168, 110),
                                List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, -1, 0)))
                        .isEmpty());
    }

    @Test
    public void delayedTurnStaysOnEarlierNote() {
        var f =
                detect(
                        page(192, 110),
                        List.of(
                                new PortableNoteOrnaments.Anchor(165, 179, 16, 0, 0),
                                new PortableNoteOrnaments.Anchor(300, 179, 16, 0, 0)));
        assertEquals(1, f.size());
        assertEquals(0, f.get(0).noteIndex());
        assertTrue((f.get(0).marks() & NoteOrnament.DELAYED) != 0);
    }

    @Test
    public void laterInterveningNoteOwnsTurn() {
        var f =
                detect(
                        page(192, 110),
                        List.of(
                                new PortableNoteOrnaments.Anchor(165, 179, 16, 0, 0),
                                new PortableNoteOrnaments.Anchor(202, 179, 16, 0, 0)));
        assertEquals(1, f.size());
        assertEquals(1, f.get(0).noteIndex());
        assertFalse((f.get(0).marks() & NoteOrnament.DELAYED) != 0);
    }

    @Test
    public void textTrillNeedsVisibleInk() {
        byte[] p = page(168, 110);
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                "tr", 168f / W, 110f / H, 193f / W, 123f / H));
        var n = List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0));
        var f =
                PortableNoteOrnaments.detect(
                        new PortableOrnamentGlyphs(), p, W, H, staffs(), n, words);
        assertEquals(1, f.size());
        assertEquals(NoteOrnament.TRILL, NoteOrnament.type(f.get(0).marks()));
        Arrays.fill(p, (byte) 255);
        assertTrue(
                PortableNoteOrnaments.detect(
                                new PortableOrnamentGlyphs(), p, W, H, staffs(), n, words)
                        .isEmpty());
    }

    @Test
    public void sourceRasterIsPreserved() {
        byte[] p = page(168, 110), before = p.clone();
        detect(p, List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0)));
        assertArrayEquals(before, p);
    }

    @Test
    public void disconnectedAuxiliarySignsFillBothSidesAndRereadMutableInk() {
        byte[] upper = new byte[12 * 16], lower = new byte[12 * 16];
        Arrays.fill(upper, (byte) 255);
        Arrays.fill(lower, (byte) 255);
        for (int y : new int[] {0, 1, 14, 15})
            for (int x = 0; x < 12; x++) {
                upper[y * 12 + x] = 0;
                if (y >= 14 || x < 6) lower[y * 12 + x] = 0;
            }
        var r = recognizer();
        r.add(upper, 12, 16, 3, true);
        r.add(lower, 12, 16, 1, true);
        byte[] raster = page(168, 110);
        for (int y = 0; y < 16; y++) {
            System.arraycopy(upper, y * 12, raster, (84 + y) * W + 174, 12);
            System.arraycopy(lower, y * 12, raster, (125 + y) * W + 174, 12);
        }
        var anchors = List.of(new PortableNoteOrnaments.Anchor(180, 179, 16, 0, 0));
        byte[] before = raster.clone();
        var both = PortableNoteOrnaments.detect(r, raster, W, H, staffs(), anchors);
        assertEquals(1, both.size());
        assertEquals(0, both.get(0).noteIndex());
        assertEquals(168, both.get(0).bounds().left);
        assertEquals(110, both.get(0).bounds().top);
        assertEquals(193, both.get(0).bounds().right);
        assertEquals(119, both.get(0).bounds().bottom);
        assertEquals(
                NoteOrnament.withAccidental(
                        NoteOrnament.withAccidental(NoteOrnament.TURN, true, 1), false, -1),
                both.get(0).marks());
        assertArrayEquals(before, raster);
        for (int y = 84; y < 100; y++) Arrays.fill(raster, y * W + 174, y * W + 186, (byte) 255);
        byte[] changed = raster.clone();
        var lowerOnly = PortableNoteOrnaments.detect(r, raster, W, H, staffs(), anchors);
        assertEquals(1, lowerOnly.size());
        assertEquals(
                NoteOrnament.withAccidental(NoteOrnament.TURN, false, -1),
                lowerOnly.get(0).marks());
        assertArrayEquals(changed, raster);
    }
}
