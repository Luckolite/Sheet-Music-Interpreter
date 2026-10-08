// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural waves on shaded paper; no score pixels or coordinates. */
public final class ShadedOrnamentRecognitionTest {
    private static final int W = 380, H = 270, GW = 25, GH = 9;

    private byte[] glyph(int paper, int ink) {
        byte[] a = new byte[GW * GH];
        Arrays.fill(a, (byte) paper);
        for (int x = 0; x < GW; x++) {
            int y = 1 + Math.abs(x % 12 - 6);
            for (int d = -1; d <= 1; d++) a[(y + d) * GW + x] = (byte) ink;
        }
        return a;
    }

    private byte[] page(int paper, int ink, int yy) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        byte[] g = glyph(paper, ink);
        for (int y = 0; y < GH; y++) System.arraycopy(g, y * GW, p, (yy + y) * W + 167, GW);
        return p;
    }

    private PortableOrnamentGlyphs matcher() {
        var r = new PortableOrnamentGlyphs();
        r.add(glyph(255, 0), GW, GH, NoteOrnament.MORDENT, false);
        return r;
    }

    private List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(new PlayingTechniqueDetector.Staff(150, 214, 16, 0, 1));
    }

    private List<PortableNoteOrnaments.Anchor> anchors() {
        return List.of(new PortableNoteOrnaments.Anchor(180, 175, 16, 0, 0));
    }

    private List<PortableNoteOrnaments.Found> detect(byte[] p) {
        return PortableNoteOrnaments.detect(matcher(), p, W, H, staffs(), anchors());
    }

    @Test
    public void darkPhotographedPaperDoesNotJoinAnIsolatedOrnamentToThePage() {
        var f = detect(page(136, 25, 112));
        assertEquals(1, f.size());
        assertEquals(NoteOrnament.MORDENT, NoteOrnament.type(f.get(0).marks()));
        assertEquals(0, f.get(0).noteIndex());
    }

    @Test
    public void brightOriginalDecisionIsPreserved() {
        var f = detect(page(255, 0, 112));
        assertEquals(1, f.size());
        assertEquals(NoteOrnament.MORDENT, NoteOrnament.type(f.get(0).marks()));
    }

    @Test
    public void isolatedShadedGlyphUsesPaperRelativeMask() {
        var r = matcher().match(glyph(140, 35), GW, new PortableNoteOrnaments.Bounds(0, 0, GW, GH));
        assertTrue(r.toString(), r.accepted());
        assertEquals(NoteOrnament.MORDENT, r.kind());
    }

    @Test
    public void blankShadedPaperStaysEmpty() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 132);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void lowContrastPaperGrainDoesNotBecomeOrnament() {
        assertTrue(detect(page(138, 126, 112)).isEmpty());
    }

    @Test
    public void belowStaffDarkGlyphCannotBecomeOrnament() {
        assertTrue(detect(page(136, 25, 238)).isEmpty());
    }

    @Test
    public void aWrittenHeadInsideGlyphBoxVetoesOrnament() {
        var p = page(136, 25, 112);
        var notes = List.of(new PortableNoteOrnaments.Anchor(180, 116, 16, 0, 0));
        assertTrue(PortableNoteOrnaments.detect(matcher(), p, W, H, staffs(), notes).isEmpty());
    }

    @Test
    public void distantGlyphNeedsANearbyMusicalOwner() {
        var p = page(136, 25, 112);
        var notes = List.of(new PortableNoteOrnaments.Anchor(320, 175, 16, 0, 0));
        assertTrue(PortableNoteOrnaments.detect(matcher(), p, W, H, staffs(), notes).isEmpty());
    }

    @Test
    public void sourceRasterIsNeverNormalizedInPlace() {
        var p = page(136, 25, 112);
        var original = p.clone();
        detect(p);
        assertArrayEquals(original, p);
    }
}
