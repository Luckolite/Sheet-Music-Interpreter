// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original detached complete ornament and nearby curve; no score-derived pixels. */
public final class CompleteMordentBesideSlurOriginalTest {
    static final int W = 360, H = 240, GW = 35, GH = 15, L = 110, T = 110;

    static byte[] glyph(double cycles) {
        byte[] a = new byte[GW * GH];
        Arrays.fill(a, (byte) 255);
        for (int x = 0; x < GW; x++) {
            double y = 7 - 4 * Math.sin(cycles * 2 * Math.PI * x / (GW - 1));
            for (int yy = 0; yy < GH; yy++) if (Math.abs(yy - y) <= 2.5) a[yy * GW + x] = 30;
        }
        return a;
    }

    static byte[] page(boolean symbol, boolean curve, double cycles) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        if (symbol) {
            byte[] g = glyph(cycles);
            for (int y = 0; y < GH; y++) System.arraycopy(g, y * GW, p, (T + y) * W + L, GW);
        }
        if (curve)
            for (int x = L + 25; x <= L + 75; x++) {
                int y = T - 5 + Math.round(25 * (float) Math.pow((x - L - 25) / 50., 2));
                for (int dy = 0; dy < 2; dy++) p[(y + dy) * W + x] = 35;
            }
        return p;
    }

    static PortableOrnamentGlyphs recognizer() {
        var r = new PortableOrnamentGlyphs();
        r.add(glyph(2), GW, GH, NoteOrnament.MORDENT, false);
        return r;
    }

    static List<PortableNoteOrnaments.Found> read(byte[] p) {
        byte[] old = p.clone();
        var result =
                PortableNoteOrnaments.detect(
                        recognizer(),
                        p,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(150, 214, 16, 0, 1)),
                        List.of(new PortableNoteOrnaments.Anchor(127, 174, 16, 0, 0)));
        assertArrayEquals(old, p);
        return result;
    }

    @Test
    public void completeMordentSurvivesNearbyOverlappingSlurBox() {
        var found = read(page(true, true, 2));
        assertEquals(1, found.size());
        assertEquals(NoteOrnament.MORDENT, NoteOrnament.type(found.get(0).marks()));
        assertEquals(0, found.get(0).noteIndex());
    }

    @Test
    public void standaloneMordentRemainsRecognized() {
        assertEquals(1, read(page(true, false, 2)).size());
    }

    @Test
    public void slurByItselfCannotBecomeMordent() {
        assertTrue(read(page(false, true, 2)).isEmpty());
    }

    @Test
    public void oneCyclePlusSlurIsNotACompleteMordent() {
        assertTrue(read(page(true, true, 1)).isEmpty());
    }

    @Test
    public void aMissingMiddleColumnDoesNotGainCompleteGlyphAuthority() {
        byte[] p = page(true, true, 2);
        for (int y = T; y < T + GH; y++) p[y * W + L + 17] = (byte) 255;
        assertTrue(read(p).isEmpty());
    }

    @Test
    public void detachedTrLettersStillJoinForTheirOwnTemplate() {
        int w = 24, h = 16;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 1; y < 15; y++) for (int x = 2; x < 5; x++) g[y * w + x] = 0;
        for (int y = 2; y < 5; y++) for (int x = 0; x < 9; x++) g[y * w + x] = 0;
        for (int y = 4; y < 15; y++) for (int x = 12; x < 15; x++) g[y * w + x] = 0;
        for (int y = 4; y < 7; y++) for (int x = 15; x < 23; x++) g[y * w + x] = 0;
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int y = 0; y < h; y++) System.arraycopy(g, y * w, p, (110 + y) * W + 110, w);
        var r = new PortableOrnamentGlyphs();
        r.add(g, w, h, NoteOrnament.TRILL, false);
        var f =
                PortableNoteOrnaments.detect(
                        r,
                        p,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(150, 214, 16, 0, 1)),
                        List.of(new PortableNoteOrnaments.Anchor(122, 174, 16, 0, 0)));
        assertEquals(1, f.size());
        assertEquals(NoteOrnament.TRILL, NoteOrnament.type(f.get(0).marks()));
    }
}
