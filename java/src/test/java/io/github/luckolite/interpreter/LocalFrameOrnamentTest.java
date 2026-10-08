// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original sloping five-rule drawings; no private score pixels or coordinates. */
public final class LocalFrameOrnamentTest {
    private static final int W = 1100, H = 430, G = 10;

    private record Page(byte[] labels, byte[] gray, int x, int glyphY, float noteY) {}

    private static byte[] glyph(int paper, int ink) {
        byte[] a = new byte[25 * 9];
        Arrays.fill(a, (byte) paper);
        for (int x = 0; x < 25; x++) {
            int y = 1 + Math.abs(x % 12 - 6);
            for (int d = -1; d <= 1; d++) a[(y + d) * 25 + x] = (byte) ink;
        }
        return a;
    }

    private static Page page(float slope, int paper, int printed, int semantic, float labelShift) {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        for (int line = 0; line < 5; line++)
            for (int x = 35; x < W - 35; x++) {
                int y = Math.round(220 + line * G + slope * (x - W * .5f));
                if (line < printed) gray[y * W + x] = 25;
                int sy = Math.round(y + labelShift);
                if (line < semantic && sy >= 0 && sy < H) labels[sy * W + x] = 4;
            }
        int x = slope < 0 ? 235 : 865;
        float localTop = 220 + slope * (x - W * .5f);
        int yy = Math.round(localTop - 25);
        byte[] a = glyph(paper, 25);
        for (int y = 0; y < 9; y++) System.arraycopy(a, y * 25, gray, (yy + y) * W + x - 12, 25);
        return new Page(labels, gray, x, yy, localTop + 16);
    }

    private static PortableOrnamentGlyphs matcher() {
        var r = new PortableOrnamentGlyphs();
        r.add(glyph(255, 0), 25, 9, NoteOrnament.MORDENT, false);
        return r;
    }

    private static List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(new PlayingTechniqueDetector.Staff(220, 260, G, 0, 1));
    }

    private static List<PortableNoteOrnaments.Found> detect(Page p, byte[] labels) {
        return PortableNoteOrnaments.detect(
                matcher(),
                labels,
                p.gray,
                W,
                H,
                staffs(),
                List.of(new PortableNoteOrnaments.Anchor(p.x, p.noteY, G, 0, 0)),
                List.of());
    }

    private static void positive(float slope, int paper) {
        var p = page(slope, paper, 5, 5, 0);
        var f = detect(p, p.labels);
        assertEquals(1, f.size());
        assertEquals(NoteOrnament.MORDENT, NoteOrnament.type(f.get(0).marks()));
        assertEquals(0, f.get(0).noteIndex());
    }

    @Test
    public void steepRisingStaffOnShadedPaperOwnsItsAboveStaffGlyph() {
        positive(-.09f, 136);
    }

    @Test
    public void steepFallingStaffOnShadedPaperOwnsItsAboveStaffGlyph() {
        positive(.09f, 136);
    }

    @Test
    public void steepRisingStaffOnBrightPaperOwnsItsAboveStaffGlyph() {
        positive(-.09f, 255);
    }

    @Test
    public void steepFallingStaffOnBrightPaperOwnsItsAboveStaffGlyph() {
        positive(.09f, 255);
    }

    @Test
    public void missingLabelsCannotExpandTheFixedStaffBand() {
        var p = page(-.09f, 136, 5, 5, 0);
        assertTrue(detect(p, null).isEmpty());
    }

    @Test
    public void emptyLabelsCannotExpandTheFixedStaffBand() {
        var p = page(.09f, 136, 5, 0, 0);
        assertTrue(detect(p, p.labels).isEmpty());
    }

    @Test
    public void mismatchedRasterLengthCannotExpandTheFixedStaffBand() {
        var p = page(-.09f, 136, 5, 5, 0);
        assertTrue(detect(p, new byte[W * H - 1]).isEmpty());
    }

    @Test
    public void shiftedSemanticRulesCannotBorrowAnotherPhysicalPhase() {
        var p = page(-.09f, 136, 5, 5, 20);
        assertTrue(detect(p, p.labels).isEmpty());
    }

    @Test
    public void fourPrintedRulesDoNotProveFiveSemanticRules() {
        var p = page(-.09f, 136, 4, 5, 0);
        assertTrue(detect(p, p.labels).isEmpty());
    }

    @Test
    public void semanticRulesWithoutPrintedInkCannotExpandOwnership() {
        var p = page(.09f, 136, 0, 5, 0);
        assertTrue(detect(p, p.labels).isEmpty());
    }

    @Test
    public void anotherStaffsVerifiedTrackCannotOwnThisGlyph() {
        var p = page(-.09f, 136, 5, 5, 0);
        var wrong = List.of(new PlayingTechniqueDetector.Staff(110, 150, G, 0, 1));
        assertTrue(
                PortableNoteOrnaments.detect(
                                matcher(),
                                p.labels,
                                p.gray,
                                W,
                                H,
                                wrong,
                                List.of(new PortableNoteOrnaments.Anchor(p.x, p.noteY, G, 0, 0)),
                                List.of())
                        .isEmpty());
    }

    @Test
    public void verifiedLocalFrameDoesNotOverrideWrittenHeadOwnership() {
        var p = page(-.09f, 136, 5, 5, 0);
        assertTrue(
                PortableNoteOrnaments.detect(
                                matcher(),
                                p.labels,
                                p.gray,
                                W,
                                H,
                                staffs(),
                                List.of(
                                        new PortableNoteOrnaments.Anchor(
                                                p.x, p.glyphY + 4, G, 0, 0)),
                                List.of())
                        .isEmpty());
    }

    @Test
    public void labelsAndGrayRemainImmutable() {
        var p = page(.09f, 136, 5, 5, 0);
        var l = p.labels.clone();
        var g = p.gray.clone();
        detect(p, p.labels);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
