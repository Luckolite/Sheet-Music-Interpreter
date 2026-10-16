// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-voice layout rendered using the bundled SIL-OFL Bravura glyphs. */
public class CrossedWholeHeadTest {
    static final int W = 400, H = 240;

    static class Page {
        byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
            for (int y = 80; y <= 144; y += 16) rect(20, y, 360, 1, 4);
        }

        void rect(int x, int y, int w, int h, int tag) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    gray[yy * W + xx] = 0;
                    labels[yy * W + xx] = (byte) tag;
                }
        }

        int[] glyph(int code, int cx, int cy) throws Exception {
            var f =
                    Font.createFont(Font.TRUETYPE_FONT, WholeHeadPocketEvidenceTest.fontFile())
                            .deriveFont(64f);
            var im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
            var g = im.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setColor(Color.BLACK);
            g.setFont(f);
            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var b =
                    f.createGlyphVector(g.getFontRenderContext(), Character.toString((char) code))
                            .getPixelBounds(null, 0, 0);
            g.drawString(
                    Character.toString((char) code),
                    cx - b.width / 2 - b.x,
                    cy - b.height / 2 - b.y);
            g.dispose();
            int l = W, r = 0, t = H, bottom = 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int v = im.getRaster().getSample(x, y, 0);
                    if (v < 200) {
                        gray[y * W + x] = (byte) Math.min(gray[y * W + x] & 255, v);
                        labels[y * W + x] = 2;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y);
                        bottom = Math.max(bottom, y);
                    }
                }
            return new int[] {l, t, r, bottom};
        }

        java.util.List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    labels,
                    gray,
                    W,
                    H,
                    java.util.List.of(new MeasureRegion(.05f, .95f, .08f, .92f)));
        }
    }

    static Page page(boolean whole) throws Exception {
        Page p = new Page();
        int[] head = p.glyph(whole ? 0xe0a2 : 0xe0a3, 120, 72);
        int stem = head[2] - 1;
        p.glyph(0xe0a4, stem - 10, 120);
        p.glyph(0xe0a4, 210, 120);
        p.rect(stem, 35, 2, 86, 1);
        p.rect(220, 35, 2, 86, 1);
        p.rect(stem, 35, 222 - stem, 5, 5);
        return p;
    }

    @Test
    public void wholeCrossedByAnotherVoiceRetainsFourBeats() throws Exception {
        var n = page(true).notes();
        var high = n.stream().max(Comparator.comparingInt(ScoreNoteEvent::staffStep)).orElseThrow();
        assertEquals(n.toString(), 4, high.unbeamedDurationBeats(), .001);
    }

    @Test
    public void genuineHalfStillRetainsTwoBeats() throws Exception {
        var n = page(false).notes();
        var high = n.stream().max(Comparator.comparingInt(ScoreNoteEvent::staffStep)).orElseThrow();
        assertEquals(n.toString(), 2, high.unbeamedDurationBeats(), .001);
    }

    @Test
    public void companionVoiceKeepsTwoEighths() throws Exception {
        var n = page(true).notes();
        assertEquals(n.toString(), 2, n.stream().filter(x -> x.beamCount() == 1).count());
    }

    @Test
    public void inputsRemainUnchanged() throws Exception {
        var p = page(true);
        var a = p.gray.clone();
        var b = p.labels.clone();
        p.notes();
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }
}
