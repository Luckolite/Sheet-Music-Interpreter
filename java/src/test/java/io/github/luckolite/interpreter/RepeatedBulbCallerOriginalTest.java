// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original licensed Bravura scenes, fixed before the first execution. No private score pixels. */
public class RepeatedBulbCallerOriginalTest {
    static final int W = 480, H = 260;

    static Path fontFile() {
        for (Path candidate :
                new Path[] {
                    Path.of("java/assets/Bravura.otf"),
                    Path.of("app/src/main/assets/engraving/Bravura.otf"),
                    Path.of("src/main/assets/engraving/Bravura.otf")
                }) if (Files.isRegularFile(candidate)) return candidate;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    static class Page {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final float gap;

        Page(float gap) {
            this.gap = gap;
            Arrays.fill(g, (byte) 250);
            for (int k = 0; k < 5; k++)
                for (int x = 12; x < W - 12; x++) {
                    int y = Math.round(80 + k * gap);
                    g[y * W + x] = 80;
                    l[y * W + x] = OmrMeasurePostProcessor.STAFF;
                }
        }

        void glyph(char code, int x, float top, float height, float phase) throws Exception {
            Font f = Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile()).deriveFont(100f);
            Shape shape =
                    f.createGlyphVector(
                                    new FontRenderContext(null, true, true), String.valueOf(code))
                            .getOutline();
            Rectangle2D b = shape.getBounds2D();
            double scale = height / b.getHeight();
            AffineTransform t = new AffineTransform();
            t.translate(x + phase - b.getX() * scale, top - b.getY() * scale);
            t.scale(scale, scale);
            BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D gg = im.createGraphics();
            gg.setColor(Color.WHITE);
            gg.fillRect(0, 0, W, H);
            gg.setColor(Color.BLACK);
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gg.fill(t.createTransformedShape(shape));
            gg.dispose();
            for (int y = 0; y < H; y++)
                for (int xx = 0; xx < W; xx++) {
                    int shade = 80 + im.getRaster().getSample(xx, y, 0) * 170 / 255;
                    if (shade < 250) {
                        g[y * W + xx] = (byte) Math.min(g[y * W + xx] & 255, shade);
                        if (shade < 170) l[y * W + xx] = OmrMeasurePostProcessor.SYMBOL;
                    }
                }
        }

        void head(int cx, int cy) {
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if ((x - cx) * (x - cx) / 49. + (y - cy) * (y - cy) / 16. <= 1) {
                        g[y * W + x] = 40;
                        l[y * W + x] = 2;
                    }
            for (int y = cy - 44; y <= cy; y++) {
                g[y * W + cx + 7] = 40;
                l[y * W + cx + 7] = 1;
            }
        }

        OmrScoreInterpreter.Analysis read() {
            byte[] a = g.clone(), b = l.clone();
            var p =
                    OmrScoreInterpreter.analyze(
                            l, g, W, H, List.of(new MeasureRegion(.02f, .98f, .20f, .85f)));
            assertArrayEquals(a, g);
            assertArrayEquals(b, l);
            return p;
        }
    }

    @Test
    public void originalTwoBulbFullCallerKeepsSixteenthAcrossLockedScales() throws Exception {
        for (float gap : new float[] {14, 16, 20})
            for (float phase : new float[] {0, .5f}) {
                Page p = new Page(gap);
                p.glyph('\uE4E7', 210, 80 + .85f * gap, 3 * gap, phase);
                var r = p.read().rests();
                assertEquals("gap" + gap + " phase" + phase + " " + r, 1, r.size());
                assertEquals("gap" + gap + " phase" + phase, .25, r.get(0).durationBeats(), 0);
            }
    }

    @Test
    public void trueQuarterKeepsOneBeatAcrossLockedScales() throws Exception {
        for (float gap : new float[] {14, 16, 20}) {
            Page p = new Page(gap);
            p.glyph('\uE4E5', 210, 80 + .7f * gap, 3 * gap, 0);
            var r = p.read().rests();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(1, r.get(0).durationBeats(), 0);
        }
    }

    @Test
    public void oneBulbKeepsEighth() throws Exception {
        Page p = new Page(16);
        p.glyph('\uE4E6', 210, 80 + .85f * 16, 2 * 16, 0);
        var r = p.read().rests();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void threeBulbsKeepThirtySecond() throws Exception {
        Page p = new Page(16);
        p.glyph('\uE4E8', 210, 80 + .15f * 16, 4 * 16, 0);
        var r = p.read().rests();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.125, r.get(0).durationBeats(), 0);
    }

    @Test
    public void accidentalsAndDynamicsAreNotRests() throws Exception {
        for (char code : new char[] {'\uE260', '\uE261', '\uE262', '\uE522'}) {
            Page p = new Page(16);
            p.glyph(code, 210, 90, 44, 0);
            assertTrue(Integer.toHexString(code) + p.read().rests(), p.read().rests().isEmpty());
        }
    }

    @Test
    public void distinctNearbyRestsKeepTheirOwnDuration() throws Exception {
        Page p = new Page(16);
        p.glyph('\uE4E7', 150, 93.6f, 48, 0);
        p.glyph('\uE4E6', 260, 93.6f, 32, 0);
        var r = p.read().rests();
        assertEquals(r.toString(), 2, r.size());
        assertEquals(.25, r.get(0).durationBeats(), 0);
        assertEquals(.5, r.get(1).durationBeats(), 0);
    }

    @Test
    public void nearbyRealHeadsRemainNotes() throws Exception {
        Page p = new Page(16);
        p.glyph('\uE4E7', 210, 93.6f, 48, 0);
        p.head(110, 128);
        p.head(360, 128);
        var a = p.read();
        assertEquals(a.notes().toString(), 2, a.notes().size());
        assertEquals(a.rests().toString(), 1, a.rests().size());
        assertEquals(.25, a.rests().get(0).durationBeats(), 0);
    }

    @Test
    public void croppedUpperBulbDoesNotCreateCompleteSixteenth() throws Exception {
        Page p = new Page(16);
        p.glyph('\uE4E7', 210, 93.6f, 48, 0);
        for (int y = 90; y <= 109; y++)
            for (int x = 205; x < 240; x++) {
                p.g[y * W + x] = (byte) 250;
                p.l[y * W + x] = 0;
            }
        assertTrue(p.read().rests().stream().noneMatch(r -> r.durationBeats() == .25));
    }
}
